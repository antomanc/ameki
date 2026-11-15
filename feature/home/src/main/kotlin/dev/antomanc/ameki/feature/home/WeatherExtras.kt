package dev.antomanc.ameki.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.antomanc.ameki.core.model.*
import dev.antomanc.ameki.core.ui.TimeFormatter
import dev.antomanc.ameki.core.ui.theme.LocalInstrumentColors
import kotlinx.coroutines.delay
import java.time.Instant

/** Keep time-dependent labels current while the screen remains open. */
@Composable
internal fun rememberWeatherTime(): Instant {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = Instant.now() } }
    return now
}

@Composable
internal fun RainOutlookSummary(snapshot: WeatherSnapshot, now: Instant, fmt: TimeFormatter) {
    val text = when (val outlook = Nowcast.outlook(snapshot, now)) {
        RainOutlook.NoneSoon -> stringResource(R.string.rain_none)
        RainOutlook.RainingNow -> stringResource(R.string.rain_now)
        is RainOutlook.StartsIn -> stringResource(R.string.rain_starts, outlook.minutes)
        is RainOutlook.StopsIn -> stringResource(R.string.rain_stops, outlook.minutes)
        is RainOutlook.LikelyAround -> stringResource(R.string.rain_likely, fmt.time(outlook.at, snapshot.timeZone))
        else -> stringResource(R.string.rain_unavailable)
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
}

@Composable
internal fun AlertsCard(snapshot: WeatherSnapshot, fmt: TimeFormatter) {
    var selected by remember { mutableStateOf<WeatherAlert?>(null) }
    val now = rememberWeatherTime()
    val alerts = snapshot.alerts.filter { alert -> alert.end?.let { it > now } ?: true }
    if (alerts.isEmpty()) return
    Card(stringResource(R.string.weather_alerts)) {
        alerts.forEach { alert ->
            TextButton(onClick = { selected = alert }) {
                Column {
                    Text(alert.title, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(severityLabel(alert.severity)), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    selected?.let { alert ->
        val uri = LocalUriHandler.current
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(alert.title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(severityLabel(alert.severity)))
                    alert.areaName?.let { Text(it) }
                    alert.start?.let { Text(stringResource(R.string.alert_from, "${fmt.dateMedium(it.atZone(snapshot.timeZone).toLocalDate())} ${fmt.time(it, snapshot.timeZone)}")) }
                    alert.end?.let { Text(stringResource(R.string.alert_until, "${fmt.dateMedium(it.atZone(snapshot.timeZone).toLocalDate())} ${fmt.time(it, snapshot.timeZone)}")) }
                    alert.description?.let { Text(it) }
                    alert.instructions.forEach { Text(it) }
                    alert.source?.let { Text(stringResource(R.string.alert_source, it)) }
                    alert.sourceUrl?.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let { url ->
                        TextButton(onClick = { uri.openUri(url) }) { Text(stringResource(R.string.alert_open_source)) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text(stringResource(R.string.close)) } },
        )
    }
}

private fun severityLabel(severity: AlertSeverity): Int = when (severity) {
    AlertSeverity.EXTREME -> R.string.alert_extreme
    AlertSeverity.SEVERE -> R.string.alert_severe
    AlertSeverity.MODERATE -> R.string.alert_moderate
    AlertSeverity.MINOR -> R.string.alert_minor
    AlertSeverity.UNKNOWN -> R.string.alert_unknown
}

internal const val MIN_DAY_CHART_HOURS = 2

@Composable
internal fun DayTemperatureChart(day: DailyForecast, hours: List<HourlyForecast>, s: WeatherSnapshot, units: Units, fmt: TimeFormatter) {
    if (hours.size < MIN_DAY_CHART_HOURS) return
    val min = hours.minOf { it.temperature }
    val max = hours.maxOf { it.temperature }
    val peak = hours.maxBy { it.temperature }
    val summary = stringResource(R.string.day_chart_summary, units.temp(min), units.temp(max), fmt.time(peak.time, s.timeZone))
    val colors = LocalInstrumentColors.current
    val sunrise = day.sunrise
    val sunset = day.sunset
    Card(stringResource(R.string.day_temperature), Modifier.padding(top = 16.dp, bottom = 8.dp), contentPadding = 20.dp) {
        Text(summary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 16.dp))
        Canvas(Modifier.fillMaxWidth().height(152.dp).padding(vertical = 12.dp).semantics { contentDescription = summary }) {
            val start = day.date.atStartOfDay(s.timeZone).toEpochSecond().toDouble()
            val duration = day.date.plusDays(1).atStartOfDay(s.timeZone).toEpochSecond() - start
            fun x(time: Instant) = ((time.epochSecond - start) / duration).toFloat().coerceIn(0f, 1f) * size.width
            if (sunrise != null && sunset != null && sunset > sunrise) {
                drawRect(colors.warm.copy(alpha = .12f), Offset(x(sunrise), 0f), androidx.compose.ui.geometry.Size((x(sunset) - x(sunrise)).coerceAtLeast(0f), size.height))
                listOf(sunrise, sunset).forEach { drawLine(colors.warm, Offset(x(it), 0f), Offset(x(it), size.height), 1.dp.toPx()) }
            }
            val path = Path()
            hours.forEachIndexed { i, hour ->
                val y = size.height - (((hour.temperature - min) / (max - min).coerceAtLeast(1.0)).toFloat() * .8f + .1f) * size.height
                if (i == 0 || hour.time.epochSecond - hours[i - 1].time.epochSecond > 5400) path.moveTo(x(hour.time), y) else path.lineTo(x(hour.time), y)
            }
            drawPath(path, colors.mild, style = Stroke(2.dp.toPx()))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt.hour(hours.first().time, s.timeZone), style = MaterialTheme.typography.labelMedium)
            Text(fmt.hour(hours.last().time, s.timeZone), style = MaterialTheme.typography.labelMedium)
        }
        if (sunrise != null && sunset != null) {
            val start = day.date.atStartOfDay(s.timeZone).toEpochSecond()
            val duration = day.date.plusDays(1).atStartOfDay(s.timeZone).toEpochSecond() - start
            val middle = if (sunset <= sunrise) .5f else (((sunrise.epochSecond - start) + (sunset.epochSecond - start)) / (2.0 * duration)).toFloat().coerceIn(0f, 1f)
            BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
                val labelWidth = minOf(220.dp, maxWidth)
                val left = (maxWidth * middle - labelWidth / 2).coerceIn(0.dp, maxWidth - labelWidth)
                Text(stringResource(R.string.day_sun, fmt.time(sunrise, s.timeZone), fmt.time(sunset, s.timeZone)),
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                    modifier = Modifier.offset(x = left).width(labelWidth))
            }
        }
    }
}
