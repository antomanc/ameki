package dev.antomanc.ameki.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.cos
import kotlin.math.sin

/**
 * The temperature dial drawn into a bitmap: Glance can't run a Canvas, but it can show an image.
 * Same geometry as the app's TemperatureDial so the two read as one instrument.
 */
internal object DialBitmap {
    private const val START = 135f
    private const val SWEEP = 270f

    fun draw(
        sizePx: Int,
        current: Double,
        low: Double,
        high: Double,
        track: Color,
        lowColor: Color,
        highColor: Color,
        now: Color,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val stroke = sizePx * 0.065f
        val pad = stroke * 1.2f
        val rect = RectF(pad, pad, sizePx - pad, sizePx - pad)
        val span = (high - low).coerceAtLeast(4.0)
        val min = low - span * 0.25
        val max = high + span * 0.25
        fun frac(t: Double) = ((t - min) / (max - min)).coerceIn(0.0, 1.0).toFloat()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND; color = track.toArgb()
        }
        c.drawArc(rect, START, SWEEP, false, paint)

        val a0 = START + SWEEP * frac(low)
        val a1 = START + SWEEP * frac(high)
        val cx = sizePx / 2f
        val r = rect.width() / 2
        fun px(a: Float) = cx + r * cos(Math.toRadians(a.toDouble())).toFloat()
        fun py(a: Float) = cx + r * sin(Math.toRadians(a.toDouble())).toFloat()
        // Paint alpha also scales a shader: reset it after the translucent track.
        paint.color = android.graphics.Color.BLACK
        paint.shader = LinearGradient(px(a0), py(a0), px(a1), py(a1), lowColor.toArgb(), highColor.toArgb(), Shader.TileMode.CLAMP)
        c.drawArc(rect, a0, (a1 - a0).coerceAtLeast(1f), false, paint)
        paint.shader = null

        val am = START + SWEEP * frac(current)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = now.copy(alpha = 0.2f).toArgb() }
        c.drawCircle(px(am), py(am), stroke * 1.5f, fill)
        fill.color = now.toArgb()
        c.drawCircle(px(am), py(am), stroke * 0.9f, fill)
        return bmp
    }
}

/** Day range bar as an image, so it can carry the same cold → warm gradient as the app. */
internal object RangeBitmap {
    fun draw(widthPx: Int, heightPx: Int, from: Float, to: Float, track: Color, low: Color, high: Color): Bitmap {
        val bmp = Bitmap.createBitmap(widthPx.coerceAtLeast(8), heightPx.coerceAtLeast(2), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val h = heightPx.toFloat()
        val r = h / 2
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = track.toArgb() }
        c.drawRoundRect(RectF(0f, 0f, widthPx.toFloat(), h), r, r, paint)
        val x0 = (from.coerceIn(0f, 1f) * (widthPx - h))
        val x1 = (to.coerceIn(0f, 1f) * (widthPx - h)) + h
        paint.color = android.graphics.Color.BLACK // opaque: paint alpha would fade the gradient
        paint.shader = LinearGradient(x0, 0f, x1, 0f, low.toArgb(), high.toArgb(), Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(x0, 0f, maxOf(x1, x0 + h), h), r, r, paint)
        return bmp
    }
}
