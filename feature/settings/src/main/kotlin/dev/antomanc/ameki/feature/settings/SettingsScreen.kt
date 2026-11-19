package dev.antomanc.ameki.feature.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antomanc.ameki.core.data.update.ReleaseUpdateChecker
import dev.antomanc.ameki.core.model.DistanceUnit
import dev.antomanc.ameki.core.model.PrecipitationUnit
import dev.antomanc.ameki.core.model.PressureUnit
import dev.antomanc.ameki.core.model.RefreshPolicy
import dev.antomanc.ameki.core.model.SourceId
import dev.antomanc.ameki.core.model.TemperatureUnit
import dev.antomanc.ameki.core.model.ThemeMode
import dev.antomanc.ameki.core.model.TimeFormat
import dev.antomanc.ameki.core.model.WindSpeedUnit
import org.koin.androidx.compose.koinViewModel
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(onBack: () -> Unit, viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val key by viewModel.keyState.collectAsStateWithLifecycle()
    val update by viewModel.updateState.collectAsStateWithLifecycle()
    val s = state.settings
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.sources.size > 1) {
                SettingsGroup(R.string.section_source) {
                    Row(R.string.source, sourceName(s.source)) { dialog = "source" }
                    if (s.source == SourceId.GOOGLE) {
                        GroupDivider()
                        GoogleKeySetup(
                            hasKey = s.hasGoogleApiKey, check = key, identity = viewModel.appIdentity,
                            onSubmit = { viewModel.submitKey(it) }, onRemove = viewModel::removeKey,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }

            SettingsGroup(R.string.section_updates) {
                Row(R.string.refresh_interval, stringResource(R.string.every_n_minutes, s.refreshIntervalMinutes)) { dialog = "interval" }
                if (s.source == SourceId.GOOGLE) {
                    val nf = NumberFormat.getIntegerInstance()
                    Text(
                        stringResource(
                            if (state.overFreeTier) R.string.google_estimate_over else R.string.google_estimate,
                            nf.format(state.googleCallsPerMonth), nf.format(RefreshPolicy.GOOGLE_FREE_CALLS_PER_MONTH), state.activePlaces,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.overFreeTier) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                BackgroundLocationRow()
            }

            SettingsGroup(R.string.section_units) {
                SegmentedRow(
                    title = R.string.unit_temperature,
                    options = TemperatureUnit.entries,
                    selected = s.units.temperature,
                    label = { it.symbol },
                    onPick = { viewModel.setUnits(s.units.copy(temperature = it)) },
                )
                GroupDivider()
                Row(R.string.unit_wind, s.units.windSpeed.symbol) { dialog = "wind" }
                GroupDivider()
                Row(R.string.unit_pressure, s.units.pressure.symbol) { dialog = "pressure" }
                GroupDivider()
                Row(R.string.unit_precipitation, s.units.precipitation.symbol) { dialog = "precip" }
                GroupDivider()
                Row(R.string.unit_distance, s.units.distance.symbol) { dialog = "distance" }
            }

            SettingsGroup(R.string.section_look) {
                SegmentedRow(
                    title = R.string.theme,
                    options = ThemeMode.entries,
                    selected = s.theme,
                    label = { stringResource(themeShortName(it)) },
                    onPick = viewModel::setTheme,
                )
                GroupDivider()
                SegmentedRow(
                    title = R.string.time_format,
                    options = TimeFormat.entries,
                    selected = s.timeFormat,
                    label = { stringResource(timeFormatShortName(it)) },
                    onPick = viewModel::setTimeFormat,
                )
                GroupDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.dynamic_color)) },
                    supportingContent = { Text(stringResource(R.string.dynamic_color_hint)) },
                    trailingContent = { Switch(s.dynamicColor, onCheckedChange = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.toggleable(s.dynamicColor, role = Role.Switch, onValueChange = viewModel::setDynamicColor),
                )
            }

            SettingsGroup(R.string.section_about) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.app_information)) },
                    supportingContent = { Text(stringResource(R.string.version_licenses)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { dialog = "about" },
                )
                GroupDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.update_check)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                when (update) {
                                    UpdateUi.Checking -> R.string.update_checking
                                    else -> R.string.update_check_hint
                                },
                            ),
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(enabled = update !is UpdateUi.Checking) { viewModel.checkForUpdate() },
                )
                GroupDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.data_sources)) },
                    supportingContent = { Text(stringResource(R.string.data_sources_body)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                GroupDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.privacy)) },
                    supportingContent = { Text(stringResource(R.string.privacy_body)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                GroupDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.source_code)) },
                    supportingContent = { Text("github.com/antomanc/ameki · GPL-3.0") },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/antomanc/ameki")))
                    },
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    when (dialog) {
        "notices" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.third_party_notices)) },
            text = {
                val notices = remember { context.resources.openRawResource(R.raw.third_party_notices).bufferedReader().use { it.readText() } }
                Text(notices, Modifier.verticalScroll(rememberScrollState()))
            },
            confirmButton = { TextButton(onClick = { dialog = "about" }) { Text(stringResource(R.string.close)) } },
        )
        "about" -> {
            val info = remember { context.packageManager.getPackageInfo(context.packageName, 0) }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Ameki ${info.versionName} (${info.longVersionCode})") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        TextButton(onClick = { dialog = "notices" }) { Text(stringResource(R.string.third_party_notices)) }
                        listOf(
                            "Ameki · GPL-3.0" to "https://github.com/antomanc/ameki/blob/main/LICENSE",
                            "Meteocons · Bas Milius · MIT" to "https://github.com/basmilius/meteocons",
                            "Hanken Grotesk · OFL" to "https://github.com/marcologous/hanken-grotesk",
                        ).forEach { (label, url) ->
                            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) { Text(label) }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.close)) } },
            )
        }
        "source" -> Choice(R.string.source, state.sources, s.source, { stringResource(sourceName(it)) }, { viewModel.setSource(it) }) { dialog = null }
        "interval" -> Choice(
            R.string.refresh_interval, RefreshPolicy.intervalOptions(s.source), s.refreshIntervalMinutes,
            { stringResource(R.string.every_n_minutes, it) }, { viewModel.setInterval(it) },
        ) { dialog = null }
        "wind" -> Choice(R.string.unit_wind, WindSpeedUnit.entries, s.units.windSpeed, { it.symbol }, { viewModel.setUnits(s.units.copy(windSpeed = it)) }) { dialog = null }
        "pressure" -> Choice(R.string.unit_pressure, PressureUnit.entries, s.units.pressure, { it.symbol }, { viewModel.setUnits(s.units.copy(pressure = it)) }) { dialog = null }
        "precip" -> Choice(R.string.unit_precipitation, PrecipitationUnit.entries, s.units.precipitation, { it.symbol }, { viewModel.setUnits(s.units.copy(precipitation = it)) }) { dialog = null }
        "distance" -> Choice(R.string.unit_distance, DistanceUnit.entries, s.units.distance, { it.symbol }, { viewModel.setUnits(s.units.copy(distance = it)) }) { dialog = null }
    }

    when (val u = update) {
        is UpdateUi.Available -> UpdateAvailableDialog(
            versionName = u.versionName,
            onView = {
                context.openAmekiReleases()
                viewModel.acknowledgeUpdate(u.versionName)
            },
            onLater = { viewModel.acknowledgeUpdate(u.versionName) },
            onDismiss = { viewModel.acknowledgeUpdate(u.versionName) },
        )
        UpdateUi.Failed -> AlertDialog(
            onDismissRequest = viewModel::clearUpdateUi,
            title = { Text(stringResource(R.string.update_check)) },
            text = { Text(stringResource(R.string.update_check_failed)) },
            confirmButton = { TextButton(onClick = viewModel::clearUpdateUi) { Text(stringResource(R.string.close)) } },
        )
        UpdateUi.Idle, UpdateUi.Checking -> Unit
    }
}

