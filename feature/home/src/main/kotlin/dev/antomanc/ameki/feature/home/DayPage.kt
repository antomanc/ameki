package dev.antomanc.ameki.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.antomanc.ameki.core.data.sync.PlaceWeather
import dev.antomanc.ameki.core.model.DailyForecast
import dev.antomanc.ameki.core.model.HourlyForecast
import dev.antomanc.ameki.core.model.Units
import dev.antomanc.ameki.core.model.WeatherSnapshot
import dev.antomanc.ameki.core.ui.TimeFormatter
import dev.antomanc.ameki.core.ui.components.RainDot
import dev.antomanc.ameki.core.ui.components.WeatherIcon
import dev.antomanc.ameki.core.ui.label
import dev.antomanc.ameki.core.ui.theme.LocalInstrumentColors
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.roundToInt
import dev.antomanc.ameki.core.ui.R as UiR

/** Which day page is open, and optionally the hour to scroll to. */
data class DayTarget(val day: Int, val hour: Long? = null)

/**
 * One full page per day, swiped sideways: no sheet to fight with while scrolling the hours.
 * Hours missing from the cache (Google, beyond 24 h) are fetched on arrival.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DayPage(
    pw: PlaceWeather,
    state: HomeUiState,
    target: DayTarget,
    onDayChange: (Int) -> Unit,
    onNeedHours: (Instant) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = pw.snapshot ?: return
    if (s.daily.isEmpty()) return
    val context = LocalContext.current
    val fmt = remember(state.settings.timeFormat) { TimeFormatter(context, state.settings.timeFormat) }
    val units = state.settings.units
    val pager = rememberPagerState(initialPage = target.day.coerceIn(0, s.daily.lastIndex)) { s.daily.size }
    val chips = rememberLazyListState()
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)
    LaunchedEffect(target.day) { pager.scrollToPage(target.day.coerceIn(0, s.daily.lastIndex)) }
    LaunchedEffect(pager.currentPage) {
        onDayChange(pager.currentPage)
        chips.animateScrollToItem((pager.currentPage - 1).coerceAtLeast(0))
        val day = s.daily[pager.currentPage]
        onNeedHours(day.date.plusDays(1).atStartOfDay(s.timeZone).toInstant())
    }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            TopAppBar(
                title = { Text(pw.place.name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyRow(state = chips, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(s.daily) { i, d ->
                    FilterChip(
                        selected = i == pager.currentPage,
                        onClick = { scope.launch { pager.animateScrollToPage(i) } },
                        label = { Text(if (d.date == java.time.LocalDate.now(s.timeZone)) stringResource(UiR.string.today) else "${fmt.weekdayShort(d.date)} ${d.date.dayOfMonth}") },
                    )
                }
            }
            HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                DayContent(
                    s, page, units, fmt,
                    focusHour = target.hour.takeIf { page == target.day },
                    loading = pw.place.id in state.loadingHours,
                    failed = state.hoursError != null,
                    onRetry = { onNeedHours(s.daily[page].date.plusDays(1).atStartOfDay(s.timeZone).toInstant()) },
                )
            }
        }
    }
}

@Composable
private fun DayContent(s: WeatherSnapshot, index: Int, units: Units, fmt: TimeFormatter, focusHour: Long?, loading: Boolean, failed: Boolean, onRetry: () -> Unit) {
    val day = s.daily[index]
    val now = Instant.now()
    val dayHours = s.hourly.filter { it.time.atZone(s.timeZone).toLocalDate() == day.date }
    val hasChart = dayHours.size >= MIN_DAY_CHART_HOURS
    val hours = dayHours
        .let { list -> if (day.date == java.time.LocalDate.now(s.timeZone)) list.filter { it.time.plusSeconds(3600) > now } else list }
    var waitingForRequest by androidx.compose.runtime.remember(day.date) { androidx.compose.runtime.mutableStateOf(true) }
    LaunchedEffect(day.date) { androidx.compose.runtime.withFrameNanos { }; waitingForRequest = false }
    val list = rememberLazyListState()
    LaunchedEffect(focusHour, hours.size) {
        val i = hours.indexOfFirst { it.time.epochSecond == focusHour }
        if (i >= 0) list.scrollToItem(i + 4)
    }
    LazyColumn(state = list, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp)) {
        item { DayHeader(day, s, units, fmt, showSun = !hasChart) }
        item { DayTemperatureChart(day, dayHours, s, units, fmt) }
        item { HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant) }
        when {
            hours.isNotEmpty() -> {
                item { HourHeaderRow() }
                items(hours, key = { it.time.epochSecond }) {
                    // Rows fade in when they replace the skeleton, instead of popping in.
                    Box(Modifier.animateItem(fadeInSpec = androidx.compose.animation.core.tween(300))) {
                        HourRow(it, s, units, fmt, highlighted = it.time.epochSecond == focusHour)
                    }
                }
            }
            // No hours yet and no error: they are on their way (the fetch starts a frame later).
            loading || waitingForRequest -> {
                item { HourHeaderRow() }
                items(8) { SkeletonRow() }
            }
            else -> item {
                Text(
                    stringResource(if (failed) R.string.day_hours_failed else R.string.day_no_hourly),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.material3.Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
        }
    }
}

/** Placeholder shaped like an hour row, gently pulsing while the hours load. */
@Composable
private fun SkeletonRow() {
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "skeleton")
    val alpha by pulse.animateFloat(
        initialValue = 0.35f, targetValue = 0.7f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(800), androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "alpha",
    )
    val c = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics { contentDescription = "" }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(48.dp).height(16.dp).background(c, shape))
        Spacer(Modifier.width(28.dp))
        Box(Modifier.size(32.dp).background(c, androidx.compose.foundation.shape.CircleShape))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.width(40.dp).height(20.dp).background(c, shape))
        Spacer(Modifier.weight(1f))
        Box(Modifier.width(48.dp).height(16.dp).background(c, shape))
        Spacer(Modifier.width(28.dp))
        Box(Modifier.width(64.dp).height(16.dp).background(c, shape))
    }
}

