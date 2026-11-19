package dev.antomanc.ameki.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.antomanc.ameki.core.model.AlertSeverity
import dev.antomanc.ameki.core.model.DailyForecast
import dev.antomanc.ameki.core.model.HourlyForecast
import dev.antomanc.ameki.core.model.Units
import dev.antomanc.ameki.core.model.WeatherAlert
import dev.antomanc.ameki.core.model.WeatherSnapshot
import dev.antomanc.ameki.core.ui.ForecastView
import dev.antomanc.ameki.core.ui.TimeFormatter
import dev.antomanc.ameki.core.ui.effects.rememberWeatherTime
import dev.antomanc.ameki.core.ui.label
import dev.antomanc.ameki.core.ui.theme.InstrumentColors
import dev.antomanc.ameki.core.ui.theme.LocalInstrumentColors
import java.time.Instant
import kotlin.math.abs

@Composable
internal fun RainOutlookSummary(view: ForecastView, fmt: TimeFormatter) {
    Text(
        view.rainText(LocalContext.current, fmt, short = false),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
    )
}

@Composable
internal fun AlertsCard(snapshot: WeatherSnapshot, fmt: TimeFormatter) {
    var selected by remember { mutableStateOf<WeatherAlert?>(null) }
    val now = rememberWeatherTime()
    val alerts = snapshot.alerts.filter { alert -> alert.end?.let { it > now } ?: true }
    if (alerts.isEmpty()) return
    val colors = LocalInstrumentColors.current
    Card(stringResource(R.string.weather_alerts)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            alerts.forEach { alert ->
                val accent = severityAccent(alert.severity, colors, MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.outline)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accent.copy(alpha = 0.09f))
                        .clickable { selected = alert }
                        .padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
                    Column(Modifier.weight(1f).padding(start = 12.dp, top = 10.dp, bottom = 10.dp)) {
                        Text(
                            alert.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(accent.copy(alpha = 0.16f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    stringResource(severityLabel(alert.severity)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent,
                                )
                            }
                            alert.end?.let { end ->
                                Text(
                                    stringResource(R.string.alert_until, fmt.time(end, snapshot.timeZone)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
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
                    Text(stringResource(severityLabel(alert.severity)), style = MaterialTheme.typography.labelLarge)
                    alert.areaName?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    alert.start?.let { Text(stringResource(R.string.alert_from, "${fmt.dateMedium(it.atZone(snapshot.timeZone).toLocalDate())} ${fmt.time(it, snapshot.timeZone)}")) }
                    alert.end?.let { Text(stringResource(R.string.alert_until, "${fmt.dateMedium(it.atZone(snapshot.timeZone).toLocalDate())} ${fmt.time(it, snapshot.timeZone)}")) }
                    alert.description?.let { Text(it) }
                    alert.instructions.forEach { Text(it) }
                    alert.source?.let { Text(stringResource(R.string.alert_source, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    alert.sourceUrl?.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let { url ->
                        TextButton(onClick = { uri.openUri(url) }) { Text(stringResource(R.string.alert_open_source)) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text(stringResource(R.string.close)) } },
        )
    }
}

private fun severityAccent(
    severity: AlertSeverity,
    colors: InstrumentColors,
    errorColor: Color,
    neutralColor: Color,
): Color = when (severity) {
    AlertSeverity.EXTREME -> errorColor
    AlertSeverity.SEVERE -> colors.now
    AlertSeverity.MODERATE -> colors.warm
    AlertSeverity.MINOR -> colors.mild
    AlertSeverity.UNKNOWN -> neutralColor
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
internal fun DayTemperatureChart(
    day: DailyForecast,
    hours: List<HourlyForecast>,
    s: WeatherSnapshot,
    units: Units,
    fmt: TimeFormatter,
    now: Instant? = null,
    loading: Boolean = false,
    failed: Boolean = false,
) {
    val hasData = hours.size >= MIN_DAY_CHART_HOURS
    val min = if (hasData) hours.minOf { it.temperature } else day.temperatureMin
    val max = if (hasData) hours.maxOf { it.temperature } else day.temperatureMax
    val peak = hours.maxByOrNull { it.temperature }
    val summary = stringResource(
        R.string.day_chart_summary,
        units.temp(min),
        units.temp(max),
        fmt.time(peak?.time ?: day.date.atTime(12, 0).atZone(s.timeZone).toInstant(), s.timeZone),
    )
    val range = stringResource(R.string.temperature_range, units.temp(min), units.temp(max))
    val peakLabel = stringResource(R.string.chart_peak, fmt.time(peak?.time ?: day.date.atTime(12, 0).atZone(s.timeZone).toInstant(), s.timeZone))
    val colors = LocalInstrumentColors.current
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val placeholder = if (loading && !hasData) skeletonColor() else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = .5f)
    val placeholderAlpha by animateFloatAsState(if (loading && !hasData) 1f else 0f, tween(220), label = "temperature-placeholder")
    val lineAlpha by animateFloatAsState(if (hasData) 1f else 0f, tween(220), label = "temperature-data")
    val loadingLabel = stringResource(if (loading) R.string.day_loading_hours else if (failed) R.string.day_hours_failed else R.string.day_no_hourly)
    val sunrise = day.sunrise
    val sunset = day.sunset
    val haptic = LocalHapticFeedback.current
    var scrubbedIndex by remember(day.date, hours.size) { mutableStateOf<Int?>(null) }
    val scrubbed = scrubbedIndex?.let { hours.getOrNull(it) }

    val startSec = day.date.atStartOfDay(s.timeZone).toEpochSecond().toDouble()
    val durationSec = (day.date.plusDays(1).atStartOfDay(s.timeZone).toEpochSecond() - startSec).coerceAtLeast(1.0)

    fun nearestHourIndex(xPx: Float, widthPx: Float): Int? {
        if (!hasData || widthPx <= 0f) return null
        val targetSec = startSec + (xPx / widthPx).coerceIn(0f, 1f) * durationSec
        return hours.indices.minByOrNull { i -> abs(hours[i].time.epochSecond - targetSec) }
    }

    Card(stringResource(R.string.day_temperature), Modifier.padding(top = 16.dp, bottom = 8.dp), contentPadding = 20.dp) {
        Box(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
            val headerLine1 = if (scrubbed != null) {
                "${fmt.time(scrubbed.time, s.timeZone)} · ${units.temp(scrubbed.temperature)}"
            } else {
                range
            }
            val headerLine2 = if (scrubbed != null) {
                stringResource(scrubbed.condition.label(scrubbed.isDaytime))
            } else if (hasData) {
                peakLabel
            } else {
                "–"
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (loading && !hasData) Modifier.clearAndSetSemantics { } else Modifier)
                    .graphicsLayer { alpha = if (hasData) lineAlpha else if (loading) 0f else 1f },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    "$headerLine1\n$headerLine2",
                    style = MaterialTheme.typography.bodyMedium,
                    minLines = 2,
                    modifier = Modifier.weight(1f),
                )
                if (hasData && scrubbed == null) {
                    Text(
                        stringResource(R.string.chart_scrub_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            if (placeholderAlpha > 0f) {
                Column(
                    Modifier.fillMaxWidth(.7f).align(Alignment.CenterStart).graphicsLayer { alpha = placeholderAlpha },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.fillMaxWidth().height(12.dp).background(placeholder, RoundedCornerShape(6.dp)))
                    Box(Modifier.fillMaxWidth(.65f).height(12.dp).background(placeholder, RoundedCornerShape(6.dp)))
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(152.dp)
                .padding(vertical = 8.dp)
                .pointerInput(hours, hasData) {
                    if (!hasData) return@pointerInput
                    detectTapGestures { offset ->
                        val picked = nearestHourIndex(offset.x, size.width.toFloat())
                        if (picked != null) {
                            scrubbedIndex = if (scrubbedIndex == picked) null else picked
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                }
                .pointerInput(hours, hasData) {
                    if (!hasData) return@pointerInput
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            val picked = nearestHourIndex(offset.x, size.width.toFloat())
                            if (picked != null && picked != scrubbedIndex) {
                                scrubbedIndex = picked
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val picked = nearestHourIndex(change.position.x, size.width.toFloat())
                            if (picked != null && picked != scrubbedIndex) {
                                scrubbedIndex = picked
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onDragEnd = { scrubbedIndex = null },
                        onDragCancel = { scrubbedIndex = null },
                    )
                },
        ) {
            Canvas(Modifier.fillMaxSize().semantics { contentDescription = if (hasData) summary else loadingLabel }) {
                if (placeholderAlpha > 0f) {
                    drawRoundRect(placeholder.copy(alpha = placeholder.alpha * placeholderAlpha), cornerRadius = CornerRadius(8.dp.toPx()))
                }
                fun xOf(time: Instant) = ((time.epochSecond - startSec) / durationSec).toFloat().coerceIn(0f, 1f) * size.width
                fun yOf(temp: Double) = size.height - (((temp - min) / (max - min).coerceAtLeast(1.0)).toFloat() * .78f + .12f) * size.height

                // Subtle horizontal top/bottom temperature guide rails.
                if (hasData) {
                    val guideColor = outlineColor.copy(alpha = 0.35f * lineAlpha)
                    val guideDash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                    drawLine(guideColor, Offset(0f, size.height * 0.10f), Offset(size.width, size.height * 0.10f), 1.dp.toPx(), pathEffect = guideDash)
                    drawLine(guideColor, Offset(0f, size.height * 0.88f), Offset(size.width, size.height * 0.88f), 1.dp.toPx(), pathEffect = guideDash)
                }

                // Daylight band & sunrise/sunset markers.
                if (sunrise != null && sunset != null && sunset > sunrise) {
                    val rx = xOf(sunrise)
                    val sx = xOf(sunset)
                    drawRect(colors.warm.copy(alpha = .10f), Offset(rx, 0f), Size((sx - rx).coerceAtLeast(0f), size.height))
                    val sunDash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
                    listOf(rx, sx).forEach { edgeX ->
                        drawLine(colors.warm.copy(alpha = 0.65f), Offset(edgeX, 0f), Offset(edgeX, size.height), 1.dp.toPx(), pathEffect = sunDash)
                    }
                }

                if (hasData) {
                    // Split into contiguous hour segments and draw smooth cubic Bezier curves + thermal fill.
                    val segments = mutableListOf<MutableList<Offset>>()
                    hours.forEachIndexed { i, hour ->
                        val pt = Offset(xOf(hour.time), yOf(hour.temperature))
                        if (i == 0 || hour.time.epochSecond - hours[i - 1].time.epochSecond > 5400) {
                            segments.add(mutableListOf(pt))
                        } else {
                            segments.last().add(pt)
                        }
                    }

                    val topColor = colors.temperature(max)
                    val midColor = colors.temperature((min + max) / 2.0)
                    val lowColor = colors.temperature(min)
                    val strokeBrush = Brush.verticalGradient(
                        listOf(
                            topColor.copy(alpha = lineAlpha),
                            midColor.copy(alpha = lineAlpha),
                            lowColor.copy(alpha = lineAlpha),
                        ),
                    )
                    val fillBrush = Brush.verticalGradient(
                        listOf(
                            topColor.copy(alpha = 0.22f * lineAlpha),
                            midColor.copy(alpha = 0.08f * lineAlpha),
                            Color.Transparent,
                        ),
                    )

                    segments.forEach { pts ->
                        if (pts.size < 2) return@forEach
                        val strokePath = Path().apply {
                            moveTo(pts[0].x, pts[0].y)
                            for (j in 1 until pts.size) {
                                val prev = pts[j - 1]
                                val curr = pts[j]
                                val midX = (prev.x + curr.x) / 2f
                                cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                            }
                        }
                        val fillPath = Path().apply {
                            addPath(strokePath)
                            lineTo(pts.last().x, size.height)
                            lineTo(pts.first().x, size.height)
                            close()
                        }
                        drawPath(fillPath, fillBrush)
                        drawPath(
                            strokePath,
                            brush = strokeBrush,
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                    }

                    // "Now" marker on today's temperature curve.
                    if (now != null && now.epochSecond >= startSec && now.epochSecond <= startSec + durationSec) {
                        val nowX = xOf(now)
                        val nearest = hours.minByOrNull { abs(it.time.epochSecond - now.epochSecond) }
                        if (nearest != null) {
                            val nowY = yOf(nearest.temperature)
                            drawLine(
                                colors.now.copy(alpha = 0.4f * lineAlpha),
                                Offset(nowX, 0f),
                                Offset(nowX, size.height),
                                strokeWidth = 1.dp.toPx(),
                            )
                            drawCircle(colors.now.copy(alpha = 0.22f * lineAlpha), 7.dp.toPx(), Offset(nowX, nowY))
                            drawCircle(surfaceColor.copy(alpha = lineAlpha), 4.5.dp.toPx(), Offset(nowX, nowY))
                            drawCircle(colors.now.copy(alpha = lineAlpha), 3.dp.toPx(), Offset(nowX, nowY))
                        }
                    }

                    // Active scrubbed point crosshair.
                    if (scrubbed != null) {
                        val sx = xOf(scrubbed.time)
                        val sy = yOf(scrubbed.temperature)
                        val dotColor = colors.temperature(scrubbed.temperature)
                        drawLine(
                            dotColor.copy(alpha = 0.6f),
                            Offset(sx, 0f),
                            Offset(sx, size.height),
                            strokeWidth = 1.5.dp.toPx(),
                        )
                        drawCircle(surfaceColor, 6.dp.toPx(), Offset(sx, sy))
                        drawCircle(dotColor, 4.dp.toPx(), Offset(sx, sy))
                    }
                }
            }
            if (!loading && !hasData) {
                Text(
                    loadingLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).clearAndSetSemantics { },
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt.hour(hours.firstOrNull()?.time ?: day.date.atStartOfDay(s.timeZone).toInstant(), s.timeZone), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(fmt.hour(hours.lastOrNull()?.time ?: day.date.plusDays(1).atStartOfDay(s.timeZone).minusHours(1).toInstant(), s.timeZone), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (sunrise != null && sunset != null) {
            val start = day.date.atStartOfDay(s.timeZone).toEpochSecond()
            val duration = day.date.plusDays(1).atStartOfDay(s.timeZone).toEpochSecond() - start
            val middle = if (sunset <= sunrise) .5f else (((sunrise.epochSecond - start) + (sunset.epochSecond - start)) / (2.0 * duration)).toFloat().coerceIn(0f, 1f)
            BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
                val labelWidth = minOf(220.dp, maxWidth)
                val left = (maxWidth * middle - labelWidth / 2).coerceIn(0.dp, maxWidth - labelWidth)
                Text(
                    stringResource(R.string.day_sun, fmt.time(sunrise, s.timeZone), fmt.time(sunset, s.timeZone)),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.offset(x = left).width(labelWidth),
                )
            }
        }
    }
}

