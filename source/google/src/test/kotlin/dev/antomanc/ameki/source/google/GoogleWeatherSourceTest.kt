package dev.antomanc.ameki.source.google

import com.google.common.truth.Truth.assertThat
import dev.antomanc.ameki.core.model.AlertSeverity
import dev.antomanc.ameki.core.model.DataPart
import dev.antomanc.ameki.core.model.GeoPoint
import dev.antomanc.ameki.core.model.MoonPhase
import dev.antomanc.ameki.core.model.WeatherCondition
import dev.antomanc.ameki.source.api.SourceException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList

class GoogleWeatherSourceTest {
    private fun fixture(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    private val requests = CopyOnWriteArrayList<HttpRequestData>()
    private val now = Instant.parse("2026-11-14T10:05:00Z")
    private val rome = GeoPoint(41.9, 12.5)

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun source(
        key: String? = "test-key",
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { req ->
            when {
                "currentConditions" in req.url.encodedPath -> json(fixture("current.json"))
                "hours" in req.url.encodedPath -> json(fixture("hours.json"))
                "days" in req.url.encodedPath -> json(fixture("days.json"))
                "publicAlerts" in req.url.encodedPath -> json(fixture("alerts.json"))
                else -> error("unexpected ${req.url}")
            }
        },
    ) = GoogleWeatherSource(
        http = HttpClient(MockEngine { req -> requests += req; handler(req) }),
        apiKey = { key },
        appIdentity = AndroidAppIdentity("dev.antomanc.ameki", "ab:cd:ef"),
        clock = { now },
    )

    @Test
    fun `fetches and maps all parts`() = runTest {
        val s = source().fetch(rome, "it")

        assertThat(requests).hasSize(4)
        assertThat(s.fetchedAt.keys).containsExactlyElementsIn(DataPart.entries - DataPart.NOWCAST)
        assertThat(s.timeZone.id).isEqualTo("Europe/Rome")

        val c = s.current!!
        assertThat(c.temperature).isEqualTo(15.8)
        assertThat(c.condition).isEqualTo(WeatherCondition.PARTLY_CLOUDY)
        assertThat(c.conditionText).isEqualTo("Parzialmente nuvoloso")
        assertThat(c.windDirection).isEqualTo(20)
        assertThat(c.visibility).isEqualTo(16.0)
        assertThat(c.pressure).isEqualTo(1017.6)
        assertThat(c.precipitationProbability).isEqualTo(10)

        assertThat(s.hourly).hasSize(24)
        assertThat(s.hourly[5].condition).isEqualTo(WeatherCondition.RAIN)
        assertThat(s.hourly[5].precipitation).isEqualTo(1.8)

        assertThat(s.daily).hasSize(10)
        val d0 = s.daily.first()
        assertThat(d0.date).isEqualTo(LocalDate.of(2026, 11, 14))
        assertThat(d0.temperatureMax).isEqualTo(18.0)
        assertThat(d0.moonPhase).isEqualTo(MoonPhase.WAXING_GIBBOUS)
        assertThat(d0.sunrise).isEqualTo(Instant.parse("2026-11-14T06:05:00Z"))
        assertThat(d0.precipitationProbability).isEqualTo(15) // max of day 10%, night 15%
        assertThat(d0.nightCondition).isEqualTo(WeatherCondition.MOSTLY_CLEAR)

        val alert = s.alerts.single()
        assertThat(alert.title).isEqualTo("Allerta gialla per temporali")
        assertThat(alert.severity).isEqualTo(AlertSeverity.MODERATE)
        assertThat(alert.source).isEqualTo("Dipartimento della Protezione Civile")
        assertThat(alert.instructions).hasSize(2)
    }

    @Test
    fun `sends key and app identity as headers, never in the url`() = runTest {
        source().fetch(rome, "it", setOf(DataPart.CURRENT))
        val req = requests.single()
        assertThat(req.headers["X-Goog-Api-Key"]).isEqualTo("test-key")
        assertThat(req.headers["X-Android-Package"]).isEqualTo("dev.antomanc.ameki")
        assertThat(req.headers["X-Android-Cert"]).isEqualTo("ABCDEF")
        assertThat(req.url.toString()).doesNotContain("test-key")
        assertThat(req.url.parameters["unitsSystem"]).isEqualTo("METRIC")
        assertThat(req.url.parameters["languageCode"]).isEqualTo("it")
    }

    @Test
    fun `only requested parts cost a call`() = runTest {
        val s = source().fetch(rome, "en", setOf(DataPart.CURRENT, DataPart.HOURLY))
        assertThat(requests).hasSize(2)
        assertThat(s.daily).isEmpty()
        assertThat(s.fetchedAt.keys).containsExactly(DataPart.CURRENT, DataPart.HOURLY)
    }

    @Test
    fun `follows hourly page tokens`() = runTest {
        val p = source { req ->
            if (req.url.parameters["pageToken"] == "page2token") json(fixture("hours_page2.json"))
            else json(fixture("hours_page1.json"))
        }
        val s = p.fetch(rome, "en", setOf(DataPart.HOURLY))
        assertThat(requests).hasSize(2)
        assertThat(s.hourly).hasSize(24)
        assertThat(s.hourly.map { it.time }).isInStrictOrder()
    }

    @Test
    fun `missing key fails without a call`() = runTest {
        val e = runCatching { source(key = " ").fetch(rome, "en") }.exceptionOrNull()
        assertThat(e).isInstanceOf(SourceException.InvalidApiKey::class.java)
        assertThat(requests).isEmpty()
    }

    @Test
    fun `real invalid key error maps to InvalidApiKey`() = runTest {
        val p = source { json(fixture("error_invalid_key.json"), HttpStatusCode.BadRequest) }
        val e = runCatching { p.verifyKey("bad") }.exceptionOrNull()
        assertThat(e).isInstanceOf(SourceException.InvalidApiKey::class.java)
    }

    @Test
    fun `maps other google errors`() {
        val disabled = """{"error":{"code":403,"message":"Weather API has not been used in project 1 before or it is disabled.","status":"PERMISSION_DENIED","details":[{"reason":"SERVICE_DISABLED"}]}}"""
        assertThat(GoogleWeatherSource.mapError(403, disabled)).isInstanceOf(SourceException.AccessDenied::class.java)
        val quota = """{"error":{"code":429,"message":"Quota exceeded","status":"RESOURCE_EXHAUSTED"}}"""
        assertThat(GoogleWeatherSource.mapError(429, quota)).isInstanceOf(SourceException.QuotaExceeded::class.java)
        assertThat(GoogleWeatherSource.mapError(500, "oops")).isInstanceOf(SourceException.Unknown::class.java)
    }

    @Test
    fun `condition mapping covers documented and compound types`() {
        assertThat(googleCondition("SCATTERED_THUNDERSTORMS")).isEqualTo(WeatherCondition.THUNDERSTORM)
        assertThat(googleCondition("HEAVY_SNOW_STORM")).isEqualTo(WeatherCondition.HEAVY_SNOW)
        assertThat(googleCondition("RAIN_AND_SNOW")).isEqualTo(WeatherCondition.SLEET)
        assertThat(googleCondition("CLOUDY_WITH_RAIN_LATER")).isEqualTo(WeatherCondition.MOSTLY_CLOUDY)
        assertThat(googleCondition("RAIN_ACCOMPANIED_BY_LIGHTNING")).isEqualTo(WeatherCondition.THUNDERSTORM)
        assertThat(googleCondition("SOMETHING_NEW")).isEqualTo(WeatherCondition.UNKNOWN)
    }
}
