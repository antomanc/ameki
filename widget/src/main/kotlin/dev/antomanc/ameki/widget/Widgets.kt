package dev.antomanc.ameki.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.unit.ColorProvider
import dev.antomanc.ameki.core.model.DailyForecast
import dev.antomanc.ameki.core.model.HourlyForecast
import dev.antomanc.ameki.core.model.Nowcast
import dev.antomanc.ameki.core.model.RainOutlook
import dev.antomanc.ameki.core.model.Units
import dev.antomanc.ameki.core.ui.TimeFormatter
import dev.antomanc.ameki.core.ui.label
import java.time.Instant

// Size buckets in dp, close to launcher cells: 2×1, 2×2, 4×1, 4×2, 4×3, 5×5...
private val S2x1 = DpSize(110.dp, 48.dp)
private val S2x2 = DpSize(110.dp, 110.dp)
private val S3x2 = DpSize(180.dp, 110.dp)
private val S4x1 = DpSize(250.dp, 48.dp)
private val S4x2 = DpSize(250.dp, 110.dp)
private val S5x2 = DpSize(320.dp, 110.dp)
private val S4x3 = DpSize(250.dp, 180.dp)
private val S4x4 = DpSize(250.dp, 250.dp)
private val S5x5 = DpSize(320.dp, 320.dp)

@Composable
private fun Dial(data: WidgetData, palette: WidgetPalette, size: Dp, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val c = data.weather?.snapshot?.current ?: return
    val today = data.weather.snapshot?.today(Instant.now())
    val low = today?.temperatureMin ?: c.temperature
    val high = today?.temperatureMax ?: c.temperature
    val night = palette.dark ?: context.isNight()
    val px = (size.value * context.resources.displayMetrics.density).toInt().coerceIn(64, 900)
    val bmp = DialBitmap.draw(
        px, c.temperature, low, high,
        track = Color(palette.track.getColor(context).value).copy(alpha = 0.35f),
        lowColor = palette.temperatureColor(low, night),
        highColor = palette.temperatureColor(high, night),
        now = if (night) Color(0xFFFF6A36) else Color(0xFFE2501F),
    )
    val units = data.settings.units
    Box(
        GlanceModifier.size(size).semantics {
            contentDescription = context.getString(dev.antomanc.ameki.core.ui.R.string.cd_temperature_dial, units.temp(c.temperature), units.temp(low), units.temp(high), c.feelsLike?.let { context.getString(dev.antomanc.ameki.core.ui.R.string.feels_like, units.temp(it)) } ?: "")
        },
        contentAlignment = Alignment.Center,
    ) {
        Image(ImageProvider(bmp), contentDescription = null, modifier = GlanceModifier.fillMaxSize())
        content()
    }
}

@Composable
private fun TempText(celsius: Double, units: Units, palette: WidgetPalette, size: Int, light: Boolean = true) =
    Text(units.temp(celsius), style = textStyle(palette.onSurface, size.sp, FontWeight.Normal, light = light))

/** High and low from the same daily row the dial uses, with explicit meaning for TalkBack. */
@Composable
private fun HighLow(today: DailyForecast?, units: Units, palette: WidgetPalette, size: Int = 12) {
    today ?: return
    val context = LocalContext.current
    Text(
        context.getString(R.string.widget_high_low, units.temp(today.temperatureMax), units.temp(today.temperatureMin)),
        style = textStyle(palette.onSurfaceVariant, size.sp),
        maxLines = 1,
        modifier = GlanceModifier.semantics {
            contentDescription = context.getString(R.string.widget_high_low_cd, units.temp(today.temperatureMax), units.temp(today.temperatureMin))
        },
    )
}

// ---------------------------------------------------------------- Compact (2×1 → 2×2+)

class CompactWidget : AmekiWidget(setOf(S2x1, S2x2, S3x2)) {
    override val need = WidgetNeed.CURRENT

