package dev.antomanc.ameki.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.antomanc.ameki.core.data.sync.DayHours
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
 * Each page shows its [DayHours] status; a page whose hours are still needed asks for them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DayPage(
    pw: PlaceWeather,
    state: HomeUiState,
    target: DayTarget,
    onDayChange: (Int) -> Unit,
    onNeedHours: (java.time.LocalDate) -> Unit,
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
    LaunchedEffect(target.day) {
        val page = target.day.coerceIn(0, s.daily.lastIndex)
        if (pager.currentPage != page) pager.scrollToPage(page)
    }
    val currentDay = s.daily.getOrNull(pager.currentPage)
    val currentStatus = currentDay?.let { pw.dayHours(it.date) }
    LaunchedEffect(pager.currentPage) { onDayChange(pager.currentPage) }
    LaunchedEffect(pw.place.id, currentDay?.date, currentStatus) {
        if (currentDay != null && currentStatus == DayHours.NEEDED) onNeedHours(currentDay.date)
    }
    // Scrolling the chip strip must never delay or cancel the network request.
    LaunchedEffect(pager.currentPage) { chips.animateScrollToItem((pager.currentPage - 1).coerceAtLeast(0)) }


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
                val day = s.daily.getOrNull(page) ?: return@HorizontalPager
                val status = pw.dayHours(day.date)
                DayContent(
                    s, page, units, fmt,
                    focusHour = target.hour.takeIf { page == target.day },
                    loading = status == DayHours.LOADING || status == DayHours.NEEDED,
                    failed = status == DayHours.FAILED,
                    onRetry = { onNeedHours(day.date) },
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
    val hours = dayHours
        .let { list -> if (day.date == java.time.LocalDate.now(s.timeZone)) list.filter { it.time.plusSeconds(3600) > now } else list }
    val list = rememberLazyListState()
    LaunchedEffect(focusHour, hours.size) {
        val i = hours.indexOfFirst { it.time.epochSecond == focusHour }
        if (i >= 0) list.scrollToItem(i + 4)
    }
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp)) {
        item { DayHeader(day, units, fmt) }
        item { DayTemperatureChart(day, dayHours, s, units, fmt, loading = loading, failed = failed) }
        item { HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant) }
        if (hours.isNotEmpty() || loading) {
            item { HourHeaderRow() }
            val byTime = hours.associateBy { it.time }
            val times = if (loading) {
                val start = day.date.atStartOfDay(s.timeZone).toInstant()
                val end = day.date.plusDays(1).atStartOfDay(s.timeZone).toInstant()
                generateSequence(start) { it.plusSeconds(3600) }.takeWhile { it < end }
                    .filter { day.date != java.time.LocalDate.now(s.timeZone) || it.plusSeconds(3600) > now }.toList()
            } else hours.map { it.time }
            items(times, key = { it.epochSecond }) { time ->
                Crossfade(targetState = byTime[time], modifier = Modifier.testTag("day-hour-${time.epochSecond}"), animationSpec = tween(220), label = "hour-data") { hour ->
                    HourRow(hour, time, s, units, fmt, highlighted = time.epochSecond == focusHour)
                }
            }
        }
        if (failed) item {
            Text(stringResource(R.string.day_hours_failed), style = MaterialTheme.typography.bodyMedium)
            androidx.compose.material3.Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        } else if (!loading && hours.isEmpty()) item {
            Text(stringResource(R.string.day_hours_unavailable), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun skeletonColor(): Color {
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "skeleton")
    val alpha by pulse.animateFloat(
        initialValue = 0.35f, targetValue = 0.7f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(tween(800), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "alpha",
    )
    return MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)
}

@Composable
private fun DayHeader(d: DailyForecast, units: Units, fmt: TimeFormatter) {
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

/** Real and pending hours use one layout, so every column keeps its position. */
@Composable
private fun HourRow(h: HourlyForecast?, time: Instant, s: WeatherSnapshot, units: Units, fmt: TimeFormatter, highlighted: Boolean) {
    val pulse = if (h == null) skeletonColor() else Color.Transparent
    val semantics = if (h == null) Modifier.clearAndSetSemantics { } else Modifier
    val feelsTemplate = stringResource(UiR.string.feels_like, units.temp(18.0))
    val feels = h?.let { it.feelsLike?.let { value -> stringResource(UiR.string.feels_like, units.temp(value)) } ?: "–" }
    if (LocalDensity.current.fontScale > 1.3f) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp).then(semantics)) {
            Text(fmt.time(time, s.timeZone), style = MaterialTheme.typography.titleMedium)
            HourText(h?.let { stringResource(it.condition.label(it.isDaytime)) }, stringResource(dev.antomanc.ameki.core.model.WeatherCondition.PARTLY_CLOUDY.label()), MaterialTheme.typography.bodyMedium, pulse)
            HourText(h?.let { units.temp(it.temperature) }, units.temp(18.0), MaterialTheme.typography.headlineSmall, pulse)
            HourText(feels, feelsTemplate, MaterialTheme.typography.bodyLarge, pulse)
            HourText(h?.let { it.precipitationProbability?.let { value -> stringResource(R.string.day_rain, value, it.precipitation?.let(units::precipitation) ?: "") } ?: "–" }, stringResource(R.string.day_rain, 100, units.precipitation(1.0)), MaterialTheme.typography.bodyLarge, pulse)
            HourText(h?.let { it.windSpeed?.let { value -> stringResource(R.string.day_wind, units.wind(value)) } ?: "–" }, stringResource(R.string.day_wind, units.wind(18.0)), MaterialTheme.typography.bodyLarge, pulse)
        }
        return
    }
    val colors = LocalInstrumentColors.current
    val bg = if (highlighted) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).androidxBackground(bg).then(semantics), verticalAlignment = Alignment.CenterVertically) {
        Text(fmt.time(time, s.timeZone), style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.width(76.dp))
        if (h == null) Box(Modifier.size(32.dp).background(pulse, androidx.compose.foundation.shape.CircleShape))
        else WeatherIcon(h.condition, h.isDaytime, size = 32.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            HourText(h?.let { units.temp(it.temperature) }, units.temp(18.0), MaterialTheme.typography.titleLarge, pulse)
            HourText(feels, feelsTemplate, MaterialTheme.typography.bodySmall, pulse, textColor = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.width(76.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                if (h == null) Box(Modifier.size(18.dp).background(pulse, androidx.compose.foundation.shape.CircleShape))
                else RainDot(h.precipitationProbability, max = 18.dp)
            }
            HourText(h?.let { it.precipitationProbability?.let { value -> "$value%" } ?: "–" }, "100%", MaterialTheme.typography.bodyMedium, pulse,
                modifier = Modifier.padding(start = 4.dp), textColor = colors.rain)
        }
        Row(Modifier.width(96.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            h?.windDirection?.let {
                Icon(Icons.Outlined.KeyboardArrowUp, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp).rotate(it + 180f))
                Spacer(Modifier.width(4.dp))
            }
            HourText(h?.let { it.windSpeed?.let(units::wind) ?: "–" }, units.wind(18.0), MaterialTheme.typography.bodyMedium, pulse)
        }
    }
}

@Composable
private fun HourText(value: String?, template: String, style: androidx.compose.ui.text.TextStyle, pulse: Color, modifier: Modifier = Modifier, textColor: Color = MaterialTheme.colorScheme.onSurface) {
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Text(value ?: template, style = style, color = textColor, modifier = Modifier.alpha(if (value == null) 0f else 1f))
        if (value == null) Box(Modifier.fillMaxWidth(.7f).height(12.dp).background(pulse, androidx.compose.foundation.shape.RoundedCornerShape(6.dp)))
    }
}

private fun Modifier.androidxBackground(c: Color) =
    this.background(c, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
