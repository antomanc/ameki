package dev.antomanc.ameki.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.antomanc.ameki.core.model.DailyForecast
import dev.antomanc.ameki.core.model.HourlyForecast
import dev.antomanc.ameki.core.model.Units
import dev.antomanc.ameki.core.model.WeatherSnapshot
import dev.antomanc.ameki.core.ui.TimeFormatter
import dev.antomanc.ameki.core.ui.components.RainDot
import dev.antomanc.ameki.core.ui.components.WeatherIcon
import dev.antomanc.ameki.core.ui.label
import dev.antomanc.ameki.core.ui.theme.LocalInstrumentColors
import java.time.Instant
import kotlin.math.roundToInt
import dev.antomanc.ameki.core.ui.R as UiR

/**
 * One day, hour by hour: "tomorrow at 10" is one tap from the 10-day list.
 * Chips at the top switch day without closing the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DayDetailSheet(
    s: WeatherSnapshot,
    dayIndex: Int,
    units: Units,
    fmt: TimeFormatter,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val day = s.daily[dayIndex]
    val hours = s.hourly.filter { it.time.atZone(s.timeZone).toLocalDate() == day.date }
        .let { list -> if (dayIndex == 0) list.filter { it.time.plusSeconds(3600) > Instant.now() } else list }
    val chips = rememberLazyListState()
    LaunchedEffect(dayIndex) { chips.animateScrollToItem((dayIndex - 1).coerceAtLeast(0)) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyRow(state = chips, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(s.daily) { i, d ->
                FilterChip(
                    selected = i == dayIndex,
                    onClick = { onSelect(i) },
                    label = { Text(if (i == 0) stringResource(UiR.string.today) else "${fmt.weekdayShort(d.date)} ${d.date.dayOfMonth}") },
                )
            }
        }
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp)) {
            item { DayHeader(day, s, units, fmt) }
            item { HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant) }
            if (hours.isEmpty()) {
                item {
                    Text(stringResource(R.string.day_no_hourly), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                item { HourHeaderRow() }
                items(hours, key = { it.time.epochSecond }) { HourRow(it, s, units, fmt) }
            }
        }
    }
}

@Composable
private fun DayHeader(d: DailyForecast, s: WeatherSnapshot, units: Units, fmt: TimeFormatter) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        WeatherIcon(d.condition, true, size = 64.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(fmt.dateMedium(d.date), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(d.conditionText ?: stringResource(d.condition.label()), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(units.temp(d.temperatureMax), style = MaterialTheme.typography.headlineMedium)
            Text(units.temp(d.temperatureMin), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(12.dp))
    val facts = listOfNotNull(
        d.precipitationProbability?.let { stringResource(R.string.day_rain, it, d.precipitation?.let(units::precipitation) ?: "") },
        d.windSpeedMax?.let { stringResource(R.string.day_wind, units.wind(it)) },
        d.uvIndexMax?.let { stringResource(R.string.day_uv, it.roundToInt()) },
        if (d.sunrise != null && d.sunset != null) stringResource(R.string.day_sun, fmt.time(d.sunrise!!, s.timeZone), fmt.time(d.sunset!!, s.timeZone)) else null,
    )
    facts.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp)) }
}

@Composable
private fun HourHeaderRow() {
    val style = MaterialTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(stringResource(R.string.col_hour).uppercase(), style = style, color = color, modifier = Modifier.width(56.dp))
        Spacer(Modifier.width(40.dp))
        Text(stringResource(R.string.col_temp).uppercase(), style = style, color = color, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.col_rain).uppercase(), style = style, color = color, modifier = Modifier.width(76.dp))
        Text(stringResource(R.string.col_wind).uppercase(), style = style, color = color, modifier = Modifier.width(96.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun HourRow(h: HourlyForecast, s: WeatherSnapshot, units: Units, fmt: TimeFormatter) {
    val colors = LocalInstrumentColors.current
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(fmt.time(h.time, s.timeZone), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(56.dp))
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
                // Arrow points where the wind blows to.
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Outlined.KeyboardArrowUp, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp).rotate(it + 180f),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(h.windSpeed?.let(units::wind) ?: "–", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
