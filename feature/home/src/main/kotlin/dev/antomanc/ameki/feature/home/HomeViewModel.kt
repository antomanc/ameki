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
)

class HomeViewModel(
    private val weather: WeatherRepository,
    private val places: PlaceRepository,
    private val settings: SettingsRepository,
    private val sources: SourceRegistry,
) : ViewModel() {
    private val refreshing = MutableStateFlow<Set<Long>>(emptySet())

    val state: StateFlow<HomeUiState> = combine(weather.all, settings.settings, refreshing) { list, s, r ->
        HomeUiState(
            loading = false,
            places = list,
            settings = s,
            capabilities = sources[s.source].capabilities,
            refreshing = r,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Called when a place becomes visible: marks it as in use and fetches what is due. */
    fun onPlaceShown(id: Long) = viewModelScope.launch {
        places.markOpened(id)
        settings.setSelectedPlace(id)
        refresh(id, force = false)
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
