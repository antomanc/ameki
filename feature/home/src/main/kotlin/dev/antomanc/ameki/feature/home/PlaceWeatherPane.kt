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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
    onOpenDay: (DayTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val fmt = remember(state.settings.timeFormat) { TimeFormatter(context, state.settings.timeFormat) }
    val snapshot = pw.snapshot
    val units = state.settings.units
    val now = Instant.now()

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        if (snapshot == null || !pw.hasData) {
            NoData(pw, refreshing, onFixProblem)
            return@PullToRefreshBox
        }
        val primary: List<@Composable () -> Unit> = buildList {
            add { Hero(pw, snapshot, units, fmt) { Freshness(pw, snapshot, fmt, state.settings.refreshIntervalMinutes, onFixProblem) } }
            // 48 hours: Open-Meteo has them already; Google refreshes 24 (each extra page is a call).
            if (snapshot.hourly.isNotEmpty()) add { HourlyCard(snapshot.hoursFrom(now).take(48), snapshot, units, fmt, onOpenDay) }
            if (snapshot.hourly.isNotEmpty()) add { HourlyCharts(snapshot.hoursFrom(now).take(24), snapshot, units, fmt) }
        }
        val secondary: List<@Composable () -> Unit> = buildList {
            if (snapshot.daily.isNotEmpty()) add { DailyCard(snapshot.daily, snapshot, units, fmt) { onOpenDay(DayTarget(it)) } }
            add { DetailGrid(snapshot, units, fmt, state.capabilities) }
            add { Attribution(snapshot, fmt) }
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
internal fun Atmosphere(snapshot: WeatherSnapshot?) {
    val colors = LocalInstrumentColors.current
    val c = snapshot?.current ?: return
    val tint = colors.temperature(c.temperature).copy(alpha = if (c.isDaytime) 0.16f else 0.10f)
    Box(Modifier.fillMaxWidth().height(520.dp).background(Brush.verticalGradient(listOf(tint, Color.Transparent))))
}

@Composable
private fun Hero(pw: PlaceWeather, s: WeatherSnapshot, units: Units, fmt: TimeFormatter, stamp: @Composable () -> Unit) {
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
                    modifier = Modifier.fillMaxWidth(0.78f).widthIn(max = 340.dp).aspectRatio(1f),
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
                        // The stamp sits under the reading, in the gauge's open bottom.
                        Spacer(Modifier.height(10.dp))
                        stamp()
                    }
                }
            }
        }
    }
}

internal fun isStale(updated: Instant, intervalMin: Int): Boolean =
    Duration.between(updated, Instant.now()) > Duration.ofMinutes(maxOf(120L, intervalMin * 3L))

/** Shown under the dial only for stale data or a problem to fix. Old data stays visible, stamped with its age. */
@Composable
private fun Freshness(pw: PlaceWeather, s: WeatherSnapshot, fmt: TimeFormatter, intervalMin: Int, onFixProblem: (SyncError) -> Unit) {
    val updated = s.updatedAt ?: return
    val stale = isStale(updated, intervalMin)
    val problem = pw.error?.takeIf { it.needsUserAction }
    // Fresh data needs no stamp here: the time lives in the footer. Speak up only when it matters.
    if (!stale && problem == null) return
    val colors = LocalInstrumentColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
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
private fun HourlyCard(hours: List<HourlyForecast>, s: WeatherSnapshot, units: Units, fmt: TimeFormatter, onOpenDay: (DayTarget) -> Unit) {
    val colors = LocalInstrumentColors.current
    val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
    val today = s.daily.firstOrNull()?.date ?: java.time.LocalDate.now(s.timeZone)
    // The title names the day under the first visible hour, so you always know where you are.
    val firstVisible by androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf { rowState.firstVisibleItemIndex }
    }
    val visibleDate = hours.getOrNull(firstVisible)?.time?.atZone(s.timeZone)?.toLocalDate() ?: today
    val dayName = when (visibleDate) {
        today -> stringResource(UiR.string.today)
        today.plusDays(1) -> stringResource(R.string.tomorrow)
        else -> fmt.weekdayShort(visibleDate)
    }
    Card("${stringResource(R.string.next_hours)} · $dayName") {
        LazyRow(state = rowState, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(hours.size, key = { hours[it].time.epochSecond }) { i ->
                val h = hours[i]
                val local = h.time.atZone(s.timeZone)
                val date = local.toLocalDate()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Midnight: a thin rule with the new day's name.
                    if (i > 0 && local.hour == 0) {
                        Column(Modifier.padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (date == today.plusDays(1)) stringResource(R.string.tomorrow) else fmt.weekdayShort(date),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                            )
                            Box(Modifier.padding(top = 4.dp).width(1.dp).height(112.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        }
                    }
                    Column(
                        Modifier.width(52.dp).clip(MaterialTheme.shapes.small).clickable {
                            val dayIndex = s.daily.indexOfFirst { it.date == date }
                            if (dayIndex >= 0) onOpenDay(DayTarget(dayIndex, h.time.epochSecond))
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val isNow = i == 0
                        Text(
                            if (isNow) stringResource(UiR.string.now) else fmt.hour(h.time, s.timeZone),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isNow) colors.now else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        WeatherIcon(h.condition, h.isDaytime, Modifier.padding(vertical = 4.dp), size = 34.dp)
                        Text(units.temp(h.temperature), style = MaterialTheme.typography.titleMedium)
                        Box(Modifier.height(30.dp), contentAlignment = Alignment.Center) { RainDot(h.precipitationProbability, max = 22.dp) }
                        Text(h.precipitationProbability?.let { "$it%" } ?: "", style = MaterialTheme.typography.labelMedium, color = colors.rain)
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyCard(days: List<DailyForecast>, s: WeatherSnapshot, units: Units, fmt: TimeFormatter, onDay: (Int) -> Unit) {
    val min = days.minOf { it.temperatureMin }
    val max = days.maxOf { it.temperatureMax }
    val current = s.current?.temperature
    Card(stringResource(R.string.ten_days)) {
        days.forEachIndexed { i, d ->
            Row(
                Modifier.fillMaxWidth().height(48.dp).clip(MaterialTheme.shapes.small).clickable { onDay(i) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
private fun Attribution(s: WeatherSnapshot, fmt: TimeFormatter) {
    val source = stringResource(if (s.source == SourceId.GOOGLE) UiR.string.attribution_google else UiR.string.attribution_open_meteo)
    val updated = s.updatedAt?.let { stringResource(UiR.string.updated_at, fmt.time(it, s.timeZone)) }
    Text(
        listOfNotNull(updated, source).joinToString("\n"),
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

