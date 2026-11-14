package dev.antomanc.ameki.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.antomanc.ameki.core.model.AppSettings
import dev.antomanc.ameki.core.model.SourceId
import dev.antomanc.ameki.core.model.RefreshPolicy
import dev.antomanc.ameki.core.model.ThemeMode
import dev.antomanc.ameki.core.model.TimeFormat
import dev.antomanc.ameki.core.model.Units
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The single typed entry point for settings. To add a setting: a field in [AppSettings],
 * a key below, one line in [read], one setter.
 */
class SettingsRepository(
    private val store: DataStore<Preferences>,
    private val cipher: SecretCipher,
    /** Sources compiled into this build; a stored choice outside this set falls back to the default. */
    private val availableSources: Set<SourceId> = SourceId.entries.toSet(),
    private val defaults: () -> AppSettings = { AppSettings() },
) {
    private object Keys {
        val source = stringPreferencesKey("source")
        val apiKey = stringPreferencesKey("google_api_key_enc")
        val tempUnit = stringPreferencesKey("unit_temperature")
        val windUnit = stringPreferencesKey("unit_wind")
        val pressureUnit = stringPreferencesKey("unit_pressure")
        val precipUnit = stringPreferencesKey("unit_precipitation")
        val distanceUnit = stringPreferencesKey("unit_distance")
        val theme = stringPreferencesKey("theme")
        val timeFormat = stringPreferencesKey("time_format")
        val dataLanguage = stringPreferencesKey("data_language")
        val interval = intPreferencesKey("refresh_interval_min")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val selectedPlace = longPreferencesKey("selected_place")
    }

    val settings: Flow<AppSettings> = store.data.map(::read).distinctUntilChanged()

    suspend fun current(): AppSettings = settings.first()

    private fun read(p: Preferences): AppSettings {
        val d = defaults()
        val source = p[Keys.source].enumOr(d.source).takeIf { it in availableSources } ?: d.source
        return AppSettings(
            source = source,
            hasGoogleApiKey = p[Keys.apiKey] != null,
            units = Units(
                temperature = p[Keys.tempUnit].enumOr(d.units.temperature),
                windSpeed = p[Keys.windUnit].enumOr(d.units.windSpeed),
                pressure = p[Keys.pressureUnit].enumOr(d.units.pressure),
                precipitation = p[Keys.precipUnit].enumOr(d.units.precipitation),
                distance = p[Keys.distanceUnit].enumOr(d.units.distance),
            ),
            theme = p[Keys.theme].enumOr(d.theme),
            timeFormat = p[Keys.timeFormat].enumOr(d.timeFormat),
            dataLanguage = p[Keys.dataLanguage],
            refreshIntervalMinutes = RefreshPolicy.clampInterval(source, p[Keys.interval] ?: d.refreshIntervalMinutes),
            dynamicColor = p[Keys.dynamicColor] ?: d.dynamicColor,
            onboardingDone = p[Keys.onboardingDone] ?: d.onboardingDone,
            selectedPlaceId = p[Keys.selectedPlace],
        )
    }

    /** Decrypted only on demand, never exposed through [settings]. */
    suspend fun googleApiKey(): String? = store.data.first()[Keys.apiKey]?.let(cipher::decrypt)

    suspend fun setGoogleApiKey(key: String?) = edit {
        val trimmed = key?.trim()
        if (trimmed.isNullOrEmpty()) remove(Keys.apiKey) else this[Keys.apiKey] = cipher.encrypt(trimmed)
    }

    suspend fun setSource(source: SourceId) = edit {
        this[Keys.source] = source.name
        this[Keys.interval] = RefreshPolicy.clampInterval(source, this[Keys.interval] ?: RefreshPolicy.DEFAULT_INTERVAL_MINUTES)
    }

    suspend fun setUnits(units: Units) = edit {
        this[Keys.tempUnit] = units.temperature.name
        this[Keys.windUnit] = units.windSpeed.name
        this[Keys.pressureUnit] = units.pressure.name
        this[Keys.precipUnit] = units.precipitation.name
        this[Keys.distanceUnit] = units.distance.name
    }

    suspend fun setTheme(theme: ThemeMode) = edit { this[Keys.theme] = theme.name }
    suspend fun setTimeFormat(format: TimeFormat) = edit { this[Keys.timeFormat] = format.name }
    suspend fun setDataLanguage(tag: String?) = edit { if (tag == null) remove(Keys.dataLanguage) else this[Keys.dataLanguage] = tag }
    suspend fun setRefreshInterval(minutes: Int) = edit { this[Keys.interval] = minutes }
    suspend fun setDynamicColor(enabled: Boolean) = edit { this[Keys.dynamicColor] = enabled }
    suspend fun setOnboardingDone(done: Boolean) = edit { this[Keys.onboardingDone] = done }
    suspend fun setSelectedPlace(id: Long?) = edit { if (id == null) remove(Keys.selectedPlace) else this[Keys.selectedPlace] = id }

    private suspend fun edit(block: MutablePreferences.() -> Unit) {
        store.edit { it.block() }
    }

    private inline fun <reified E : Enum<E>> String?.enumOr(default: E): E =
        this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
}

