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
import dev.antomanc.ameki.core.ui.AppRequest
import dev.antomanc.ameki.core.ui.PendingRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
    /** Where the current request wants the screen to go. */
    val navigation: Navigation? = null,
)

/**
 * The screen's next move for request [requestId]: show [placeId] (null: stay) and, once [done],
 * open [day] (null: close any open day). Applied by the screen, then reported back.
 */
data class Navigation(val requestId: Long, val placeId: Long?, val day: DayTarget?, val done: Boolean)

class HomeViewModel(
    private val weather: WeatherRepository,
    private val places: PlaceRepository,
    private val settings: SettingsRepository,
    private val sources: SourceRegistry,
) : ViewModel() {
    private val refreshing = MutableStateFlow<Set<Long>>(emptySet())
    private val navigation = MutableStateFlow<Navigation?>(null)
    private var requestJob: Job? = null
    private var requestId: Long? = null

    val state: StateFlow<HomeUiState> = combine(weather.all, settings.settings, refreshing, navigation) { list, s, r, n ->
        HomeUiState(
            loading = false,
            places = list,
            settings = s,
            capabilities = sources[s.source].capabilities,
            refreshing = r,
            navigation = n,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Called when a place becomes visible: marks it as in use and fetches what is due. */
    fun onPlaceShown(id: Long) = viewModelScope.launch {
        places.markOpened(id)
        settings.setSelectedPlace(id)
        refresh(id, force = false)
    }

    /** A day page is showing [date]: fetch its missing hours; progress shows in [PlaceWeather.dayHours]. */
    fun loadDayHours(id: Long, date: java.time.LocalDate) {
        viewModelScope.launch { weather.loadDayHours(id, date) }
    }

    /** Works out where [pending] leads; the screen follows [HomeUiState.navigation]. */
    fun handle(pending: PendingRequest) {
        if (pending.id == requestId) return
        requestId = pending.id
        requestJob?.cancel()
        navigation.value = null
        requestJob = viewModelScope.launch {
            when (val r = pending.request) {
                is AppRequest.Show -> show(pending.id, r)
                AppRequest.Refresh -> {
                    val list = weather.all.first()
                    val selected = settings.current().selectedPlaceId
                    (list.firstOrNull { it.place.id == selected } ?: list.firstOrNull())?.let { refresh(it.place.id) }
                    navigation.value = Navigation(pending.id, null, null, done = true)
                }
                // Handled by the activity; nothing to do on this screen.
                AppRequest.OpenPlaces, AppRequest.PinWidget -> navigation.value = Navigation(pending.id, null, null, done = true)
            }
        }
    }

    private suspend fun show(id: Long, request: AppRequest.Show) {
        var fetchedFrom: Attempt? = null
        combine(weather.all, refreshing, ::Pair).first { (list, busy) ->
            when (val step = nextStep(request, list, busy, fetchedFrom)) {
                is ShowStep.Show -> {
                    navigation.value = Navigation(id, step.placeId, step.day, done = true)
                    true
                }
                is ShowStep.Fetch -> {
                    fetchedFrom = Attempt(list.first { it.place.id == step.placeId }.lastAttemptAt)
                    navigation.value = Navigation(id, step.placeId, null, done = false)
                    refresh(step.placeId, force = false)
                    false
                }
                is ShowStep.Wait -> {
                    navigation.value = Navigation(id, step.placeId, null, done = false)
                    false
                }
            }
        }
    }

    /** The screen applied the final [Navigation] for [id]. */
    fun navigationApplied(id: Long) {
        if (navigation.value?.requestId == id && navigation.value?.done == true) navigation.value = null
    }

    fun refresh(id: Long, force: Boolean = true) {
        if (id in refreshing.value) return
        // Marked before launching, so a second call in the same frame sees it.
        refreshing.value += id
        viewModelScope.launch {
            try {
                weather.refresh(id, force = force)
            } finally {
                refreshing.value -= id
            }
        }
    }
}
