package dev.antomanc.ameki.widget

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
import androidx.glance.unit.ColorProvider
import dev.antomanc.ameki.core.data.sync.SyncError
import dev.antomanc.ameki.core.model.WeatherCondition
import dev.antomanc.ameki.core.ui.TimeFormatter
import dev.antomanc.ameki.core.ui.WeatherIcons
import java.time.Duration
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
        val alpha = config.opacity.coerceIn(0f, 1f)
        val background = if (alpha >= 0.999f) bg else ColorProvider(
            day = Color(bg.getColor(ctx.lightContext()).value).copy(alpha = alpha),
            night = Color(bg.getColor(ctx.darkContext()).value).copy(alpha = alpha),
        )
        WidgetPalette(background, GlanceTheme.colors.onSurface, GlanceTheme.colors.onSurfaceVariant, GlanceTheme.colors.outline, dark = null)
    }
    WidgetStyle.DARK -> WidgetPalette(
        ColorProvider(Color(0xFF171816).copy(alpha = config.opacity.coerceIn(0f, 1f))),
        ColorProvider(Color(0xFFF2F0EA)), ColorProvider(Color(0xFFB3B2AA)), ColorProvider(Color(0x40FFFFFF)), dark = true,
    )
    WidgetStyle.TRANSPARENT -> WidgetPalette(
        // A light scrim keeps white text readable on bright wallpapers without hiding them.
        ColorProvider(Color.Black.copy(alpha = (config.opacity * 0.45f).coerceIn(0f, 0.45f))),
        ColorProvider(Color.White), ColorProvider(Color(0xE6FFFFFF)), ColorProvider(Color(0x55FFFFFF)), dark = true,
    )
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

internal fun textStyle(color: ColorProvider, size: TextUnit, weight: FontWeight = FontWeight.Normal, light: Boolean = false) =
    TextStyle(color = color, fontSize = size, fontWeight = weight, fontFamily = if (light) FontFamily("sans-serif-light") else FontFamily.SansSerif)

@Composable
internal fun ConditionIcon(condition: WeatherCondition, isDay: Boolean, size: Dp, palette: WidgetPalette) {
    Image(
        ImageProvider(WeatherIcons.mono(condition, isDay)),
        contentDescription = null,
        modifier = GlanceModifier.size(size),
        colorFilter = ColorFilter.tint(palette.onSurface),
    )
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

/** "09:10" with a dot: grey when fresh, orange when stale. Tapping it asks for a refresh. */
@Composable
internal fun Freshness(data: WidgetData, palette: WidgetPalette, fmt: TimeFormatter) {
    val s = data.weather?.snapshot ?: return
    val updated = s.updatedAt ?: return
    val stale = Duration.between(updated, Instant.now()) > Duration.ofMinutes(data.settings.refreshIntervalMinutes * 2L + 10)
    Row(
        GlanceModifier.clickable(actionRunCallback<RefreshAction>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(GlanceModifier.size(6.dp).cornerRadius(3.dp).background(if (stale) palette.now else palette.onSurfaceVariant)) {}
        Spacer(GlanceModifier.width(4.dp))
        Text(fmt.time(updated, s.timeZone), style = textStyle(palette.onSurfaceVariant, 11.sp))
    }
}

/** The non-weather states: no place yet, first load, a problem only the user can fix. */
@Composable
internal fun WidgetMessage(data: WidgetData, palette: WidgetPalette): Boolean {
    val context = LocalContext.current
    val w = data.weather
    val text = when {
        w == null -> context.getString(R.string.widget_no_place)
        w.hasData -> return false
        w.error == SyncError.MISSING_KEY || w.error == SyncError.INVALID_KEY -> context.getString(R.string.widget_key_problem)
        w.error == SyncError.NO_LOCATION -> context.getString(R.string.widget_no_location)
        w.error == SyncError.NETWORK -> context.getString(R.string.widget_offline)
        else -> context.getString(R.string.widget_loading)
    }
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(w?.place?.name ?: "Ameki", style = textStyle(palette.onSurface, 14.sp, FontWeight.Medium))
        Spacer(GlanceModifier.size(4.dp))
        Text(text, style = textStyle(palette.onSurfaceVariant, 12.sp).copy(textAlign = androidx.glance.text.TextAlign.Center))
    }
    return true
}

