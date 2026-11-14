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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.unit.ColorProvider
import dev.antomanc.ameki.core.model.DailyForecast
import dev.antomanc.ameki.core.model.HourlyForecast
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
    val today = data.weather.snapshot?.today
    val low = minOf(today?.temperatureMin ?: c.temperature, c.temperature)
    val high = maxOf(today?.temperatureMax ?: c.temperature, c.temperature)
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
            contentDescription = context.getString(dev.antomanc.ameki.core.ui.R.string.cd_temperature_dial, units.temp(c.temperature), units.temp(low), units.temp(high))
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

// ---------------------------------------------------------------- Compact (2×1 → 2×2+)

class CompactWidget : AmekiWidget(setOf(S2x1, S2x2, S3x2)) {
    @Composable
    override fun Content(data: WidgetData, palette: WidgetPalette, fmt: TimeFormatter) {
        val size = LocalSize.current
        val context = LocalContext.current
        val c = data.weather!!.snapshot!!.current ?: return
        val units = data.settings.units
        val today = data.weather.snapshot!!.today
        when {
            size.height < 90.dp -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                ConditionIcon(c.condition, c.isDaytime, 32.dp, palette)
                Spacer(GlanceModifier.width(8.dp))
                TempText(c.temperature, units, palette, 28)
                Spacer(GlanceModifier.defaultWeight())
                today?.let { Text(context.getString(dev.antomanc.ameki.core.ui.R.string.high_low, units.temp(it.temperatureMax), units.temp(it.temperatureMin)), style = textStyle(palette.onSurfaceVariant, 12.sp)) }
            }
            size.width < 170.dp -> Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Dial(data, palette, minOf(size.width, size.height) - 20.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ConditionIcon(c.condition, c.isDaytime, 22.dp, palette)
                        TempText(c.temperature, units, palette, 30)
                        Text(data.weather.place.name, style = textStyle(palette.onSurfaceVariant, 10.sp), maxLines = 1)
                    }
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
                    today?.let { Text(context.getString(dev.antomanc.ameki.core.ui.R.string.high_low, units.temp(it.temperatureMax), units.temp(it.temperatureMin)), style = textStyle(palette.onSurfaceVariant, 12.sp)) }
                    Spacer(GlanceModifier.height(4.dp))
                    Freshness(data, palette, fmt)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Hourly (4×1 → 5×2)

@Composable
private fun HourStrip(hours: List<HourlyForecast>, data: WidgetData, palette: WidgetPalette, fmt: TimeFormatter, showRain: Boolean) {
    val s = data.weather!!.snapshot!!
    val units = data.settings.units
    val context = LocalContext.current
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        hours.forEachIndexed { i, h ->
            Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (i == 0) context.getString(dev.antomanc.ameki.core.ui.R.string.now) else fmt.hour(h.time, s.timeZone),
                    style = textStyle(if (i == 0) palette.now else palette.onSurfaceVariant, 11.sp),
                )
                ConditionIcon(h.condition, h.isDaytime, 26.dp, palette)
                Text(units.temp(h.temperature), style = textStyle(palette.onSurface, 14.sp, FontWeight.Medium))
                if (showRain) RainDot(h.precipitationProbability, 14.dp, palette)
            }
        }
    }
}

class HourlyWidget : AmekiWidget(setOf(S4x1, S4x2, S5x2)) {
    @Composable
    override fun Content(data: WidgetData, palette: WidgetPalette, fmt: TimeFormatter) {
        val size = LocalSize.current
        val s = data.weather!!.snapshot!!
        val c = s.current
        val units = data.settings.units
        val count = if (size.width >= 300.dp) 8 else 6
        val hours = s.hoursFrom(Instant.now()).take(count)
        if (size.height < 90.dp || c == null) {
            HourStrip(hours, data, palette, fmt, showRain = false)
            return
        }
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TempText(c.temperature, units, palette, 30)
                Spacer(GlanceModifier.width(8.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text(data.weather.place.name, style = textStyle(palette.onSurface, 13.sp, FontWeight.Medium), maxLines = 1)
                    Text(c.conditionText ?: LocalContext.current.getString(c.condition.label(c.isDaytime)), style = textStyle(palette.onSurfaceVariant, 11.sp), maxLines = 1)
                }
                Freshness(data, palette, fmt)
            }
            Spacer(GlanceModifier.defaultWeight())
            HourStrip(hours, data, palette, fmt, showRain = true)
        }
    }
}

