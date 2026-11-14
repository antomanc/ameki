package dev.antomanc.ameki.source.google

import com.google.common.truth.Truth.assertThat
import dev.antomanc.ameki.core.model.AlertSeverity
import dev.antomanc.ameki.core.model.DataPart
import dev.antomanc.ameki.core.model.GeoPoint
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

/** Real Weather API responses, served by a mock that is as strict as Google. */
class GoogleRealResponsesTest {
    private fun real(name: String) = javaClass.classLoader!!.getResource("real/$name")!!.readText()
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val place = GeoPoint(42.21, 12.68)

    private fun source(alertsBroken: Boolean = false) = GoogleWeatherSource(
        http = HttpClient(MockEngine { req ->
            val path = req.url.encodedPath
            when {
                // Like the real API: publicAlerts has no unitsSystem field.
                "publicAlerts" in path && (alertsBroken || req.url.parameters.contains("unitsSystem")) ->
                    respond(real("error_alerts_units.json"), HttpStatusCode.BadRequest, json)
                "publicAlerts" in path -> respond(real("alerts.json"), HttpStatusCode.OK, json)
                "currentConditions" in path -> respond(real("current.json"), HttpStatusCode.OK, json)
                "hours" in path -> respond(real("hours.json"), HttpStatusCode.OK, json)
                "days" in path -> respond(real("days.json"), HttpStatusCode.OK, json)
                else -> error("unexpected $path")
            }
        }),
        apiKey = { "k" },
        clock = { Instant.parse("2026-11-14T13:00:00Z") },
    )

    @Test
    fun `real responses map into a full snapshot`() = runTest {
        val s = source().fetch(place, "it")
        assertThat(s.fetchedAt.keys).containsExactly(DataPart.CURRENT, DataPart.HOURLY, DataPart.DAILY, DataPart.ALERTS)
        assertThat(s.timeZone.id).isEqualTo("Europe/Rome")
        assertThat(s.current!!.conditionText).isEqualTo("Prevalentemente soleggiato")
        assertThat(s.hourly).hasSize(24)
        assertThat(s.daily).hasSize(10)
        assertThat(s.daily.all { it.temperatureMax >= it.temperatureMin }).isTrue()
        assertThat(s.alerts.map { it.severity }).contains(AlertSeverity.SEVERE)
        assertThat(s.alerts.first().source).isEqualTo("Servizio Meteorologico dell'Aeronautica Militare")
    }

    @Test
    fun `a failing part does not lose the others`() = runTest {
        val s = source(alertsBroken = true).fetch(place, "it")
        assertThat(s.current).isNotNull()
        assertThat(s.daily).hasSize(10)
        assertThat(s.fetchedAt.keys).doesNotContain(DataPart.ALERTS)
    }
}
