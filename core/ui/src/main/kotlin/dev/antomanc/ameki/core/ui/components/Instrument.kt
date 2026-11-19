package dev.antomanc.ameki.core.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import dev.antomanc.ameki.core.model.WeatherCondition
import dev.antomanc.ameki.core.ui.WeatherIcons
import dev.antomanc.ameki.core.ui.theme.LocalInstrumentColors
import kotlin.math.cos
import kotlin.math.sin

/** Sweep of the dial, in degrees, opening at the bottom like a gauge. */
private const val START_ANGLE = 135f
private const val SWEEP = 270f

/**
 * The signature: an arc from the lowest to the highest temperature of the scale, today's range
 * drawn on it in the temperature ramp, and the orange "now" marker on the current temperature.
 * Content (the big numeral) sits inside.
 */
@Composable
fun TemperatureDial(
    current: Double,
    low: Double,
    high: Double,
    modifier: Modifier = Modifier,
    description: String,
    strokeWidth: Dp = 12.dp,
    lowLabel: String? = null,
    highLabel: String? = null,
    content: @Composable () -> Unit,
) {
    val colors = LocalInstrumentColors.current
    // Scale: today's range padded a little so the marker never sits on the very end.
    val span = (high - low).coerceAtLeast(4.0)
    val scaleMin = low - span * 0.25
    val scaleMax = high + span * 0.25
    fun frac(t: Double) = ((t - scaleMin) / (scaleMax - scaleMin)).coerceIn(0.0, 1.0).toFloat()

    val marker = remember { Animatable(frac(current)) }
    val animate = rememberAnimationsEnabled()
    LaunchedEffect(current, low, high) {
        if (animate) {
            marker.animateTo(frac(current), spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessLow))
        } else {
            marker.snapTo(frac(current))
        }
    }

    Box(modifier.clearAndSetSemantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val track = dialTrackLayout(strokeWidth.toPx())
            drawDialTrack(colors.track, track)

            val a0 = START_ANGLE + SWEEP * frac(low)
            val a1 = START_ANGLE + SWEEP * frac(high)
            val midTemp = (low + high) / 2.0
            val center = Offset(size.width / 2, size.height / 2)
            drawArc(
                brush = Brush.linearGradient(
                    listOf(colors.temperature(low), colors.temperature(midTemp), colors.temperature(high)),
                    start = point(center, track.diameter / 2, a0),
                    end = point(center, track.diameter / 2, a1),
                ),
                startAngle = a0,
                sweepAngle = (a1 - a0).coerceAtLeast(1f),
                useCenter = false,
                topLeft = track.topLeft,
                size = track.arcSize,
                style = Stroke(track.stroke, cap = StrokeCap.Round),
            )
            // Ticks: an engraved scale, only where there is room for it to stay quiet.
            val ticks = if (track.diameter > 200.dp.toPx()) 36 else 0
            for (i in 0..ticks) {
                if (ticks == 0) break
                val a = START_ANGLE + SWEEP * i / ticks
                val outer = track.diameter / 2 - track.stroke * 1.4f
                val inner = outer - if (i % 6 == 0) track.stroke * 0.9f else track.stroke * 0.45f
                drawLine(colors.track, point(center, inner, a), point(center, outer, a), strokeWidth = track.stroke * 0.16f, cap = StrokeCap.Round)
            }
            val m = point(center, track.diameter / 2, START_ANGLE + SWEEP * marker.value)
            drawCircle(color = colors.now.copy(alpha = 0.22f), radius = track.stroke * 1.55f, center = m)
            drawCircle(color = colors.now, radius = track.stroke * 0.92f, center = m)
        }
        content()
        if (lowLabel != null && highLabel != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.56f)
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    lowLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    highLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Cold-start placeholder: the same ring footprint as [TemperatureDial], track only — no range,
 * marker, or reading. Crossfade into the real dial when current conditions arrive.
 */
@Composable
fun MuteTemperatureDial(
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 12.dp,
) {
    val colors = LocalInstrumentColors.current
    Box(modifier.clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawDialTrack(colors.track, dialTrackLayout(strokeWidth.toPx()))
        }
    }
}

