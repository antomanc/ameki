package dev.antomanc.ameki.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.antomanc.ameki.core.data.db.AmekiDatabase
import dev.antomanc.ameki.core.data.location.DeviceLocation
import dev.antomanc.ameki.core.model.GeoPoint
import dev.antomanc.ameki.core.model.Place
import dev.antomanc.ameki.core.model.PlaceSearchResult
import dev.antomanc.ameki.source.api.PlaceSearch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class PlaceRepositoryTest {
    private var now = Instant.parse("2026-11-14T10:00:00Z")
    private lateinit var db: AmekiDatabase
    private lateinit var places: PlaceRepository
    private var restored = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AmekiDatabase::class.java).allowMainThreadQueries().build()
        val search = object : PlaceSearch {
            override suspend fun search(query: String, language: String, limit: Int) = emptyList<PlaceSearchResult>()
        }
        places = PlaceRepository(
            db.places(), search, DeviceLocation(context),
            onRestored = { restored++ },
            clock = { now },
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun addRome() = places.add(
        PlaceSearchResult("Roma", "Lazio", "Italia", GeoPoint(41.89, 12.48), "Europe/Rome"),
    )

    @Test
    fun `renaming keeps the original city name only once`() = runTest {
        val id = addRome()
        places.rename(id, "  Casa  ")
        assertThat(places.get(id)!!.name).isEqualTo("Casa")
        assertThat(places.get(id)!!.originalName).isEqualTo("Roma")
        assertThat(places.get(id)!!.subtitle).contains("Roma")

        places.rename(id, "Ufficio")
        assertThat(places.get(id)!!.name).isEqualTo("Ufficio")
        assertThat(places.get(id)!!.originalName).isEqualTo("Roma")
    }

    @Test
    fun `a blank rename changes nothing`() = runTest {
        val id = addRome()
        places.rename(id, "   ")
        assertThat(places.get(id)!!.name).isEqualTo("Roma")
        assertThat(places.get(id)!!.originalName).isNull()
    }

    @Test
    fun `restore keeps the id and position and schedules a refresh`() = runTest {
        val id = addRome()
        val before = places.get(id)!!
        places.remove(id)
        assertThat(places.get(id)).isNull()

        places.restore(before.copy(name = "Casa", originalName = "Roma"))
        val after = places.get(id)!!
        assertThat(after.id).isEqualTo(id)
        assertThat(after.sortOrder).isEqualTo(before.sortOrder)
        assertThat(after.name).isEqualTo("Casa")
        assertThat(after.originalName).isEqualTo("Roma")
        assertThat(restored).isEqualTo(1)
    }

    @Test
    fun `restoring an undone place with the same model keeps widgets pointing at it`() = runTest {
        val id = addRome()
        val snapshot = places.get(id)!!
        places.remove(id)
        places.restore(snapshot)
        assertThat(places.all().single()).isEqualTo(Place(
            id, "Roma", "Lazio", "Italia", GeoPoint(41.89, 12.48), "Europe/Rome",
            isCurrentLocation = false, sortOrder = snapshot.sortOrder,
        ))
    }
}
