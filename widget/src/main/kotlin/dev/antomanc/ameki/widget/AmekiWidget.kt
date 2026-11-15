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
import dev.antomanc.ameki.core.data.widget.WidgetData
import dev.antomanc.ameki.core.data.widget.WidgetInstances
import dev.antomanc.ameki.core.model.Place
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import dev.antomanc.ameki.core.ui.ForecastView
import dev.antomanc.ameki.core.ui.TimeFormatter
import java.time.Instant
import dev.antomanc.ameki.core.ui.label

/**
 * Base for every Ameki widget: loads cached data for its instance, then renders [Content].
 * Responsive sizes let each widget reveal more as it grows.
 */
abstract class AmekiWidget(sizes: Set<DpSize>) : GlanceAppWidget(), KoinComponent {
    override val sizeMode: SizeMode = SizeMode.Responsive(sizes)

    /** The one part this widget cannot render without; a partial cache still serves the others. */
    abstract val need: WidgetNeed

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val instances = get<WidgetInstances>()
        val initial = instances.load(appWidgetId)
        val updates = instances.observe(appWidgetId)
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
        WidgetFrame(data, palette, padding = WidgetLayout.padding(LocalSize.current)) {
            val weather = data.weather
            val snapshot = weather?.snapshot
            if (!WidgetMessage(data, palette, need) && weather != null && snapshot != null) {
                val w = ReadyWidget(data, weather.place, ForecastView(snapshot, Instant.now(), data.settings.refreshIntervalMinutes))
                if (context.resources.configuration.fontScale > 1.3f) LargeFontContent(context, w, palette, fmt)
                else Content(w, palette, fmt)
            }
        }
    }

    /** At large fonts prioritise the reading and show fewer, full-size forecast targets. */
    @Composable
    private fun LargeFontContent(context: Context, w: ReadyWidget, palette: WidgetPalette, fmt: TimeFormatter) {
        val units = w.units
        val zone = w.snapshot.timeZone
        val layout = WidgetLayout.largeFont(LocalSize.current)
        Column(GlanceModifier.fillMaxSize()) {
            val temperature = w.snapshot.current?.let { units.temp(it.temperature) }.orEmpty()
            Text("${w.place.name} $temperature", style = textStyle(palette.onSurface, if (layout.dense) 11.sp else 14.sp), maxLines = 1)
            when (need) {
                WidgetNeed.HOURLY -> w.view.hours.take(layout.rows).forEach { hv ->
                    val hour = hv.forecast
                    val label = "${fmt.hour(hour.time, zone)} · ${units.temp(hv.temperature)}"
                    Box(GlanceModifier.fillMaxWidth().height(WidgetLayout.ROW).clickable(actionStartActivity(detailIntent(context, w.place.id, hour.time.atZone(zone).toLocalDate(), hour.time)))) {
                        Text(label, style = textStyle(palette.onSurface, 12.sp), maxLines = 1)
                    }
                }
                WidgetNeed.RAIN -> if (layout.rows > 0) Text(w.view.rainText(context, fmt, short = true), style = textStyle(palette.onSurface, if (layout.dense) 10.sp else 12.sp), maxLines = 2)
                WidgetNeed.DAILY -> w.view.days.take(layout.rows).forEach { day ->
                    val label = "${fmt.weekdayShort(day.date)} · ${units.temp(day.temperatureMin)} / ${units.temp(day.temperatureMax)}"
                    Box(GlanceModifier.fillMaxWidth().height(WidgetLayout.ROW).clickable(actionStartActivity(detailIntent(context, w.place.id, day.date)))) {
                        Text(label, style = textStyle(palette.onSurface, 12.sp), maxLines = 1)
                    }
                }
                else -> if (layout.rows > 0) w.snapshot.current?.let { current ->
                    Text(context.getString(current.condition.label(current.isDaytime)), style = textStyle(palette.onSurfaceVariant, 12.sp), maxLines = 1)
                }
            }
            Freshness(w.view, palette, fmt, compact = layout.dense)
        }
    }

    @Composable
    abstract fun Content(w: ReadyWidget, palette: WidgetPalette, fmt: TimeFormatter)
}

/** A widget with something to draw: its place and the snapshot as shown now. */
class ReadyWidget internal constructor(val data: WidgetData, val place: Place, val view: ForecastView) {
    val snapshot get() = view.snapshot
    val units get() = data.settings.units
}
