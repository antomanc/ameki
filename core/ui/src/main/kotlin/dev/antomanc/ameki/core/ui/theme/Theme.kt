package dev.antomanc.ameki.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.antomanc.ameki.core.model.ThemeMode

val AmekiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * Material 3 stable (1.4) keeps the Expressive theme internal, so expressiveness comes from our
 * shapes, springs and type rather than MaterialExpressiveTheme. Swap in when it goes public.
 */
@Composable
fun AmekiTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> WarmDark
        else -> WarmLight
    }
    CompositionLocalProvider(LocalInstrumentColors provides if (dark) InstrumentDark else InstrumentLight) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AmekiTypography,
            shapes = AmekiShapes,
            content = content,
        )
    }
}
