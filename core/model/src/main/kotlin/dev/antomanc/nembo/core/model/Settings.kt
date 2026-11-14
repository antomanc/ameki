package dev.antomanc.nembo.core.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class TimeFormat { SYSTEM, H24, H12 }

/**
 * Every user setting lives here. Adding one means: a field with a default here, a key in
 * SettingsRepository, and a row in the settings screen.
 */
data class AppSettings(
    val provider: ProviderId = ProviderId.OPEN_METEO,
    val hasGoogleApiKey: Boolean = false,
    val units: Units = Units(),
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val timeFormat: TimeFormat = TimeFormat.SYSTEM,
    /** Data language, as a BCP-47 tag; null follows the system. */
    val dataLanguage: String? = null,
    val refreshIntervalMinutes: Int = RefreshPolicy.DEFAULT_INTERVAL_MINUTES,
    val dynamicColor: Boolean = true,
    val onboardingDone: Boolean = false,
    val selectedPlaceId: Long? = null,
)
