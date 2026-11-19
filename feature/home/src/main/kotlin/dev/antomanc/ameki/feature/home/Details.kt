package dev.antomanc.ameki.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.antomanc.ameki.core.model.Astronomy
import dev.antomanc.ameki.core.model.Capability
import dev.antomanc.ameki.core.model.HourlyForecast
import dev.antomanc.ameki.core.model.MoonPhase
import dev.antomanc.ameki.core.model.Units
import dev.antomanc.ameki.core.model.WeatherSnapshot
import dev.antomanc.ameki.core.model.compassPoint
import dev.antomanc.ameki.core.ui.ForecastView
import dev.antomanc.ameki.core.ui.TimeFormatter
import dev.antomanc.ameki.core.ui.theme.InstrumentColors
import dev.antomanc.ameki.core.ui.theme.LocalInstrumentColors
import java.time.Duration
import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
internal fun HourlyCharts(hours: List<HourlyForecast>, s: WeatherSnapshot, units: Units, fmt: TimeFormatter) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var scrubbedIndex by remember(tab, hours.size) { mutableStateOf<Int?>(null) }
    val tabs = listOf(R.string.tab_precipitation, R.string.tab_wind, R.string.tab_humidity, R.string.tab_uv)
    val safeTab = tab.coerceIn(0, tabs.lastIndex)
    val colors = LocalInstrumentColors.current
    val errorColor = MaterialTheme.colorScheme.error
    val onVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val compassLabels = stringResource(R.string.compass_points).split(",")
    val haptic = LocalHapticFeedback.current

    Card(null) {
        if (LocalDensity.current.fontScale > 1.3f) {
            Column {
                tabs.forEachIndexed { i, t ->
                    FilterChip(
                        selected = safeTab == i,
                        onClick = { tab = i },
                        label = { Text(stringResource(t)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            SecondaryTabRow(selectedTabIndex = safeTab, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = safeTab == i, onClick = { tab = i }, text = { Text(stringResource(t), maxLines = 1) })
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val values: List<Double?> = when (safeTab) {
            0 -> hours.map { it.precipitation }
            1 -> hours.map { it.windSpeed }
            2 -> hours.map { it.humidity?.toDouble() }
            else -> hours.map { it.uvIndex }
        }
        val available = values.filterNotNull()
        val summary = if (available.isEmpty()) stringResource(R.string.chart_no_data) else when (safeTab) {
            0 -> units.precipitation(available.sum())
            1 -> units.wind(available.maxOrNull() ?: 0.0)
            2 -> "${available.average().roundToInt()}%"
            else -> (available.maxOrNull() ?: 0.0).roundToInt().toString()
        }
        if (available.size != values.size && available.isNotEmpty()) {
            Text(stringResource(R.string.chart_coverage, available.size, values.size), style = MaterialTheme.typography.bodySmall, color = onVariant)
        }
        val peakIndex = values.withIndex()
            .filter { it.value != null }
            .maxByOrNull { it.value ?: Double.NEGATIVE_INFINITY }
            ?.index
        val peakLabel = peakIndex?.let { stringResource(R.string.chart_peak, fmt.time(hours[it].time, s.timeZone)) }

        val activeIndex = scrubbedIndex?.takeIf { it in hours.indices }
        val activeHour = activeIndex?.let { hours[it] }
        val activeValue = activeIndex?.let { values.getOrNull(it) }

        val headlineText = if (activeHour != null && activeValue != null) {
            when (safeTab) {
                0 -> units.precipitation(activeValue)
                1 -> {
                    val dir = activeHour.windDirection?.let { compassLabels.getOrElse(compassPoint(it)) { "" } }
                    if (dir.isNullOrEmpty()) units.wind(activeValue) else "${units.wind(activeValue)} · $dir"
                }
                2 -> "${activeValue.roundToInt()}%"
                else -> "${activeValue.roundToInt()} · ${stringResource(uvLevelLabel(activeValue))}"
            }
        } else {
            summary
        }
        val subtitleText = if (activeHour != null) {
            fmt.time(activeHour.time, s.timeZone)
        } else {
            stringResource(listOf(R.string.chart_precip_total, R.string.chart_wind_max, R.string.chart_humidity_avg, R.string.chart_uv_max)[safeTab])
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(headlineText, style = MaterialTheme.typography.headlineSmall)
                Text(subtitleText, style = MaterialTheme.typography.bodySmall, color = onVariant)
                if (activeHour == null) {
                    peakLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = onVariant) }
                }
            }
            if (available.isNotEmpty() && activeHour == null) {
                Text(
                    stringResource(R.string.chart_scrub_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = onVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        val maxV = when (safeTab) {
            0 -> maxOf(available.maxOrNull() ?: 0.0, 2.0)
            1 -> maxOf(available.maxOrNull() ?: 0.0, 20.0)
            2 -> 100.0
            else -> maxOf(available.maxOrNull() ?: 0.0, 11.0)
        }
        val chartHeight = if (safeTab == 1) 114.dp else 96.dp
        val n = values.size.coerceAtLeast(1)

        fun barIndexAt(xPx: Float, widthPx: Float): Int? {
            if (available.isEmpty() || widthPx <= 0f) return null
            return ((xPx / widthPx) * n).toInt().coerceIn(0, values.lastIndex)
        }

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .pointerInput(values, safeTab) {
                    if (available.isEmpty()) return@pointerInput
                    detectTapGestures { offset ->
                        val picked = barIndexAt(offset.x, size.width.toFloat())
                        if (picked != null && values.getOrNull(picked) != null) {
                            scrubbedIndex = if (scrubbedIndex == picked) null else picked
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                }
                .pointerInput(values, safeTab) {
                    if (available.isEmpty()) return@pointerInput
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            val picked = barIndexAt(offset.x, size.width.toFloat())
                            if (picked != null && values.getOrNull(picked) != null && picked != scrubbedIndex) {
                                scrubbedIndex = picked
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val picked = barIndexAt(change.position.x, size.width.toFloat())
                            if (picked != null && values.getOrNull(picked) != null && picked != scrubbedIndex) {
                                scrubbedIndex = picked
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onDragEnd = { scrubbedIndex = null },
                        onDragCancel = { scrubbedIndex = null },
                    )
                }
                .semantics { contentDescription = listOfNotNull(summary, peakLabel).joinToString(". ") },
        ) {
            val w = size.width / n
            val barStroke = (w * 0.48f).coerceIn(3.dp.toPx(), 10.dp.toPx())
            val capPad = barStroke / 2f
            val topBand = if (safeTab == 1) 20.dp.toPx() else capPad
            val barBottom = size.height - capPad
            val usableHeight = (barBottom - topBand).coerceAtLeast(1f)
            val arrowCenterY = 8.dp.toPx()
            val arrowHalfW = 3.dp.toPx()
            val arrowTipY = arrowCenterY - 4.5.dp.toPx()
            val arrowBaseY = arrowCenterY + 4.dp.toPx()

            values.forEachIndexed { i, v ->
                val x = i * w + w / 2f
                val isSelected = activeIndex == i
                val barAlpha = if (activeIndex == null || isSelected) 1f else 0.36f

                if (isSelected) {
                    drawRoundRect(
                        color = colors.track.copy(alpha = 0.85f),
                        topLeft = Offset(i * w + w * 0.1f, topBand - capPad),
                        size = Size(w * 0.8f, usableHeight + capPad * 2f),
                        cornerRadius = CornerRadius(6.dp.toPx()),
                    )
                }

                if (v == null) return@forEachIndexed
                val barColor = when (safeTab) {
                    0 -> colors.rain
                    1 -> colors.mild
                    2 -> colors.cold
                    else -> uvAccentColor(v, colors, errorColor)
                }
                val h = (v / maxV).toFloat().coerceIn(0f, 1f) * usableHeight
                drawLine(
                    colors.track,
                    Offset(x, barBottom),
                    Offset(x, topBand),
                    strokeWidth = barStroke,
                    cap = StrokeCap.Round,
                    alpha = if (isSelected) 0.7f else 0.45f,
                )
                if (h > 0.5f) {
                    drawLine(
                        barColor,
                        Offset(x, barBottom),
                        Offset(x, barBottom - h),
                        strokeWidth = barStroke,
                        cap = StrokeCap.Round,
                        alpha = barAlpha,
                    )
                }
                if (safeTab == 1) {
                    hours[i].windDirection?.let { dir ->
                        rotate(dir.toFloat() + 180f, Offset(x, arrowCenterY)) {
                            val p = Path().apply {
                                moveTo(x, arrowTipY)
                                lineTo(x - arrowHalfW, arrowBaseY)
                                lineTo(x + arrowHalfW, arrowBaseY)
                                close()
                            }
                            drawPath(p, onVariant.copy(alpha = barAlpha))
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0, hours.size / 3, 2 * hours.size / 3, hours.lastIndex).distinct().forEach { i ->
                hours.getOrNull(i)?.let { Text(fmt.hour(it.time, s.timeZone), style = MaterialTheme.typography.labelMedium, color = onVariant) }
            }
        }
    }
}

@Composable
internal fun DetailGrid(view: ForecastView, units: Units, fmt: TimeFormatter, caps: Set<Capability>) {
    val s = view.snapshot
    val c = s.current
    val today = view.today
    val tiles = buildList<@Composable (Modifier) -> Unit> {
        c?.windSpeed?.let { speed -> add { m -> WindTile(speed, c.windGust, c.windDirection, units, m) } }
        c?.humidity?.let { h -> add { m -> HumidityTile(h, c.dewPoint, units, m) } }
        if (Capability.UV in caps) (c?.uvIndex ?: today?.uvIndexMax)?.let { uv -> add { m -> UvTile(uv, m) } }
        c?.pressure?.let { p -> add { m -> PressureTile(p, units, m) } }
        if (Capability.VISIBILITY in caps) c?.visibility?.let { v -> add { m -> VisibilityTile(v, units, m) } }
        c?.cloudCover?.let { cover -> add { m -> CloudCoverTile(cover, m) } }
        if (Capability.SUN_EVENTS in caps) {
            today?.let { d ->
                val rise = d.sunrise
                val set = d.sunset
                if (rise != null && set != null) add { m -> SunTile(rise, set, view.now, s, fmt, m) }
            }
        }
        if (Capability.MOON_PHASE in caps) {
            today?.let { d -> add { m -> MoonTile(d.moonPhase ?: Astronomy.moonPhase(d.date), Astronomy.illumination(d.date), m) } }
        }
    }
    // Two per row, both tiles stretched to the taller one, so rows line up.
    val columns = if (LocalDensity.current.fontScale > 1.3f) 1 else 2
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { it(Modifier.weight(1f).fillMaxHeight()) }
                if (row.size < columns) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Fixed minimum height keeps every tile the same size across rows. */
@Composable
private fun Tile(title: String, modifier: Modifier, content: @Composable () -> Unit) {
    Card(title, modifier.heightIn(min = 152.dp)) { content() }
}

@Composable
private fun WindTile(speed: Double, gust: Double?, dir: Int?, units: Units, modifier: Modifier) = Tile(stringResource(R.string.wind), modifier) {
    val colors = LocalInstrumentColors.current
    val outline = MaterialTheme.colorScheme.outlineVariant
    val labels = stringResource(R.string.compass_points).split(",")
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(units.wind(speed), style = MaterialTheme.typography.headlineMedium)
            val sub = listOfNotNull(
                dir?.let { labels.getOrElse(compassPoint(it)) { "" } },
                gust?.let { stringResource(R.string.gusts, units.wind(it)) },
            )
            sub.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (dir != null) {
            Canvas(Modifier.size(58.dp)) {
                val stroke = 1.25.dp.toPx()
                val r = size.minDimension / 2f - stroke
                val c = Offset(size.width / 2f, size.height / 2f)
                drawCircle(outline, r, c, style = Stroke(stroke))
                for (i in 0 until 16) {
                    val a = i * PI / 8
                    val major = i % 4 == 0
                    val len = if (major) 4.5.dp.toPx() else 2.dp.toPx()
                    val tickColor = if (i == 0) colors.now else outline
                    drawLine(
                        tickColor,
                        Offset(c.x + (r - len) * sin(a).toFloat(), c.y - (r - len) * cos(a).toFloat()),
                        Offset(c.x + r * sin(a).toFloat(), c.y - r * cos(a).toFloat()),
                        strokeWidth = if (major) 1.5.dp.toPx() else 1.dp.toPx(),
                    )
                }
                // The arrow points where the wind blows to.
                rotate(dir + 180f, c) {
                    val tipY = c.y - r + 5.dp.toPx()
                    val headBaseY = tipY + 7.5.dp.toPx()
                    val headHalfW = 3.5.dp.toPx()
                    val tailY = c.y + r - 7.dp.toPx()
                    val p = Path().apply {
                        moveTo(c.x, tipY)
                        lineTo(c.x - headHalfW, headBaseY)
                        lineTo(c.x + headHalfW, headBaseY)
                        close()
                    }
                    drawLine(colors.now, Offset(c.x, tailY), Offset(c.x, headBaseY - 1.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                    drawPath(p, colors.now)
                }
            }
        }
    }
}

@Composable
private fun HumidityTile(humidity: Int, dewPoint: Double?, units: Units, modifier: Modifier) = Tile(stringResource(R.string.humidity), modifier) {
    val colors = LocalInstrumentColors.current
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val comfortRes = when {
        dewPoint != null && dewPoint >= 20.0 -> R.string.comfort_muggy
        dewPoint != null && dewPoint >= 16.0 -> R.string.comfort_humid
        humidity < 30 -> R.string.comfort_dry
        humidity <= 65 -> R.string.comfort_pleasant
        humidity <= 80 -> R.string.comfort_humid
        else -> R.string.comfort_muggy
    }
    Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text("$humidity%", style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(comfortRes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            dewPoint?.let {
                Text(stringResource(R.string.dew_point, units.temp(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(10.dp))
        Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            val cy = size.height / 2f
            val trackH = 5.dp.toPx()
            val pad = trackH / 2f
            drawLine(colors.track, Offset(pad, cy), Offset(size.width - pad, cy), trackH, StrokeCap.Round)
            val fillEnd = pad + (humidity / 100f).coerceIn(0f, 1f) * (size.width - 2f * pad)
            drawLine(
                Brush.horizontalGradient(listOf(colors.mild, colors.cold, colors.rain)),
                Offset(pad, cy),
                Offset(fillEnd.coerceAtLeast(pad), cy),
                trackH,
                StrokeCap.Round,
            )
            drawCircle(surface, 4.5.dp.toPx(), Offset(fillEnd, cy))
            drawCircle(colors.cold, 3.dp.toPx(), Offset(fillEnd, cy))
        }
    }
}

@Composable
private fun UvTile(uv: Double, modifier: Modifier) = Tile(stringResource(R.string.uv_index), modifier) {
    val colors = LocalInstrumentColors.current
    val errorColor = MaterialTheme.colorScheme.error
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val advice = when {
        uv < 3 -> R.string.uv_advice_low
        uv < 6 -> R.string.uv_advice_moderate
        uv < 8 -> R.string.uv_advice_high
        uv < 11 -> R.string.uv_advice_very_high
        else -> R.string.uv_advice_extreme
    }
    val dotColor = uvAccentColor(uv, colors, errorColor)
    Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(uv.roundToInt().toString(), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(uvLevelLabel(uv)), style = MaterialTheme.typography.bodyMedium, color = dotColor)
            Text(stringResource(advice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Canvas(Modifier.fillMaxWidth().height(12.dp)) {
            val cy = size.height / 2f
            val trackH = 5.dp.toPx()
            val pad = 5.dp.toPx()
            val spectrum = Brush.horizontalGradient(
                listOf(colors.mild, colors.warm, colors.hot, colors.now, errorColor),
                startX = pad,
                endX = size.width - pad,
            )
            drawLine(spectrum, Offset(pad, cy), Offset(size.width - pad, cy), trackH, StrokeCap.Round)
            val x = pad + (uv / 11.0).toFloat().coerceIn(0f, 1f) * (size.width - 2f * pad)
            drawCircle(surface, 5.dp.toPx(), Offset(x, cy))
            drawCircle(dotColor, 3.5.dp.toPx(), Offset(x, cy))
        }
    }
}

@Composable
private fun PressureTile(hpa: Double, units: Units, modifier: Modifier) = Tile(stringResource(R.string.pressure), modifier) {
    val colors = LocalInstrumentColors.current
    val outline = MaterialTheme.colorScheme.outlineVariant
    val statusRes = when {
        hpa < 1005.0 -> R.string.pressure_low
        hpa <= 1022.0 -> R.string.pressure_normal
        else -> R.string.pressure_high
    }
    val fraction = ((hpa - 970.0) / 70.0).toFloat().coerceIn(0f, 1f)
    Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(units.pressure(hpa), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(statusRes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(42.dp)) {
            val stroke = 4.dp.toPx()
            val arcW = (size.width - stroke * 2f).coerceAtMost((size.height - stroke) * 2f)
            val left = (size.width - arcW) / 2f
            val top = size.height - arcW / 2f - stroke / 2f
            val arcSize = Size(arcW, arcW)
            // Background aneroid arc (180° sweep).
            drawArc(
                color = colors.track,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(left, top),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            // Normal zone highlight (1005..1022 hPa -> ~0.50..0.74).
            drawArc(
                color = colors.mild.copy(alpha = 0.45f),
                startAngle = 180f + 0.50f * 180f,
                sweepAngle = 0.24f * 180f,
                useCenter = false,
                topLeft = Offset(left, top),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Butt),
            )
            // Needle from center pivot toward current reading on arc.
            val cx = size.width / 2f
            val cy = top + arcW / 2f
            val rad = PI * (1.0 - fraction)
            val r = arcW / 2f
            val nx = cx + (r - 1.dp.toPx()) * cos(rad).toFloat()
            val ny = cy - (r - 1.dp.toPx()) * sin(rad).toFloat()
            drawLine(colors.now, Offset(cx, cy), Offset(nx, ny), 2.dp.toPx(), StrokeCap.Round)
            drawCircle(outline, 3.dp.toPx(), Offset(cx, cy))
            drawCircle(colors.now, 3.dp.toPx(), Offset(nx, ny))
        }
    }
}

@Composable
private fun VisibilityTile(meters: Double, units: Units, modifier: Modifier) = Tile(stringResource(R.string.visibility), modifier) {
    val colors = LocalInstrumentColors.current
    val km = meters / 1000.0
    val qualityRes = when {
        km >= 15.0 -> R.string.visibility_excellent
        km >= 8.0 -> R.string.visibility_good
        km >= 3.0 -> R.string.visibility_moderate
        else -> R.string.visibility_poor
    }
    val fraction = (km / 20.0).toFloat().coerceIn(0.08f, 1f)
    Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(units.distance(meters), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(qualityRes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            val segments = 5
            val gap = 4.dp.toPx()
            val segW = (size.width - gap * (segments - 1)) / segments
            val filledSegments = (fraction * segments).roundToInt().coerceIn(1, segments)
            for (i in 0 until segments) {
                val left = i * (segW + gap)
                drawRoundRect(
                    color = if (i < filledSegments) colors.mild else colors.track,
                    topLeft = Offset(left, 1.dp.toPx()),
                    size = Size(segW, size.height - 2.dp.toPx()),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
        }
    }
}

@Composable
private fun CloudCoverTile(percent: Int, modifier: Modifier) = Tile(stringResource(R.string.cloud_cover), modifier) {
    val colors = LocalInstrumentColors.current
    val labelRes = when {
        percent < 20 -> R.string.cloud_clear
        percent < 55 -> R.string.cloud_partly
        percent < 85 -> R.string.cloud_mostly
        else -> R.string.cloud_overcast
    }
    Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text("$percent%", style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(labelRes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            val cy = size.height / 2f
            val trackH = 5.dp.toPx()
            val pad = trackH / 2f
            drawLine(colors.track, Offset(pad, cy), Offset(size.width - pad, cy), trackH, StrokeCap.Round)
            val fillEnd = pad + (percent / 100f).coerceIn(0f, 1f) * (size.width - 2f * pad)
            if (percent > 0) {
                drawLine(colors.cold, Offset(pad, cy), Offset(fillEnd.coerceAtLeast(pad), cy), trackH, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun SunTile(rise: Instant, set: Instant, now: Instant, s: WeatherSnapshot, fmt: TimeFormatter, modifier: Modifier) = Tile(stringResource(R.string.sun), modifier) {
    val colors = LocalInstrumentColors.current
    val outline = MaterialTheme.colorScheme.outlineVariant
    val totalSec = (set.epochSecond - rise.epochSecond).coerceAtLeast(1L)
    val rawFraction = (now.epochSecond - rise.epochSecond).toDouble() / totalSec.toDouble()
    val isDaylight = rawFraction in 0.0..1.0
    val f = rawFraction.coerceIn(0.0, 1.0)
    val daylight = Duration.between(rise, set)
    val hours = daylight.toHours().coerceAtLeast(0L)
    val minutes = (daylight.toMinutes() % 60L).coerceAtLeast(0L)

    Column {
        Canvas(Modifier.fillMaxWidth().height(56.dp)) {
            val pad = 5.dp.toPx()
            val horizonY = size.height - 4.dp.toPx()
            val arcW = (size.width - pad * 2f).coerceAtLeast(1f)
            val arcH = ((horizonY - pad) * 2f).coerceAtLeast(1f)
            // Horizon line.
            drawLine(outline.copy(alpha = 0.6f), Offset(0f, horizonY), Offset(size.width, horizonY), 1.dp.toPx())
            // Full dashed path.
            drawArc(
                outline,
                180f,
                180f,
                false,
                Offset(pad, pad),
                Size(arcW, arcH),
                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))),
            )
            // Elapsed daylight arc.
            if (f > 0.01) {
                drawArc(
                    colors.warm,
                    180f,
                    (180.0 * f).toFloat(),
                    false,
                    Offset(pad, pad),
                    Size(arcW, arcH),
                    style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            val a = PI * (1.0 - f)
            val x = size.width / 2f + (arcW / 2f) * cos(a).toFloat()
            val y = horizonY - (arcH / 2f) * sin(a).toFloat()
            if (isDaylight) {
                drawCircle(colors.warm.copy(alpha = 0.25f), 8.dp.toPx(), Offset(x, y))
                drawCircle(colors.warm, 4.5.dp.toPx(), Offset(x, y))
            } else {
                drawCircle(outline, 3.5.dp.toPx(), Offset(x, y))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.sunrise_at, fmt.time(rise, s.timeZone)), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.sunset_at, fmt.time(set, s.timeZone)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (hours > 0L || minutes > 0L) {
            Text(
                stringResource(R.string.daylight_duration, hours, minutes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun MoonTile(phase: MoonPhase, illumination: Double, modifier: Modifier) = Tile(stringResource(R.string.moon), modifier) {
    val lit = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
    val dark = MaterialTheme.colorScheme.surfaceContainerHighest
    val rim = MaterialTheme.colorScheme.outlineVariant
    val waxing = phase.ordinal in 1..3
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(52.dp)) {
            val stroke = 1.dp.toPx()
            val r = size.minDimension / 2f - stroke
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(dark, r, c)
            // Lit half on the side the sun is: right while waxing, left while waning.
            drawArc(lit, if (waxing) -90f else 90f, 180f, true, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r))
            // Terminator: an ellipse that adds light past half, or takes it away before half.
            val w = (r * abs(1 - 2 * illumination)).toFloat()
            drawOval(if (illumination > 0.5) lit else dark, Offset(c.x - w, c.y - r), Size(2 * w, 2 * r))
            drawCircle(rim, r, c, style = Stroke(stroke))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(stringResource(moonLabel(phase)), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.moon_illumination, (illumination * 100).roundToInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun uvLevelLabel(uv: Double): Int = when {
    uv < 3 -> R.string.uv_low
    uv < 6 -> R.string.uv_moderate
    uv < 8 -> R.string.uv_high
    uv < 11 -> R.string.uv_very_high
    else -> R.string.uv_extreme
}

private fun uvAccentColor(uv: Double, colors: InstrumentColors, errorColor: Color): Color = when {
    uv < 3 -> colors.mild
    uv < 6 -> colors.warm
    uv < 8 -> colors.hot
    uv < 11 -> colors.now
    else -> errorColor
}

private fun moonLabel(p: MoonPhase) = when (p) {
    MoonPhase.NEW_MOON -> R.string.moon_new
    MoonPhase.WAXING_CRESCENT -> R.string.moon_waxing_crescent
    MoonPhase.FIRST_QUARTER -> R.string.moon_first_quarter
    MoonPhase.WAXING_GIBBOUS -> R.string.moon_waxing_gibbous
    MoonPhase.FULL_MOON -> R.string.moon_full
    MoonPhase.WANING_GIBBOUS -> R.string.moon_waning_gibbous
    MoonPhase.LAST_QUARTER -> R.string.moon_last_quarter
    MoonPhase.WANING_CRESCENT -> R.string.moon_waning_crescent
}

