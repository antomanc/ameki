package dev.antomanc.ameki.widget

import dev.antomanc.ameki.core.data.widget.WidgetConfig
import dev.antomanc.ameki.core.data.widget.WidgetData
import dev.antomanc.ameki.core.data.widget.WidgetStyle
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.height
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.unit.ColorProvider
import dev.antomanc.ameki.core.model.WeatherCondition
import dev.antomanc.ameki.core.ui.ForecastView
import dev.antomanc.ameki.core.ui.TimeFormatter
import dev.antomanc.ameki.core.ui.R as UiR
import dev.antomanc.ameki.core.ui.WeatherIcons
import java.time.Instant

/** Resolved palette for one widget instance. Instrument colours stay fixed; the ground follows the style. */
class WidgetPalette internal constructor(
    val background: ColorProvider?,
    val onSurface: ColorProvider,
    val onSurfaceVariant: ColorProvider,
    val track: ColorProvider,
    val dark: Boolean?,
) {
    val now = fixed(Color(0xFFE2501F), Color(0xFFFF6A36))
    val rain = fixed(Color(0xFF2F68B5), Color(0xFF7AAEF5))

    private fun fixed(light: Color, darkColor: Color): ColorProvider = when (dark) {
        true -> ColorProvider(darkColor)
        false -> ColorProvider(light)
        null -> ColorProvider(day = light, night = darkColor)
    }

    /** Same blue → amber ramp as the app. */
    fun temperatureColor(celsius: Double, night: Boolean): Color {
        val cold = if (night) Color(0xFF7FB0EC) else Color(0xFF4F86C6)
        val mild = if (night) Color(0xFF9CC7BD) else Color(0xFF7FA9A0)
        val warm = if (night) Color(0xFFF0BC62) else Color(0xFFD9A040)
        val hot = if (night) Color(0xFFFF8752) else Color(0xFFD5612E)
        val t = ((celsius + 5.0) / 40.0).coerceIn(0.0, 1.0).toFloat()
        return when {
            t < 1f / 3 -> androidx.compose.ui.graphics.lerp(cold, mild, t * 3)
            t < 2f / 3 -> androidx.compose.ui.graphics.lerp(mild, warm, (t - 1f / 3) * 3)
            else -> androidx.compose.ui.graphics.lerp(warm, hot, (t - 2f / 3) * 3)
        }
    }
}

@Composable
internal fun rememberPalette(config: WidgetConfig): WidgetPalette = when (config.style) {
    WidgetStyle.DYNAMIC -> {
        val ctx = LocalContext.current
        val bg = GlanceTheme.colors.widgetBackground
        val alpha = (0.95f + config.opacity.coerceIn(0f, 1f) * 0.05f)
        val background = if (alpha >= 0.999f) bg else ColorProvider(
            day = Color(bg.getColor(ctx.lightContext()).value).copy(alpha = alpha),
            night = Color(bg.getColor(ctx.darkContext()).value).copy(alpha = alpha),
        )
        WidgetPalette(background, GlanceTheme.colors.onSurface, GlanceTheme.colors.onSurfaceVariant, GlanceTheme.colors.outline, dark = null)
    }
    WidgetStyle.DARK -> WidgetPalette(
        // Keep a minimum dark ground: white text must stay readable even at 0%.
        ColorProvider(Color(0xFF171816).copy(alpha = (0.8f + config.opacity.coerceIn(0f, 1f) * 0.2f))),
        ColorProvider(Color(0xFFF2F0EA)), ColorProvider(Color(0xFFB3B2AA)), ColorProvider(Color(0x40FFFFFF)), dark = true,
    )
    WidgetStyle.TRANSPARENT -> {
        // Even at 0% the minimum scrim keeps white text legible on a bright wallpaper.
        val alpha = (0.65f + config.opacity.coerceIn(0f, 1f) * 0.35f)
        WidgetPalette(
            ColorProvider(Color.Black.copy(alpha = alpha)),
            ColorProvider(Color.White), ColorProvider(Color(0xE6FFFFFF)), ColorProvider(Color(0x55FFFFFF)), dark = true,
        )
    }
}

private fun Context.lightContext(): Context = createConfigurationContext(
    android.content.res.Configuration(resources.configuration).apply {
        uiMode = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or android.content.res.Configuration.UI_MODE_NIGHT_NO
    },
)

private fun Context.darkContext(): Context = createConfigurationContext(
    android.content.res.Configuration(resources.configuration).apply {
        uiMode = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or android.content.res.Configuration.UI_MODE_NIGHT_YES
    },
)

internal fun Context.isNight(): Boolean =
    (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

/** System-rounded container that opens the app on the widget's place. */
@Composable
internal fun WidgetFrame(data: WidgetData, palette: WidgetPalette, padding: Dp = 14.dp, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var m = GlanceModifier.fillMaxSize().appWidgetBackground()
        .clickable(actionStartActivity(openAppIntent(context, data.weather?.place?.id)))
    palette.background?.let { m = m.background(it) }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) m = m.cornerRadius(android.R.dimen.system_app_widget_background_radius)
    Box(m.padding(padding)) { content() }
}

