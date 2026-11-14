package dev.antomanc.ameki.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import dev.antomanc.ameki.core.ui.TimeFormatter

/**
 * Base for every Ameki widget: loads cached data for its instance, then renders [Content].
 * Responsive sizes let each widget reveal more as it grows.
 */
abstract class AmekiWidget(sizes: Set<DpSize>) : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(sizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val data = WidgetRepository.load(appWidgetId)
        provideContent {
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
        WidgetFrame(data, palette, padding = padding()) {
            if (!WidgetMessage(data, palette)) Content(data, palette, fmt)
        }
    }

    open fun padding(): androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(14f)

    @Composable
    abstract fun Content(data: WidgetData, palette: WidgetPalette, fmt: TimeFormatter)
}
