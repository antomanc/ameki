package dev.antomanc.ameki.feature.locations

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antomanc.ameki.core.data.sync.PlaceWeather
import dev.antomanc.ameki.core.model.Place
import dev.antomanc.ameki.core.model.TimeFormat
import dev.antomanc.ameki.core.model.Units
import dev.antomanc.ameki.core.ui.TimeFormatter
import dev.antomanc.ameki.core.ui.components.WeatherIcon
import dev.antomanc.ameki.core.ui.effects.rememberWeatherTime
import dev.antomanc.ameki.core.ui.label
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import dev.antomanc.ameki.core.ui.R as UiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationsRoute(onBack: () -> Unit, onOpenPlace: (Long) -> Unit, viewModel: LocationsViewModel = koinViewModel()) {
    val list by viewModel.list.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val units by viewModel.units.collectAsStateWithLifecycle()
    val locating by viewModel.locating.collectAsStateWithLifecycle()
    val locationFailed by viewModel.locationFailed.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val fmt = remember(context) { TimeFormatter(context, TimeFormat.SYSTEM) }
    val now = rememberWeatherTime()
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var renameId by rememberSaveable { mutableStateOf<Long?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }
    val removedMessage = stringResource(R.string.place_removed)
    val undoLabel = stringResource(R.string.undo)
    val remove: (Place) -> Unit = { place ->
        scope.launch {
            viewModel.remove(place.id).join()
            if (snackbar.showSnackbar(removedMessage, undoLabel) == SnackbarResult.ActionPerformed) viewModel.restore(place)
        }
    }
    val rename: (Place) -> Unit = { place -> renameId = place.id; renameText = place.name }
    val currentName = stringResource(UiR.string.current_location)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { permissionDenied = false; viewModel.useCurrentLocation(currentName, onOpenPlace) }
        else permissionDenied = true
    }
    val hasCurrent = list.any { it.place.isCurrentLocation }
    val saved = list.filterNot { it.place.isCurrentLocation }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.places_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "search") { PlaceSearch(search, viewModel::onQuery, { viewModel.add(it, onOpenPlace) }) }
            if (search.query.isBlank()) {
                if (permissionDenied || locationFailed) item(key = "perm-help") { LocationPermissionHelp(locationFailed) }
                if (!hasCurrent) {
                    item(key = "use-location") {
                        Row(
                            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.secondaryContainer)
                                .clickable {
                                    if (viewModel.hasLocationPermission()) viewModel.useCurrentLocation(currentName, onOpenPlace)
                                    else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (locating) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Outlined.LocationOn, null)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(if (locating) R.string.locating else R.string.use_my_location), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                list.firstOrNull { it.place.isCurrentLocation }?.let { cur ->
                    item(key = cur.place.id) {
                        Column(Modifier.animateItem()) {
                            SwipeablePlaceRow(
                                name = cur.place.name.ifBlank { currentName },
                                pw = cur,
                                units = units,
                                fmt = fmt,
                                now = now,
                                onOpen = { onOpenPlace(cur.place.id) },
                                onRemove = { remove(cur.place) },
                                current = true,
                            )
                            TextButton(onClick = {
                                if (viewModel.hasLocationPermission()) viewModel.useCurrentLocation(currentName, onOpenPlace)
                                else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            }, enabled = !locating) { Text(stringResource(R.string.retry_location)) }
                        }
                    }
                }
                itemsIndexed(saved, key = { _, it -> it.place.id }) { i, pw ->
                    Box(Modifier.animateItem()) {
                        SwipeablePlaceRow(
                            name = pw.place.name,
                            pw = pw,
                            units = units,
                            fmt = fmt,
                            now = now,
                            onOpen = { onOpenPlace(pw.place.id) },
                            onRemove = { remove(pw.place) },
                            onRename = { rename(pw.place) },
                            onUp = if (i > 0) ({ viewModel.move(i, i - 1) }) else null,
                            onDown = if (i < saved.lastIndex) ({ viewModel.move(i, i + 1) }) else null,
                        )
                    }
                }
                if (list.isEmpty()) {
                    item(key = "empty-hint") {
                        Text(
                            stringResource(R.string.places_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                        )
                    }
                }
                item(key = "bottom-space") { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
    renameId?.let { id ->
        AlertDialog(
            onDismissRequest = { renameId = null },
            title = { Text(stringResource(R.string.rename)) },
            text = { OutlinedTextField(renameText, { renameText = it.take(80) }, singleLine = true, label = { Text(stringResource(R.string.place_label)) }) },
            confirmButton = { TextButton(onClick = { viewModel.rename(id, renameText); renameId = null }, enabled = renameText.isNotBlank()) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { renameId = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Recovery remains available after repeated permission denials. City search needs no permission. */
@Composable
fun LocationPermissionHelp(locationFailed: Boolean = false) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(stringResource(if (locationFailed) R.string.location_failed else R.string.location_permission_denied), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))) }) {
            Text(stringResource(R.string.open_settings))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeablePlaceRow(
    name: String,
    pw: PlaceWeather,
    units: Units,
    fmt: TimeFormatter,
    now: Instant,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onRename: (() -> Unit)? = null,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    current: Boolean = false,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        onDismiss = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onRemove()
        },
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Outlined.Delete, stringResource(R.string.remove), tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        PlaceRow(name, pw, units, fmt, now, onOpen, onRemove, onRename, onUp, onDown, current)
    }
}

@Composable
private fun PlaceRow(
    name: String,
    pw: PlaceWeather,
    units: Units,
    fmt: TimeFormatter,
    now: Instant,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onRename: (() -> Unit)? = null,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    current: Boolean = false,
) {
    var menu by remember { mutableStateOf(false) }
    val snap = pw.snapshot
    val cur = snap?.current
    val today = snap?.daily?.firstOrNull()
    val conditionText = cur?.let { stringResource(it.condition.label(it.isDaytime)) }
    val localTime = snap?.let { fmt.time(now, it.timeZone) }
    val metaLine = listOfNotNull(localTime, conditionText ?: pw.place.subtitle).joinToString(" · ").takeIf { it.isNotEmpty() }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (current) {
            Icon(Icons.Outlined.LocationOn, null, Modifier.padding(end = 10.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            metaLine?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        cur?.let {
            WeatherIcon(it.condition, it.isDaytime, size = 32.dp)
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp, end = 4.dp)) {
                Text(units.temp(it.temperature), style = MaterialTheme.typography.titleLarge)
                today?.let { d ->
                    Text(
                        "${units.temp(d.temperatureMin)} / ${units.temp(d.temperatureMax)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.place_actions, name)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                onRename?.let { DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { menu = false; it() }) }
                onUp?.let { DropdownMenuItem(text = { Text(stringResource(R.string.move_up)) }, onClick = { menu = false; it() }) }
                onDown?.let { DropdownMenuItem(text = { Text(stringResource(R.string.move_down)) }, onClick = { menu = false; it() }) }
                DropdownMenuItem(text = { Text(stringResource(R.string.remove)) }, onClick = { menu = false; onRemove() })
            }
        }
    }
}

