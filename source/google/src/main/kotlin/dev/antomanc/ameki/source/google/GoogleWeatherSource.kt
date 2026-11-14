package dev.antomanc.ameki.source.google

import dev.antomanc.ameki.core.model.Capability
import dev.antomanc.ameki.core.model.CurrentWeather
import dev.antomanc.ameki.core.model.DailyForecast
import dev.antomanc.ameki.core.model.DataPart
import dev.antomanc.ameki.core.model.GeoPoint
import dev.antomanc.ameki.core.model.HourlyForecast
import dev.antomanc.ameki.core.model.SourceId
import dev.antomanc.ameki.core.model.WeatherAlert
import dev.antomanc.ameki.core.model.WeatherSnapshot
import dev.antomanc.ameki.source.api.SourceException
import dev.antomanc.ameki.source.api.WeatherSource
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Identifies the calling app, so a key restricted to "Android apps" accepts the request.
 * [certSha1] is the signing certificate SHA-1 as hex, colons optional.
 */
data class AndroidAppIdentity(val packageName: String, val certSha1: String)

/**
 * Google Maps Platform Weather API. Every user brings their own key: none ships with the app.
 *
 * Calls per refresh: one per requested [DataPart]. Hourly is a single 24-hour page (the API pages
 * by 24), daily a single 10-day page.
 */
