package dev.antomanc.ameki.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.antomanc.ameki.core.ui.R

private fun hanken(weight: FontWeight) = Font(
    R.font.hanken_grotesk,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Hanken Grotesk: one grotesque for display and text, like the instruments it borrows from. */
val Hanken = FontFamily(
    hanken(FontWeight.Light),
    hanken(FontWeight.Normal),
    hanken(FontWeight.Medium),
    hanken(FontWeight.SemiBold),
    hanken(FontWeight.Bold),
)

/** Tabular figures everywhere: numbers line up in columns and don't jitter when they change. */
private const val TNUM = "tnum"

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = Hanken,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
    fontFeatureSettings = TNUM,
)

val AmekiTypography = Typography(
    displayLarge = style(88, 92, FontWeight.Light, -0.03),
    displayMedium = style(56, 60, FontWeight.Light, -0.02),
    displaySmall = style(40, 44, FontWeight.Light, -0.01),
    headlineLarge = style(30, 36, FontWeight.Normal),
    headlineMedium = style(26, 32, FontWeight.Normal),
    headlineSmall = style(22, 28, FontWeight.Medium),
    titleLarge = style(20, 26, FontWeight.Medium),
    titleMedium = style(16, 22, FontWeight.Medium),
    titleSmall = style(14, 20, FontWeight.Medium),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 20, FontWeight.Normal),
    bodySmall = style(12, 16, FontWeight.Normal),
    labelLarge = style(14, 20, FontWeight.Medium),
    labelMedium = style(12, 16, FontWeight.Medium, 0.02),
    // Small tracked caps: the instrument's engraved labels.
    labelSmall = style(11, 16, FontWeight.Medium, 0.12),
)
