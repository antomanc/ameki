package dev.antomanc.ameki.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
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
import dev.antomanc.ameki.core.ui.theme.LocalInstrumentColors
import java.time.Instant
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
internal fun HourlyCharts(hours: List<HourlyForecast>, s: WeatherSnapshot, units: Units, fmt: TimeFormatter) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(R.string.tab_precipitation, R.string.tab_wind, R.string.tab_humidity)
    val colors = LocalInstrumentColors.current
    val onVariant = MaterialTheme.colorScheme.onSurfaceVariant
    Card(null) {
        if (LocalDensity.current.fontScale > 1.3f) {
            Column {
                tabs.forEachIndexed { i, t ->
                    androidx.compose.material3.FilterChip(selected = tab == i, onClick = { tab = i },
                        label = { Text(stringResource(t)) }, modifier = Modifier.fillMaxWidth())
                }
            }
        } else SecondaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(t)) }) }
        }
        Spacer(Modifier.height(12.dp))
        val values: List<Double?> = when (tab) {
            0 -> hours.map { it.precipitation }
            1 -> hours.map { it.windSpeed }
            else -> hours.map { it.humidity?.toDouble() }
        }
        val available = values.filterNotNull()
        val summary = if (available.isEmpty()) stringResource(R.string.chart_no_data) else when (tab) {
            0 -> units.precipitation(available.sum())
            1 -> units.wind(available.maxOrNull() ?: 0.0)
            else -> "${available.average().roundToInt()}%"
        }
        if (available.size != values.size) Text(stringResource(R.string.chart_coverage, available.size, values.size), style = MaterialTheme.typography.bodySmall)
        val peakIndex = values.indices.filter { values[it] != null }.maxByOrNull { values[it]!! }
        val peakLabel = peakIndex?.let { stringResource(R.string.chart_peak, fmt.time(hours[it].time, s.timeZone)) }
        Text(summary, style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(listOf(R.string.chart_precip_total, R.string.chart_wind_max, R.string.chart_humidity_avg)[tab]),
            style = MaterialTheme.typography.bodySmall, color = onVariant,
        )
        peakLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(12.dp))
        val barColor = when (tab) { 0 -> colors.rain; 1 -> colors.mild; else -> colors.cold }
        val maxV = when (tab) { 0 -> maxOf(available.maxOrNull() ?: 0.0, 2.0); 1 -> maxOf(available.maxOrNull() ?: 0.0, 20.0); else -> 100.0 }
        Canvas(Modifier.fillMaxWidth().height(96.dp).semantics { contentDescription = listOfNotNull(summary, peakLabel).joinToString(". ") }) {
            val n = values.size.coerceAtLeast(1)
            val w = size.width / n
            values.forEachIndexed { i, v ->
                if (v == null) return@forEachIndexed
                val h = (v / maxV).toFloat().coerceIn(0f, 1f) * size.height
                val x = i * w + w / 2
                drawLine(colors.track, Offset(x, size.height), Offset(x, 0f), strokeWidth = w * 0.42f, cap = StrokeCap.Round, alpha = 0.5f)
                if (h > 0.5f) drawLine(barColor, Offset(x, size.height), Offset(x, size.height - h), strokeWidth = w * 0.42f, cap = StrokeCap.Round)
                if (tab == 1) {
                    hours[i].windDirection?.let { dir ->
                        rotate(dir.toFloat() + 180f, Offset(x, 10f)) {
                            val p = Path().apply { moveTo(x, 4f); lineTo(x - 4f, 14f); lineTo(x + 4f, 14f); close() }
                            drawPath(p, onVariant)
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
        c?.humidity?.let { h -> add { m -> SimpleTile(stringResource(R.string.humidity), "$h%", c.dewPoint?.let { stringResource(R.string.dew_point, units.temp(it)) }, m) } }
        if (Capability.UV in caps) (c?.uvIndex ?: today?.uvIndexMax)?.let { uv -> add { m -> UvTile(uv, m) } }
        c?.pressure?.let { p -> add { m -> SimpleTile(stringResource(R.string.pressure), units.pressure(p), null, m) } }
        if (Capability.VISIBILITY in caps) c?.visibility?.let { v -> add { m -> SimpleTile(stringResource(R.string.visibility), units.distance(v), null, m) } }
        if (Capability.SUN_EVENTS in caps) today?.let { d -> val rise = d.sunrise; val set = d.sunset; if (rise != null && set != null) add { m -> SunTile(rise, set, view.now, s, fmt, m) } }
        if (Capability.MOON_PHASE in caps) today?.let { d -> add { m -> MoonTile(d.moonPhase ?: Astronomy.moonPhase(d.date), Astronomy.illumination(d.date), m) } }
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
    Card(title, modifier.heightIn(min = 150.dp)) { content() }
}

@Composable
private fun SimpleTile(title: String, value: String, sub: String?, modifier: Modifier) = Tile(title, modifier) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        sub?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun WindTile(speed: Double, gust: Double?, dir: Int?, units: Units, modifier: Modifier) = Tile(stringResource(R.string.wind), modifier) {
    val colors = LocalInstrumentColors.current
    val outline = MaterialTheme.colorScheme.outlineVariant
    val labels = stringResource(R.string.compass_points).split(",")
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(units.wind(speed), style = MaterialTheme.typography.headlineMedium)
            val sub = listOfNotNull(dir?.let { labels.getOrElse(compassPoint(it)) { "" } }, gust?.let { stringResource(R.string.gusts, units.wind(it)) })
            sub.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (dir != null) {
            Canvas(Modifier.size(56.dp)) {
                val r = size.minDimension / 2 - 2
                val c = Offset(size.width / 2, size.height / 2)
                drawCircle(outline, r, c, style = Stroke(2f))
                for (i in 0 until 16) {
                    val a = i * PI / 8
                    val len = if (i % 4 == 0) 7f else 3f
                    drawLine(outline, Offset(c.x + (r - len) * sin(a).toFloat(), c.y - (r - len) * cos(a).toFloat()), Offset(c.x + r * sin(a).toFloat(), c.y - r * cos(a).toFloat()), 2f)
                }
                // The arrow points where the wind blows to.
                rotate(dir + 180f, c) {
                    val p = Path().apply { moveTo(c.x, c.y - r + 4); lineTo(c.x - 6, c.y - r + 18); lineTo(c.x + 6, c.y - r + 18); close() }
                    drawLine(colors.now, Offset(c.x, c.y + r - 8), Offset(c.x, c.y - r + 12), 4f, StrokeCap.Round)
                    drawPath(p, colors.now)
                }
            }
        }
    }
}

@Composable
private fun UvTile(uv: Double, modifier: Modifier) = Tile(stringResource(R.string.uv_index), modifier) {
    val colors = LocalInstrumentColors.current
    val level = when {
        uv < 3 -> R.string.uv_low; uv < 6 -> R.string.uv_moderate; uv < 8 -> R.string.uv_high
        uv < 11 -> R.string.uv_very_high; else -> R.string.uv_extreme
    }
    Column {
        Text(uv.roundToInt().toString(), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(level), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            drawLine(colors.track, Offset(3f, 3.dp.toPx() / 2), Offset(size.width - 3f, 3.dp.toPx() / 2), size.height, StrokeCap.Round)
            val x = (uv / 11.0).toFloat().coerceIn(0f, 1f) * size.width
            drawCircle(colors.now, size.height, Offset(x.coerceIn(size.height, size.width - size.height), size.height / 2))
        }
    }
}

@Composable
private fun SunTile(rise: Instant, set: Instant, now: Instant, s: WeatherSnapshot, fmt: TimeFormatter, modifier: Modifier) = Tile(stringResource(R.string.sun), modifier) {
    val colors = LocalInstrumentColors.current
    val outline = MaterialTheme.colorScheme.outlineVariant
    val f = ((now.epochSecond - rise.epochSecond).toDouble() / (set.epochSecond - rise.epochSecond)).coerceIn(0.0, 1.0)
    Column {
        Canvas(Modifier.fillMaxWidth().height(56.dp)) {
            val w = size.width; val h = size.height
            drawArc(outline, 180f, 180f, false, Offset(4f, 4f), Size(w - 8, (h - 4) * 2), style = Stroke(3f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f))))
            val a = PI * (1 - f)
            val x = w / 2 + (w / 2 - 4) * cos(a).toFloat()
            val y = h - (h - 4) * sin(a).toFloat()
            drawCircle(colors.warm, 9f, Offset(x, y))
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.sunrise_at, fmt.time(rise, s.timeZone)), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.sunset_at, fmt.time(set, s.timeZone)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MoonTile(phase: MoonPhase, illumination: Double, modifier: Modifier) = Tile(stringResource(R.string.moon), modifier) {
    val lit = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
    val dark = MaterialTheme.colorScheme.surfaceContainerHighest
    val waxing = phase.ordinal in 1..3
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(52.dp)) {
            val r = size.minDimension / 2
            val c = Offset(size.width / 2, size.height / 2)
            drawCircle(dark, r, c)
            // Lit half on the side the sun is: right while waxing, left while waning.
            drawArc(lit, if (waxing) -90f else 90f, 180f, true, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r))
            // Terminator: an ellipse that adds light past half, or takes it away before half.
            val w = (r * kotlin.math.abs(1 - 2 * illumination)).toFloat()
            drawOval(if (illumination > 0.5) lit else dark, Offset(c.x - w, c.y - r), Size(2 * w, 2 * r))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(stringResource(moonLabel(phase)), style = MaterialTheme.typography.titleMedium)
            Text("${(illumination * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
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