class GoogleWeatherSource(
    private val http: HttpClient,
    private val apiKey: suspend () -> String?,
    private val appIdentity: AndroidAppIdentity? = null,
    private val clock: () -> Instant = Instant::now,
    private val baseUrl: String = "https://weather.googleapis.com/v1",
) : WeatherSource {

    override val id = SourceId.GOOGLE

    override val capabilities = setOf(
        Capability.CURRENT, Capability.HOURLY, Capability.DAILY, Capability.ALERTS,
        Capability.SUN_EVENTS, Capability.MOON_PHASE, Capability.UV, Capability.VISIBILITY,
    )

    override suspend fun fetch(location: GeoPoint, language: String, parts: Set<DataPart>): WeatherSnapshot {
        val key = apiKey()?.takeIf { it.isNotBlank() } ?: throw SourceException.InvalidApiKey("No API key")
        val now = clock()
        return coroutineScope {
            // Each part is fetched on its own: one failing endpoint (say, alerts) must not
            // throw away the forecast the others returned.
            suspend fun <T> part(p: DataPart, block: suspend () -> T): Result<T>? =
                if (p !in parts) null else try {
                    Result.success(block())
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            val current = async { part(DataPart.CURRENT) { currentConditions(key, location, language) } }
            val hours = async { part(DataPart.HOURLY) { hours(key, location, language) } }
            val days = async { part(DataPart.DAILY) { days(key, location, language) } }
            val alerts = async { part(DataPart.ALERTS) { alerts(key, location, language) } }
            val results = listOf(current, hours, days, alerts).map { it.await() }

            val requested = results.filterNotNull()
            if (requested.isNotEmpty() && requested.all { it.isFailure }) {
                // Nothing came back: report the most actionable error (key or access first).
                val errors = requested.mapNotNull { it.exceptionOrNull() }
                throw errors.firstOrNull { it is SourceException.InvalidApiKey }
                    ?: errors.firstOrNull { it is SourceException.AccessDenied }
                    ?: errors.first()
            }

            val c = current.await()?.getOrNull()
            val h = hours.await()?.getOrNull()
            val d = days.await()?.getOrNull()
            val a = alerts.await()?.getOrNull()
            val zone = listOfNotNull(c?.timeZone?.id, h?.timeZone?.id, d?.timeZone?.id).firstOrNull()
                ?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneOffset.UTC

            val hourly = h?.forecastHours?.mapNotNull(::mapHour).orEmpty()
            WeatherSnapshot(
                source = SourceId.GOOGLE,
                timeZone = zone,
                current = c?.let { mapCurrent(it, now, hourly) },
                hourly = hourly,
                daily = d?.forecastDays?.mapNotNull { mapDay(it, zone) }.orEmpty(),
                alerts = a?.weatherAlerts?.mapNotNull(::mapAlert).orEmpty(),
                fetchedAt = buildMap {
                    if (c != null) put(DataPart.CURRENT, now)
                    if (h != null) put(DataPart.HOURLY, now)
                    if (d != null) put(DataPart.DAILY, now)
                    if (a != null) put(DataPart.ALERTS, now)
                },
            )
        }
    }

    /** One cheap call to check a key: succeeds or throws a [SourceException] saying what is wrong. */
    suspend fun verifyKey(key: String, location: GeoPoint = GeoPoint(41.9, 12.5)) {
        currentConditions(key, location, "en")
    }

    private suspend fun currentConditions(key: String, p: GeoPoint, lang: String) =
        get("currentConditions:lookup", key, p, lang, CurrentConditionsResponse.serializer())

    private suspend fun hours(key: String, p: GeoPoint, lang: String): HoursResponse {
        val all = mutableListOf<ForecastHourDto>()
        var token: String? = null
        var page: HoursResponse
        do {
            val t = token
            page = get("forecast/hours:lookup", key, p, lang, HoursResponse.serializer()) {
                parameter("hours", HOURS)
                parameter("pageSize", 24)
                if (t != null) parameter("pageToken", t)
            }
            all += page.forecastHours
            token = page.nextPageToken?.takeIf { it.isNotEmpty() }
        } while (token != null && all.size < HOURS)
        return page.copy(forecastHours = all.take(HOURS), nextPageToken = null)
    }

    private suspend fun days(key: String, p: GeoPoint, lang: String): DaysResponse {
        val all = mutableListOf<ForecastDayDto>()
        var token: String? = null
        var page: DaysResponse
        do {
            val t = token
            page = get("forecast/days:lookup", key, p, lang, DaysResponse.serializer()) {
                parameter("days", DAYS)
                parameter("pageSize", DAYS)
                if (t != null) parameter("pageToken", t)
            }
            all += page.forecastDays
            token = page.nextPageToken?.takeIf { it.isNotEmpty() }
        } while (token != null && all.size < DAYS)
        return page.copy(forecastDays = all, nextPageToken = null)
    }

    private suspend fun alerts(key: String, p: GeoPoint, lang: String): AlertsResponse {
        val all = mutableListOf<AlertDto>()
        var token: String? = null
        var page: AlertsResponse
        var pages = 0
        do {
            val t = token
            // publicAlerts rejects unitsSystem with HTTP 400 (it has no units).
            page = get("publicAlerts:lookup", key, p, lang, AlertsResponse.serializer(), withUnits = false) {
                if (t != null) parameter("pageToken", t)
            }
            all += page.weatherAlerts
            token = page.nextPageToken?.takeIf { it.isNotEmpty() }
        } while (token != null && ++pages < 3)
        return page.copy(weatherAlerts = all, nextPageToken = null)
    }

    private suspend fun <T> get(
        path: String,
        key: String,
        p: GeoPoint,
        lang: String,
        serializer: KSerializer<T>,
        withUnits: Boolean = true,
        extra: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): T {
        val response: HttpResponse = try {
            http.get("$baseUrl/$path") {
                // Header, not the `key` query parameter, so the key never shows up in URLs or logs.
                header("X-Goog-Api-Key", key)
                appIdentity?.let {
                    header("X-Android-Package", it.packageName)
                    header("X-Android-Cert", it.certSha1.replace(":", "").uppercase())
                }
                parameter("location.latitude", p.latitude)
                parameter("location.longitude", p.longitude)
                if (withUnits) parameter("unitsSystem", "METRIC")
                parameter("languageCode", lang)
                extra()
            }
        } catch (e: IOException) {
            throw SourceException.Network(e)
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw mapError(response.status.value, text)
        return try {
            json.decodeFromString(serializer, text)
        } catch (e: IllegalArgumentException) {
            throw SourceException.Unknown("Malformed Google response for $path", e)
        }
    }

    internal companion object {
        const val HOURS = 24
        const val DAYS = 10

        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

        fun mapError(status: Int, body: String): SourceException {
            val error = runCatching { json.decodeFromString(ErrorEnvelope.serializer(), body).error }.getOrNull()
            val reasons = error?.details?.mapNotNull { it.reason }.orEmpty()
            val message = error?.message ?: "HTTP $status"
            return when {
                "API_KEY_INVALID" in reasons || "API_KEY_EXPIRED" in reasons -> SourceException.InvalidApiKey(message)
                status == 429 || error?.status == "RESOURCE_EXHAUSTED" -> SourceException.QuotaExceeded(message)
                status == 403 || status == 401 || error?.status == "PERMISSION_DENIED" -> SourceException.AccessDenied(message)
                status == 404 || (status == 400 && "location" in message.lowercase()) -> SourceException.Unsupported(message)
                status == 400 && "key" in message.lowercase() -> SourceException.InvalidApiKey(message)
                else -> SourceException.Unknown("Google HTTP $status: $message")
            }
        }

        private fun instant(s: String?): Instant? = s?.let { runCatching { Instant.parse(it) }.getOrNull() }

        fun mapCurrent(c: CurrentConditionsResponse, now: Instant, hourly: List<HourlyForecast>): CurrentWeather? {
            val temp = c.temperature?.degrees ?: return null
            return CurrentWeather(
                time = instant(c.currentTime) ?: now,
                temperature = temp,
                feelsLike = c.feelsLikeTemperature?.degrees,
                condition = googleCondition(c.weatherCondition?.type),
                conditionText = c.weatherCondition?.description?.text,
                isDaytime = c.isDaytime,
                humidity = c.relativeHumidity,
                dewPoint = c.dewPoint?.degrees,
                uvIndex = c.uvIndex?.toDouble(),
                pressure = c.airPressure?.meanSeaLevelMillibars,
                windSpeed = c.wind?.speed?.value,
                windGust = c.wind?.gust?.value,
                windDirection = c.wind?.direction?.degrees,
                visibility = c.visibility?.distance,
                cloudCover = c.cloudCover,
                precipitationProbability = c.precipitation?.probability?.percent
                    ?: hourly.firstOrNull()?.precipitationProbability,
            )
        }

        fun mapHour(h: ForecastHourDto): HourlyForecast? {
            val time = instant(h.interval?.startTime) ?: return null
            val temp = h.temperature?.degrees ?: return null
            return HourlyForecast(
                time = time,
                temperature = temp,
                feelsLike = h.feelsLikeTemperature?.degrees,
                condition = googleCondition(h.weatherCondition?.type),
                isDaytime = h.isDaytime,
                precipitationProbability = h.precipitation?.probability?.percent,
                precipitation = h.precipitation?.qpf?.quantity,
                windSpeed = h.wind?.speed?.value,
                windGust = h.wind?.gust?.value,
                windDirection = h.wind?.direction?.degrees,
                humidity = h.relativeHumidity,
                dewPoint = h.dewPoint?.degrees,
                uvIndex = h.uvIndex?.toDouble(),
            )
        }

        fun mapDay(d: ForecastDayDto, zone: ZoneId): DailyForecast? {
            val max = d.maxTemperature?.degrees ?: return null
            val min = d.minTemperature?.degrees ?: return null
            val date = d.displayDate?.takeIf { it.year > 0 && it.month > 0 && it.day > 0 }
                ?.let { LocalDate.of(it.year, it.month, it.day) }
                ?: instant(d.interval?.startTime)?.atZone(zone)?.toLocalDate()
                ?: return null
            val day = d.daytimeForecast ?: d.fullDayForecast
            val night = d.nighttimeForecast
            val precipProb = listOfNotNull(
                day?.precipitation?.probability?.percent,
                night?.precipitation?.probability?.percent,
            ).maxOrNull()
            val qpf = listOfNotNull(day?.precipitation?.qpf?.quantity, night?.precipitation?.qpf?.quantity)
                .takeIf { it.isNotEmpty() }?.sum()
            return DailyForecast(
                date = date,
                temperatureMax = max,
                temperatureMin = min,
                condition = googleCondition(day?.weatherCondition?.type),
                conditionText = day?.weatherCondition?.description?.text,
                nightCondition = night?.weatherCondition?.type?.let(::googleCondition),
                precipitationProbability = precipProb,
                precipitation = qpf,
                windSpeedMax = listOfNotNull(day?.wind?.speed?.value, night?.wind?.speed?.value).maxOrNull(),
                windDirection = day?.wind?.direction?.degrees,
                uvIndexMax = day?.uvIndex?.toDouble(),
                sunrise = instant(d.sunEvents?.sunriseTime),
                sunset = instant(d.sunEvents?.sunsetTime),
                moonPhase = googleMoonPhase(d.moonEvents?.moonPhase),
            )
        }

        fun mapAlert(a: AlertDto): WeatherAlert? {
            val title = a.alertTitle?.text ?: a.eventType?.replace('_', ' ')?.lowercase()
                ?.replaceFirstChar { it.uppercase() } ?: return null
            val instructions = a.instruction + a.safetyRecommendations.mapNotNull { r ->
                listOfNotNull(r.directive, r.subtext).joinToString(": ").ifEmpty { null }
            }
            return WeatherAlert(
                id = a.alertId ?: "${title}_${a.startTime}",
                title = title,
                description = a.description,
                severity = googleSeverity(a.severity),
                areaName = a.areaName,
                source = a.dataSource?.name ?: a.dataSource?.publisher,
                sourceUrl = a.dataSource?.authorityUri,
                start = instant(a.startTime),
                end = instant(a.expirationTime),
                instructions = instructions,
            )
        }
    }
}
