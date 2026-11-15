package dev.antomanc.ameki.core.data.widget

import dev.antomanc.ameki.core.data.PlaceRepository
import dev.antomanc.ameki.core.data.WeatherRepository
import dev.antomanc.ameki.core.data.db.WidgetConfigEntity
import dev.antomanc.ameki.core.data.db.WidgetDao
import dev.antomanc.ameki.core.data.settings.SettingsRepository
import dev.antomanc.ameki.core.data.sync.PlaceWeather
import dev.antomanc.ameki.core.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class WidgetStyle { DYNAMIC, DARK, TRANSPARENT }

/** A widget instance's own settings. [placeId] null follows the first place in the list. */
data class WidgetConfig(val placeId: Long?, val style: WidgetStyle, val opacity: Float) {
    companion object {
        val Default = WidgetConfig(null, WidgetStyle.DYNAMIC, 1f)
    }
}

/** Everything a widget renders, read from the cache only: widgets never touch the network. */
data class WidgetData(
    val config: WidgetConfig,
    val weather: PlaceWeather?,
    val settings: AppSettings,
    /** False when this widget points at a place that was deleted: never silently use another. */
    val placeExists: Boolean = true,
)

/**
 * The widget instances on the home screen: their settings, which place each one shows, and
 * which places the background refresh must keep current for them.
 */
class WidgetInstances(
    private val dao: WidgetDao,
    private val weather: WeatherRepository,
    private val places: PlaceRepository,
    private val settings: SettingsRepository,
) {
    /** Live data for one widget: emits whenever the cache, places, settings or its config change. */
    fun observe(appWidgetId: Int): Flow<WidgetData> = combine(
        dao.observe(appWidgetId),
        weather.all,
        // selectedPlaceId is the app's screen state: it must not redraw widgets on its own.
        settings.settings.map { it.copy(selectedPlaceId = null) }.distinctUntilChanged(),
    ) { entity, all, s ->
        val config = entity.toConfig()
        data(config, all.firstOrNull { it.place.id == shownPlace(config, all.map { w -> w.place.id }) }, s)
    }

    suspend fun load(appWidgetId: Int): WidgetData {
        val config = dao.get(appWidgetId).toConfig()
        val id = shownPlace(config, places.all().map { it.id })
        return data(config, id?.let { weather.get(it) }, settings.current())
    }

    /** This widget's settings, or null when it was never configured. */
    suspend fun config(appWidgetId: Int): WidgetConfig? = dao.get(appWidgetId)?.toConfig()

    suspend fun save(appWidgetId: Int, config: WidgetConfig) =
        dao.upsert(WidgetConfigEntity(appWidgetId, config.placeId, config.style.name, config.opacity))

    suspend fun delete(appWidgetIds: List<Int>) = dao.delete(appWidgetIds)

    /** The places shown by at least one widget, which the background refresh keeps current. */
    suspend fun shownPlaces(): Set<Long> {
        val ids = places.all().map { it.id }
        return dao.all().mapNotNullTo(mutableSetOf()) { shownPlace(it.toConfig(), ids) }
    }

    /** A configured place that disappeared shows nothing, never another city. */
    private fun shownPlace(config: WidgetConfig, placeIds: List<Long>): Long? =
        if (config.placeId == null) placeIds.firstOrNull() else config.placeId.takeIf { it in placeIds }

    private fun data(config: WidgetConfig, weather: PlaceWeather?, s: AppSettings) =
        WidgetData(config, weather, s, placeExists = config.placeId == null || weather != null)

    private fun WidgetConfigEntity?.toConfig(): WidgetConfig = this?.let {
        WidgetConfig(it.placeId, WidgetStyle.entries.firstOrNull { s -> s.name == it.style } ?: WidgetStyle.DYNAMIC, it.backgroundOpacity)
    } ?: WidgetConfig.Default
}
