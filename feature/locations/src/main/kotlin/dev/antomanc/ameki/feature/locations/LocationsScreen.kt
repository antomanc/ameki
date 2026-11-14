package dev.antomanc.ameki.feature.locations

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.outlined.MoreVert
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antomanc.ameki.core.ui.components.WeatherIcon
import org.koin.androidx.compose.koinViewModel
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
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var renameId by rememberSaveable { mutableStateOf<Long?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }
    val removedMessage = stringResource(R.string.place_removed)
    val undoLabel = stringResource(R.string.undo)
    val remove: (dev.antomanc.ameki.core.model.Place) -> Unit = { place ->
        scope.launch {
            viewModel.remove(place.id).join()
            if (snackbar.showSnackbar(removedMessage, undoLabel) == SnackbarResult.ActionPerformed) viewModel.restore(place)
        }
    }
    val rename: (dev.antomanc.ameki.core.model.Place) -> Unit = { place -> renameId = place.id; renameText = place.name }
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { PlaceSearch(search, viewModel::onQuery, { viewModel.add(it, onOpenPlace) }) }
            if (search.query.isBlank()) {
                if (permissionDenied || locationFailed) item { LocationPermissionHelp(locationFailed) }
                if (!hasCurrent) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.secondaryContainer)
                                .clickable {
                                    if (viewModel.hasLocationPermission()) viewModel.useCurrentLocation(currentName, onOpenPlace)
                                    else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (locating) androidx.compose.material3.CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Outlined.LocationOn, null)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(if (locating) R.string.locating else R.string.use_my_location), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                list.firstOrNull { it.place.isCurrentLocation }?.let { cur ->
                    item(key = cur.place.id) {
                        PlaceRow(cur.place.name.ifBlank { currentName }, cur, units, onOpen = { onOpenPlace(cur.place.id) }, onRemove = { remove(cur.place) }, current = true)
                        TextButton(onClick = {
                            if (viewModel.hasLocationPermission()) viewModel.useCurrentLocation(currentName, onOpenPlace)
                            else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }, enabled = !locating) { Text(stringResource(R.string.retry_location)) }
                    }
                }
                itemsIndexed(saved, key = { _, it -> it.place.id }) { i, pw ->
                    PlaceRow(
                        pw.place.name, pw, units,
                        onOpen = { onOpenPlace(pw.place.id) },
                        onRemove = { remove(pw.place) },
                        onRename = { rename(pw.place) },
                        onUp = if (i > 0) ({ viewModel.move(i, i - 1) }) else null,
                        onDown = if (i < saved.lastIndex) ({ viewModel.move(i, i + 1) }) else null,
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
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

@Composable
private fun PlaceRow(
    name: String,
    pw: dev.antomanc.ameki.core.data.sync.PlaceWeather,
    units: dev.antomanc.ameki.core.model.Units,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onRename: (() -> Unit)? = null,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    current: Boolean = false,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onOpen)
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (current) { Icon(Icons.Outlined.LocationOn, null, Modifier.padding(end = 8.dp)) }
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            pw.place.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        }
        pw.snapshot?.current?.let {
            WeatherIcon(it.condition, it.isDaytime, size = 30.dp)
            Text(units.temp(it.temperature), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 8.dp))
        }
        androidx.compose.foundation.layout.Box {
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
