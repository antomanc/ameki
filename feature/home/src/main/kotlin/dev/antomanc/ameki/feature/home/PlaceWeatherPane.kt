package dev.antomanc.ameki.feature.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antomanc.ameki.core.data.sync.PlaceWeather
import dev.antomanc.ameki.core.data.sync.SyncError
import dev.antomanc.ameki.core.model.CurrentWeather
import dev.antomanc.ameki.core.model.DailyForecast
import dev.antomanc.ameki.core.model.SourceId
import dev.antomanc.ameki.core.model.Units
import dev.antomanc.ameki.core.model.WeatherCondition
import dev.antomanc.ameki.core.model.WeatherSnapshot
import dev.antomanc.ameki.core.ui.ForecastView
import dev.antomanc.ameki.core.ui.HourView
import dev.antomanc.ameki.core.ui.TimeFormatter
import dev.antomanc.ameki.core.ui.components.AnimatedWeatherIcon
import dev.antomanc.ameki.core.ui.components.MuteTemperatureDial
import dev.antomanc.ameki.core.ui.components.RainDot
import dev.antomanc.ameki.core.ui.components.RangeBar
import dev.antomanc.ameki.core.ui.components.TemperatureDial
import dev.antomanc.ameki.core.ui.components.WeatherIcon
import dev.antomanc.ameki.core.ui.components.rememberAnimationsEnabled
import dev.antomanc.ameki.core.ui.label
import dev.antomanc.ameki.core.ui.theme.LocalInstrumentColors
import java.time.Duration
import java.time.Instant
import dev.antomanc.ameki.core.ui.R as UiR
import dev.antomanc.ameki.core.ui.effects.rememberBriefWait
import dev.antomanc.ameki.core.ui.effects.rememberWeatherTime

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
    val now = rememberWeatherTime()

    val animatePane = rememberAnimationsEnabled()
    val hasContent = snapshot != null && pw.hasData
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        // Crossfade NoData ↔ content so a dial that appears after first fetch does not hard-cut in.
        Crossfade(
            targetState = hasContent,
            animationSpec = if (animatePane) tween(220) else snap(),
            label = "place-pane-content",
            modifier = Modifier.fillMaxSize(),
        ) { showContent ->
            if (!showContent) {
                NoData(pw, refreshing, onFixProblem, onRefresh)
                return@Crossfade
            }
            val data = snapshot ?: return@Crossfade
            val view = ForecastView(data, now, state.settings.refreshIntervalMinutes)
            val primary: List<@Composable () -> Unit> = buildList {
                add { Hero(pw, view, units, fmt, refreshing) {
                    Freshness(pw, data, fmt, now, refreshing, onFixProblem, onRefresh)
                    if (data.nowcast.isNotEmpty() || data.hourly.isNotEmpty()) RainOutlookSummary(view, fmt)
                } }
                if (data.alerts.isNotEmpty()) add { AlertsCard(data, fmt) }
                // Up to 48 hours: as many as the snapshot holds.
                if (view.hours.isNotEmpty()) add { HourlyCard(view.hours.take(48), view, units, fmt, onOpenDay) }
                if (view.hours.isNotEmpty()) add { HourlyCharts(view.hours.take(24).map { it.forecast }, data, units, fmt) }
            }
            val secondary: List<@Composable () -> Unit> = buildList {
                if (view.days.isNotEmpty()) add { DailyCard(view, units, fmt) { onOpenDay(DayTarget(it)) } }
                add { DetailGrid(view, units, fmt, state.capabilities) }
                add { Attribution(data, fmt) }
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
}

/** A faint wash in the colour of the current temperature, darker at night: atmosphere, never a sky picture. */
@Composable
internal fun Atmosphere(snapshot: WeatherSnapshot?) {
    val colors = LocalInstrumentColors.current
    val c = snapshot?.current ?: return
    val tint = colors.temperature(c.temperature).copy(alpha = if (c.isDaytime) 0.16f else 0.10f)
    Box(Modifier.fillMaxWidth().height(520.dp).background(Brush.verticalGradient(listOf(tint, Color.Transparent))))
}

/** Crossfade discriminant for the Hero dial — stable so temperature ticks do not re-animate. */
internal enum class HeroDialKind { Mute, Ready, Unavailable }

/** What the Hero temperature instrument shows while current conditions catch up. */
internal sealed class HeroDialPhase {
    data object Mute : HeroDialPhase()
    data class Ready(
        val temperature: Double,
        val low: Double,
        val high: Double,
        val condition: WeatherCondition,
        val isDaytime: Boolean,
        val feelsLike: Double?,
    ) : HeroDialPhase()
    data object Unavailable : HeroDialPhase()

    val kind: HeroDialKind
        get() = when (this) {
            Mute -> HeroDialKind.Mute
            is Ready -> HeroDialKind.Ready
            Unavailable -> HeroDialKind.Unavailable
        }
}

internal fun heroDialPhase(
    current: CurrentWeather?,
    today: DailyForecast?,
    briefWait: Boolean,
): HeroDialPhase {
    if (current != null) {
        val temperature = current.temperature
        return HeroDialPhase.Ready(
            temperature = temperature,
            low = minOf(today?.temperatureMin ?: temperature, temperature),
            high = maxOf(today?.temperatureMax ?: temperature, temperature),
            condition = current.condition,
            isDaytime = current.isDaytime,
            feelsLike = current.feelsLike,
        )
    }
    return if (briefWait) HeroDialPhase.Mute else HeroDialPhase.Unavailable
}

@Composable
private fun Hero(
    pw: PlaceWeather,
    view: ForecastView,
    units: Units,
    fmt: TimeFormatter,
    refreshing: Boolean,
    stamp: @Composable () -> Unit,
) {
    val s = view.snapshot
    val c = s.current
    val today = view.today
    val largeText = LocalDensity.current.fontScale > 1.3f
    // A prior fetch/error means the wait already settled — do not mute until the 2s timeout.
    val settled = view.updatedAt != null || pw.error != null
    val briefWait = rememberBriefWait(ready = c != null, loading = refreshing, settled = settled)
    val phase = heroDialPhase(current = c, today = today, briefWait = briefWait)
    val readyReading = phase as? HeroDialPhase.Ready
    // Crossfade leave frames still need the last Ready payload after current clears.
    val retainedReady = remember { androidx.compose.runtime.mutableStateOf<HeroDialPhase.Ready?>(null) }
    readyReading?.let { retainedReady.value = it }
    val dialReady = readyReading ?: retainedReady.value
    val animate = rememberAnimationsEnabled()
    val dialFootprint = Modifier.fillMaxWidth(0.9f).widthIn(max = 360.dp).aspectRatio(1f).testTag("hero-temperature-dial")
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (pw.place.isCurrentLocation && pw.place.name.isBlank()) stringResource(UiR.string.current_location) else pw.place.name,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            view.updatedAt?.let { updated ->
                Text(stringResource(if (view.stale) UiR.string.updated_stale else UiR.string.updated_at, fmt.timestamp(updated, s.timeZone, view.now)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 140.dp).padding(top = 4.dp))
            }
        }
        c?.let {
            Text(c.conditionText ?: stringResource(c.condition.label(c.isDaytime)), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        today?.let { Text(fmt.dateMedium(it.date), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Crossfade(
            targetState = phase.kind,
            animationSpec = if (animate) tween(220) else snap(),
            label = "hero-dial",
            modifier = Modifier.fillMaxWidth(),
        ) { shown ->
            when (shown) {
                HeroDialKind.Mute -> Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    if (largeText) Spacer(Modifier.height(120.dp).testTag("hero-temperature-dial"))
                    else MuteTemperatureDial(modifier = dialFootprint)
                }
                HeroDialKind.Ready -> {
                    val readingData = dialReady ?: return@Crossfade
                    val reading: @Composable () -> Unit = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AnimatedWeatherIcon(readingData.condition, readingData.isDaytime, size = if (largeText) 48.dp else 64.dp)
                            Text(units.temp(readingData.temperature), style = if (largeText) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displayLarge)
                            readingData.feelsLike?.let { Text(stringResource(UiR.string.feels_like, units.temp(it)), style = MaterialTheme.typography.bodyMedium) }
                            Text(stringResource(R.string.temperature_range, units.temp(readingData.low), units.temp(readingData.high)),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                    }
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        if (largeText) reading()
                        else TemperatureDial(
                            current = readingData.temperature, low = readingData.low, high = readingData.high,
                            description = stringResource(UiR.string.cd_temperature_dial, units.temp(readingData.temperature), units.temp(readingData.low), units.temp(readingData.high), readingData.feelsLike?.let { stringResource(UiR.string.feels_like, units.temp(it)) } ?: ""),
                            modifier = dialFootprint,
                            content = reading,
                        )
                    }
                }
                HeroDialKind.Unavailable -> Text(
                    stringResource(R.string.current_unavailable),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 16.dp).testTag("hero-temperature-unavailable"),
                )
            }
        }
        stamp()
    }
}

