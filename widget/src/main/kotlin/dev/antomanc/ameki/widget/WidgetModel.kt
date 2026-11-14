package dev.antomanc.ameki.widget

import android.content.Context
import android.content.Intent
import dev.antomanc.ameki.core.data.PlaceRepository
import dev.antomanc.ameki.core.data.WeatherRepository
import dev.antomanc.ameki.core.data.db.WidgetConfigEntity
import dev.antomanc.ameki.core.data.db.WidgetDao
import dev.antomanc.ameki.core.data.settings.SettingsRepository
import dev.antomanc.ameki.core.data.sync.PlaceWeather
import dev.antomanc.ameki.core.model.AppSettings
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

enum class WidgetStyle { DYNAMIC, DARK, TRANSPARENT }

data class WidgetConfig(val placeId: Long?, val style: WidgetStyle, val opacity: Float) {
    companion object {
        val Default = WidgetConfig(null, WidgetStyle.DYNAMIC, 1f)
        fun from(e: WidgetConfigEntity?) = e?.let {
            WidgetConfig(it.placeId, WidgetStyle.entries.firstOrNull { s -> s.name == it.style } ?: WidgetStyle.DYNAMIC, it.backgroundOpacity)
        } ?: Default
    }
}

/** Everything a widget renders, read from the cache only: widgets never touch the network. */
data class WidgetData(val config: WidgetConfig, val weather: PlaceWeather?, val settings: AppSettings)

internal object WidgetRepository : KoinComponent {
    suspend fun load(appWidgetId: Int): WidgetData {
        val config = WidgetConfig.from(get<WidgetDao>().get(appWidgetId))
        val weatherRepo = get<WeatherRepository>()
        val placeId = config.placeId ?: get<PlaceRepository>().all().firstOrNull()?.id
        val weather = placeId?.let { weatherRepo.get(it) }
        return WidgetData(config, weather, get<SettingsRepository>().current())
    }
}

/** Opens the app on [placeId] (or its last place). The widget module can't see MainActivity, so it asks the launcher intent. */
internal fun openAppIntent(context: Context, placeId: Long?): Intent =
    (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        if (placeId != null) putExtra("dev.antomanc.ameki.PLACE_ID", placeId)
    }
