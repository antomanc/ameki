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
import androidx.glance.action.clickable
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
import dev.antomanc.ameki.core.model.Nowcast
import dev.antomanc.ameki.core.model.Units
import dev.antomanc.ameki.core.ui.HourView
import dev.antomanc.ameki.core.ui.TimeFormatter
import dev.antomanc.ameki.core.ui.label

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
private fun Dial(w: ReadyWidget, palette: WidgetPalette, size: Dp, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val c = w.snapshot.current ?: return
    val today = w.view.today
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
    val units = w.units
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

/** Place, current reading and the update stamp: the header of the Hourly, Daily and Complete widgets. */
@Composable
private fun Header(w: ReadyWidget, palette: WidgetPalette, fmt: TimeFormatter, iconSize: Dp, temperatureSp: Int, nameSp: Int = 14) {
    val c = w.snapshot.current
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (c != null) {
            ConditionIcon(c.condition, c.isDaytime, iconSize, palette)
            Spacer(GlanceModifier.width(6.dp))
            TempText(c.temperature, w.units, palette, temperatureSp)
            Spacer(GlanceModifier.width(10.dp))
        }
        Column(GlanceModifier.defaultWeight()) {
            Text(w.place.name, style = textStyle(palette.onSurface, nameSp.sp, FontWeight.Medium), maxLines = 1)
            c?.let { Text(it.conditionText ?: LocalContext.current.getString(it.condition.label(it.isDaytime)), style = textStyle(palette.onSurfaceVariant, 12.sp), maxLines = 1) }
        }
        Freshness(w.view, w.data.settings, palette, fmt)
    }
}

// ---------------------------------------------------------------- Compact (2×1 → 2×2+)

class CompactWidget : AmekiWidget(setOf(S2x1, S2x2, S3x2)) {
    override val need = WidgetNeed.CURRENT

