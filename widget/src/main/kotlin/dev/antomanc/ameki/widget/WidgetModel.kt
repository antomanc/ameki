package dev.antomanc.ameki.widget

import android.content.Context
import android.content.Intent
import dev.antomanc.ameki.core.data.PlaceRepository
import dev.antomanc.ameki.core.data.WeatherRepository
import dev.antomanc.ameki.core.data.db.WidgetConfigEntity
import dev.antomanc.ameki.core.data.db.WidgetDao
import dev.antomanc.ameki.core.data.settings.SettingsRepository
import dev.antomanc.ameki.core.data.sync.PlaceWeather
import dev.antomanc.ameki.core.data.sync.SyncError
import dev.antomanc.ameki.core.model.AppSettings
import dev.antomanc.ameki.core.ui.AppRequest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

enum class WidgetStyle { DYNAMIC, DARK, TRANSPARENT }

data class WidgetConfig(val placeId: Long?, val style: WidgetStyle, val opacity: Float) {
    companion object {
        val Default = WidgetConfig(null, WidgetStyle.DYNAMIC, 1f)
        fun from(e: WidgetConfigEntity?) = e?.let {
            WidgetConfig(it.placeId, WidgetStyle.entries.firstOrNull { s -> s.name == it.style } ?: WidgetStyle.DYNAMIC, it.backgroundOpacity)
        } ?: Default
    }
}

/** What each widget cannot render without. A partial cache still shows the widgets it can fill. */
enum class WidgetNeed { CURRENT, HOURLY, DAILY, ANY, RAIN }

/** Why a widget has nothing to draw. Specific states avoid "loading" forever. */
enum class WidgetState {
    READY,
    NO_PLACE,
    PLACE_GONE,
    KEY_PROBLEM,
    NO_LOCATION,
    ACCESS_DENIED,
    QUOTA,
    UNSUPPORTED,
    OFFLINE,
    FAILED,
    LOADING,
    PARTIAL,
}

/** Everything a widget renders, read from the cache only: widgets never touch the network. */
data class WidgetData(
    val config: WidgetConfig,
    val weather: PlaceWeather?,
    val settings: AppSettings,
    /** False when this widget points at a place that was deleted: never silently use another. */
    val placeExists: Boolean = true,
)

internal fun WidgetData.hasData(need: WidgetNeed, now: Instant = Instant.now()): Boolean {
    val s = weather?.snapshot ?: return false
    return when (need) {
        WidgetNeed.CURRENT -> s.current != null
        WidgetNeed.HOURLY -> s.hoursFrom(now).isNotEmpty()
        WidgetNeed.DAILY -> s.daily.isNotEmpty()
        WidgetNeed.RAIN -> s.nowcast.isNotEmpty() || s.hourly.isNotEmpty() || s.current != null || s.daily.isNotEmpty()
        WidgetNeed.ANY -> s.current != null || s.hourly.isNotEmpty() || s.daily.isNotEmpty()
    }
}

internal fun widgetState(data: WidgetData, need: WidgetNeed, now: Instant = Instant.now()): WidgetState {
    if (!data.placeExists) return WidgetState.PLACE_GONE
    val w = data.weather ?: return WidgetState.NO_PLACE
    if (data.hasData(need, now)) return WidgetState.READY
    return when (w.error) {
        SyncError.MISSING_KEY, SyncError.INVALID_KEY -> WidgetState.KEY_PROBLEM
        SyncError.NO_LOCATION -> WidgetState.NO_LOCATION
        SyncError.ACCESS_DENIED -> WidgetState.ACCESS_DENIED
        SyncError.QUOTA -> WidgetState.QUOTA
        SyncError.UNSUPPORTED -> WidgetState.UNSUPPORTED
        SyncError.NETWORK -> WidgetState.OFFLINE
        SyncError.UNKNOWN -> WidgetState.FAILED
        null -> {
            val attempted = w.lastAttemptAt
            when {
                w.snapshot != null -> WidgetState.PARTIAL
                attempted == null -> WidgetState.LOADING
                Duration.between(attempted, now) < Duration.ofMinutes(2) -> WidgetState.LOADING
                else -> WidgetState.FAILED
            }
        }
    }
}

internal object WidgetRepository : KoinComponent {
    /** Live data for one widget: Room emits whenever the cache, places, settings or config change. */
    fun observe(appWidgetId: Int): kotlinx.coroutines.flow.Flow<WidgetData> = kotlinx.coroutines.flow.combine(
        get<WidgetDao>().observe(appWidgetId),
        get<WeatherRepository>().all,
        // selectedPlaceId is the app's screen state: it must not redraw widgets on its own.
        get<SettingsRepository>().settings.map { it.copy(selectedPlaceId = null) }.distinctUntilChanged(),
    ) { entity, all, settings ->
        val config = WidgetConfig.from(entity)
        // A configured place that disappeared is an error, not an excuse to show another city.
        val weather = if (config.placeId != null) {
            all.firstOrNull { it.place.id == config.placeId }
        } else {
            all.firstOrNull()
        }
        WidgetData(config, weather, settings, placeExists = config.placeId == null || weather != null)
    }

    suspend fun load(appWidgetId: Int): WidgetData {
        val config = WidgetConfig.from(get<WidgetDao>().get(appWidgetId))
        val weatherRepo = get<WeatherRepository>()
        val placeId = config.placeId ?: get<PlaceRepository>().all().firstOrNull()?.id
        val weather = placeId?.let { weatherRepo.get(it) }
        return WidgetData(
            config, weather, get<SettingsRepository>().current(),
            placeExists = config.placeId == null || weather != null,
        )
    }
}

/** Opens the app on [placeId] (or its last place). The widget module can't see MainActivity, so it asks the launcher intent. */
internal fun openAppIntent(context: Context, placeId: Long?): Intent =
    (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        if (placeId != null) AppRequest.Show(placeId).writeTo(this)
    }

/** Tap on an hour or a day: the app opens that exact detail, even when already on another screen. */
internal fun detailIntent(context: Context, placeId: Long, day: LocalDate, hour: Instant? = null): Intent =
    openAppIntent(context, null).also { AppRequest.Show(placeId, day, hour).writeTo(it) }

/** Opens the places list (widget config "add a place"), without inventing a second navigation path. */
internal fun openPlacesIntent(context: Context): Intent =
    openAppIntent(context, null).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP).also { AppRequest.OpenPlaces.writeTo(it) }
