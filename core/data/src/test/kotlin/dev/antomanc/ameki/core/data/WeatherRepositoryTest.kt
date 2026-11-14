package dev.antomanc.ameki.core.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.antomanc.ameki.core.data.db.AmekiDatabase
import dev.antomanc.ameki.core.data.location.DeviceLocation
import dev.antomanc.ameki.core.data.settings.SecretCipher
import dev.antomanc.ameki.core.data.settings.SettingsRepository
import dev.antomanc.ameki.core.data.sync.SyncError
import dev.antomanc.ameki.core.model.Capability
import dev.antomanc.ameki.core.model.CurrentWeather
import dev.antomanc.ameki.core.model.DataPart
import dev.antomanc.ameki.core.model.GeoPoint
import dev.antomanc.ameki.core.model.PlaceSearchResult
import dev.antomanc.ameki.core.model.ProviderId
import dev.antomanc.ameki.core.model.WeatherCondition
import dev.antomanc.ameki.core.model.WeatherSnapshot
import dev.antomanc.ameki.provider.api.PlaceSearch
import dev.antomanc.ameki.provider.api.ProviderException
import dev.antomanc.ameki.provider.api.WeatherProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.ZoneId

private class FakeProvider(override val id: ProviderId, private val clock: () -> Instant) : WeatherProvider {
    override val capabilities = if (id == ProviderId.GOOGLE) Capability.entries.toSet() else Capability.entries.toSet() - Capability.ALERTS
    val calls = mutableListOf<Set<DataPart>>()
    var failWith: Exception? = null
    var temperature = 20.0

    override suspend fun fetch(location: GeoPoint, language: String, parts: Set<DataPart>): WeatherSnapshot {
        calls += parts
        failWith?.let { throw it }
        val now = clock()
        return WeatherSnapshot(
            provider = id,
            timeZone = ZoneId.of("Europe/Rome"),
            current = if (DataPart.CURRENT in parts) CurrentWeather(
                now, temperature, null, WeatherCondition.CLEAR, null, true,
                null, null, null, null, null, null, null, null, null,
            ) else null,
            fetchedAt = parts.associateWith { now },
        )
    }
}

private object PlainCipher : SecretCipher {
    override fun encrypt(plain: String) = plain.reversed()
    override fun decrypt(encoded: String) = encoded.reversed()
}

