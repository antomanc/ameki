package dev.antomanc.ameki.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import dev.antomanc.ameki.core.data.db.WidgetDao
import dev.antomanc.ameki.core.data.sync.RefreshScheduler
import dev.antomanc.ameki.core.data.sync.WeatherUpdateListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.reflect.KClass

abstract class AmekiReceiver : GlanceAppWidgetReceiver(), KoinComponent {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // A widget placed without configuration still needs data: ask the shared worker.
        get<RefreshScheduler>().refreshNow()
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        scope.launch { get<WidgetDao>().delete(appWidgetIds.toList()) }
    }
}

class CompactWidgetReceiver : AmekiReceiver() { override val glanceAppWidget: GlanceAppWidget = CompactWidget() }
class HourlyWidgetReceiver : AmekiReceiver() { override val glanceAppWidget: GlanceAppWidget = HourlyWidget() }
class DailyWidgetReceiver : AmekiReceiver() { override val glanceAppWidget: GlanceAppWidget = DailyWidget() }
class CompleteWidgetReceiver : AmekiReceiver() { override val glanceAppWidget: GlanceAppWidget = CompleteWidget() }
class RainWidgetReceiver : AmekiReceiver() { override val glanceAppWidget: GlanceAppWidget = RainWidget() }

internal val allWidgets: List<Pair<KClass<out GlanceAppWidgetReceiver>, () -> GlanceAppWidget>> = listOf(
    CompactWidgetReceiver::class to ::CompactWidget,
    HourlyWidgetReceiver::class to ::HourlyWidget,
    DailyWidgetReceiver::class to ::DailyWidget,
    CompleteWidgetReceiver::class to ::CompleteWidget,
    RainWidgetReceiver::class to ::RainWidget,
)

/** Tap on "updated at": refresh now through the shared worker, never a per-widget fetch. */
class RefreshAction : ActionCallback, KoinComponent {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        get<RefreshScheduler>().refreshNow(force = true)
    }
}

/** Redraws every widget after the cache changes. */
class WidgetUpdater(private val context: Context) : WeatherUpdateListener {
    override suspend fun onWeatherUpdated() {
        allWidgets.forEach { (_, make) -> make().updateAll(context) }
    }

    suspend fun update(appWidgetId: Int) {
        val manager = GlanceAppWidgetManager(context)
        val id = manager.getGlanceIdBy(appWidgetId)
        val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId) ?: return
        allWidgets.firstOrNull { it.first.java.name == info.provider.className }?.second?.invoke()?.update(context, id)
    }

    /** Generated previews for the widget picker (Android 15+); the system rate-limits these calls. */
    suspend fun publishPreviews() {
        val manager = GlanceAppWidgetManager(context)
        allWidgets.forEach { (receiver, _) -> runCatching { manager.setWidgetPreviews(receiver) } }
    }
}

object WidgetPins {
    /** One-tap "add to home screen" for the Compact widget, or null when the launcher can't. */
    fun pinCompact(context: Context): (() -> Unit)? {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return null
        return {
            manager.requestPinAppWidget(ComponentName(context, CompactWidgetReceiver::class.java), null, null)
        }
    }
}

val widgetModule = module {
    single { WidgetUpdater(get()) } bind WeatherUpdateListener::class
}

