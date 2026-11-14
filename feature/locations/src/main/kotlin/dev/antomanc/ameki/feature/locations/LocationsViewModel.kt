package dev.antomanc.ameki.feature.locations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.antomanc.ameki.core.data.PlaceRepository
import dev.antomanc.ameki.core.data.WeatherRepository
import dev.antomanc.ameki.core.data.settings.SettingsRepository
import dev.antomanc.ameki.core.data.sync.PlaceWeather
import dev.antomanc.ameki.core.model.PlaceSearchResult
import dev.antomanc.ameki.core.model.Units
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

data class SearchState(
    val query: String = "",
    val results: List<PlaceSearchResult> = emptyList(),
    val searching: Boolean = false,
    val failed: Boolean = false,
)

@OptIn(FlowPreview::class)
class LocationsViewModel(
    private val places: PlaceRepository,
    private val weather: WeatherRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val list: StateFlow<List<PlaceWeather>> = weather.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val units: StateFlow<Units> = settings.settings.map { it.units }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Units())

    private val _locating = MutableStateFlow(false)
    /** True while the device location is being looked up after "Use my location". */
    val locating: StateFlow<Boolean> = _locating.asStateFlow()

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()
    private val query = MutableStateFlow("")

    init {
        viewModelScope.launch {
            query.debounce(300).distinctUntilChanged().collect { q ->
                if (q.trim().length < 2) {
                    _search.value = _search.value.copy(results = emptyList(), searching = false, failed = false)
                    return@collect
                }
                _search.value = _search.value.copy(searching = true, failed = false)
                val result = runCatching { places.search(q, Locale.getDefault().toLanguageTag()) }
                _search.value = _search.value.copy(
                    results = result.getOrDefault(emptyList()),
                    searching = false,
                    failed = result.isFailure,
                )
            }
        }
    }

    fun onQuery(q: String) {
        _search.value = _search.value.copy(query = q)
        query.value = q
    }

    /** Adds the place, fetches its weather, and reports the new id. */
    fun add(result: PlaceSearchResult, onAdded: (Long) -> Unit = {}) = viewModelScope.launch {
        val id = places.add(result)
        onQuery("")
        settings.setSelectedPlace(id)
        onAdded(id)
        weather.refresh(id, force = true)
    }

    fun useCurrentLocation(fallbackName: String, onAdded: (Long) -> Unit = {}) = viewModelScope.launch {
        if (_locating.value) return@launch
        _locating.value = true
        try {
            val id = places.enableCurrentLocation(fallbackName)
            settings.setSelectedPlace(id)
            onAdded(id)
            weather.refresh(id, force = true)
        } finally {
            _locating.value = false
        }
    }

    fun remove(id: Long) = viewModelScope.launch { places.remove(id) }

    fun move(from: Int, to: Int) = viewModelScope.launch {
        val ids = list.value.filterNot { it.place.isCurrentLocation }.map { it.place.id }.toMutableList()
        if (from !in ids.indices || to !in ids.indices) return@launch
        ids.add(to, ids.removeAt(from))
        places.reorder(ids)
    }

    fun hasLocationPermission() = places.hasLocationPermission()
}
