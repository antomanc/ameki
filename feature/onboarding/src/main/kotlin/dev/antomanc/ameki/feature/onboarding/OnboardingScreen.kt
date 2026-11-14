package dev.antomanc.ameki.feature.onboarding

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antomanc.ameki.core.model.SourceId
import dev.antomanc.ameki.core.model.WeatherCondition
import dev.antomanc.ameki.core.ui.components.AnimatedWeatherIcon
import dev.antomanc.ameki.core.ui.components.TemperatureDial
import dev.antomanc.ameki.feature.locations.LocationsViewModel
import dev.antomanc.ameki.feature.locations.PlaceSearch
import dev.antomanc.ameki.feature.settings.GoogleKeySetup
import dev.antomanc.ameki.feature.settings.SettingsViewModel
import org.koin.androidx.compose.koinViewModel
import dev.antomanc.ameki.core.ui.R as UiR

private enum class Step { WELCOME, SOURCE, LOCATION, WIDGET }

/**
 * Three screens (four when the build has more than one source): welcome, where, widget.
 * Everything but the place is skippable.
 */
@Composable
fun OnboardingRoute(
    onDone: () -> Unit,
    onPinWidget: (() -> Unit)?,
    settingsVm: SettingsViewModel = koinViewModel(),
    placesVm: LocationsViewModel = koinViewModel(),
) {
    val settings by settingsVm.state.collectAsStateWithLifecycle()
    val keyState by settingsVm.keyState.collectAsStateWithLifecycle()
    val search by placesVm.search.collectAsStateWithLifecycle()
    val places by placesVm.list.collectAsStateWithLifecycle()
    val locating by placesVm.locating.collectAsStateWithLifecycle()
    val steps = buildList {
        add(Step.WELCOME)
        if (settings.sources.size > 1) add(Step.SOURCE)
        add(Step.LOCATION)
        add(Step.WIDGET)
    }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[index.coerceIn(0, steps.lastIndex)]
    fun next() { if (index < steps.lastIndex) index++ else onDone() }
    BackHandler(enabled = index > 0) { index-- }

    val currentName = stringResource(UiR.string.current_location)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) placesVm.useCurrentLocation(currentName) { next() }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        AnimatedContent(
            step,
            transitionSpec = { (slideInHorizontally(tween(320)) { it / 6 } + fadeIn(tween(320))) togetherWith fadeOut(tween(160)) },
            label = "onboarding",
        ) { s ->
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                    when (s) {
                        Step.WELCOME -> Welcome(onNext = ::next)
                        Step.SOURCE -> {
                            Title(R.string.ob_source_title, R.string.ob_source_body)
                            SourceCard(R.string.ob_source_om, R.string.ob_source_om_body, settings.settings.source == SourceId.OPEN_METEO) { settingsVm.setSource(SourceId.OPEN_METEO) }
                            Spacer(Modifier.height(12.dp))
                            SourceCard(R.string.ob_source_google, R.string.ob_source_google_body, settings.settings.source == SourceId.GOOGLE) { settingsVm.setSource(SourceId.GOOGLE) }
                            if (settings.settings.source == SourceId.GOOGLE) {
                                Spacer(Modifier.height(12.dp))
                                GoogleKeySetup(settings.settings.hasGoogleApiKey, keyState, settingsVm.appIdentity, { settingsVm.submitKey(it) }, settingsVm::removeKey)
                            }
                            Spacer(Modifier.height(24.dp))
                            val ready = settings.settings.source == SourceId.OPEN_METEO || settings.settings.hasGoogleApiKey
                            Button(onClick = ::next, enabled = ready, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ob_continue)) }
                        }
                        Step.LOCATION -> {
                            Title(R.string.ob_location_title, R.string.ob_location_body)
                            Button(
                                onClick = {
                                    if (placesVm.hasLocationPermission()) placesVm.useCurrentLocation(currentName) { next() }
                                    else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                },
                                enabled = !locating,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                            ) {
                                if (locating) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.size(12.dp))
                                    Text(stringResource(R.string.ob_locating))
                                } else {
                                    Text(stringResource(R.string.ob_use_location))
                                }
                            }
                            Text(
                                stringResource(R.string.ob_location_privacy),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                            Text(stringResource(R.string.ob_or_search), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp))
                            PlaceSearch(search, placesVm::onQuery, { placesVm.add(it) { next() } })
                            if (places.isNotEmpty()) {
                                TextButton(onClick = ::next, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.ob_continue)) }
                            }
                        }
                        Step.WIDGET -> {
                            Title(R.string.ob_widget_title, R.string.ob_widget_body)
                            WidgetPreview(Modifier.align(Alignment.CenterHorizontally))
                            Spacer(Modifier.height(28.dp))
                            if (onPinWidget != null) {
                                Button(onClick = { onPinWidget(); onDone() }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.ob_add_widget)) }
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ob_later)) }
                            } else {
                                Text(stringResource(R.string.ob_widget_manual), style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ob_finish)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Welcome(onNext: () -> Unit) {
    Spacer(Modifier.height(32.dp))
    Box(Modifier.fillMaxWidth(0.8f).aspectRatio(1f).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
        TemperatureDial(current = 17.0, low = 11.0, high = 21.0, description = "", modifier = Modifier.fillMaxSize()) {
            AnimatedWeatherIcon(WeatherCondition.PARTLY_CLOUDY, true, size = 120.dp)
        }
    }
    Spacer(Modifier.height(24.dp))
    Text("Ameki", style = MaterialTheme.typography.displayMedium, modifier = Modifier.semantics { heading() })
    Text("あめき · 雨木", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.ob_welcome_body), style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(32.dp))
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.ob_start)) }
}

@Composable
private fun Title(title: Int, body: Int) {
    Spacer(Modifier.height(24.dp))
    Text(stringResource(title), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(8.dp))
    Text(stringResource(body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun SourceCard(title: Int, body: Int, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(bg).clickable(onClick = onClick).padding(20.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A drawn preview of the Compact widget: the same dial, at widget scale. */
@Composable
private fun WidgetPreview(modifier: Modifier) {
    Box(
        modifier.size(180.dp).clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        TemperatureDial(current = 17.0, low = 11.0, high = 21.0, description = "", strokeWidth = 8.dp, modifier = Modifier.fillMaxSize()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("17°", style = MaterialTheme.typography.displaySmall)
                Text(stringResource(R.string.ob_widget_place), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