    @Composable
    override fun Content(data: WidgetData, palette: WidgetPalette, fmt: TimeFormatter) {
        val size = LocalSize.current
        val context = LocalContext.current
        val s = data.weather!!.snapshot!!
        val c = s.current ?: return
        val units = data.settings.units
        when {
            size.height < 90.dp -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                ConditionIcon(c.condition, c.isDaytime, 24.dp, palette)
                Spacer(GlanceModifier.width(6.dp))
                TempText(c.temperature, units, palette, 22)
                Spacer(GlanceModifier.width(6.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text(data.weather.place.name, style = textStyle(palette.onSurface, 10.sp, FontWeight.Medium), maxLines = 1)
                    Freshness(data, palette, fmt, compact = true)
                }
            }
            size.width < 170.dp -> Column(GlanceModifier.fillMaxSize()) {
                Box(GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
                    Dial(data, palette, minOf(size.width, size.height) - 48.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ConditionIcon(c.condition, c.isDaytime, 20.dp, palette)
                            TempText(c.temperature, units, palette, 26)
                        }
                    }
                }
                Text(
                    data.weather.place.name,
                    style = textStyle(palette.onSurface, 10.sp, FontWeight.Medium),
                    maxLines = 1,
                    modifier = GlanceModifier.fillMaxWidth().semantics { contentDescription = data.weather.place.name },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HighLow(s.today(Instant.now()), units, palette, 9)
                    Spacer(GlanceModifier.width(6.dp))
                    Freshness(data, palette, fmt, compact = true)
                }
            }
            else -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Dial(data, palette, size.height - 28.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ConditionIcon(c.condition, c.isDaytime, 22.dp, palette)
                        TempText(c.temperature, units, palette, 28)
                    }
                }
                Spacer(GlanceModifier.width(10.dp))
                Column {
                    Text(data.weather.place.name, style = textStyle(palette.onSurface, 14.sp, FontWeight.Medium), maxLines = 1)
                    Text(c.conditionText ?: context.getString(c.condition.label(c.isDaytime)), style = textStyle(palette.onSurfaceVariant, 12.sp), maxLines = 2)
                    HighLow(s.today(Instant.now()), units, palette, 12)
                    Spacer(GlanceModifier.height(2.dp))
                    Freshness(data, palette, fmt)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Hourly (4×1 → 5×2)

@Composable
private fun HourStrip(
    hours: List<HourlyForecast>,
    data: WidgetData,
    palette: WidgetPalette,
    fmt: TimeFormatter,
    showRain: Boolean,
    large: Boolean = false,
    tiny: Boolean = false,
    compact: Boolean = false,
    modifier: GlanceModifier = GlanceModifier.fillMaxWidth(),
    trailing: (@Composable () -> Unit)? = null,
) {
    val s = data.weather!!.snapshot!!
    val units = data.settings.units
    val context = LocalContext.current
    val nowFresh = s.currentIsFresh(Instant.now())
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        hours.forEachIndexed { i, h ->
            val label = if (i == 0 && nowFresh) context.getString(dev.antomanc.ameki.core.ui.R.string.now) else fmt.hour(h.time, s.timeZone)
            val day = h.time.atZone(s.timeZone).toLocalDate()
            Column(
                GlanceModifier.defaultWeight().height(if (tiny) 48.dp else 80.dp)
                    .clickable(
                        androidx.glance.appwidget.action.actionStartActivity(detailIntent(context, data.weather.place.id, day.toEpochDay(), h.time.epochSecond)),
                    )
                    .semantics {
                        contentDescription = context.getString(
                            R.string.widget_hour_cd, label, units.temp(h.temperature),
                        )
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (tiny) {
                    // A 1-row widget has ~30 dp of content: label and icon share a line.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = textStyle(palette.onSurfaceVariant, 9.sp), maxLines = 1)
                        Spacer(GlanceModifier.width(2.dp))
                        ConditionIcon(h.condition, h.isDaytime, 16.dp, palette)
                    }
                    Text(units.temp(h.temperature), style = textStyle(palette.onSurface, 12.sp, FontWeight.Medium), maxLines = 1)
                } else {
                    Text(label, style = textStyle(if (i == 0 && nowFresh) palette.now else palette.onSurfaceVariant, if (large) 12.sp else if (compact) 10.sp else 11.sp), maxLines = 1)
                    Spacer(GlanceModifier.height(2.dp))
                    ConditionIcon(h.condition, h.isDaytime, if (large) 34.dp else if (compact) 22.dp else 28.dp, palette)
                    Spacer(GlanceModifier.height(2.dp))
                    Text(units.temp(h.temperature), style = textStyle(palette.onSurface, if (large) 16.sp else if (compact) 13.sp else 14.sp, FontWeight.Medium), maxLines = 1)
                    if (showRain) {
                        Spacer(GlanceModifier.height(4.dp))
                        RainDot(h.precipitationProbability, 14.dp, palette)
                        if (large) Text("${h.precipitationProbability ?: 0}%", style = textStyle(palette.rain, 10.sp))
                    }
                }
            }
        }
        trailing?.let { stamp -> Box(GlanceModifier.width(42.dp)) { stamp() } }
    }
}

class HourlyWidget : AmekiWidget(setOf(S4x1, S4x2, S5x2)) {
    override val need = WidgetNeed.HOURLY

