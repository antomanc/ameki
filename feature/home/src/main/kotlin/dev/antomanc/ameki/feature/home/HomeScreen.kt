package dev.antomanc.ameki.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antomanc.ameki.core.data.sync.PlaceWeather
import dev.antomanc.ameki.core.ui.AppRequest
import dev.antomanc.ameki.core.ui.PendingRequest
import dev.antomanc.ameki.core.ui.components.WeatherIcon
import dev.antomanc.ameki.core.ui.label
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeRoute(
    onOpenPlaces: () -> Unit,
    onOpenSettings: () -> Unit,
    onFixProblem: (dev.antomanc.ameki.core.data.sync.SyncError) -> Unit,
    /** A request from a widget, a shortcut or the places list, until [onRequestHandled] reports it. */
    request: PendingRequest? = null,
    onRequestHandled: (Long) -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(request?.id) { request?.let(viewModel::handle) }
    HomeScreen(
        state = state,
        // A cold start from a widget opens on the requested place, not the last one.
        initialPlaceId = (request?.request as? AppRequest.Show)?.placeId ?: state.settings.selectedPlaceId,
        onPlaceShown = viewModel::onPlaceShown,
        onRefresh = { viewModel.refresh(it) },
        onOpenPlaces = onOpenPlaces,
        onOpenSettings = onOpenSettings,
        onFixProblem = onFixProblem,
        onNeedHours = viewModel::loadDayHours,
        requestPending = request != null,
        onNavigationApplied = { id ->
            viewModel.navigationApplied(id)
            onRequestHandled(id)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    initialPlaceId: Long?,
    onPlaceShown: (Long) -> Unit,
    onRefresh: (Long) -> Unit,
    onOpenPlaces: () -> Unit,
    onOpenSettings: () -> Unit,
    onFixProblem: (dev.antomanc.ameki.core.data.sync.SyncError) -> Unit,
    onNeedHours: (Long, java.time.LocalDate) -> Unit = { _, _ -> },
    /** A request is on its way: don't mark the page that happens to be visible as chosen. */
    requestPending: Boolean = false,
    onNavigationApplied: (Long) -> Unit = {},
) {
    val places = state.places
    val initialPage = places.indexOfFirst { it.place.id == initialPlaceId }.coerceAtLeast(0)
    val currentPlaces by androidx.compose.runtime.rememberUpdatedState(places)
    val pager = rememberPagerState(initialPage = initialPage) { currentPlaces.size }
    // An open day page, saved across rotation and folding as [epoch day, epoch second or -1, place].
    var openDay by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<List<Long>?>(null) }
    val dayTarget = openDay?.let { DayTarget(java.time.LocalDate.ofEpochDay(it[0]), it[1].takeIf { h -> h >= 0 }?.let(java.time.Instant::ofEpochSecond)) }
    fun openDayOf(owner: Long, target: DayTarget?) {
        openDay = target?.let { listOf(it.date.toEpochDay(), it.hour?.epochSecond ?: -1L, owner) }
    }
    val open: (DayTarget) -> Unit = { target ->
        places.getOrNull(pager.currentPage)?.place?.id?.let { owner -> openDayOf(owner, target) }
    }
    val scope = rememberCoroutineScope()
    val ids = places.map { it.place.id }

    // First load: start on the place chosen last time.
    var positioned by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(places.isEmpty()) {
        if (positioned || places.isEmpty()) return@LaunchedEffect
        places.indexOfFirst { it.place.id == initialPlaceId }.takeIf { it >= 0 }?.let { pager.requestScrollToPage(it) }
        positioned = true
    }
    // A request moves to its place once; a later swipe by the user is not undone.
    val navigation = state.navigation
    LaunchedEffect(navigation?.requestId, navigation?.placeId) {
        val target = navigation?.placeId ?: return@LaunchedEffect
        places.indexOfFirst { it.place.id == target }.takeIf { it >= 0 }?.let { pager.requestScrollToPage(it) }
    }
    LaunchedEffect(navigation?.requestId, navigation?.done) {
        val n = navigation?.takeIf { it.done } ?: return@LaunchedEffect
        // Showing a place closes any open day unless the request opens one; no place: stay put.
        n.placeId?.let { openDayOf(it, n.day) }
        onNavigationApplied(n.requestId)
    }
    val selectionReady = positioned && !requestPending
    LaunchedEffect(pager, ids, selectionReady, openDay?.getOrNull(2)) {
        if (selectionReady && openDay?.getOrNull(2) != null) onPlaceShown(openDay!![2])
        else if (selectionReady) snapshotFlow { pager.currentPage }.collect { page ->
            places.getOrNull(page)?.let { onPlaceShown(it.place.id) }
        }
    }

    val shown = places.getOrNull(pager.currentPage)?.snapshot
    val dayOwner = places.firstOrNull { it.place.id == openDay?.getOrNull(2) }
    LaunchedEffect(dayOwner?.snapshot?.daily, dayTarget, ids) {
        if (places.isNotEmpty() && dayTarget != null && dayOwner?.snapshot?.daily.orEmpty().none { it.date == dayTarget.date }) openDay = null
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) { Atmosphere(shown) }
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            if (dayTarget == null) {
                CenterAlignedTopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onOpenPlaces) { Icon(Icons.AutoMirrored.Outlined.List, stringResource(R.string.places)) }
                    },
                    actions = {
                        IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings)) }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                )
            }
        },
    ) { padding ->
        if (places.isEmpty()) {
            if (state.loading) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator()
            } else EmptyPlaces(Modifier.padding(padding), onOpenPlaces)
            return@Scaffold
        }
        val current = places.getOrNull(pager.currentPage.coerceIn(0, places.lastIndex))
        val dayPage: @Composable (Modifier) -> Unit = { m ->
            if (dayOwner != null && dayTarget != null) {
                DayPage(
                    dayOwner, state, dayTarget,
                    onDayChange = { d -> openDayOf(dayOwner.place.id, DayTarget(d, dayTarget.hour.takeIf { d == dayTarget.date })) },
                    onNeedHours = { date -> onNeedHours(dayOwner.place.id, date) },
                    onBack = { openDay = null },
                    modifier = m,
                )
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val width = maxWidth
            val expanded = width >= 600.dp
            if (expanded) {
                // Unfolded Fold / tablet: list of places beside the detail.
                Row(Modifier.fillMaxSize()) {
                    PlaceList(
                        places = places,
                        selected = pager.currentPage,
                        state = state,
                        onSelect = { openDay = null; pager.requestScrollToPage(it) },
                        modifier = Modifier.width(if (width < 840.dp) 200.dp else 280.dp).fillMaxHeight(),
                    )
                    val pw = places[pager.currentPage.coerceIn(0, places.lastIndex)]
                    if (dayTarget != null) {
                        dayPage(Modifier.weight(1f))
                    } else {
                        PlaceWeatherPane(
                            pw = pw, state = state, refreshing = pw.place.id in state.refreshing,
                            onRefresh = { onRefresh(pw.place.id) }, onFixProblem = onFixProblem,
                            twoColumns = width - 280.dp >= 600.dp, onOpenDay = open,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else if (dayTarget != null) {
                dayPage(Modifier.fillMaxSize())
            } else {
                Column(Modifier.fillMaxSize()) {
                    HorizontalPager(pager, Modifier.weight(1f), key = { currentPlaces.getOrNull(it)?.place?.id ?: "missing-$it" }) { page ->
                        val pw = currentPlaces.getOrNull(page) ?: return@HorizontalPager
                        PlaceWeatherPane(
                            pw = pw, state = state, refreshing = pw.place.id in state.refreshing,
                            onRefresh = { onRefresh(pw.place.id) }, onFixProblem = onFixProblem,
                            twoColumns = width >= 600.dp, onOpenDay = open,
                        )
                    }
                    if (places.size > 1) PageDots(places.size, pager.currentPage)
                }
            }
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    val pageLabel = stringResource(R.string.page_position, current + 1, count)
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp).semantics { contentDescription = pageLabel }, horizontalArrangement = Arrangement.Center) {
        repeat(count) { i ->
            Box(
                Modifier.padding(horizontal = 4.dp).size(if (i == current) 8.dp else 6.dp).clip(CircleShape)
                    .background(if (i == current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

@Composable
private fun PlaceList(places: List<PlaceWeather>, selected: Int, state: HomeUiState, onSelect: (Int) -> Unit, modifier: Modifier) {
    LazyColumn(modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        itemsIndexed(places, key = { _, it -> it.place.id }) { i, pw ->
            val bg = if (i == selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
            val rowModifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(bg).clickable { onSelect(i) }.padding(16.dp)
            if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f) {
                Column(rowModifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(pw.place.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    pw.snapshot?.current?.let { current ->
                        Text(state.settings.units.temp(current.temperature), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(current.condition.label(current.isDaytime)), style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(pw.place.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    pw.snapshot?.current?.let {
                        Text(stringResource(it.condition.label(it.isDaytime)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                pw.snapshot?.current?.let {
                    WeatherIcon(it.condition, it.isDaytime, size = 32.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(state.settings.units.temp(it.temperature), style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }
}

@Composable
private fun EmptyPlaces(modifier: Modifier, onOpenPlaces: () -> Unit) {
    Column(modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.no_places_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.no_places_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(20.dp))
        androidx.compose.material3.Button(onClick = onOpenPlaces) { Text(stringResource(R.string.add_place)) }
    }
}
