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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antomanc.ameki.core.data.sync.PlaceWeather
import dev.antomanc.ameki.core.ui.components.WeatherIcon
import dev.antomanc.ameki.core.ui.label
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeRoute(
    onOpenPlaces: () -> Unit,
    onOpenSettings: () -> Unit,
    onFixProblem: (dev.antomanc.ameki.core.data.sync.SyncError) -> Unit,
    initialPlaceId: Long? = null,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        initialPlaceId = initialPlaceId ?: state.settings.selectedPlaceId,
        onPlaceShown = viewModel::onPlaceShown,
        onRefresh = { viewModel.refresh(it) },
        onOpenPlaces = onOpenPlaces,
        onOpenSettings = onOpenSettings,
        onFixProblem = onFixProblem,
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
) {
    val places = state.places
    val initialPage = places.indexOfFirst { it.place.id == initialPlaceId }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = initialPage) { places.size }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pager, places.size) {
        snapshotFlow { pager.currentPage }.collect { page -> places.getOrNull(page)?.let { onPlaceShown(it.place.id) } }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onOpenPlaces) { Icon(Icons.AutoMirrored.Outlined.List, stringResource(R.string.places)) }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings)) }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        if (places.isEmpty()) {
            EmptyPlaces(Modifier.padding(padding), onOpenPlaces)
            return@Scaffold
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val width = maxWidth
            val expanded = width >= 840.dp
            if (expanded) {
                // Unfolded Fold / tablet: list of places beside the detail.
                Row(Modifier.fillMaxSize()) {
                    PlaceList(
                        places = places,
                        selected = pager.currentPage,
                        state = state,
                        onSelect = { scope.launch { pager.scrollToPage(it) } },
                        modifier = Modifier.width(320.dp).fillMaxHeight(),
                    )
                    val pw = places[pager.currentPage.coerceIn(0, places.lastIndex)]
                    PlaceWeatherPane(
                        pw = pw, state = state, refreshing = pw.place.id in state.refreshing,
                        onRefresh = { onRefresh(pw.place.id) }, onFixProblem = onFixProblem,
                        twoColumns = width - 320.dp >= 600.dp,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    HorizontalPager(pager, Modifier.weight(1f), key = { places[it].place.id }) { page ->
                        val pw = places[page]
                        PlaceWeatherPane(
                            pw = pw, state = state, refreshing = pw.place.id in state.refreshing,
                            onRefresh = { onRefresh(pw.place.id) }, onFixProblem = onFixProblem,
                            twoColumns = width >= 600.dp,
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
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) {
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
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(bg).clickable { onSelect(i) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