    @Composable
    override fun Content(data: WidgetData, palette: WidgetPalette, fmt: TimeFormatter) {
        val size = LocalSize.current
        val s = data.weather!!.snapshot!!
        val c = s.current
        val units = data.settings.units
        val count = if (size.width >= 300.dp) 5 else 4
        val hours = s.hoursFrom(Instant.now()).take(count)
        if (size.height < 90.dp) {
            // One row: the hours keep their labels and temperatures, the stamp gets its own slot.
            HourStrip(
                hours.take(count), data, palette, fmt, showRain = false, tiny = true,
                modifier = GlanceModifier.fillMaxSize(),
                trailing = { Freshness(data, palette, fmt, compact = true) },
            )
            return
        }
        Column(GlanceModifier.fillMaxSize()) {
            val dense = size.height < 150.dp
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (c != null) {
                    ConditionIcon(c.condition, c.isDaytime, if (dense) 26.dp else 30.dp, palette)
                    Spacer(GlanceModifier.width(6.dp))
                    TempText(c.temperature, units, palette, if (dense) 26 else 30)
                    Spacer(GlanceModifier.width(10.dp))
                }
                Column(GlanceModifier.defaultWeight()) {
                    Text(data.weather.place.name, style = textStyle(palette.onSurface, 14.sp, FontWeight.Medium), maxLines = 1)
                    c?.let { Text(it.conditionText ?: LocalContext.current.getString(it.condition.label(it.isDaytime)), style = textStyle(palette.onSurfaceVariant, 12.sp), maxLines = 1) }
                }
                Freshness(data, palette, fmt)
            }
            // The strip takes all the remaining height and centres in it: no dead band.
            HourStrip(
                hours, data, palette, fmt,
                showRain = !dense,
                large = size.height >= 150.dp,
                tiny = dense,
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            )
        }
    }
}

// ---------------------------------------------------------------- Daily (4×2 → 4×4)

