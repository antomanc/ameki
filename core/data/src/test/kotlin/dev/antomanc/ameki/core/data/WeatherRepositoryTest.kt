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
import dev.antomanc.ameki.core.data.sync.DayHours
import dev.antomanc.ameki.core.data.sync.SyncError
import dev.antomanc.ameki.core.model.Capability
import dev.antomanc.ameki.core.model.CurrentWeather
import dev.antomanc.ameki.core.model.DataPart
import dev.antomanc.ameki.core.model.GeoPoint
import dev.antomanc.ameki.core.model.HourlyForecast
import dev.antomanc.ameki.core.model.PlaceSearchResult
import dev.antomanc.ameki.core.model.SourceId
import dev.antomanc.ameki.core.model.WeatherCondition
import dev.antomanc.ameki.core.model.WeatherSnapshot
import dev.antomanc.ameki.source.api.PlaceSearch
import dev.antomanc.ameki.source.api.SourceException
import dev.antomanc.ameki.source.api.WeatherSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

private class FakeSource(override val id: SourceId, private val clock: () -> Instant) : WeatherSource {
    override val capabilities = if (id == SourceId.GOOGLE) Capability.entries.toSet() - Capability.NOWCAST else Capability.entries.toSet() - Capability.ALERTS
    val calls = mutableListOf<Set<DataPart>>()
    val hourCalls = mutableListOf<Int>()
    var failWith: Exception? = null
    var hoursFailWith: Exception? = null
    var emptyHours = false
    var hoursGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    var temperature = 20.0

    override val fetchesHoursOnDemand = id == SourceId.GOOGLE

    private fun hours(from: Instant, count: Int) = (0 until count).map { i ->
        HourlyForecast(time = from.plusSeconds(3600L * i), temperature = temperature, condition = WeatherCondition.CLEAR, isDaytime = true,
            precipitationProbability = 0, precipitation = 0.0, windSpeed = null, windDirection = null, humidity = null, uvIndex = null)
    }

    override suspend fun fetchHours(location: GeoPoint, language: String, hours: Int): WeatherSnapshot {
        check(fetchesHoursOnDemand)
        hourCalls += hours
        hoursGate?.await()
        hoursFailWith?.let { throw it }
        val now = clock()
        return WeatherSnapshot(id, ZoneId.of("Europe/Rome"), null, hourly = if (emptyHours) emptyList() else hours(now, minOf(hours, 240)), fetchedAt = mapOf(DataPart.HOURLY to now))
    }

