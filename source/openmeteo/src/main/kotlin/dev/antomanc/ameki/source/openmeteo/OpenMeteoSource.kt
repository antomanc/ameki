package dev.antomanc.ameki.source.openmeteo

import dev.antomanc.ameki.core.model.Astronomy
import dev.antomanc.ameki.core.model.Capability
import dev.antomanc.ameki.core.model.CurrentWeather
import dev.antomanc.ameki.core.model.DailyForecast
import dev.antomanc.ameki.core.model.DataPart
import dev.antomanc.ameki.core.model.GeoPoint
import dev.antomanc.ameki.core.model.HourlyForecast
import dev.antomanc.ameki.core.model.NowcastSlot
import dev.antomanc.ameki.core.model.PlaceSearchResult
import dev.antomanc.ameki.core.model.SourceId
import dev.antomanc.ameki.core.model.WeatherSnapshot
import dev.antomanc.ameki.source.api.PlaceSearch
import dev.antomanc.ameki.source.api.SourceException
import dev.antomanc.ameki.source.api.WeatherSource
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Open-Meteo: free, no key, non-commercial use. Data under CC BY 4.0.
 * One call returns current, hourly and daily together, so [parts] only trims the result.
 */
class OpenMeteoSource(
    private val http: HttpClient,
    private val clock: () -> Instant = Instant::now,
    private val forecastUrl: String = "https://api.open-meteo.com/v1/forecast",
) : WeatherSource {

    override val id = SourceId.OPEN_METEO

    override val capabilities = setOf(
        Capability.CURRENT, Capability.HOURLY, Capability.DAILY,
        Capability.SUN_EVENTS, Capability.MOON_PHASE, Capability.UV, Capability.VISIBILITY, Capability.NOWCAST,
    )

    override suspend fun fetch(location: GeoPoint, language: String, parts: Set<DataPart>): WeatherSnapshot {
        val body = request {
            http.get(forecastUrl) {
                parameter("latitude", location.latitude)
                parameter("longitude", location.longitude)
                parameter("current", CURRENT_VARS)
                parameter("hourly", HOURLY_VARS)
                parameter("daily", DAILY_VARS)
                parameter("timezone", "auto")
                parameter("timeformat", "unixtime")
                parameter("forecast_days", 10)
                // All ten days hour by hour, so any day can be opened in detail.
                parameter("forecast_hours", 240)
                parameter("minutely_15", "precipitation")
                parameter("forecast_minutely_15", 12)
            }
        }
        val response = json.decodeFromString(ForecastResponse.serializer(), body)
        return map(response, clock()).retain(parts - DataPart.ALERTS)
    }

    internal companion object {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

        const val CURRENT_VARS = "temperature_2m,relative_humidity_2m,dew_point_2m,apparent_temperature," +
            "is_day,precipitation,weather_code,cloud_cover,pressure_msl,wind_speed_10m," +
            "wind_direction_10m,wind_gusts_10m,uv_index,visibility"
        const val HOURLY_VARS = "temperature_2m,relative_humidity_2m,dew_point_2m,apparent_temperature," +
            "precipitation_probability,precipitation,weather_code,wind_speed_10m,wind_direction_10m," +
            "wind_gusts_10m,uv_index,is_day"
        const val DAILY_VARS = "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset," +
            "uv_index_max,precipitation_sum,precipitation_probability_max,wind_speed_10m_max," +
            "wind_direction_10m_dominant"

        suspend fun request(block: suspend () -> HttpResponse): String {
            val response = try {
                block()
            } catch (e: IOException) {
                throw SourceException.Network(e)
            }
            val text = response.bodyAsText()
            if (!response.status.isSuccess()) {
                val reason = runCatching { json.decodeFromString(ErrorResponse.serializer(), text).reason }.getOrNull()
                when (response.status.value) {
                    429 -> throw SourceException.QuotaExceeded(reason ?: "Open-Meteo rate limit")
                    400 -> throw SourceException.Unsupported(reason ?: "Bad request")
                    else -> throw SourceException.Unknown("Open-Meteo HTTP ${response.status.value}: ${reason.orEmpty()}")
                }
            }
            return text
        }

        fun map(r: ForecastResponse, now: Instant): WeatherSnapshot {
            val zone = runCatching { ZoneId.of(r.timezone) }.getOrElse { ZoneOffset.ofTotalSeconds(r.utcOffsetSeconds) }
            val daily = r.daily?.let { d ->
                d.time.indices.mapNotNull { i ->
                    val max = d.temperatureMax.getOrNull(i) ?: return@mapNotNull null
                    val min = d.temperatureMin.getOrNull(i) ?: return@mapNotNull null
                    val date = Instant.ofEpochSecond(d.time[i]).atZone(zone).toLocalDate()
                    DailyForecast(
                        date = date,
                        temperatureMax = max,
                        temperatureMin = min,
                        condition = wmoToCondition(d.weatherCode?.getOrNull(i)),
                        precipitationProbability = d.precipitationProbabilityMax?.getOrNull(i),
                        precipitation = d.precipitationSum?.getOrNull(i),
                        windSpeedMax = d.windSpeedMax?.getOrNull(i),
                        windDirection = d.windDirectionDominant?.getOrNull(i),
                        uvIndexMax = d.uvIndexMax?.getOrNull(i),
                        sunrise = d.sunrise?.getOrNull(i)?.let(Instant::ofEpochSecond),
                        sunset = d.sunset?.getOrNull(i)?.let(Instant::ofEpochSecond),
                        moonPhase = Astronomy.moonPhase(date),
                    )
                }
            }.orEmpty()
            val hourly = r.hourly?.let { h ->
                h.time.indices.mapNotNull { i ->
                    val temp = h.temperature.getOrNull(i) ?: return@mapNotNull null
                    HourlyForecast(
                        time = Instant.ofEpochSecond(h.time[i]),
                        temperature = temp,
                        feelsLike = h.apparentTemperature?.getOrNull(i),
                        condition = wmoToCondition(h.weatherCode?.getOrNull(i)),
                        isDaytime = (h.isDay?.getOrNull(i) ?: 1) == 1,
                        precipitationProbability = h.precipitationProbability?.getOrNull(i),
                        precipitation = h.precipitation?.getOrNull(i),
                        windSpeed = h.windSpeed?.getOrNull(i),
                        windGust = h.windGusts?.getOrNull(i),
                        windDirection = h.windDirection?.getOrNull(i),
                        humidity = h.humidity?.getOrNull(i),
                        dewPoint = h.dewPoint?.getOrNull(i),
                        uvIndex = h.uvIndex?.getOrNull(i),
                    )
                }
            }.orEmpty()
            val current = r.current?.let { c ->
                val hourNow = hourly.lastOrNull { !it.time.isAfter(Instant.ofEpochSecond(c.time)) }
                CurrentWeather(
                    time = Instant.ofEpochSecond(c.time),
                    temperature = c.temperature,
                    feelsLike = c.apparentTemperature,
                    condition = wmoToCondition(c.weatherCode, c.cloudCover),
                    isDaytime = c.isDay == 1,
                    humidity = c.humidity,
                    dewPoint = c.dewPoint,
                    uvIndex = c.uvIndex,
                    pressure = c.pressure,
                    windSpeed = c.windSpeed,
                    windGust = c.windGusts,
                    windDirection = c.windDirection,
                    visibility = c.visibility?.let { it / 1000.0 },
                    cloudCover = c.cloudCover,
                    precipitationProbability = hourNow?.precipitationProbability,
                )
            }
            val nowcast = r.minutely15?.let { m ->
                m.time.indices.mapNotNull { i ->
                    m.precipitation.getOrNull(i)?.let { NowcastSlot(Instant.ofEpochSecond(m.time[i]), it) }
                }
            }.orEmpty()
            val fetched = buildMap {
                if (current != null) put(DataPart.CURRENT, now)
                if (hourly.isNotEmpty()) put(DataPart.HOURLY, now)
                if (daily.isNotEmpty()) put(DataPart.DAILY, now)
                if (nowcast.isNotEmpty()) put(DataPart.NOWCAST, now)
            }
            return WeatherSnapshot(
                source = SourceId.OPEN_METEO,
                timeZone = zone,
                current = current,
                hourly = hourly,
                daily = daily,
                nowcast = nowcast,
                fetchedAt = fetched,
            )
        }
    }
}

/** City search through Open-Meteo's free geocoding API (GeoNames data). */
class OpenMeteoPlaceSearch(
    private val http: HttpClient,
    private val url: String = "https://geocoding-api.open-meteo.com/v1/search",
) : PlaceSearch {
    override suspend fun search(query: String, language: String, limit: Int): List<PlaceSearchResult> {
        if (query.trim().length < 2) return emptyList()
        val body = OpenMeteoSource.request {
            http.get(url) {
                parameter("name", query.trim())
                parameter("count", limit)
                parameter("language", language.substringBefore('-'))
                parameter("format", "json")
            }
        }
        return OpenMeteoSource.json.decodeFromString(GeocodingResponse.serializer(), body).results
            // Countries and regions as a whole are not useful weather locations.
            .filterNot { it.featureCode?.startsWith("PCL") == true || it.featureCode?.startsWith("ADM") == true }
            .map {
                PlaceSearchResult(
                    name = it.name,
                    region = it.admin1,
                    country = it.country,
                    point = GeoPoint(it.latitude, it.longitude),
                    timeZone = it.timezone,
                )
            }
    }
}
