package dev.antomanc.ameki.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out leaves the widget unplaced, as the platform expects.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        lifecycleScope.launch {
            val existing = WidgetConfig.from(widgets.get(appWidgetId))
            setContent {
                val s by settings.settings.collectAsStateWithLifecycle(AppSettings())
                val list by places.places.collectAsStateWithLifecycle(emptyList())
                var placeId by remember { mutableStateOf(existing.placeId) }
                var style by remember { mutableStateOf(existing.style) }
                var opacity by remember { mutableFloatStateOf(existing.opacity) }
                AmekiTheme(s.theme, s.dynamicColor) {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState())) {
                            Text(stringResource(R.string.config_title), style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(20.dp))
                            Text(stringResource(R.string.config_place).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Option(stringResource(R.string.config_place_first), placeId == null) { placeId = null }
                            list.forEach { p ->
                                Option(p.name.ifBlank { stringResource(dev.antomanc.ameki.core.ui.R.string.current_location) }, placeId == p.id) { placeId = p.id }
                            }
                            Spacer(Modifier.height(20.dp))
                            Text(stringResource(R.string.config_style).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                WidgetStyle.entries.forEach { st ->
                                    FilterChip(selected = style == st, onClick = { style = st }, label = { Text(stringResource(styleName(st))) })
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                            Text(stringResource(R.string.config_opacity, (opacity * 100).roundToInt()).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Slider(opacity, { opacity = it }, valueRange = 0f..1f, steps = 9)
                            Spacer(Modifier.height(28.dp))
                            Button(
                                onClick = {
                                    lifecycleScope.launch {
                                        widgets.upsert(WidgetConfigEntity(appWidgetId, placeId, style.name, opacity))
                                        updater.update(appWidgetId)
                                        scheduler.refreshNow()
                                        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                                        finish()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                            ) { Text(stringResource(R.string.config_save)) }
                        }
                    }
                }
            }
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
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick = null)
        Text(label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
    }
}