@Composable
private fun DayHeader(d: DailyForecast, s: WeatherSnapshot, units: Units, fmt: TimeFormatter, showSun: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        WeatherIcon(d.condition, true, size = 64.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(fmt.dateMedium(d.date), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(d.conditionText ?: stringResource(d.condition.label()), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            d.nightCondition?.let {
                Text(stringResource(R.string.day_night, stringResource(it.label(false))), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
            Text(stringResource(R.string.temperature_range, units.temp(d.temperatureMin), units.temp(d.temperatureMax)),
                style = MaterialTheme.typography.titleMedium)
        }
    }
    Spacer(Modifier.height(12.dp))
    val facts = listOfNotNull(
        d.precipitationProbability?.let { stringResource(R.string.day_rain, it, d.precipitation?.let(units::precipitation) ?: "") },
        d.windSpeedMax?.let { stringResource(R.string.day_wind, units.wind(it)) },
        d.uvIndexMax?.let { stringResource(R.string.day_uv, it.roundToInt()) },
        if (showSun && d.sunrise != null && d.sunset != null) stringResource(R.string.day_sun, fmt.time(d.sunrise!!, s.timeZone), fmt.time(d.sunset!!, s.timeZone)) else null,
    )
    facts.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp)) }
}

@Composable
private fun HourHeaderRow() {
    if (LocalDensity.current.fontScale > 1.3f) return
    val style = MaterialTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(stringResource(R.string.col_hour).uppercase(), style = style, color = color, modifier = Modifier.width(76.dp))
        Spacer(Modifier.width(40.dp))
        Text(stringResource(R.string.col_temp).uppercase(), style = style, color = color, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.col_rain).uppercase(), style = style, color = color, modifier = Modifier.width(76.dp))
        Text(stringResource(R.string.col_wind).uppercase(), style = style, color = color, modifier = Modifier.width(96.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun HourRow(h: HourlyForecast, s: WeatherSnapshot, units: Units, fmt: TimeFormatter, highlighted: Boolean) {
    if (LocalDensity.current.fontScale > 1.3f) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Text(fmt.time(h.time, s.timeZone), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(h.condition.label(h.isDaytime)), style = MaterialTheme.typography.bodyMedium)
            Text(units.temp(h.temperature), style = MaterialTheme.typography.headlineSmall)
            h.feelsLike?.let { Text(stringResource(UiR.string.feels_like, units.temp(it))) }
            h.precipitationProbability?.let { Text(stringResource(R.string.day_rain, it, h.precipitation?.let(units::precipitation) ?: "")) }
            h.windSpeed?.let { Text(stringResource(R.string.day_wind, units.wind(it))) }
        }
        return
    }
    val colors = LocalInstrumentColors.current
    val bg = if (highlighted) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).then(Modifier.androidxBackground(bg)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(fmt.time(h.time, s.timeZone), style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.width(76.dp))
        WeatherIcon(h.condition, h.isDaytime, size = 32.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(units.temp(h.temperature), style = MaterialTheme.typography.titleLarge)
            h.feelsLike?.let { Text(stringResource(UiR.string.feels_like, units.temp(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Row(Modifier.width(76.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) { RainDot(h.precipitationProbability, max = 18.dp) }
            Text(h.precipitationProbability?.let { "$it%" } ?: "–", style = MaterialTheme.typography.bodyMedium, color = colors.rain, modifier = Modifier.padding(start = 4.dp))
        }
        Row(Modifier.width(96.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            h.windDirection?.let {
                Icon(Icons.Outlined.KeyboardArrowUp, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp).rotate(it + 180f))
                Spacer(Modifier.width(4.dp))
            }
            Text(h.windSpeed?.let(units::wind) ?: "–", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun Modifier.androidxBackground(c: Color) =
    this.background(c, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