// ---------------------------------------------------------------- Daily (4×2 → 4×4)

@Composable
private fun DayRows(days: List<DailyForecast>, data: WidgetData, palette: WidgetPalette, fmt: TimeFormatter, rowHeight: Dp) {
    val context = LocalContext.current
    val units = data.settings.units
    val min = days.minOf { it.temperatureMin }
    val max = days.maxOf { it.temperatureMax }
    val night = palette.dark ?: context.isNight()
    val barWidth = (LocalSize.current.width - 28.dp - 150.dp).coerceAtLeast(40.dp)
    days.forEachIndexed { i, d ->
        Row(GlanceModifier.fillMaxWidth().height(rowHeight), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (i == 0) context.getString(dev.antomanc.ameki.core.ui.R.string.today) else fmt.weekdayShort(d.date),
                style = textStyle(palette.onSurface, 13.sp), modifier = GlanceModifier.width(44.dp),
            )
            ConditionIcon(d.condition, true, 22.dp, palette)
            Spacer(GlanceModifier.width(6.dp))
            Text(units.temp(d.temperatureMin), style = textStyle(palette.onSurfaceVariant, 13.sp).copy(textAlign = TextAlign.End), modifier = GlanceModifier.width(30.dp))
            Spacer(GlanceModifier.width(6.dp))
            // Range bar: a track with the day's span placed on the week's scale.
            val total = (max - min).coerceAtLeast(1.0)
            val start = (barWidth.value * ((d.temperatureMin - min) / total)).toFloat().dp
            val len = (barWidth.value * ((d.temperatureMax - d.temperatureMin) / total)).toFloat().coerceAtLeast(6f).dp
            Box(GlanceModifier.width(barWidth).height(6.dp).cornerRadius(3.dp).background(ColorProvider(Color(palette.track.getColor(context).value).copy(alpha = 0.3f)))) {
                Row {
                    Spacer(GlanceModifier.width(start))
                    Box(GlanceModifier.width(len).height(6.dp).cornerRadius(3.dp).background(ColorProvider(palette.temperatureColor((d.temperatureMin + d.temperatureMax) / 2, night)))) {}
                }
            }
            Spacer(GlanceModifier.width(6.dp))
            Text(units.temp(d.temperatureMax), style = textStyle(palette.onSurface, 13.sp, FontWeight.Medium), modifier = GlanceModifier.width(30.dp))
        }
    }
}

class DailyWidget : AmekiWidget(setOf(S4x2, S4x3, S4x4)) {
    @Composable
    override fun Content(data: WidgetData, palette: WidgetPalette, fmt: TimeFormatter) {
        val size = LocalSize.current
        val s = data.weather!!.snapshot!!
        val c = s.current
        val units = data.settings.units
        val rows = when { size.height >= 240.dp -> 7; size.height >= 170.dp -> 5; else -> 3 }
        Column(GlanceModifier.fillMaxSize()) {
            if (c != null) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ConditionIcon(c.condition, c.isDaytime, 28.dp, palette)
                    Spacer(GlanceModifier.width(6.dp))
                    TempText(c.temperature, units, palette, 26)
                    Spacer(GlanceModifier.width(8.dp))
                    Text(data.weather.place.name, style = textStyle(palette.onSurface, 13.sp, FontWeight.Medium), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                    Freshness(data, palette, fmt)
                }
                Spacer(GlanceModifier.height(4.dp))
            }
            DayRows(s.daily.take(rows), data, palette, fmt, if (rows >= 5) 26.dp else 22.dp)
        }
    }
}

// ---------------------------------------------------------------- Complete (4×3 → 5×5)

