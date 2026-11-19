package dev.antomanc.ameki.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antomanc.ameki.core.data.sync.PlaceWeather
import dev.antomanc.ameki.core.model.Place
import dev.antomanc.ameki.core.ui.AppRequest
import dev.antomanc.ameki.core.ui.PendingRequest
import dev.antomanc.ameki.core.ui.components.WeatherIcon
import dev.antomanc.ameki.core.ui.components.rememberAnimationsEnabled
import dev.antomanc.ameki.core.ui.label
import dev.antomanc.ameki.core.ui.theme.LocalInstrumentColors
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
    var openDay by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<List<Long>?>(null) }
    val dayTarget = openDay?.let { DayTarget(java.time.LocalDate.ofEpochDay(it[0]), it[1].takeIf { h -> h >= 0 }?.let(java.time.Instant::ofEpochSecond)) }
    fun openDayOf(owner: Long, target: DayTarget?) {
        openDay = target?.let { listOf(it.date.toEpochDay(), it.hour?.epochSecond ?: -1L, owner) }
    }
    val open: (DayTarget) -> Unit = { target ->
        places.getOrNull(pager.currentPage)?.place?.id?.let { owner -> openDayOf(owner, target) }
    }
    val ids = places.map { it.place.id }
    val animateUi = rememberAnimationsEnabled()

    // First load: start on the place chosen last time.
    var positioned by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(places.isEmpty(), requestPending) {
        // A pending request positions the pager itself once it knows where it leads.
        if (positioned || places.isEmpty() || requestPending) return@LaunchedEffect
        places.indexOfFirst { it.place.id == initialPlaceId }.takeIf { it >= 0 }?.let { pager.requestScrollToPage(it) }
        positioned = true
    }
    // A request moves to its place once; a later swipe by the user is not undone.
    val navigation = state.navigation
    LaunchedEffect(navigation?.requestId, navigation?.placeId) {
        val target = navigation?.placeId ?: return@LaunchedEffect
        places.indexOfFirst { it.place.id == target }.takeIf { it >= 0 }?.let { pager.requestScrollToPage(it) }
        positioned = true
    }
    LaunchedEffect(navigation?.requestId, navigation?.done) {
        val n = navigation?.takeIf { it.done } ?: return@LaunchedEffect
        // Showing a place closes any open day unless the request opens one; no place: stay put.
        n.placeId?.let { openDayOf(it, n.day) }
        positioned = true
        onNavigationApplied(n.requestId)
    }
    val selectionReady = positioned && !requestPending
    val openDayOwnerId = openDay?.getOrNull(2)
    LaunchedEffect(pager, ids, selectionReady, openDayOwnerId) {
        if (selectionReady && openDayOwnerId != null) onPlaceShown(openDayOwnerId)
        else if (selectionReady) snapshotFlow { pager.currentPage }.collect { page ->
            places.getOrNull(page)?.let { onPlaceShown(it.place.id) }
        }
    }

    val dayOwner = places.firstOrNull { it.place.id == openDayOwnerId }
    LaunchedEffect(dayOwner?.snapshot?.daily, dayTarget, ids) {
        if (places.isNotEmpty() && dayTarget != null && dayOwner?.snapshot?.daily.orEmpty().none { it.date == dayTarget.date }) openDay = null
    }

    // Smoothly interpolate atmospheric wash as the user swipes between places.
    val colors = LocalInstrumentColors.current
    fun atmosphereTint(index: Int): Color {
        val c = places.getOrNull(index)?.snapshot?.current ?: return Color.Transparent
        return colors.temperature(c.temperature).copy(alpha = if (c.isDaytime) 0.16f else 0.10f)
    }
    val offset = pager.currentPageOffsetFraction
    val baseTint = atmosphereTint(pager.currentPage)
    val adjacentIndex = when {
        offset > 0f -> pager.currentPage + 1
        offset < 0f -> pager.currentPage - 1
        else -> pager.currentPage
    }.coerceIn(0, (places.size - 1).coerceAtLeast(0))
    val targetTint = lerp(baseTint, atmosphereTint(adjacentIndex), kotlin.math.abs(offset).coerceIn(0f, 1f))
    val animatedTint by animateColorAsState(
        targetValue = targetTint,
        animationSpec = if (animateUi) tween(260) else snap(),
        label = "atmosphere-tint",
    )

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (animatedTint != Color.Transparent) {
            Box(Modifier.fillMaxWidth().height(520.dp).background(Brush.verticalGradient(listOf(animatedTint, Color.Transparent))))
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth
        val expanded = width >= 600.dp
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            topBar = {
                if (dayTarget == null || expanded) {
                    CenterAlignedTopAppBar(
                        title = {
                            if (!expanded && places.size > 1 && dayTarget == null) {
                                PageDots(places.map { it.place }, pager.currentPage)
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onOpenPlaces) { Icon(Icons.AutoMirrored.Outlined.List, stringResource(R.string.places)) }
                        },
                        actions = {
                            IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings)) }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
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
            val dayPage: @Composable (Modifier, Boolean) -> Unit = { m, embedded ->
                if (dayOwner != null && dayTarget != null) {
                    DayPage(
                        pw = dayOwner,
                        state = state,
                        target = dayTarget,
                        onDayChange = { d -> openDayOf(dayOwner.place.id, DayTarget(d, dayTarget.hour.takeIf { d == dayTarget.date })) },
                        onNeedHours = { date -> onNeedHours(dayOwner.place.id, date) },
                        onFixProblem = onFixProblem,
                        onBack = { openDay = null },
                        embedded = embedded,
                        modifier = m,
                    )
                }
            }
            if (expanded) {
                // Unfolded Fold / tablet: list of places beside the detail.
                Row(Modifier.fillMaxSize().padding(padding)) {
                    PlaceList(
                        places = places,
                        selected = pager.currentPage,
                        state = state,
                        onSelect = { openDay = null; pager.requestScrollToPage(it) },
                        onAddPlace = onOpenPlaces,
                        modifier = Modifier.width(if (width < 840.dp) 216.dp else 284.dp).fillMaxHeight(),
                    )
                    val pw = places[pager.currentPage.coerceIn(0, places.lastIndex)]
                    AnimatedContent(
                        targetState = dayTarget != null,
                        transitionSpec = {
                            if (!animateUi) fadeIn(snap()) togetherWith fadeOut(snap())
                            else if (targetState) (slideInHorizontally(tween(220)) { it / 12 } + fadeIn(tween(200))) togetherWith fadeOut(tween(150))
                            else fadeIn(tween(200)) togetherWith (slideOutHorizontally(tween(200)) { it / 12 } + fadeOut(tween(150)))
                        },
                        label = "expanded-pane",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) { showingDay ->
                        if (showingDay) {
                            dayPage(Modifier.fillMaxSize(), true)
                        } else {
                            PlaceWeatherPane(
                                pw = pw, state = state, refreshing = pw.place.id in state.refreshing,
                                onRefresh = { onRefresh(pw.place.id) }, onFixProblem = onFixProblem,
                                twoColumns = width - 284.dp >= 600.dp, onOpenDay = open,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            } else {
                AnimatedContent(
                    targetState = dayTarget != null,
                    transitionSpec = {
                        if (!animateUi) fadeIn(snap()) togetherWith fadeOut(snap())
                        else if (targetState) (slideInHorizontally(tween(220)) { it / 10 } + fadeIn(tween(200))) togetherWith fadeOut(tween(140))
                        else fadeIn(tween(200)) togetherWith (slideOutHorizontally(tween(200)) { it / 10 } + fadeOut(tween(140)))
                    },
                    label = "compact-pane",
                    modifier = Modifier.fillMaxSize(),
                ) { showingDay ->
                    if (showingDay) {
                        // DayPage owns its own top bar and status-bar insets when fullscreen on compact.
                        dayPage(Modifier.fillMaxSize(), false)
                    } else {
                        HorizontalPager(
                            pager,
                            Modifier.fillMaxSize().padding(padding),
                            key = { currentPlaces.getOrNull(it)?.place?.id ?: "missing-$it" },
                        ) { page ->
                            val pw = currentPlaces.getOrNull(page) ?: return@HorizontalPager
                            PlaceWeatherPane(
                                pw = pw, state = state, refreshing = pw.place.id in state.refreshing,
                                onRefresh = { onRefresh(pw.place.id) }, onFixProblem = onFixProblem,
                                twoColumns = false, onOpenDay = open,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageDots(places: List<Place>, current: Int) {
    val pageLabel = stringResource(R.string.page_position, current + 1, places.size)
    val animate = rememberAnimationsEnabled()
    Row(
        Modifier.semantics { contentDescription = pageLabel },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        places.forEachIndexed { i, place ->
            val active = i == current
            val color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
            if (place.isCurrentLocation) {
                Icon(
                    Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(if (active) 14.dp else 11.dp),
                )
            } else {
                val pillWidth by animateDpAsState(
                    targetValue = if (active) 18.dp else 6.dp,
                    animationSpec = if (animate) tween(220) else snap(),
                    label = "page-dot-$i",
                )
                Box(
                    Modifier
                        .height(6.dp)
                        .width(pillWidth)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }
    }
}

@Composable
private fun PlaceList(
    places: List<PlaceWeather>,
    selected: Int,
    state: HomeUiState,
    onSelect: (Int) -> Unit,
    onAddPlace: () -> Unit,
    modifier: Modifier,
) {
    val colors = LocalInstrumentColors.current
    LazyColumn(modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(places, key = { _, it -> it.place.id }) { i, pw ->
            val isSelected = i == selected
            val bg = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
            val rowModifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(bg)
                .clickable { onSelect(i) }
                .padding(horizontal = 14.dp, vertical = 14.dp)
            if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f) {
                Column(rowModifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(pw.place.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    pw.snapshot?.current?.let { current ->
                        Text(state.settings.units.temp(current.temperature), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(current.condition.label(current.isDaytime)), style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
                if (isSelected) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(28.dp)
                            .clip(CircleShape)
                            .background(colors.now),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (pw.place.isCurrentLocation) {
                            Icon(Icons.Outlined.LocationOn, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Text(pw.place.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    pw.snapshot?.current?.let {
                        Text(stringResource(it.condition.label(it.isDaytime)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                pw.snapshot?.current?.let {
                    WeatherIcon(it.condition, it.isDaytime, size = 30.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(state.settings.units.temp(it.temperature), style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
        item {
            Spacer(Modifier.height(4.dp))
            FilledTonalButton(
                onClick = onAddPlace,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.add_place), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(16.dp))
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
