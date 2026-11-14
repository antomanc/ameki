package dev.antomanc.ameki.feature.home

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.antomanc.ameki.core.data.sync.PlaceWeather
import dev.antomanc.ameki.core.data.sync.SyncError
import dev.antomanc.ameki.core.model.DailyForecast
import dev.antomanc.ameki.core.model.HourlyForecast
import dev.antomanc.ameki.core.model.SourceId
import dev.antomanc.ameki.core.model.Units
import dev.antomanc.ameki.core.model.WeatherSnapshot
import dev.antomanc.ameki.core.ui.TimeFormatter
import dev.antomanc.ameki.core.ui.components.AnimatedWeatherIcon
import dev.antomanc.ameki.core.ui.components.RainDot
import dev.antomanc.ameki.core.ui.components.RangeBar
import dev.antomanc.ameki.core.ui.components.TemperatureDial
import dev.antomanc.ameki.core.ui.components.WeatherIcon
import dev.antomanc.ameki.core.ui.label
import dev.antomanc.ameki.core.ui.theme.LocalInstrumentColors
import java.time.Duration
import java.time.Instant
import dev.antomanc.ameki.core.ui.R as UiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaceWeatherPane(
    pw: PlaceWeather,
    state: HomeUiState,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onFixProblem: (SyncError) -> Unit,
    twoColumns: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val fmt = remember(state.settings.timeFormat) { TimeFormatter(context, state.settings.timeFormat) }
    val snapshot = pw.snapshot
    val units = state.settings.units
    val now = Instant.now()

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        Atmosphere(snapshot)
        if (snapshot == null || !pw.hasData) {
            NoData(pw, refreshing, onFixProblem)
            return@PullToRefreshBox
        }
        val primary: List<@Composable () -> Unit> = buildList {
            add { Hero(pw, snapshot, units, fmt) }
            add { Freshness(pw, snapshot, fmt, state.settings.refreshIntervalMinutes, onFixProblem) }
            if (snapshot.hourly.isNotEmpty()) add { HourlyCard(snapshot.hoursFrom(now).take(24), snapshot, units, fmt) }
            if (snapshot.hourly.isNotEmpty()) add { HourlyCharts(snapshot.hoursFrom(now).take(24), snapshot, units, fmt) }
        }
        val secondary: List<@Composable () -> Unit> = buildList {
            if (snapshot.daily.isNotEmpty()) add { DailyCard(snapshot.daily, snapshot, units, fmt) }
            add { DetailGrid(snapshot, units, fmt, state.capabilities) }
            add { Attribution(snapshot.source) }
        }
        if (twoColumns) {
            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(primary.size) { primary[it]() }
                    item { Spacer(Modifier.height(24.dp)) }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Spacer(Modifier.height(8.dp)) }
                    items(secondary.size) { secondary[it]() }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(primary.size) { primary[it]() }
                items(secondary.size) { secondary[it]() }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

/** A faint wash in the colour of the current temperature, darker at night: atmosphere, never a sky picture. */
@Composable
private fun Atmosphere(snapshot: WeatherSnapshot?) {
    val colors = LocalInstrumentColors.current
    val c = snapshot?.current ?: return
    val tint = colors.temperature(c.temperature).copy(alpha = if (c.isDaytime) 0.16f else 0.10f)
    Box(Modifier.fillMaxWidth().height(420.dp).background(Brush.verticalGradient(listOf(tint, Color.Transparent))))
}

@Composable
private fun Hero(pw: PlaceWeather, s: WeatherSnapshot, units: Units, fmt: TimeFormatter) {
    val c = s.current
    val today = s.today
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (pw.place.isCurrentLocation && pw.place.name.isBlank()) stringResource(UiR.string.current_location) else pw.place.name,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                if (c != null) {
                    Text(
                        c.conditionText ?: stringResource(c.condition.label(c.isDaytime)),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            today?.let { Text(fmt.dateMedium(it.date), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (c != null) {
            val low = minOf(today?.temperatureMin ?: c.temperature, c.temperature)
            val high = maxOf(today?.temperatureMax ?: c.temperature, c.temperature)
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                TemperatureDial(
                    current = c.temperature, low = low, high = high,
                    description = stringResource(UiR.string.cd_temperature_dial, units.temp(c.temperature), units.temp(low), units.temp(high)),
                    modifier = Modifier.fillMaxWidth(0.82f).aspectRatio(1f),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedWeatherIcon(c.condition, c.isDaytime, size = 72.dp)
                        Text(units.temp(c.temperature), style = MaterialTheme.typography.displayLarge)
                        val feels = c.feelsLike?.let { stringResource(UiR.string.feels_like, units.temp(it)) }
                        val range = stringResource(UiR.string.high_low, units.temp(low), units.temp(high))
                        Text(
                            listOfNotNull(feels, range).joinToString("  ·  "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** "Updated at" stamp. Old data stays visible, stamped with its age, never hidden (ticket-wallet raise). */
@Composable
private fun Freshness(pw: PlaceWeather, s: WeatherSnapshot, fmt: TimeFormatter, intervalMin: Int, onFixProblem: (SyncError) -> Unit) {
    val updated = s.updatedAt ?: return
    val stale = Duration.between(updated, Instant.now()) > Duration.ofMinutes(intervalMin * 2L + 10)
    val colors = LocalInstrumentColors.current
    Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(if (stale) colors.now else MaterialTheme.colorScheme.outline))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(if (stale) UiR.string.updated_stale else UiR.string.updated_at, fmt.time(updated, s.timeZone)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        pw.error?.takeIf { it.needsUserAction }?.let { err ->
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(errorText(err)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onFixProblem(err) }.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
internal fun Card(title: String?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
    ) {
        if (title != null) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp).semantics { heading() },
            )
        }
        content()
    }
}

@Composable
private fun HourlyCard(hours: List<HourlyForecast>, s: WeatherSnapshot, units: Units, fmt: TimeFormatter) {
    Card(stringResource(R.string.next_hours)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(hours, key = { it.time.epochSecond }) { h ->
                Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val isNow = h === hours.first()
                    Text(
                        if (isNow) stringResource(UiR.string.now) else fmt.hour(h.time, s.timeZone),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isNow) LocalInstrumentColors.current.now else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    WeatherIcon(h.condition, h.isDaytime, Modifier.padding(vertical = 4.dp), size = 34.dp)
                    Text(units.temp(h.temperature), style = MaterialTheme.typography.titleMedium)
                    Box(Modifier.height(30.dp), contentAlignment = Alignment.Center) { RainDot(h.precipitationProbability, max = 22.dp) }
                    Text(
                        h.precipitationProbability?.let { "$it%" } ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        color = LocalInstrumentColors.current.rain,
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyCard(days: List<DailyForecast>, s: WeatherSnapshot, units: Units, fmt: TimeFormatter) {
    val min = days.minOf { it.temperatureMin }
    val max = days.maxOf { it.temperatureMax }
    val current = s.current?.temperature
    Card(stringResource(R.string.ten_days)) {
        days.forEachIndexed { i, d ->
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (i == 0) stringResource(UiR.string.today) else fmt.weekdayShort(d.date),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.width(56.dp),
                )
                WeatherIcon(d.condition, true, size = 30.dp)
                Text(
                    d.precipitationProbability?.takeIf { it >= 10 }?.let { "$it%" } ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalInstrumentColors.current.rain,
                    modifier = Modifier.width(40.dp).padding(start = 4.dp),
                )
                Text(units.temp(d.temperatureMin), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
                RangeBar(
                    d.temperatureMin, d.temperatureMax, min, max,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp).height(6.dp),
                    current = if (i == 0) current else null,
                )
                Text(units.temp(d.temperatureMax), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(36.dp))
            }
        }
    }
}

@Composable
private fun Attribution(source: SourceId) {
    Text(
        stringResource(if (source == SourceId.GOOGLE) UiR.string.attribution_google else UiR.string.attribution_open_meteo),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun NoData(pw: PlaceWeather, refreshing: Boolean, onFixProblem: (SyncError) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Text(pw.place.name, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            val err = pw.error
            Text(
                when {
                    refreshing || err == null -> stringResource(R.string.loading_weather)
                    else -> stringResource(errorText(err))
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (err != null && err.needsUserAction && !refreshing) {
                Spacer(Modifier.height(16.dp))
                androidx.compose.material3.FilledTonalButton(onClick = { onFixProblem(err) }) { Text(stringResource(R.string.fix)) }
            }
        }
    }
}

internal fun errorText(e: SyncError): Int = when (e) {
    SyncError.NETWORK -> R.string.err_network
    SyncError.MISSING_KEY -> R.string.err_missing_key
    SyncError.INVALID_KEY -> R.string.err_invalid_key
    SyncError.ACCESS_DENIED -> R.string.err_access_denied
    SyncError.QUOTA -> R.string.err_quota
    SyncError.UNSUPPORTED -> R.string.err_unsupported
    SyncError.NO_LOCATION -> R.string.err_no_location
    SyncError.UNKNOWN -> R.string.err_unknown
}

