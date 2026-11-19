package dev.antomanc.ameki.feature.onboarding

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antomanc.ameki.core.model.SourceId
import dev.antomanc.ameki.core.model.WeatherCondition
import dev.antomanc.ameki.core.ui.components.AnimatedWeatherIcon
import dev.antomanc.ameki.core.ui.components.TemperatureDial
import dev.antomanc.ameki.feature.locations.LocationPermissionHelp
import dev.antomanc.ameki.feature.locations.LocationsViewModel
import dev.antomanc.ameki.feature.locations.PlaceSearch
import dev.antomanc.ameki.feature.settings.GoogleKeySetup
import dev.antomanc.ameki.feature.settings.SettingsViewModel
import org.koin.androidx.compose.koinViewModel
import dev.antomanc.ameki.core.ui.R as UiR

private enum class Step { WELCOME, SOURCE, LOCATION, WIDGET }

/**
 * Three screens (four when the build has more than one source): welcome, where, widget.
 * Setup can be completed offline and the place added later.
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
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    val locationFailed by placesVm.locationFailed.collectAsStateWithLifecycle()
    val locating by placesVm.locating.collectAsStateWithLifecycle()
    val steps = buildList {
        add(Step.WELCOME)
        if (settings.sources.size > 1) add(Step.SOURCE)
        add(Step.LOCATION)
        add(Step.WIDGET)
    }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val stepIndex = index.coerceIn(0, steps.lastIndex)
    val step = steps[stepIndex]
    fun next() { if (index < steps.lastIndex) index++ else onDone() }
    fun back() { if (index > 0) index-- }
    BackHandler(enabled = index > 0, onBack = ::back)

    val currentName = stringResource(UiR.string.current_location)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { permissionDenied = false; placesVm.useCurrentLocation(currentName) { next() } }
        else permissionDenied = true
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OnboardingHeader(
                stepIndex = stepIndex,
                stepCount = steps.size,
                canGoBack = stepIndex > 0,
                onBack = ::back,
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
            )
            AnimatedContent(
                targetState = step,
                modifier = Modifier.weight(1f).widthIn(max = 520.dp).fillMaxWidth(),
                transitionSpec = {
                    val forward = targetState.ordinal >= initialState.ordinal
                    val sign = if (forward) 1 else -1
                    (slideInHorizontally(tween(300)) { sign * it / 5 } + fadeIn(tween(260))) togetherWith
                        (slideOutHorizontally(tween(200)) { -sign * it / 8 } + fadeOut(tween(160)))
                },
                label = "onboarding",
            ) { s ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 12.dp),
                ) {
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
                            Spacer(Modifier.height(28.dp))
                            val ready = settings.settings.source == SourceId.OPEN_METEO || settings.settings.hasGoogleApiKey
                            Button(onClick = ::next, enabled = ready, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                                Text(stringResource(R.string.ob_continue))
                            }
                        }
                        Step.LOCATION -> {
                            Title(R.string.ob_location_title, R.string.ob_location_body)
                            Button(
                                onClick = {
                                    if (placesVm.hasLocationPermission()) placesVm.useCurrentLocation(currentName) { next() }
                                    else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                },
                                enabled = !locating,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            ) {
                                if (locating) {
                                    CircularProgressIndicator(
                                        Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.size(12.dp))
                                    Text(stringResource(R.string.ob_locating))
                                } else {
                                    Text(stringResource(R.string.ob_use_location))
                                }
                            }
                            if (permissionDenied || locationFailed) LocationPermissionHelp(locationFailed)
                            Text(
                                stringResource(R.string.ob_location_privacy),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                            Text(stringResource(R.string.ob_or_search), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp))
                            PlaceSearch(search, placesVm::onQuery, { placesVm.add(it) { next() } })
                            Spacer(Modifier.height(12.dp))
                            TextButton(onClick = if (places.isNotEmpty()) ::next else onDone, modifier = Modifier.align(Alignment.End)) {
                                Text(stringResource(if (places.isNotEmpty()) R.string.ob_continue else R.string.ob_choose_later))
                            }
                        }
                        Step.WIDGET -> {
                            Title(R.string.ob_widget_title, R.string.ob_widget_body)
                            WidgetPreview(Modifier.align(Alignment.CenterHorizontally))
                            Spacer(Modifier.height(28.dp))
                            if (onPinWidget != null) {
                                Button(onClick = { onPinWidget(); onDone() }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                                    Text(stringResource(R.string.ob_add_widget))
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                    Text(stringResource(R.string.ob_later))
                                }
                            } else {
                                Text(stringResource(R.string.ob_widget_manual), style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                                    Text(stringResource(R.string.ob_finish))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingHeader(
    stepIndex: Int,
    stepCount: Int,
    canGoBack: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stepDescription = stringResource(R.string.ob_step_of, stepIndex + 1, stepCount)
    Row(
        modifier = modifier.heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canGoBack) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.ob_back))
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
        Row(
            Modifier.weight(1f).semantics { contentDescription = stepDescription },
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(stepCount) { i ->
                val active = i == stepIndex
                val passed = i < stepIndex
                val width by animateDpAsState(if (active) 24.dp else 8.dp, tween(240), label = "ob-step-w")
                val color by animateColorAsState(
                    when {
                        active -> MaterialTheme.colorScheme.primary
                        passed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        else -> MaterialTheme.colorScheme.outlineVariant
                    },
                    tween(240),
                    label = "ob-step-c",
                )
                Box(Modifier.width(width).height(6.dp).clip(CircleShape).background(color))
            }
        }
        Spacer(Modifier.size(48.dp))
    }
}

@Composable
private fun ColumnScope.Welcome(onNext: () -> Unit) {
    Spacer(Modifier.height(16.dp))
    Box(Modifier.fillMaxWidth(0.76f).widthIn(max = 260.dp).aspectRatio(1f).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
        TemperatureDial(
            current = 17.0,
            low = 11.0,
            high = 21.0,
            description = "",
            lowLabel = "11°",
            highLabel = "21°",
            modifier = Modifier.fillMaxSize(),
        ) {
            AnimatedWeatherIcon(WeatherCondition.PARTLY_CLOUDY, true, size = 112.dp)
        }
    }
    Spacer(Modifier.height(24.dp))
    Text("Ameki", style = MaterialTheme.typography.displayMedium, modifier = Modifier.semantics { heading() })
    Text("あめき · 雨木", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.ob_welcome_body), style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(32.dp))
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.ob_start)) }
}

@Composable
private fun Title(title: Int, body: Int) {
    Spacer(Modifier.height(12.dp))
    Text(stringResource(title), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(8.dp))
    Text(stringResource(body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun SourceCard(title: Int, body: Int, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer
    val border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
    Surface(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = bg,
        border = border,
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) {
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                }
            } else {
                Spacer(Modifier.width(40.dp))
            }
        }
    }
}

/** A drawn preview of the Compact widget: the same dial, at widget scale. */
@Composable
private fun WidgetPreview(modifier: Modifier) {
    Surface(
        modifier = modifier.size(188.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Box(Modifier.fillMaxSize().padding(14.dp), contentAlignment = Alignment.Center) {
            TemperatureDial(
                current = 17.0,
                low = 11.0,
                high = 21.0,
                description = "",
                strokeWidth = 8.dp,
                lowLabel = "11°",
                highLabel = "21°",
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("17°", style = MaterialTheme.typography.displaySmall)
                    Text(stringResource(R.string.ob_widget_place), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