@Composable
private fun DayRows(days: List<DailyForecast>, data: WidgetData, palette: WidgetPalette, fmt: TimeFormatter, modifier: GlanceModifier) {
    if (days.isEmpty()) return
    val placeId = data.weather?.place?.id ?: return
    val context = LocalContext.current
    val units = data.settings.units
    val min = days.minOf { it.temperatureMin }
    val max = days.maxOf { it.temperatureMax }
    val night = palette.dark ?: context.isNight()
    val wide = LocalSize.current.width >= 280.dp
    val barWidth = (LocalSize.current.width - 28.dp - (if (wide) 190.dp else 150.dp)).coerceAtLeast(40.dp)
    val density = context.resources.displayMetrics.density
    val track = Color(palette.track.getColor(context).value).copy(alpha = 0.25f)
    Column(modifier) {
        days.forEachIndexed { i, d ->
            val label = if (d.date == java.time.LocalDate.now(data.weather!!.snapshot!!.timeZone)) context.getString(dev.antomanc.ameki.core.ui.R.string.today) else fmt.weekdayShort(d.date)
            // Every row takes an equal share of the height, so the widget is filled at any size.
            Row(
                GlanceModifier.fillMaxWidth().height(48.dp)
                    .clickable(
                        androidx.glance.appwidget.action.actionStartActivity(detailIntent(context, placeId, d.date.toEpochDay(), null)),
                    )
                    .semantics {
                        contentDescription = context.getString(
                            R.string.widget_day_cd, label, units.temp(d.temperatureMax), units.temp(d.temperatureMin),
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    style = textStyle(palette.onSurface, 14.sp), modifier = GlanceModifier.width(44.dp), maxLines = 1,
                )
                ConditionIcon(d.condition, true, 26.dp, palette)
                if (wide) {
                    Text(
                        d.precipitationProbability?.takeIf { it >= 10 }?.let { "$it%" } ?: "",
                        style = textStyle(palette.rain, 11.sp), modifier = GlanceModifier.width(40.dp).padding(start = 4.dp),
                    )
                } else {
                    Spacer(GlanceModifier.width(6.dp))
                }
                Text(units.temp(d.temperatureMin), style = textStyle(palette.onSurfaceVariant, 14.sp).copy(textAlign = TextAlign.End), modifier = GlanceModifier.width(32.dp))
                Spacer(GlanceModifier.width(8.dp))
                val total = (max - min).coerceAtLeast(1.0)
                val bmp = RangeBitmap.draw(
                    (barWidth.value * density).toInt(), (6 * density).toInt(),
                    ((d.temperatureMin - min) / total).toFloat(), ((d.temperatureMax - min) / total).toFloat(),
                    track, palette.temperatureColor(d.temperatureMin, night), palette.temperatureColor(d.temperatureMax, night),
                )
                Image(ImageProvider(bmp), contentDescription = null, modifier = GlanceModifier.width(barWidth).height(6.dp))
                Spacer(GlanceModifier.width(8.dp))
                Text(units.temp(d.temperatureMax), style = textStyle(palette.onSurface, 14.sp, FontWeight.Medium), modifier = GlanceModifier.width(32.dp))
            }
        }
    }
}

class DailyWidget : AmekiWidget(setOf(S4x2, S4x3, S4x4)) {
    override val need = WidgetNeed.DAILY

    @Composable
    override fun Content(data: WidgetData, palette: WidgetPalette, fmt: TimeFormatter) {
        val size = LocalSize.current
        val s = data.weather!!.snapshot!!
        val c = s.current
        val units = data.settings.units
        // Rows sized ~34 dp each: as many days as fit, then they share the height evenly.
        val rows = ((size.height.value - 60f) / 48f).toInt().coerceIn(1, s.daily.size.coerceAtLeast(1))
        val dense = size.height < 150.dp
        Column(GlanceModifier.fillMaxSize()) {
            if (c != null) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ConditionIcon(c.condition, c.isDaytime, if (dense) 26.dp else 32.dp, palette)
                    Spacer(GlanceModifier.width(6.dp))
                    TempText(c.temperature, units, palette, if (dense) 24 else 30)
                    Spacer(GlanceModifier.width(10.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Text(data.weather.place.name, style = textStyle(palette.onSurface, 14.sp, FontWeight.Medium), maxLines = 1)
                        Text(c.conditionText ?: LocalContext.current.getString(c.condition.label(c.isDaytime)), style = textStyle(palette.onSurfaceVariant, 12.sp), maxLines = 1)
                    }
                    Freshness(data, palette, fmt)
                }
                Spacer(GlanceModifier.height(6.dp))
            } else {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(data.weather.place.name, style = textStyle(palette.onSurface, 14.sp, FontWeight.Medium), modifier = GlanceModifier.defaultWeight(), maxLines = 1)
                    Freshness(data, palette, fmt)
                }
                Spacer(GlanceModifier.height(6.dp))
            }
            DayRows(s.daily.take(rows), data, palette, fmt, GlanceModifier.fillMaxWidth().defaultWeight())
        }
    }
}

// ---------------------------------------------------------------- Complete (4×3 → 5×5)

class CompleteWidget : AmekiWidget(setOf(S4x3, S4x4, S5x5)) {
    override val need = WidgetNeed.ANY

    @Composable
    override fun Content(data: WidgetData, palette: WidgetPalette, fmt: TimeFormatter) {
        val size = LocalSize.current
        val context = LocalContext.current
        val s = data.weather!!.snapshot!!
        val c = s.current
        val units = data.settings.units
        val alert = s.alerts.firstOrNull()
        val dense = size.height < 240.dp
        Column(GlanceModifier.fillMaxSize()) {
            if (c != null) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Dial(data, palette, when {
                        dense -> 64.dp
                        size.height >= 300.dp -> 112.dp
                        else -> 88.dp
                    }) {
                        TempText(c.temperature, units, palette, when {
                            dense -> 22
                            size.height >= 300.dp -> 32
                            else -> 26
                        })
                    }
                    Spacer(GlanceModifier.width(12.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Text(data.weather.place.name, style = textStyle(palette.onSurface, 16.sp, FontWeight.Medium), maxLines = 1)
                        Text(c.conditionText ?: context.getString(c.condition.label(c.isDaytime)), style = textStyle(palette.onSurfaceVariant, 12.sp), maxLines = 1)
                        if (!dense) c.feelsLike?.let { Text(context.getString(dev.antomanc.ameki.core.ui.R.string.feels_like, units.temp(it)), style = textStyle(palette.onSurfaceVariant, 12.sp)) }
                        Spacer(GlanceModifier.height(2.dp))
                        Freshness(data, palette, fmt)
                    }
                    ConditionIcon(c.condition, c.isDaytime, 40.dp, palette)
                }
            } else {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(data.weather.place.name, style = textStyle(palette.onSurface, 16.sp, FontWeight.Medium), modifier = GlanceModifier.defaultWeight(), maxLines = 1)
                    Freshness(data, palette, fmt)
                }
            }
            if (alert != null) {
                Spacer(GlanceModifier.height(6.dp))
                Box(GlanceModifier.fillMaxWidth().cornerRadius(12.dp).background(palette.now).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(alert.title, style = textStyle(ColorProvider(Color.White), 12.sp, FontWeight.Medium), maxLines = 1)
                }
            }
            if (s.hourly.isNotEmpty()) {
                Spacer(GlanceModifier.height(if (dense) 4.dp else 8.dp))
                HourStrip(
                    s.hoursFrom(Instant.now()).take(if (size.width >= 300.dp) 7 else 6),
                    data, palette, fmt,
                    showRain = !dense && size.height >= 240.dp,
                    large = size.height >= 300.dp,
                    compact = size.height < 300.dp,
                    tiny = dense,
                )
            }
            if (s.daily.isNotEmpty()) {
                Spacer(GlanceModifier.height(if (dense) 4.dp else 6.dp))
                val rows = ((size.height.value - if (alert != null) 220f else 180f) / 48f).toInt().coerceAtLeast(0)
                if (rows > 0) DayRows(s.daily.take(rows), data, palette, fmt, GlanceModifier.fillMaxWidth().defaultWeight())
            }
        }
    }
}


