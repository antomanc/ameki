package dev.antomanc.ameki.feature.locations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.antomanc.ameki.core.data.PlaceRepository
import dev.antomanc.ameki.core.data.WeatherRepository
import dev.antomanc.ameki.core.data.settings.SettingsRepository
import dev.antomanc.ameki.core.data.sync.PlaceWeather
import dev.antomanc.ameki.core.model.PlaceSearchResult
import dev.antomanc.ameki.core.model.Units
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import dev.antomanc.ameki.core.model.Place
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _locationFailed = MutableStateFlow(false)
    val locationFailed = _locationFailed.asStateFlow()

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()
    private val query = MutableStateFlow("")

    init {
        viewModelScope.launch {
            query.collectLatest { q ->
                if (q.trim().length < 2) return@collectLatest
                delay(300)
                val result: Result<List<PlaceSearchResult>> = try {
                    Result.success(places.search(q, Locale.getDefault().toLanguageTag()))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Result.failure(error)
                }
                if (_search.value.query == q) _search.value = _search.value.copy(
                    results = result.getOrDefault(emptyList()), searching = false, failed = result.isFailure,
                )
            }
        }
    }

    fun onQuery(q: String) {
        _search.value = SearchState(query = q, searching = q.trim().length >= 2)
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
        _locationFailed.value = false
        try {
            val id = places.enableCurrentLocation(fallbackName)
            settings.setSelectedPlace(id)
            onAdded(id)
            weather.refresh(id, force = true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _locationFailed.value = true
        } finally {
            _locating.value = false
        }
    }

    fun remove(id: Long) = viewModelScope.launch { places.remove(id) }

    fun restore(place: Place) = viewModelScope.launch {
        places.restore(place)
        weather.refresh(place.id)
    }

    fun rename(id: Long, name: String) = viewModelScope.launch { places.rename(id, name.trim()) }

    fun move(from: Int, to: Int) = viewModelScope.launch {
        val ids = list.value.filterNot { it.place.isCurrentLocation }.map { it.place.id }.toMutableList()
        if (from !in ids.indices || to !in ids.indices) return@launch
        ids.add(to, ids.removeAt(from))
        places.reorder(ids)
    }

    fun hasLocationPermission() = places.hasLocationPermission()
}
