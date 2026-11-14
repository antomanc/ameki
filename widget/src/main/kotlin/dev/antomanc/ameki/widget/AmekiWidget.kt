package dev.antomanc.ameki.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.GlanceModifier
import androidx.glance.layout.*
import androidx.glance.text.Text
import androidx.compose.ui.unit.sp
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.semantics.semantics
import androidx.glance.semantics.contentDescription
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import dev.antomanc.ameki.core.ui.TimeFormatter
import dev.antomanc.ameki.core.ui.label

/**
 * Base for every Ameki widget: loads cached data for its instance, then renders [Content].
 * Responsive sizes let each widget reveal more as it grows.
 */
abstract class AmekiWidget(sizes: Set<DpSize>) : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(sizes)

    /** The one part this widget cannot render without; a partial cache still serves the others. */
    abstract val need: WidgetNeed

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val initial = WidgetRepository.load(appWidgetId)
        val updates = WidgetRepository.observe(appWidgetId)
        provideContent {
            // Glance keeps a session alive between updates: observe the cache instead of
            // rendering a one-off snapshot, or a refresh landing mid-session draws old data.
            val data by updates.collectAsState(initial)
            GlanceTheme {
                Render(context, data)
            }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val data = PreviewData.sample()
        provideContent { GlanceTheme { Render(context, data) } }
    }

    @Composable
    private fun Render(context: Context, data: WidgetData) {
        val palette = rememberPalette(data.config)
        val fmt = TimeFormatter(context, data.settings.timeFormat)
        // Dense tiles get a smaller inset: leave room for the content instead of clipping it.
        val pad = when {
            LocalSize.current.height < 90.dp -> 0.dp
            LocalSize.current.height < 150.dp -> 0.dp
            LocalSize.current.height < 200.dp -> 8.dp
            else -> 12.dp
        }
        WidgetFrame(data, palette, padding = pad) {
            if (!WidgetMessage(data, palette, need)) {
                if (context.resources.configuration.fontScale > 1.3f) LargeFontContent(context, data, palette, fmt)
                else Content(data, palette, fmt)
            }
        }
    }

    /** At large fonts prioritise the reading and show fewer, full-size forecast targets. */
    @Composable
    private fun LargeFontContent(context: Context, data: WidgetData, palette: WidgetPalette, fmt: TimeFormatter) {
        val weather = data.weather ?: return
        val snapshot = weather.snapshot ?: return
        val now = java.time.Instant.now()
        val units = data.settings.units
        val small = LocalSize.current.height < 90.dp
        val dense = LocalSize.current.height < 150.dp
        Column(GlanceModifier.fillMaxSize()) {
            val temperature = snapshot.current?.let { units.temp(it.temperature) }.orEmpty()
            Text("${weather.place.name} $temperature", style = textStyle(palette.onSurface, if (dense) 11.sp else 14.sp), maxLines = 1)
            val count = ((LocalSize.current.height.value - if (dense) 50 else 75) / 48).toInt().coerceAtLeast(0)
            if (!small) when (need) {
                WidgetNeed.HOURLY -> snapshot.hoursFrom(now).take(count).forEach { hour ->
                    val label = "${fmt.hour(hour.time, snapshot.timeZone)} · ${units.temp(hour.temperature)}"
                    Box(GlanceModifier.fillMaxWidth().height(48.dp).clickable(actionStartActivity(detailIntent(context, weather.place.id, hour.time.atZone(snapshot.timeZone).toLocalDate().toEpochDay(), hour.time.epochSecond)))) {
                        Text(label, style = textStyle(palette.onSurface, 12.sp), maxLines = 1)
                    }
                }
                WidgetNeed.RAIN -> Text(rainSummary(context, snapshot, now, fmt), style = textStyle(palette.onSurface, if (dense) 10.sp else 12.sp), maxLines = 2)
                WidgetNeed.DAILY -> snapshot.daily.filter { !it.date.isBefore(java.time.LocalDate.now(snapshot.timeZone)) }.take(count).forEach { day ->
                    val label = "${fmt.weekdayShort(day.date)} · ${units.temp(day.temperatureMin)} / ${units.temp(day.temperatureMax)}"
                    Box(GlanceModifier.fillMaxWidth().height(48.dp).clickable(actionStartActivity(detailIntent(context, weather.place.id, day.date.toEpochDay())))) {
                        Text(label, style = textStyle(palette.onSurface, 12.sp), maxLines = 1)
                    }
                }
                else -> snapshot.current?.let { current ->
                    Text(context.getString(current.condition.label(current.isDaytime)), style = textStyle(palette.onSurfaceVariant, 12.sp), maxLines = 1)
                }
            }
            Freshness(data, palette, fmt, compact = dense)
        }
    }

    @Composable
    abstract fun Content(data: WidgetData, palette: WidgetPalette, fmt: TimeFormatter)
}
