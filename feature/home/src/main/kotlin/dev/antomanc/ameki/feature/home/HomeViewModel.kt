package dev.antomanc.ameki.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.antomanc.ameki.core.data.PlaceRepository
import dev.antomanc.ameki.core.data.SourceRegistry
import dev.antomanc.ameki.core.data.WeatherRepository
import dev.antomanc.ameki.core.data.settings.SettingsRepository
import dev.antomanc.ameki.core.data.sync.PlaceWeather
import dev.antomanc.ameki.core.model.AppSettings
import dev.antomanc.ameki.core.model.Capability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val places: List<PlaceWeather> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val capabilities: Set<Capability> = emptySet(),
    val refreshing: Set<Long> = emptySet(),
    /** Places whose extra hours are being fetched for a day page. */
    val loadingHours: Set<Long> = emptySet(),
    val hoursError: dev.antomanc.ameki.core.data.sync.SyncError? = null,
)

class HomeViewModel(
    private val weather: WeatherRepository,
    private val places: PlaceRepository,
    private val settings: SettingsRepository,
    private val sources: SourceRegistry,
) : ViewModel() {
    private val refreshing = MutableStateFlow<Set<Long>>(emptySet())
    private val loadingHours = MutableStateFlow<Set<Long>>(emptySet())
    private val hoursError = MutableStateFlow<dev.antomanc.ameki.core.data.sync.SyncError?>(null)

    val state: StateFlow<HomeUiState> = combine(weather.all, settings.settings, refreshing, loadingHours, hoursError) { list, s, r, lh, he ->
        HomeUiState(
            loading = false,
            places = list,
            settings = s,
            capabilities = sources[s.source].capabilities,
            refreshing = r,
            loadingHours = lh,
            hoursError = he,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Called when a place becomes visible: marks it as in use and fetches what is due. */
    fun onPlaceShown(id: Long) = viewModelScope.launch {
        places.markOpened(id)
        settings.setSelectedPlace(id)
        refresh(id, force = false)
    }

    /** A day page needs hours up to [until]: fetch the missing ones (Google only; Open-Meteo has them). */
    fun ensureHours(id: Long, until: java.time.Instant) {
        if (id in loadingHours.value) return
        viewModelScope.launch {
            loadingHours.value += id
            try {
                hoursError.value = weather.ensureHourlyUntil(id, until)
            } finally {
                loadingHours.value -= id
            }
        }
    }

    fun refresh(id: Long, force: Boolean = true) {
        if (id in refreshing.value) return
        viewModelScope.launch {
            refreshing.value += id
            try {
                weather.refresh(id, force = force)
            } finally {
                refreshing.value -= id
            }
        }
    }
}
