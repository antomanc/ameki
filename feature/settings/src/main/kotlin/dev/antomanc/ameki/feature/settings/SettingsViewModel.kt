package dev.antomanc.ameki.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.antomanc.ameki.core.data.GoogleKeyCheck
import dev.antomanc.ameki.core.data.PlaceRepository
import dev.antomanc.ameki.core.data.SourceRegistry
import dev.antomanc.ameki.core.data.WeatherRepository
import dev.antomanc.ameki.core.data.settings.SettingsRepository
import dev.antomanc.ameki.core.data.sync.RefreshScheduler
import dev.antomanc.ameki.core.data.sync.SyncError
import dev.antomanc.ameki.core.model.AppSettings
import dev.antomanc.ameki.core.model.RefreshPolicy
import dev.antomanc.ameki.core.model.SourceId
import dev.antomanc.ameki.core.model.ThemeMode
import dev.antomanc.ameki.core.model.TimeFormat
import dev.antomanc.ameki.core.model.Units
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface KeyCheck {
    data object Idle : KeyCheck
    data object Checking : KeyCheck
    data object Valid : KeyCheck
    data class Invalid(val error: SyncError) : KeyCheck
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val sources: List<SourceId> = listOf(SourceId.OPEN_METEO),
    /** Places refreshed in the background: what the Google call estimate multiplies by. */
    val activePlaces: Int = 1,
) {
    val googleCallsPerMonth: Int get() = RefreshPolicy.estimateGoogleCallsPerMonth(activePlaces, settings.refreshIntervalMinutes)
    val overFreeTier: Boolean get() = googleCallsPerMonth > RefreshPolicy.GOOGLE_FREE_CALLS_PER_MONTH
}

class SettingsViewModel(
    private val repo: SettingsRepository,
    private val registry: SourceRegistry,
    private val weather: WeatherRepository,
    private val places: PlaceRepository,
    private val scheduler: RefreshScheduler,
    private val keyCheck: GoogleKeyCheck,
) : ViewModel() {
    val appIdentity = keyCheck.appIdentity

    val state: StateFlow<SettingsUiState> = combine(repo.settings, places.places) { s, list ->
        SettingsUiState(
            settings = s,
            sources = SourceId.entries.filter { it in registry.ids },
            activePlaces = list.size.coerceAtLeast(1),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _key = MutableStateFlow<KeyCheck>(KeyCheck.Idle)
    val keyState: StateFlow<KeyCheck> = _key.asStateFlow()

    fun setUnits(u: Units) = viewModelScope.launch { repo.setUnits(u) }
    fun setTheme(t: ThemeMode) = viewModelScope.launch { repo.setTheme(t) }
    fun setTimeFormat(f: TimeFormat) = viewModelScope.launch { repo.setTimeFormat(f) }
    fun setDynamicColor(on: Boolean) = viewModelScope.launch { repo.setDynamicColor(on) }

    fun setInterval(minutes: Int) = viewModelScope.launch {
        repo.setRefreshInterval(minutes)
        scheduler.schedule(repo.current().refreshIntervalMinutes, replace = true)
    }

    /** Hot switch: old data is dropped (it belongs to the other source) and everything refetches. */
    fun setSource(id: SourceId) = viewModelScope.launch {
        if (repo.current().source == id) return@launch
        repo.setSource(id)
        weather.clearAll()
        scheduler.schedule(repo.current().refreshIntervalMinutes, replace = true)
        scheduler.refreshNow(force = true)
    }

    /** Verify, then store (encrypted) only a key Google accepted. */
    fun submitKey(key: String, onValid: () -> Unit = {}) = viewModelScope.launch {
        _key.value = KeyCheck.Checking
        val error = keyCheck.verify(key)
        if (error == null) {
            repo.setGoogleApiKey(key)
            _key.value = KeyCheck.Valid
            scheduler.refreshNow(force = true)
            onValid()
        } else {
            _key.value = KeyCheck.Invalid(error)
        }
    }

    fun removeKey() = viewModelScope.launch {
        repo.setGoogleApiKey(null)
        _key.value = KeyCheck.Idle
    }
}
