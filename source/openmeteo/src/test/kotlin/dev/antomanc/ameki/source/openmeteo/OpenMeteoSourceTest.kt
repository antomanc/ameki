package dev.antomanc.ameki.source.openmeteo

import com.google.common.truth.Truth.assertThat
import dev.antomanc.ameki.core.model.DataPart
import dev.antomanc.ameki.core.model.GeoPoint
import dev.antomanc.ameki.core.model.SourceId
import dev.antomanc.ameki.core.model.WeatherCondition
import dev.antomanc.ameki.source.api.SourceException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class OpenMeteoSourceTest {
    private fun fixture(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    private val now = Instant.parse("2026-11-14T10:00:00Z")

    private fun client(status: HttpStatusCode = HttpStatusCode.OK, body: String) = HttpClient(MockEngine { request ->
        lastUrl = request.url.toString()
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
    })

    private var lastUrl = ""

    @Test
    fun `maps a real forecast response`() = runTest {
        val source = OpenMeteoSource(client(body = fixture("forecast_rome.json")), clock = { now })
        val s = source.fetch(GeoPoint(41.89, 12.48), "it")

        assertThat(s.source).isEqualTo(SourceId.OPEN_METEO)
        assertThat(s.timeZone).isEqualTo(ZoneId.of("Europe/Rome"))
        assertThat(s.current).isNotNull()
        assertThat(s.current!!.temperature).isEqualTo(15.3)
        assertThat(s.current!!.condition).isEqualTo(WeatherCondition.PARTLY_CLOUDY)
        assertThat(s.current!!.visibility).isEqualTo(34.4)
        assertThat(s.hourly).hasSize(48)
        assertThat(s.daily).hasSize(10)
        assertThat(s.daily.first().sunrise).isNotNull()
        assertThat(s.daily.first().moonPhase).isNotNull()
        assertThat(s.daily.first().temperatureMax).isAtLeast(s.daily.first().temperatureMin)
        assertThat(s.fetchedAt.keys).containsExactly(DataPart.CURRENT, DataPart.HOURLY, DataPart.DAILY)
        assertThat(s.alerts).isEmpty()
        assertThat(lastUrl).contains("timeformat=unixtime")
    }

    @Test
    fun `daily dates are local dates`() = runTest {
        val source = OpenMeteoSource(client(body = fixture("forecast_rome.json")), clock = { now })
        val s = source.fetch(GeoPoint(41.89, 12.48), "it")
        val dates = s.daily.map { it.date }
        assertThat(dates).isInStrictOrder()
        assertThat(dates.zipWithNext { a, b -> b.toEpochDay() - a.toEpochDay() }.toSet()).containsExactly(1L)
    }

    @Test
    fun `retains only requested parts`() = runTest {
        val source = OpenMeteoSource(client(body = fixture("forecast_rome.json")), clock = { now })
        val s = source.fetch(GeoPoint(41.89, 12.48), "it", setOf(DataPart.CURRENT))
        assertThat(s.hourly).isEmpty()
        assertThat(s.daily).isEmpty()
        assertThat(s.fetchedAt.keys).containsExactly(DataPart.CURRENT)
    }

    @Test
    fun `maps errors`() = runTest {
        val source = OpenMeteoSource(
            client(HttpStatusCode.BadRequest, """{"error":true,"reason":"Latitude must be in range"}"""),
        )
        val e = runCatching { source.fetch(GeoPoint(0.0, 0.0), "en") }.exceptionOrNull()
        assertThat(e).isInstanceOf(SourceException.Unsupported::class.java)
        assertThat(e!!.message).contains("Latitude")

        val limited = OpenMeteoSource(client(HttpStatusCode.TooManyRequests, "{}"))
        assertThat(runCatching { limited.fetch(GeoPoint(0.0, 0.0), "en") }.exceptionOrNull())
            .isInstanceOf(SourceException.QuotaExceeded::class.java)
    }

    @Test
    fun `geocoding filters out countries`() = runTest {
        val search = OpenMeteoPlaceSearch(client(body = fixture("geocoding_roma.json")))
        val results = search.search("Roma", "it-IT")
        assertThat(results).isNotEmpty()
        assertThat(results.first().name).isEqualTo("Roma")
        assertThat(results.first().region).isEqualTo("Lazio")
        assertThat(results.map { it.name }).doesNotContain("Romania")
        assertThat(lastUrl).contains("language=it")
    }

    @Test
    fun `wmo mapping`() {
        assertThat(wmoToCondition(0)).isEqualTo(WeatherCondition.CLEAR)
        assertThat(wmoToCondition(95)).isEqualTo(WeatherCondition.THUNDERSTORM)
        assertThat(wmoToCondition(null)).isEqualTo(WeatherCondition.UNKNOWN)
        assertThat(wmoToCondition(3, cloudCover = 100)).isEqualTo(WeatherCondition.CLOUDY)
    }
}

class OpenMeteoNowcastTest {
    @Test
    fun `maps minutely_15 into the nowcast part`() = kotlinx.coroutines.test.runTest {
        val body = javaClass.classLoader!!.getResource("forecast_nowcast.json")!!.readText()
        val http = io.ktor.client.HttpClient(io.ktor.client.engine.mock.MockEngine { req ->
            com.google.common.truth.Truth.assertThat(req.url.parameters["minutely_15"]).isEqualTo("precipitation")
            respond(body, io.ktor.http.HttpStatusCode.OK, io.ktor.http.headersOf(io.ktor.http.HttpHeaders.ContentType, "application/json"))
        })
        val s = OpenMeteoSource(http).fetch(dev.antomanc.ameki.core.model.GeoPoint(51.49, -0.13), "en")
        com.google.common.truth.Truth.assertThat(s.nowcast.map { it.precipitation }).containsExactly(0.0, 0.1, 0.4).inOrder()
        com.google.common.truth.Truth.assertThat(s.fetchedAt.keys).contains(dev.antomanc.ameki.core.model.DataPart.NOWCAST)
    }
}