@Composable
internal fun textStyle(color: ColorProvider, size: TextUnit, weight: FontWeight = FontWeight.Normal, light: Boolean = false): TextStyle {
    // Widgets have fixed cells: respect large fonts but adapt the information density to avoid clipping.
    val scale = LocalContext.current.resources.configuration.fontScale.coerceIn(1f, 2f)
    val actual = LocalContext.current.resources.configuration.fontScale
    return TextStyle(
        color = color,
        fontSize = (size.value * scale / actual).sp,
        fontWeight = weight,
        fontFamily = if (light) FontFamily("sans-serif-light") else FontFamily.SansSerif,
    )
}

@Composable
internal fun ConditionIcon(condition: WeatherCondition, isDay: Boolean, size: Dp, palette: WidgetPalette) {
    // Full-colour Meteocons on dark grounds; on light ones the white clouds would vanish,
    // so the monochrome set tinted with the theme takes over.
    val dark = palette.dark ?: LocalContext.current.isNight()
    if (dark) {
        Image(ImageProvider(WeatherIcons.color(condition, isDay)), contentDescription = null, modifier = GlanceModifier.size(size))
    } else {
        Image(
            ImageProvider(WeatherIcons.mono(condition, isDay)),
            contentDescription = null,
            modifier = GlanceModifier.size(size),
            colorFilter = ColorFilter.tint(palette.onSurface),
        )
    }
}

/** Rain probability dot on the same diameter ramp as the app. */
@Composable
internal fun RainDot(probability: Int?, max: Dp, palette: WidgetPalette) {
    val p = ((probability ?: 0).coerceIn(0, 100)) / 100f
    val d = (max.value * (0.18f + 0.82f * kotlin.math.sqrt(p))).dp
    Box(GlanceModifier.size(max), contentAlignment = Alignment.Center) {
        Box(GlanceModifier.size(d).cornerRadius(d / 2).background(palette.rain)) {}
    }
}

/**
 * Honest data: every size shows when the weather was fetched. The stamp also refreshes the
 * shared worker when tapped, and says so to TalkBack. Old readings are marked as such.
 */
@Composable
internal fun Freshness(view: ForecastView, palette: WidgetPalette, fmt: TimeFormatter, compact: Boolean = false) {
    val updated = view.updatedAt ?: return
    val time = fmt.timestamp(updated, view.snapshot.timeZone, view.now)
    val context = LocalContext.current
    val full = context.getString(if (view.stale) UiR.string.updated_stale else UiR.string.updated_at, time)
    val action = if (compact) GlanceModifier.semantics { contentDescription = full }
        else GlanceModifier.height(48.dp).clickable(actionRunCallback<RefreshAction>()).semantics { contentDescription = context.getString(R.string.widget_refresh_cd, full) }
    Box(
        action
            .padding(vertical = if (compact) 1.dp else 2.dp),
    ) {
        Text(
            if (compact) time else full,
            style = textStyle(if (view.stale) palette.now else palette.onSurfaceVariant, if (compact) 9.sp else 10.sp),
            maxLines = 1,
        )
    }
}

/** The non-weather states: no place, a deleted place, first load, a problem only the user can fix. */
@Composable
internal fun WidgetMessage(data: WidgetData, palette: WidgetPalette, need: WidgetNeed): Boolean {
    val state = widgetState(data, need)
    if (state == WidgetState.READY) return false
    val context = LocalContext.current
    val text = when (state) {
        WidgetState.READY -> return false
        WidgetState.NO_PLACE -> context.getString(R.string.widget_no_place)
        WidgetState.PLACE_GONE -> context.getString(R.string.widget_place_gone)
        WidgetState.KEY_PROBLEM -> context.getString(R.string.widget_key_problem)
        WidgetState.NO_LOCATION -> context.getString(R.string.widget_no_location)
        WidgetState.ACCESS_DENIED -> context.getString(R.string.widget_access_denied)
        WidgetState.QUOTA -> context.getString(R.string.widget_quota)
        WidgetState.UNSUPPORTED -> context.getString(R.string.widget_unsupported)
        WidgetState.OFFLINE -> context.getString(R.string.widget_offline)
        WidgetState.FAILED -> context.getString(R.string.widget_failed)
        WidgetState.PARTIAL -> context.getString(R.string.widget_partial)
        WidgetState.LOADING -> context.getString(R.string.widget_loading)
    }
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(data.weather?.place?.name ?: "Ameki", style = textStyle(palette.onSurface, 14.sp, FontWeight.Medium), maxLines = 1)
        Spacer(GlanceModifier.size(4.dp))
        Text(
            text,
            style = textStyle(palette.onSurfaceVariant, 12.sp).copy(textAlign = androidx.glance.text.TextAlign.Center),
            maxLines = 3,
        )
        data.weather?.snapshot?.let {
            Freshness(ForecastView(it, Instant.now(), data.settings.refreshIntervalMinutes), palette, TimeFormatter(context, data.settings.timeFormat), compact = true)
        }
    }
    return true
}

