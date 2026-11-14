package dev.antomanc.ameki.feature.locations

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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
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
    val currentName = stringResource(UiR.string.current_location)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.useCurrentLocation(currentName, onOpenPlace)
    }
    val hasCurrent = list.any { it.place.isCurrentLocation }
    val saved = list.filterNot { it.place.isCurrentLocation }

    Scaffold(
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
                        PlaceRow(cur.place.name.ifBlank { currentName }, cur, units, onOpen = { onOpenPlace(cur.place.id) }, onRemove = { viewModel.remove(cur.place.id) }, current = true)
                    }
                }
                itemsIndexed(saved, key = { _, it -> it.place.id }) { i, pw ->
                    PlaceRow(
                        pw.place.name, pw, units,
                        onOpen = { onOpenPlace(pw.place.id) },
                        onRemove = { viewModel.remove(pw.place.id) },
                        onUp = if (i > 0) ({ viewModel.move(i, i - 1) }) else null,
                        onDown = if (i < saved.lastIndex) ({ viewModel.move(i, i + 1) }) else null,
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
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
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    current: Boolean = false,
) {
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
        onUp?.let { IconButton(onClick = it) { Icon(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.move_up)) } }
        onDown?.let { IconButton(onClick = it) { Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.move_down)) } }
        IconButton(onClick = onRemove) { Icon(Icons.Outlined.Delete, stringResource(R.string.remove)) }
    }
}