@RunWith(RobolectricTestRunner::class)
class WeatherRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private var now = Instant.parse("2026-11-14T10:00:00Z")
    private lateinit var db: AmekiDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var places: PlaceRepository
    private lateinit var repo: WeatherRepository
    private val google = FakeProvider(ProviderId.GOOGLE) { now }
    private val openMeteo = FakeProvider(ProviderId.OPEN_METEO) { now }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AmekiDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsRepository(
            PreferenceDataStoreFactory.create { tmp.newFile("s.preferences_pb") },
            PlainCipher,
        )
        val search = object : PlaceSearch {
            override suspend fun search(query: String, language: String, limit: Int) = emptyList<PlaceSearchResult>()
        }
        places = PlaceRepository(db.places(), search, DeviceLocation(context)) { now }
        repo = WeatherRepository(
            db.places(), db.snapshots(), settings, ProviderRegistry(listOf(google, openMeteo)), places,
        ) { now }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun addRome() = places.add(PlaceSearchResult("Roma", "Lazio", "Italia", GeoPoint(41.89, 12.48), "Europe/Rome"))

    @Test
    fun `refresh stores data and only due parts are refetched`() = runTest {
        settings.setProvider(ProviderId.GOOGLE)
        val id = addRome()

        assertThat(repo.refresh(id)).isNull()
        assertThat(google.calls.last()).containsExactlyElementsIn(DataPart.entries)
        assertThat(repo.get(id)!!.snapshot!!.current!!.temperature).isEqualTo(20.0)

        now = now.plusSeconds(10 * 60)
        repo.refresh(id)
        assertThat(google.calls).hasSize(1) // nothing due after 10 minutes

        now = now.plusSeconds(20 * 60)
        repo.refresh(id)
        assertThat(google.calls.last()).containsExactly(DataPart.CURRENT, DataPart.HOURLY)
    }

    @Test
    fun `google current data disappears after one hour, daily survives`() = runTest {
        settings.setProvider(ProviderId.GOOGLE)
        val id = addRome()
        repo.refresh(id)

        now = now.plusSeconds(61 * 60)
        val w = repo.get(id)!!
        assertThat(w.snapshot!!.current).isNull()
        assertThat(w.snapshot!!.fetchedAt.keys).containsExactly(DataPart.DAILY)

        repo.purgeExpired()
        assertThat(db.snapshots().get(id)!!.json).doesNotContain("\"current\":{")
    }

    @Test
    fun `open-meteo data stays available offline`() = runTest {
        val id = addRome()
        repo.refresh(id)
        assertThat(openMeteo.calls.single()).doesNotContain(DataPart.ALERTS)

        now = now.plusSeconds(6 * 3600)
        openMeteo.failWith = ProviderException.Network(java.io.IOException("offline"))
        assertThat(repo.refresh(id)).isEqualTo(SyncError.NETWORK)

        val w = repo.get(id)!!
        assertThat(w.error).isEqualTo(SyncError.NETWORK)
        assertThat(w.snapshot!!.current!!.temperature).isEqualTo(20.0)
        assertThat(w.snapshot!!.updatedAt).isEqualTo(Instant.parse("2026-11-14T10:00:00Z"))
    }

    @Test
    fun `switching provider hides the other provider's data`() = runTest {
        val id = addRome()
        repo.refresh(id)
        settings.setProvider(ProviderId.GOOGLE)
        assertThat(repo.get(id)!!.snapshot).isNull()

        repo.refresh(id)
        assertThat(repo.get(id)!!.snapshot!!.provider).isEqualTo(ProviderId.GOOGLE)
    }

    @Test
    fun `missing key is reported as needing user action`() = runTest {
        settings.setProvider(ProviderId.GOOGLE)
        google.failWith = ProviderException.InvalidApiKey("No API key")
        val id = addRome()
        val error = repo.refresh(id)
        assertThat(error).isEqualTo(SyncError.MISSING_KEY)
        assertThat(error!!.needsUserAction).isTrue()
    }

    @Test
    fun `background refresh covers widget places and recently opened ones`() = runTest {
        val rome = addRome()
        val milan = places.add(PlaceSearchResult("Milano", "Lombardia", "Italia", GeoPoint(45.46, 9.19), null))
        now = now.plusSeconds(10 * 86_400) // both now "stale"
        val oslo = places.add(PlaceSearchResult("Oslo", null, "Norge", GeoPoint(59.91, 10.75), null))

        val results = repo.refreshActive(widgetPlaceIds = setOf(milan))
        assertThat(results.keys).containsExactly(milan, oslo)
        assertThat(results.keys).doesNotContain(rome)
    }

    @Test
    fun `flow emits weather for all places`() = runTest {
        val id = addRome()
        repo.refresh(id)
        val list = repo.all.first()
        assertThat(list.single().place.name).isEqualTo("Roma")
        assertThat(list.single().hasData).isTrue()
    }

    @Test
    fun `api key is stored encrypted and never in the settings flow`() = runTest {
        settings.setGoogleApiKey("  AIzaSecret  ")
        assertThat(settings.googleApiKey()).isEqualTo("AIzaSecret")
        assertThat(settings.current().hasGoogleApiKey).isTrue()
        assertThat(settings.current().toString()).doesNotContain("AIzaSecret")
        settings.setGoogleApiKey(null)
        assertThat(settings.current().hasGoogleApiKey).isFalse()
    }

    @Test
    fun `google provider clamps refresh interval`() = runTest {
        settings.setRefreshInterval(180)
        assertThat(settings.current().refreshIntervalMinutes).isEqualTo(180)
        settings.setProvider(ProviderId.GOOGLE)
        assertThat(settings.current().refreshIntervalMinutes).isEqualTo(60)
    }
}