    override suspend fun fetch(location: GeoPoint, language: String, parts: Set<DataPart>): WeatherSnapshot {
        calls += parts
        failWith?.let { throw it }
        val now = clock()
        return WeatherSnapshot(
            source = id,
            timeZone = ZoneId.of("Europe/Rome"),
            current = if (DataPart.CURRENT in parts) CurrentWeather(
                now, temperature, null, WeatherCondition.CLEAR, null, true,
                null, null, null, null, null, null, null, null, null,
            ) else null,
            hourly = if (DataPart.HOURLY in parts) hours(now, if (id == SourceId.GOOGLE) 24 else 240) else emptyList(),
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
    private val google = FakeSource(SourceId.GOOGLE) { now }
    private val openMeteo = FakeSource(SourceId.OPEN_METEO) { now }

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
            db.places(), db.snapshots(), settings, SourceRegistry(listOf(google, openMeteo)), places,
        ) { now }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun addRome() = places.add(PlaceSearchResult("Roma", "Lazio", "Italia", GeoPoint(41.89, 12.48), "Europe/Rome"))

    @Test
    fun `refresh stores data and only due parts are refetched`() = runTest {
        settings.setSource(SourceId.GOOGLE)
        val id = addRome()

        assertThat(repo.refresh(id)).isNull()
        assertThat(google.calls.last()).containsExactlyElementsIn(DataPart.entries - DataPart.NOWCAST)
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
        settings.setSource(SourceId.GOOGLE)
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
    fun `next expiry includes the local midnight boundary`() {
        val s = WeatherSnapshot(
            SourceId.GOOGLE, ZoneId.of("Europe/Rome"), null,
            fetchedAt = mapOf(DataPart.CURRENT to now, DataPart.DAILY to now),
        )
        assertThat(s.nextChange(now)).isEqualTo(now.plusSeconds(3600))
        assertThat(s.nextChange(now.plusSeconds(2 * 3600))).isEqualTo(Instant.parse("2026-11-14T23:00:00Z"))
        assertThat(s.nextChange(now.plusSeconds(25 * 3600))).isNull()
    }

    @Test
    fun `the flow does not expose expired parts when no refresh happened`() = runTest {
        settings.setSource(SourceId.GOOGLE)
        val id = addRome()
        repo.refresh(id)

        now = now.plusSeconds(61 * 60)
        val w = repo.all.first().single()
        assertThat(w.snapshot!!.current).isNull()
        assertThat(w.snapshot!!.fetchedAt.keys).containsExactly(DataPart.DAILY)
    }

    @Test
    fun `a moved place never shows the previous coordinates' snapshot`() = runTest {
        val id = addRome()
        repo.refresh(id)
        assertThat(repo.get(id)!!.snapshot).isNotNull()

        val rome = db.places().byId(id)!!
        db.places().upsert(rome.copy(name = "Milano", latitude = 45.46, longitude = 9.19))

        val moved = repo.get(id)!!
        assertThat(moved.place.name).isEqualTo("Milano")
        assertThat(moved.snapshot).isNull()
    }

    @Test
    fun `a failed refresh after a move drops the old place's data`() = runTest {
        val id = addRome()
        repo.refresh(id)

        val rome = db.places().byId(id)!!
        db.places().upsert(rome.copy(name = "Milano", latitude = 45.46, longitude = 9.19))
        openMeteo.failWith = SourceException.Network(java.io.IOException("offline"))
        assertThat(repo.refresh(id)).isEqualTo(SyncError.NETWORK)

        assertThat(repo.get(id)!!.snapshot).isNull()
        assertThat(db.snapshots().get(id)!!.json).isEmpty()
    }

    @Test
    fun `a key whose ciphertext cannot be decrypted counts as no key`() = runTest {
        val broken = SettingsRepository(
            PreferenceDataStoreFactory.create { tmp.newFile("broken.preferences_pb") },
            object : SecretCipher {
                override fun encrypt(plain: String) = "not-really-encrypted"
                override fun decrypt(encoded: String): String? = null
            },
        )
        broken.setGoogleApiKey("secret")
        assertThat(broken.current().hasGoogleApiKey).isFalse()
        assertThat(broken.googleApiKey()).isNull()
        // The unusable value is removed, so a later read is plain "no key" too.
        assertThat(broken.current().hasGoogleApiKey).isFalse()
    }

    @Test
    fun `open-meteo data stays available offline`() = runTest {
        val id = addRome()
        repo.refresh(id)
        assertThat(openMeteo.calls.single()).doesNotContain(DataPart.ALERTS)

        now = now.plusSeconds(6 * 3600)
        openMeteo.failWith = SourceException.Network(java.io.IOException("offline"))
        assertThat(repo.refresh(id)).isEqualTo(SyncError.NETWORK)

        val w = repo.get(id)!!
        assertThat(w.error).isEqualTo(SyncError.NETWORK)
        assertThat(w.snapshot!!.current!!.temperature).isEqualTo(20.0)
        assertThat(w.snapshot!!.updatedAt).isEqualTo(Instant.parse("2026-11-14T10:00:00Z"))
    }

    @Test
    fun `switching source hides the other source's data`() = runTest {
        val id = addRome()
        repo.refresh(id)
        settings.setSource(SourceId.GOOGLE)
        assertThat(repo.get(id)!!.snapshot).isNull()

        repo.refresh(id)
        assertThat(repo.get(id)!!.snapshot!!.source).isEqualTo(SourceId.GOOGLE)
    }

    @Test
    fun `missing key is reported as needing user action`() = runTest {
        settings.setSource(SourceId.GOOGLE)
        google.failWith = SourceException.MissingApiKey()
        val id = addRome()
        val error = repo.refresh(id)
        assertThat(error).isEqualTo(SyncError.MISSING_KEY)
        assertThat(error!!.needsUserAction).isTrue()
    }

    @Test
    fun `a rejected key is not mistaken for a missing one`() = runTest {
        settings.setSource(SourceId.GOOGLE)
        google.failWith = SourceException.InvalidApiKey("No API key")
        assertThat(repo.refresh(addRome())).isEqualTo(SyncError.INVALID_KEY)
    }

    private val later = java.time.LocalDate.of(2026, 11, 17)

    @Test
    fun `a later day's hours are fetched once and survive the next refresh`() = runTest {
        settings.setSource(SourceId.GOOGLE)
        val id = addRome()
        repo.refresh(id)
        assertThat(repo.get(id)!!.dayHours(later)).isEqualTo(DayHours.NEEDED)

        repo.loadDayHours(id, later)
        assertThat(google.hourCalls).hasSize(1)
        assertThat(repo.get(id)!!.dayHours(later)).isEqualTo(DayHours.READY)

        now = now.plusSeconds(30 * 60)
        repo.refresh(id)
        assertThat(google.calls.last()).contains(DataPart.HOURLY)
        assertThat(repo.get(id)!!.dayHours(later)).isEqualTo(DayHours.READY)
        repo.loadDayHours(id, later)
        assertThat(google.hourCalls).hasSize(1)

        // Google's one-hour limit runs from the extra hours' own fetch, not from the later refresh.
        now = now.plusSeconds(31 * 60)
        val expired = repo.get(id)!!
        assertThat(expired.dayHours(later)).isEqualTo(DayHours.NEEDED)
        assertThat(expired.snapshot!!.hourly.last().time).isLessThan(now.plusSeconds(24 * 3600))
    }

    @Test
    fun `a failed day request is reported for that place only`() = runTest {
        settings.setSource(SourceId.GOOGLE)
        val rome = addRome()
        val milan = places.add(PlaceSearchResult("Milano", "Lombardia", "Italia", GeoPoint(45.46, 9.19), "Europe/Rome"))
        repo.refresh(rome)
        repo.refresh(milan)

        google.hoursFailWith = SourceException.Network(java.io.IOException("offline"))
        repo.loadDayHours(rome, later)

        val all = repo.all.first().associateBy { it.place.id }
        assertThat(all.getValue(rome).dayHours(later)).isEqualTo(DayHours.FAILED)
        assertThat(all.getValue(rome).hourRequests.failed[later]).isEqualTo(SyncError.NETWORK)
        assertThat(all.getValue(milan).dayHours(later)).isEqualTo(DayHours.NEEDED)
        // A nearer day that was never asked for is not reported as failed.
        assertThat(all.getValue(rome).dayHours(later.minusDays(1))).isEqualTo(DayHours.NEEDED)

        google.hoursFailWith = null
        repo.loadDayHours(rome, later)
        assertThat(repo.get(rome)!!.dayHours(later)).isEqualTo(DayHours.READY)
        assertThat(repo.get(rome)!!.hourRequests.failed).isEmpty()
    }

    @Test
    fun `a cancelled day request keeps the last failure and stops loading`() = runTest {
        settings.setSource(SourceId.GOOGLE)
        val id = addRome()
        repo.refresh(id)
        google.hoursFailWith = SourceException.Network(java.io.IOException("offline"))
        repo.loadDayHours(id, later)

        google.hoursGate = kotlinx.coroutines.CompletableDeferred()
        val job = launch { repo.loadDayHours(id, later) }
        // DataStore and Room run on real threads: wait for the request to reach the source.
        while (google.hourCalls.size < 2) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { kotlinx.coroutines.delay(10) }
        assertThat(repo.get(id)!!.dayHours(later)).isEqualTo(DayHours.LOADING)
        job.cancel()
        job.join()

        val w = repo.get(id)!!
        assertThat(w.hourRequests.loadingUntil).isNull()
        assertThat(w.dayHours(later)).isEqualTo(DayHours.FAILED)
    }

    @Test
    fun `an answer without hours keeps the stored ones and reports a failure`() = runTest {
        settings.setSource(SourceId.GOOGLE)
        val id = addRome()
        repo.refresh(id)
        google.emptyHours = true
        repo.loadDayHours(id, later)
        val w = repo.get(id)!!
        assertThat(w.snapshot!!.hourly).hasSize(24)
        assertThat(w.dayHours(later)).isEqualTo(DayHours.FAILED)
    }

    @Test
    fun `open-meteo days never fetch extra hours`() = runTest {
        val id = addRome()
        repo.refresh(id)
        repo.loadDayHours(id, later)
        assertThat(openMeteo.hourCalls).isEmpty()
        assertThat(repo.get(id)!!.dayHours(later)).isEqualTo(DayHours.READY)
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
    fun `widget instances resolve their place once, for drawing and for refresh`() = runTest {
        val widgets = dev.antomanc.ameki.core.data.widget.WidgetInstances(db.widgets(), repo, places, settings)
        val rome = addRome()
        val milan = places.add(PlaceSearchResult("Milano", "Lombardia", "Italia", GeoPoint(45.46, 9.19), null))
        val style = dev.antomanc.ameki.core.data.widget.WidgetStyle.DARK
        widgets.save(1, dev.antomanc.ameki.core.data.widget.WidgetConfig(null, style, 0.5f))
        widgets.save(2, dev.antomanc.ameki.core.data.widget.WidgetConfig(milan, style, 0.5f))

        assertThat(widgets.shownPlaces()).containsExactly(rome, milan)
        assertThat(widgets.load(1).weather!!.place.id).isEqualTo(rome)
        assertThat(widgets.observe(2).first().weather!!.place.id).isEqualTo(milan)
        assertThat(widgets.config(3)).isNull()

        // A deleted place is reported, never replaced by another city.
        places.remove(milan)
        assertThat(widgets.load(2).placeExists).isFalse()
        assertThat(widgets.observe(2).first().weather).isNull()
        assertThat(widgets.shownPlaces()).containsExactly(rome)
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
    fun `a source missing from this build falls back to the default`() = runTest {
        val openOnly = SettingsRepository(
            PreferenceDataStoreFactory.create { tmp.newFile("o.preferences_pb") },
            PlainCipher,
            availableSources = setOf(SourceId.OPEN_METEO),
        )
        openOnly.setSource(SourceId.GOOGLE)
        assertThat(openOnly.current().source).isEqualTo(SourceId.OPEN_METEO)
    }

    @Test
    fun `google source clamps refresh interval`() = runTest {
        settings.setRefreshInterval(180)
        assertThat(settings.current().refreshIntervalMinutes).isEqualTo(180)
        settings.setSource(SourceId.GOOGLE)
        assertThat(settings.current().refreshIntervalMinutes).isEqualTo(60)
    }
}