/** Shared TemperatureDial / MuteTemperatureDial ring geometry. */
private data class DialTrackLayout(val topLeft: Offset, val arcSize: Size, val stroke: Float, val diameter: Float)

private fun DrawScope.dialTrackLayout(strokePx: Float): DialTrackLayout {
    val diameter = size.minDimension - strokePx * 2.2f
    val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
    return DialTrackLayout(topLeft, Size(diameter, diameter), strokePx, diameter)
}

private fun DrawScope.drawDialTrack(color: Color, track: DialTrackLayout) {
    drawArc(
        color, START_ANGLE, SWEEP, false, track.topLeft, track.arcSize,
        style = Stroke(track.stroke, cap = StrokeCap.Round),
    )
}

private fun point(c: Offset, r: Float, deg: Float): Offset {
    val rad = Math.toRadians(deg.toDouble())
    return Offset(c.x + r * cos(rad).toFloat(), c.y + r * sin(rad).toFloat())
}

/** Rain probability as a dot whose diameter follows a fixed ramp: 0% is a pinprick, 100% fills [max]. */
@Composable
fun RainDot(probability: Int?, modifier: Modifier = Modifier, max: Dp = 24.dp) {
    val colors = LocalInstrumentColors.current
    val p = ((probability ?: 0).coerceIn(0, 100)) / 100f
    Canvas(modifier.size(max)) {
        val r = (size.minDimension / 2) * (0.12f + 0.88f * kotlin.math.sqrt(p))
        drawCircle(colors.rain.copy(alpha = if (p < 0.05f) 0.35f else 1f), radius = r)
    }
}

/** Day range bar: where today's min–max sits within the whole forecast's range. */
@Composable
fun RangeBar(low: Double, high: Double, scaleMin: Double, scaleMax: Double, modifier: Modifier = Modifier, current: Double? = null) {
    val colors = LocalInstrumentColors.current
    Canvas(modifier) {
        val h = size.height
        val total = (scaleMax - scaleMin).coerceAtLeast(1.0)
        fun x(t: Double) = (((t - scaleMin) / total).coerceIn(0.0, 1.0) * size.width).toFloat()
        drawLine(colors.track, Offset(h / 2, h / 2), Offset(size.width - h / 2, h / 2), strokeWidth = h, cap = StrokeCap.Round)
        val x0 = x(low).coerceIn(h / 2, size.width - h / 2)
        val x1 = x(high).coerceIn(h / 2, size.width - h / 2)
        val mid = (low + high) / 2.0
        drawLine(
            Brush.horizontalGradient(
                listOf(colors.temperature(low), colors.temperature(mid), colors.temperature(high)),
                startX = x0,
                endX = maxOf(x1, x0 + 1f),
            ),
            Offset(x0, h / 2), Offset(maxOf(x1, x0 + 0.1f), h / 2), strokeWidth = h, cap = StrokeCap.Round,
        )
        if (current != null) {
            val cx = x(current).coerceIn(h, size.width - h)
            drawCircle(colors.now.copy(alpha = 0.24f), radius = h * 1.45f, center = Offset(cx, h / 2))
            drawCircle(colors.now, radius = h * 0.85f, center = Offset(cx, h / 2))
        }
    }
}

@Composable
fun WeatherIcon(condition: WeatherCondition, isDay: Boolean, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Image(painterResource(WeatherIcons.color(condition, isDay)), contentDescription = null, modifier = modifier.size(size))
}

/** The animated hero icon. Falls back to the still image while Lottie loads or when animations are off. */
@Composable
fun AnimatedWeatherIcon(condition: WeatherCondition, isDay: Boolean, modifier: Modifier = Modifier, size: Dp = 96.dp) {
    if (!rememberAnimationsEnabled()) {
        WeatherIcon(condition, isDay, modifier, size)
        return
    }
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(WeatherIcons.animated(condition, isDay)))
    if (composition == null) {
        WeatherIcon(condition, isDay, modifier, size)
    } else {
        LottieAnimation(composition, iterations = LottieConstants.IterateForever, modifier = modifier.size(size))
    }
}

@Composable
fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}

/** Scales its children with available width; used by widgets-in-app previews and the hero. */
@Composable
fun Fill(content: @Composable (maxWidth: Dp) -> Unit) = BoxWithConstraints { content(maxWidth) }