fun Context.openAmekiReleases() {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ReleaseUpdateChecker.RELEASES_PAGE_URL)))
}

@Composable
fun UpdateAvailableDialog(
    versionName: String,
    onView: () -> Unit,
    onLater: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_available_title)) },
        text = { Text(stringResource(R.string.update_available_body, versionName)) },
        confirmButton = { TextButton(onClick = onView) { Text(stringResource(R.string.update_view)) } },
        dismissButton = { TextButton(onClick = onLater) { Text(stringResource(R.string.update_later)) } },
    )
}

/** Optional "allow all the time" location, so a widget on the current location follows you (ADR 0004). */
@Composable
private fun BackgroundLocationRow() {
    val context = LocalContext.current
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    var bg by remember { mutableStateOf(granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) }
    var foreground by remember { mutableStateOf(granted(Manifest.permission.ACCESS_COARSE_LOCATION)) }
    var explain by rememberSaveable { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                bg = granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                foreground = granted(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    if (!foreground) return
    fun openSettings() = context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    GroupDivider()
    ListItem(
        headlineContent = { Text(stringResource(R.string.background_location)) },
        supportingContent = { Text(stringResource(if (bg) R.string.background_location_on else R.string.background_location_hint)) },
        trailingContent = { Switch(bg, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.toggleable(bg, role = Role.Switch, onValueChange = { if (bg) openSettings() else explain = true }),
    )
    if (explain) AlertDialog(
        onDismissRequest = { explain = false },
        title = { Text(stringResource(R.string.background_location)) },
        text = { Text(stringResource(R.string.background_location_settings)) },
        confirmButton = { TextButton(onClick = { explain = false; openSettings() }) { Text(stringResource(R.string.open_settings)) } },
        dismissButton = { TextButton(onClick = { explain = false }) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun SettingsGroup(@StringRes title: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(title).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, bottom = 6.dp).semantics { heading() },
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(
        Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@Composable
private fun <T> SegmentedRow(
    @StringRes title: Int,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onPick: (T) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onPick(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    label = { Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }
}

@Composable
private fun Row(@StringRes title: Int, value: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(value) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun Row(@StringRes title: Int, @StringRes value: Int, onClick: () -> Unit) = Row(title, stringResource(value), onClick)

@Composable
private fun <T> Choice(
    @StringRes title: Int,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { o ->
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth().selectable(o == selected, role = Role.RadioButton) { onPick(o); onDismiss() }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(o == selected, onClick = null)
                        Text(label(o), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@StringRes fun sourceName(s: SourceId) = when (s) { SourceId.GOOGLE -> R.string.source_google; SourceId.OPEN_METEO -> R.string.source_open_meteo }
@StringRes private fun themeShortName(t: ThemeMode) = when (t) { ThemeMode.SYSTEM -> R.string.theme_system_short; ThemeMode.LIGHT -> R.string.theme_light; ThemeMode.DARK -> R.string.theme_dark }
@StringRes private fun timeFormatShortName(t: TimeFormat) = when (t) { TimeFormat.SYSTEM -> R.string.time_system_short; TimeFormat.H24 -> R.string.time_24; TimeFormat.H12 -> R.string.time_12 }

