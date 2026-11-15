package dev.antomanc.ameki.widget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.antomanc.ameki.core.data.PlaceRepository
import dev.antomanc.ameki.core.data.db.WidgetConfigEntity
import dev.antomanc.ameki.core.data.db.WidgetDao
import dev.antomanc.ameki.core.data.settings.SettingsRepository
import dev.antomanc.ameki.core.data.sync.RefreshScheduler
import dev.antomanc.ameki.core.model.AppSettings
import dev.antomanc.ameki.core.ui.theme.AmekiTheme
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import kotlin.math.roundToInt

/** Per-instance setup: place, style, background opacity. Also reachable later via long-press "Reconfigure". */
class WidgetConfigActivity : ComponentActivity() {
    private val widgets: WidgetDao by inject()
    private val places: PlaceRepository by inject()
    private val settings: SettingsRepository by inject()
    private val updater: WidgetUpdater by inject()
    private val scheduler: RefreshScheduler by inject()

    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out leaves the widget unplaced, as the platform expects.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        lifecycleScope.launch {
            val entity = widgets.get(appWidgetId)
            val existing = WidgetConfig.from(entity)
            val previewLayout = previewLayoutFor(appWidgetId)
            setContent {
                val s by settings.settings.collectAsStateWithLifecycle(AppSettings())
                val list by places.places.collectAsStateWithLifecycle(emptyList())
                var placeId by rememberSaveable { mutableStateOf(existing.placeId) }
                var styleKey by rememberSaveable { mutableStateOf(existing.style.name) }
                var opacity by rememberSaveable { mutableStateOf(existing.opacity) }
                val style = WidgetStyle.entries.firstOrNull { it.name == styleKey } ?: WidgetStyle.DYNAMIC
                val selectedPlace = list.firstOrNull { it.id == placeId }
                    ?: if (placeId == null) list.firstOrNull() else null
                AmekiTheme(s.theme, s.dynamicColor) {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState())) {
                            Text(stringResource(R.string.config_title), style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(16.dp))
                            // The real picker preview of this instance's widget type (Android 15+ layout).
                            val previewBackground = when (style) {
                                WidgetStyle.DYNAMIC -> MaterialTheme.colorScheme.surface.copy(alpha = 0.95f + opacity * 0.05f)
                                WidgetStyle.DARK -> androidx.compose.ui.graphics.Color(0xFF171816).copy(alpha = 0.8f + opacity * 0.2f)
                                WidgetStyle.TRANSPARENT -> androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f + opacity * 0.35f)
                            }
                            val previewText = if (style == WidgetStyle.DYNAMIC) MaterialTheme.colorScheme.onSurface else androidx.compose.ui.graphics.Color.White
                            AndroidView(
                                factory = { context -> LayoutInflater.from(context).inflate(previewLayout, null) },
                                update = { view ->
                                    view.setBackgroundColor(previewBackground.toArgb())
                                    fun updateText(child: android.view.View) {
                                        if (child is android.widget.TextView) {
                                            val original = (child.tag as? String) ?: child.text.toString().also { child.tag = it }
                                            child.text = original.replace("Roma", selectedPlace?.name ?: "Roma")
                                            child.setTextColor(previewText.toArgb())
                                        }
                                        if (child is android.view.ViewGroup) for (i in 0 until child.childCount) updateText(child.getChildAt(i))
                                    }
                                    updateText(view)
                                },
                                modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp)),
                            )
                            Spacer(Modifier.height(20.dp))
                            Text(stringResource(R.string.config_place).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Option(stringResource(R.string.config_place_first), placeId == null) { placeId = null }
                            list.forEach { p ->
                                Option(p.name.ifBlank { stringResource(dev.antomanc.ameki.core.ui.R.string.current_location) }, placeId == p.id) { placeId = p.id }
                            }
                            TextButton(onClick = { startActivity(openPlacesIntent(this@WidgetConfigActivity)) }) {
                                Text(stringResource(R.string.config_add_place))
                            }
                            if (selectedPlace?.isCurrentLocation == true &&
                                checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED
                            ) {
                                Text(
                                    stringResource(R.string.config_background_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(onClick = { openAppSettings() }) {
                                    Text(stringResource(R.string.config_open_settings))
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                            Text(stringResource(R.string.config_style).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                WidgetStyle.entries.forEach { st ->
                                    FilterChip(selected = style == st, onClick = { styleKey = st.name }, label = { Text(stringResource(styleName(st))) })
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                            val transparent = style == WidgetStyle.TRANSPARENT
                            val shaded = style != WidgetStyle.DYNAMIC
                            val shown = when {
                                transparent -> (65 + opacity * 35).roundToInt()
                                style == WidgetStyle.DARK -> (80 + opacity * 20).roundToInt()
                                else -> (95 + opacity * 5).roundToInt()
                            }
                            Text(
                                stringResource(if (shaded) R.string.config_scrim else R.string.config_opacity, shown).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Slider(opacity, { opacity = it }, valueRange = 0f..1f, steps = 9)
                            if (shaded) {
                                Text(
                                    stringResource(R.string.config_scrim_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(28.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(
                                    onClick = { finish() },
                                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                                ) { Text(stringResource(R.string.config_cancel)) }
                                Button(
                                    onClick = {
                                        lifecycleScope.launch {
                                            widgets.upsert(WidgetConfigEntity(appWidgetId, placeId, style.name, opacity))
                                            updater.update(appWidgetId)
                                            // A style change is local; only a new widget or a new place needs weather.
                                            if (entity == null || placeId != existing.placeId) scheduler.refreshNow()
                                            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                                            finish()
                                        }
                                    },
                                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                                ) { Text(stringResource(R.string.config_save)) }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** The static layout the launcher itself uses before Android 15 dynamic previews. */
    private fun previewLayoutFor(appWidgetId: Int): Int {
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)
        if (info != null && info.previewLayout != 0) return info.previewLayout
        val provider = info?.provider?.className.orEmpty()
        return when {
            provider.contains("Compact") -> R.layout.widget_preview_compact
            provider.contains("Hourly") -> R.layout.widget_preview_hourly
            provider.contains("Daily") -> R.layout.widget_preview_daily
            provider.contains("Rain") -> R.layout.widget_preview_rain
            else -> R.layout.widget_preview_complete
        }
    }

    private fun styleName(s: WidgetStyle) = when (s) {
        WidgetStyle.DYNAMIC -> R.string.style_dynamic
        WidgetStyle.DARK -> R.string.style_dark
        WidgetStyle.TRANSPARENT -> R.string.style_transparent
    }
}

@androidx.compose.runtime.Composable
private fun Option(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.medium)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick = null)
        Text(label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
    }
}