// ---------------------------------------------------------------- Rain (2×2 → 4×2)

class RainWidget : AmekiWidget(setOf(S2x2, S4x2)) {
    override val need = WidgetNeed.RAIN

    @Composable
    override fun Content(data: WidgetData, palette: WidgetPalette, fmt: TimeFormatter) {
        val context = LocalContext.current
        val size = LocalSize.current
        val s = data.weather!!.snapshot!!
        val now = Instant.now()
        val sentence = rainSummary(context, s, now, fmt)
        val slots = s.nowcast.filter { it.time.plusSeconds(900) > now }.take(8)
        val wide = size.width >= 200.dp
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    context.getString(R.string.widget_rain_name).uppercase(),
                    style = textStyle(palette.onSurfaceVariant, 10.sp, FontWeight.Medium),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Freshness(data, palette, fmt, compact = !wide)
            }
            Spacer(GlanceModifier.height(4.dp))
            Text(sentence, style = textStyle(palette.onSurface, if (wide) 17.sp else 15.sp, FontWeight.Medium), maxLines = 2)
            Spacer(GlanceModifier.defaultWeight())
            if (slots.isNotEmpty()) {
                val shown = if (wide) slots else slots.filterIndexed { i, _ -> i % 2 == 0 }
                val dot = if (wide) 20.dp else 18.dp
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    shown.forEach { slot ->
                        Box(GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                            RainDot((Nowcast.intensity(slot.precipitation) * 100).toInt(), dot, palette)
                        }
                    }
                }
                Row(GlanceModifier.fillMaxWidth()) {
                    listOf(context.getString(dev.antomanc.ameki.core.ui.R.string.now), "+1h", "+2h").forEachIndexed { i, label ->
                        Text(
                            label, style = textStyle(palette.onSurfaceVariant, 10.sp).copy(textAlign = if (i == 0) TextAlign.Start else if (i == 1) TextAlign.Center else TextAlign.End),
                            modifier = GlanceModifier.defaultWeight(),
                        )
                    }
                }
            } else if (s.hourly.isNotEmpty()) {
                // No 15-minute data (e.g. Google): the next hours' probability instead.
                HourStrip(s.hoursFrom(now).take(if (wide) 6 else 3), data, palette, fmt, showRain = true)
            }
        }
    }
}

internal fun rainSummary(context: android.content.Context, s: dev.antomanc.ameki.core.model.WeatherSnapshot, now: Instant, fmt: TimeFormatter): String {
    return when (val outlook = Nowcast.outlook(s, now)) {
            RainOutlook.Unavailable -> context.getString(R.string.widget_rain_unavailable)
            RainOutlook.NoneSoon -> context.getString(dev.antomanc.ameki.core.ui.R.string.rain_none_2h)
            RainOutlook.RainingNow -> context.getString(dev.antomanc.ameki.core.ui.R.string.rain_now)
            is RainOutlook.StopsIn -> context.getString(dev.antomanc.ameki.core.ui.R.string.rain_stops_in, outlook.minutes)
            is RainOutlook.StartsIn -> context.getString(dev.antomanc.ameki.core.ui.R.string.rain_in, outlook.minutes)
            is RainOutlook.LikelyAround -> context.getString(dev.antomanc.ameki.core.ui.R.string.rain_in_hours, fmt.timestamp(outlook.at, s.timeZone, now))
        }
}