class CompleteWidget : AmekiWidget(setOf(S4x3, S4x4, S5x5)) {
    @Composable
    override fun Content(data: WidgetData, palette: WidgetPalette, fmt: TimeFormatter) {
        val size = LocalSize.current
        val context = LocalContext.current
        val s = data.weather!!.snapshot!!
        val c = s.current ?: return
        val units = data.settings.units
        val alert = s.alerts.firstOrNull()
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Dial(data, palette, if (size.height >= 300.dp) 112.dp else 88.dp) {
                    TempText(c.temperature, units, palette, if (size.height >= 300.dp) 32 else 26)
                }
                Spacer(GlanceModifier.width(12.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text(data.weather.place.name, style = textStyle(palette.onSurface, 16.sp, FontWeight.Medium), maxLines = 1)
                    Text(c.conditionText ?: context.getString(c.condition.label(c.isDaytime)), style = textStyle(palette.onSurfaceVariant, 12.sp), maxLines = 1)
                    c.feelsLike?.let { Text(context.getString(dev.antomanc.ameki.core.ui.R.string.feels_like, units.temp(it)), style = textStyle(palette.onSurfaceVariant, 12.sp)) }
                    Spacer(GlanceModifier.height(2.dp))
                    Freshness(data, palette, fmt)
                }
                ConditionIcon(c.condition, c.isDaytime, 40.dp, palette)
            }
            if (alert != null) {
                Spacer(GlanceModifier.height(6.dp))
                Box(GlanceModifier.fillMaxWidth().cornerRadius(12.dp).background(palette.now).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(alert.title, style = textStyle(ColorProvider(Color.White), 12.sp, FontWeight.Medium), maxLines = 1)
                }
            }
            Spacer(GlanceModifier.height(8.dp))
            HourStrip(s.hoursFrom(Instant.now()).take(if (size.width >= 300.dp) 7 else 6), data, palette, fmt, showRain = size.height >= 240.dp)
            Spacer(GlanceModifier.height(6.dp))
            val rows = when { size.height >= 300.dp -> 5; size.height >= 240.dp -> 3; else -> 2 }
            DayRows(s.daily.take(rows), data, palette, fmt, 24.dp)
        }
    }
}


// ---------------------------------------------------------------- Rain (2×2 → 4×2)

class RainWidget : AmekiWidget(setOf(S2x2, S4x2)) {
    @Composable
    override fun Content(data: WidgetData, palette: WidgetPalette, fmt: TimeFormatter) {
        val context = LocalContext.current
        val size = LocalSize.current
        val s = data.weather!!.snapshot!!
        val now = Instant.now()
        val outlook = dev.antomanc.ameki.core.model.Nowcast.outlook(s, now)
        val sentence = when (outlook) {
            dev.antomanc.ameki.core.model.RainOutlook.NoneSoon -> context.getString(dev.antomanc.ameki.core.ui.R.string.rain_none_2h)
            dev.antomanc.ameki.core.model.RainOutlook.RainingNow -> context.getString(dev.antomanc.ameki.core.ui.R.string.rain_now)
            is dev.antomanc.ameki.core.model.RainOutlook.StopsIn -> context.getString(dev.antomanc.ameki.core.ui.R.string.rain_stops_in, outlook.minutes)
            is dev.antomanc.ameki.core.model.RainOutlook.StartsIn -> context.getString(dev.antomanc.ameki.core.ui.R.string.rain_in, outlook.minutes)
            is dev.antomanc.ameki.core.model.RainOutlook.LikelyAround -> context.getString(dev.antomanc.ameki.core.ui.R.string.rain_in_hours, fmt.time(outlook.at, s.timeZone))
        }
        val slots = s.nowcast.filter { it.time.plusSeconds(900) > now }.take(8)
        val wide = size.width >= 200.dp
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    context.getString(R.string.widget_rain_name).uppercase(),
                    style = textStyle(palette.onSurfaceVariant, 10.sp, FontWeight.Medium),
                    modifier = GlanceModifier.defaultWeight(),
                )
                if (wide) Freshness(data, palette, fmt)
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
                            RainDot((dev.antomanc.ameki.core.model.Nowcast.intensity(slot.precipitation) * 100).toInt(), dot, palette)
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
            } else {
                // No 15-minute data (e.g. Google): the next hours' probability instead.
                HourStrip(s.hoursFrom(now).take(if (wide) 6 else 3), data, palette, fmt, showRain = true)
            }
        }
    }
}