/** Update status is always visible, including retained data and the last failed attempt. */
@Composable
private fun Freshness(pw: PlaceWeather, s: WeatherSnapshot, fmt: TimeFormatter, now: Instant,
    refreshing: Boolean, onFixProblem: (SyncError) -> Unit, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        pw.lastAttemptAt?.takeIf { pw.error != null }?.let { Text(stringResource(R.string.last_attempt, fmt.timestamp(it, s.timeZone, now)), style = MaterialTheme.typography.bodySmall) }
        pw.error?.let { Text(stringResource(errorText(it)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        if (refreshing) Text(stringResource(R.string.loading_weather), style = MaterialTheme.typography.bodySmall)
        pw.error?.let { error ->
            androidx.compose.material3.TextButton(onClick = { error.takeIf { it.needsUserAction }?.let(onFixProblem) ?: onRefresh() }, enabled = !refreshing) {
                Text(stringResource(if (error.needsUserAction) R.string.fix else R.string.retry))
            }
        }
    }
}

@Composable
internal fun Card(title: String?, modifier: Modifier = Modifier, contentPadding: Dp = 16.dp, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer).padding(contentPadding),
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

/** Fixed height for every item in the hourly row (hours and day separators alike). */
private val HourItemHeight = 140.dp

@Composable
private fun HourlyCard(hours: List<HourView>, view: ForecastView, units: Units, fmt: TimeFormatter, onOpenDay: (DayTarget) -> Unit) {
    val s = view.snapshot
    val colors = LocalInstrumentColors.current
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
    val today = view.todayDate
    // The title names the day under the first visible hour, so you always know where you are.
    val firstVisible by androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf { rowState.firstVisibleItemIndex }
    }
    val visibleDate = hours.getOrNull(firstVisible)?.forecast?.time?.atZone(s.timeZone)?.toLocalDate() ?: today
    val dayName = when (visibleDate) {
        today -> stringResource(UiR.string.today)
        today.plusDays(1) -> stringResource(R.string.tomorrow)
        else -> fmt.weekdayShort(visibleDate)
    }
    Card("${stringResource(R.string.next_hours)} · $dayName") {
        LazyRow(state = rowState, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(hours.size, key = { hours[it].forecast.time.epochSecond }) { i ->
                val hv = hours[i]
                val h = hv.forecast
                val local = h.time.atZone(s.timeZone)
                val date = local.toLocalDate()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Midnight: a thin rule with the new day's name.
                    if (i > 0 && local.hour == 0) {
                        // Same fixed height as an hour: the card never grows when this scrolls in.
                        Column(Modifier.heightIn(min = HourItemHeight * fontScale).padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (date == today.plusDays(1)) stringResource(R.string.tomorrow) else fmt.weekdayShort(date),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                            )
                            Box(Modifier.padding(top = 4.dp).width(1.dp).weight(1f).background(MaterialTheme.colorScheme.outlineVariant))
                        }
                    }
                    Column(
                        Modifier.width(60.dp * fontScale).heightIn(min = HourItemHeight * fontScale).clip(MaterialTheme.shapes.small).clickable {
                            if (s.daily.any { it.date == date }) onOpenDay(DayTarget(date, h.time))
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val isNow = hv.isNow
                        Text(
                            if (isNow) stringResource(UiR.string.now) else fmt.hour(h.time, s.timeZone),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isNow) colors.now else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        WeatherIcon(h.condition, h.isDaytime, Modifier.padding(vertical = 4.dp), size = 34.dp)
                        Text(units.temp(hv.temperature), style = MaterialTheme.typography.titleMedium)
                        Box(Modifier.height(30.dp), contentAlignment = Alignment.Center) { RainDot(h.precipitationProbability, max = 22.dp) }
                        Text(h.precipitationProbability?.let { "$it%" } ?: "", style = MaterialTheme.typography.labelMedium, color = colors.rain)
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyCard(view: ForecastView, units: Units, fmt: TimeFormatter, onDay: (java.time.LocalDate) -> Unit) {
    val days = view.days
    val s = view.snapshot
    val min = days.minOf { it.temperatureMin }
    val max = days.maxOf { it.temperatureMax }
    val current = s.current?.temperature
    Card(stringResource(R.string.ten_days)) {
        days.forEachIndexed { i, d ->
            if (LocalDensity.current.fontScale > 1.3f) {
                Column(Modifier.fillMaxWidth().clickable { onDay(d.date) }.padding(vertical = 12.dp)) {
                    Text(if (view.isToday(d.date)) stringResource(UiR.string.today) else fmt.dateMedium(d.date), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(d.condition.label()), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.temperature_range, units.temp(d.temperatureMin), units.temp(d.temperatureMax)))
                    d.precipitationProbability?.let { Text(stringResource(R.string.day_rain, it, d.precipitation?.let(units::precipitation) ?: "")) }
                }
            } else Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(MaterialTheme.shapes.small).clickable { onDay(d.date) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (view.isToday(d.date)) stringResource(UiR.string.today) else fmt.weekdayShort(d.date),
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
                    current = if (view.isToday(d.date)) current else null,
                )
                Text(units.temp(d.temperatureMax), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(36.dp))
            }
        }
    }
}

@Composable
private fun Attribution(s: WeatherSnapshot, fmt: TimeFormatter) {
    val source = stringResource(if (s.source == SourceId.GOOGLE) UiR.string.attribution_google else UiR.string.attribution_open_meteo)
    Text(
        source,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun NoData(pw: PlaceWeather, refreshing: Boolean, onFixProblem: (SyncError) -> Unit, onRefresh: () -> Unit) {
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
            if (!refreshing) {
                androidx.compose.material3.Button(onClick = onRefresh) { Text(stringResource(R.string.retry)) }
            }
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

