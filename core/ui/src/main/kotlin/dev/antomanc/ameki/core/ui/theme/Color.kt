package dev.antomanc.ameki.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/*
 * "Instrument" world: a warm neutral ground (Material You when available, warm greys as the
 * static fallback), one fixed signal colour for "now", and fixed data ramps for temperature and
 * rain that read the same on every wallpaper.
 */

internal val WarmLight = lightColorScheme(
    primary = Color(0xFF4F5B62),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9E1E6),
    onPrimaryContainer = Color(0xFF0C1A21),
    secondary = Color(0xFF66605A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9E2DA),
    onSecondaryContainer = Color(0xFF211C17),
    tertiary = Color(0xFF8A4F2F),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFECEBE6),
    onBackground = Color(0xFF1D1E1C),
    surface = Color(0xFFECEBE6),
    onSurface = Color(0xFF1D1E1C),
    surfaceVariant = Color(0xFFE2E1DA),
    onSurfaceVariant = Color(0xFF55564F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F3EE),
    surfaceContainer = Color(0xFFF8F7F3),
    surfaceContainerHigh = Color(0xFFE6E5DF),
    surfaceContainerHighest = Color(0xFFDDDCD5),
    outline = Color(0xFF77786F),
    outlineVariant = Color(0xFFC8C7BF),
)

internal val WarmDark = darkColorScheme(
    primary = Color(0xFFB7C6CE),
    onPrimary = Color(0xFF213037),
    primaryContainer = Color(0xFF38464E),
    onPrimaryContainer = Color(0xFFD9E1E6),
    secondary = Color(0xFFCFC5BB),
    onSecondary = Color(0xFF362F29),
    secondaryContainer = Color(0xFF4D463F),
    onSecondaryContainer = Color(0xFFEBE1D7),
    tertiary = Color(0xFFFFB68F),
    onTertiary = Color(0xFF52230A),
    background = Color(0xFF171816),
    onBackground = Color(0xFFE6E4DE),
    surface = Color(0xFF171816),
    onSurface = Color(0xFFE6E4DE),
    surfaceVariant = Color(0xFF2B2C29),
    onSurfaceVariant = Color(0xFFB3B2AA),
    surfaceContainerLowest = Color(0xFF111210),
    surfaceContainerLow = Color(0xFF1C1D1B),
    surfaceContainer = Color(0xFF21221F),
    surfaceContainerHigh = Color(0xFF2B2C29),
    surfaceContainerHighest = Color(0xFF363733),
    outline = Color(0xFF8E8D85),
    outlineVariant = Color(0xFF45463F),
)

/** Colours that carry meaning and therefore never follow the wallpaper. */
@Immutable
data class InstrumentColors(
    /** The single signal colour: the present moment on the dial and timelines. */
    val now: Color,
    val cold: Color,
    val mild: Color,
    val warm: Color,
    val hot: Color,
    val rain: Color,
    val snow: Color,
    val track: Color,
) {
    /** Blue → amber ramp over the usual range of air temperatures. */
    fun temperature(celsius: Double): Color {
        val t = ((celsius + 5.0) / 40.0).coerceIn(0.0, 1.0).toFloat()
        return when {
            t < 1f / 3 -> lerp(cold, mild, t * 3)
            t < 2f / 3 -> lerp(mild, warm, (t - 1f / 3) * 3)
            else -> lerp(warm, hot, (t - 2f / 3) * 3)
        }
    }
}

internal val InstrumentLight = InstrumentColors(
    now = Color(0xFFE2501F),
    cold = Color(0xFF4F86C6),
    mild = Color(0xFF7FA9A0),
    warm = Color(0xFFD9A040),
    hot = Color(0xFFD5612E),
    rain = Color(0xFF2F68B5),
    snow = Color(0xFF6E9CC9),
    track = Color(0x22000000),
)

internal val InstrumentDark = InstrumentColors(
    now = Color(0xFFFF6A36),
    cold = Color(0xFF7FB0EC),
    mild = Color(0xFF9CC7BD),
    warm = Color(0xFFF0BC62),
    hot = Color(0xFFFF8752),
    rain = Color(0xFF7AAEF5),
    snow = Color(0xFFB3D3F2),
    track = Color(0x2EFFFFFF),
)

val LocalInstrumentColors = staticCompositionLocalOf { InstrumentLight }

internal fun ColorScheme.isLight() = background.luminance() > 0.5f

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
