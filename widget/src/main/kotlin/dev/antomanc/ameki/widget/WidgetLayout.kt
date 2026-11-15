package dev.antomanc.ameki.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * How much each widget shows at a given size. Every threshold lives here, so "what does the
 * Daily widget show at 4×3" has one answer and one test.
 */
internal object WidgetLayout {
    /** Rows and tap targets: the platform's minimum touch height. */
    val ROW: Dp = 48.dp

    /** Dense tiles get a smaller inset: room for the content instead of clipping it. */
    fun padding(size: DpSize): Dp = when {
        size.height < 150.dp -> 0.dp
        size.height < 200.dp -> 8.dp
        else -> 12.dp
    }

    enum class Compact { ROW, DIAL, DIAL_AND_TEXT }

    fun compact(size: DpSize): Compact = when {
        size.height < 90.dp -> Compact.ROW
        size.width < 170.dp -> Compact.DIAL
        else -> Compact.DIAL_AND_TEXT
    }

    /** A strip of hours. [tiny]: about 30 dp of height, so label and icon share a line. */
    data class Hours(val count: Int, val tiny: Boolean, val compact: Boolean, val large: Boolean, val rain: Boolean)

    /** [stripOnly]: a 1-row widget, just the hours and the stamp; [dense]: a smaller header. */
    data class Hourly(val stripOnly: Boolean, val dense: Boolean, val strip: Hours)

    fun hourly(size: DpSize): Hourly {
        val stripOnly = size.height < 90.dp
        val dense = size.height < 150.dp
        return Hourly(
            stripOnly = stripOnly,
            dense = dense,
            strip = Hours(
                count = if (size.width >= 300.dp) 5 else 4,
                tiny = dense,
                compact = false,
                large = !dense,
                rain = !dense,
            ),
        )
    }

    /** Day rows under a header, at most [available]. */
    fun dailyRows(size: DpSize, available: Int): Int =
        rowsIn(size.height - 60.dp).coerceIn(1, available.coerceAtLeast(1))

    data class Complete(val dense: Boolean, val large: Boolean, val dial: Dp, val temperatureSp: Int, val hours: Hours, val dayRows: Int)

    fun complete(size: DpSize, hasAlert: Boolean): Complete {
        val dense = size.height < 240.dp
        val large = size.height >= 300.dp
        return Complete(
            dense = dense,
            large = large,
            dial = if (dense) 64.dp else if (large) 112.dp else 88.dp,
            temperatureSp = if (dense) 22 else if (large) 32 else 26,
            hours = Hours(count = if (size.width >= 300.dp) 7 else 6, tiny = dense, compact = !large, large = large, rain = !dense),
            dayRows = rowsIn(size.height - if (hasAlert) 220.dp else 180.dp),
        )
    }

    data class Rain(val wide: Boolean, val everyOtherSlot: Boolean, val fallbackHours: Int)

    fun rain(size: DpSize): Rain {
        val wide = size.width >= 200.dp
        return Rain(wide = wide, everyOtherSlot = !wide, fallbackHours = if (wide) 6 else 3)
    }

    /** At large font sizes: fewer, full-height rows, none on a 1-row widget. */
    data class LargeFont(val dense: Boolean, val rows: Int)

    fun largeFont(size: DpSize): LargeFont {
        val dense = size.height < 150.dp
        val rows = if (size.height < 90.dp) 0 else rowsIn(size.height - if (dense) 50.dp else 75.dp)
        return LargeFont(dense, rows)
    }

    private fun rowsIn(height: Dp): Int = (height.value / ROW.value).toInt().coerceAtLeast(0)
}