    @Composable
    override fun Content(w: ReadyWidget, palette: WidgetPalette, fmt: TimeFormatter) {
        val size = LocalSize.current
        val context = LocalContext.current
        val c = w.snapshot.current ?: return
        val units = w.units
        when (WidgetLayout.compact(size)) {
            WidgetLayout.Compact.ROW -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                ConditionIcon(c.condition, c.isDaytime, 24.dp, palette)
                Spacer(GlanceModifier.width(6.dp))
                TempText(c.temperature, units, palette, 22)
                Spacer(GlanceModifier.width(6.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text(w.place.name, style = textStyle(palette.onSurface, 10.sp, FontWeight.Medium), maxLines = 1)
                    Freshness(w.view, w.data.settings, palette, fmt, compact = true)
                }
            }
            WidgetLayout.Compact.DIAL -> Column(GlanceModifier.fillMaxSize()) {
                Box(GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
                    Dial(w, palette, minOf(size.width, size.height) - 48.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ConditionIcon(c.condition, c.isDaytime, 20.dp, palette)
                            TempText(c.temperature, units, palette, 26)
                        }
                    }
                }
                Text(
                    w.place.name,
                    style = textStyle(palette.onSurface, 10.sp, FontWeight.Medium),
                    maxLines = 1,
                    modifier = GlanceModifier.fillMaxWidth().semantics { contentDescription = w.place.name },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HighLow(w.view.today, units, palette, 9)
                    Spacer(GlanceModifier.width(6.dp))
                    Freshness(w.view, w.data.settings, palette, fmt, compact = true)
                }
            }
            WidgetLayout.Compact.DIAL_AND_TEXT -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Dial(w, palette, size.height - 28.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ConditionIcon(c.condition, c.isDaytime, 22.dp, palette)
                        TempText(c.temperature, units, palette, 28)
                    }
                }
                Spacer(GlanceModifier.width(10.dp))
                Column {
                    Text(w.place.name, style = textStyle(palette.onSurface, 14.sp, FontWeight.Medium), maxLines = 1)
                    Text(c.conditionText ?: context.getString(c.condition.label(c.isDaytime)), style = textStyle(palette.onSurfaceVariant, 12.sp), maxLines = 2)
                    HighLow(w.view.today, units, palette, 12)
                    Spacer(GlanceModifier.height(2.dp))
                    Freshness(w.view, w.data.settings, palette, fmt)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Hourly (4×1 → 5×2)

@Composable
private fun HourStrip(
    hours: List<HourView>,
    w: ReadyWidget,
    palette: WidgetPalette,
    fmt: TimeFormatter,
    layout: WidgetLayout.Hours,
    modifier: GlanceModifier = GlanceModifier.fillMaxWidth(),
    trailing: (@Composable () -> Unit)? = null,
) {
    val s = w.snapshot
    val units = w.units
    val context = LocalContext.current
    val large = layout.large
    val compact = layout.compact
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        hours.forEach { hv ->
            val h = hv.forecast
            val label = if (hv.isNow) context.getString(dev.antomanc.ameki.core.ui.R.string.now) else fmt.hour(h.time, s.timeZone)
            val day = h.time.atZone(s.timeZone).toLocalDate()
            Column(
                GlanceModifier.defaultWeight().height(if (layout.tiny) WidgetLayout.ROW else 80.dp)
                    .clickable(androidx.glance.appwidget.action.actionStartActivity(detailIntent(context, w.place.id, day, h.time)))
                    .semantics { contentDescription = context.getString(R.string.widget_hour_cd, label, units.temp(hv.temperature)) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (layout.tiny) {
                    // A 1-row widget has ~30 dp of content: label and icon share a line.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = textStyle(palette.onSurfaceVariant, 9.sp), maxLines = 1)
                        Spacer(GlanceModifier.width(2.dp))
                        ConditionIcon(h.condition, h.isDaytime, 16.dp, palette)
                    }
                    Text(units.temp(hv.temperature), style = textStyle(palette.onSurface, 12.sp, FontWeight.Medium), maxLines = 1)
                } else {
                    Text(label, style = textStyle(if (hv.isNow) palette.now else palette.onSurfaceVariant, if (large) 12.sp else if (compact) 10.sp else 11.sp), maxLines = 1)
                    Spacer(GlanceModifier.height(2.dp))
                    ConditionIcon(h.condition, h.isDaytime, if (large) 34.dp else if (compact) 22.dp else 28.dp, palette)
                    Spacer(GlanceModifier.height(2.dp))
                    Text(units.temp(hv.temperature), style = textStyle(palette.onSurface, if (large) 16.sp else if (compact) 13.sp else 14.sp, FontWeight.Medium), maxLines = 1)
                    if (layout.rain) {
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
    override fun Content(w: ReadyWidget, palette: WidgetPalette, fmt: TimeFormatter) {
        val layout = WidgetLayout.hourly(LocalSize.current)
        val hours = w.view.hours.take(layout.strip.count)
        if (layout.stripOnly) {
            // One row: the hours keep their labels and temperatures, the stamp gets its own slot.
            HourStrip(
                hours, w, palette, fmt, layout.strip.copy(tiny = true, rain = false, large = false),
                modifier = GlanceModifier.fillMaxSize(),
                trailing = { Freshness(w.view, w.data.settings, palette, fmt, compact = true) },
            )
            return
        }
        Column(GlanceModifier.fillMaxSize()) {
            Header(w, palette, fmt, iconSize = if (layout.dense) 26.dp else 30.dp, temperatureSp = if (layout.dense) 26 else 30)
            // The strip takes all the remaining height and centres in it: no dead band.
            HourStrip(hours, w, palette, fmt, layout.strip, modifier = GlanceModifier.fillMaxWidth().defaultWeight())
        }
    }
}

// ---------------------------------------------------------------- Daily (4×2 → 4×4)

@Composable
private fun DayRows(days: List<DailyForecast>, w: ReadyWidget, palette: WidgetPalette, fmt: TimeFormatter, modifier: GlanceModifier) {
    if (days.isEmpty()) return
    val context = LocalContext.current
    val units = w.units
    val min = days.minOf { it.temperatureMin }
    val max = days.maxOf { it.temperatureMax }
    val night = palette.dark ?: context.isNight()
    val wide = LocalSize.current.width >= 280.dp
    val barWidth = (LocalSize.current.width - 28.dp - (if (wide) 190.dp else 150.dp)).coerceAtLeast(40.dp)
    val density = context.resources.displayMetrics.density
    val track = Color(palette.track.getColor(context).value).copy(alpha = 0.25f)
    Column(modifier) {
        days.forEach { d ->
            val label = if (w.view.isToday(d.date)) context.getString(dev.antomanc.ameki.core.ui.R.string.today) else fmt.weekdayShort(d.date)
            // Every row takes an equal share of the height, so the widget is filled at any size.
            Row(
                GlanceModifier.fillMaxWidth().height(WidgetLayout.ROW)
                    .clickable(androidx.glance.appwidget.action.actionStartActivity(detailIntent(context, w.place.id, d.date)))
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
    override fun Content(w: ReadyWidget, palette: WidgetPalette, fmt: TimeFormatter) {
        val size = LocalSize.current
        val days = w.view.days
        val dense = size.height < 150.dp
        Column(GlanceModifier.fillMaxSize()) {
            Header(w, palette, fmt, iconSize = if (dense) 26.dp else 32.dp, temperatureSp = if (dense) 24 else 30)
            Spacer(GlanceModifier.height(6.dp))
            DayRows(days.take(WidgetLayout.dailyRows(size, days.size)), w, palette, fmt, GlanceModifier.fillMaxWidth().defaultWeight())
        }
    }
}

// ---------------------------------------------------------------- Complete (4×3 → 5×5)

class CompleteWidget : AmekiWidget(setOf(S4x3, S4x4, S5x5)) {
    override val need = WidgetNeed.ANY

    @Composable
    override fun Content(w: ReadyWidget, palette: WidgetPalette, fmt: TimeFormatter) {
        val context = LocalContext.current
        val s = w.snapshot
        val c = s.current
        val units = w.units
        val alert = s.alerts.firstOrNull()
        val layout = WidgetLayout.complete(LocalSize.current, hasAlert = alert != null)
        Column(GlanceModifier.fillMaxSize()) {
            if (c != null) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Dial(w, palette, layout.dial) { TempText(c.temperature, units, palette, layout.temperatureSp) }
                    Spacer(GlanceModifier.width(12.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Text(w.place.name, style = textStyle(palette.onSurface, 16.sp, FontWeight.Medium), maxLines = 1)
                        Text(c.conditionText ?: context.getString(c.condition.label(c.isDaytime)), style = textStyle(palette.onSurfaceVariant, 12.sp), maxLines = 1)
                        if (!layout.dense) c.feelsLike?.let { Text(context.getString(dev.antomanc.ameki.core.ui.R.string.feels_like, units.temp(it)), style = textStyle(palette.onSurfaceVariant, 12.sp)) }
                        Spacer(GlanceModifier.height(2.dp))
                        Freshness(w.view, w.data.settings, palette, fmt)
                    }
                    ConditionIcon(c.condition, c.isDaytime, 40.dp, palette)
                }
            } else {
                Header(w, palette, fmt, iconSize = 0.dp, temperatureSp = 0, nameSp = 16)
            }
            if (alert != null) {
                Spacer(GlanceModifier.height(6.dp))
                Box(GlanceModifier.fillMaxWidth().cornerRadius(12.dp).background(palette.now).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(alert.title, style = textStyle(ColorProvider(Color.White), 12.sp, FontWeight.Medium), maxLines = 1)
                }
            }
            if (w.view.hours.isNotEmpty()) {
                Spacer(GlanceModifier.height(if (layout.dense) 4.dp else 8.dp))
                HourStrip(w.view.hours.take(layout.hours.count), w, palette, fmt, layout.hours)
            }
            if (w.view.days.isNotEmpty() && layout.dayRows > 0) {
                Spacer(GlanceModifier.height(if (layout.dense) 4.dp else 6.dp))
                DayRows(w.view.days.take(layout.dayRows), w, palette, fmt, GlanceModifier.fillMaxWidth().defaultWeight())
            }
        }
    }
}

// ---------------------------------------------------------------- Rain (2×2 → 4×2)

class RainWidget : AmekiWidget(setOf(S2x2, S4x2)) {
    override val need = WidgetNeed.RAIN

    @Composable
    override fun Content(w: ReadyWidget, palette: WidgetPalette, fmt: TimeFormatter) {
        val context = LocalContext.current
        val layout = WidgetLayout.rain(LocalSize.current)
        val sentence = w.view.rainText(context, fmt, short = true)
        // Dots only while the nowcast backs the sentence above them.
        val slots = w.view.rainSlots.take(8)
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    context.getString(R.string.widget_rain_name).uppercase(),
                    style = textStyle(palette.onSurfaceVariant, 10.sp, FontWeight.Medium),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Freshness(w.view, w.data.settings, palette, fmt, compact = !layout.wide)
            }
            Spacer(GlanceModifier.height(4.dp))
            Text(sentence, style = textStyle(palette.onSurface, if (layout.wide) 17.sp else 15.sp, FontWeight.Medium), maxLines = 2)
            Spacer(GlanceModifier.defaultWeight())
            if (slots.isNotEmpty()) {
                val shown = if (layout.everyOtherSlot) slots.filterIndexed { i, _ -> i % 2 == 0 } else slots
                val dot = if (layout.wide) 20.dp else 18.dp
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
            } else if (w.view.hours.isNotEmpty()) {
                // No 15-minute data (e.g. Google): the next hours' probability instead.
                HourStrip(w.view.hours.take(layout.fallbackHours), w, palette, fmt, WidgetLayout.Hours(layout.fallbackHours, tiny = false, compact = false, large = false, rain = true))
            }
        }
    }
}
