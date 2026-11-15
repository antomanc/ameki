package dev.antomanc.ameki.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WidgetLayoutTest {
    private fun size(w: Int, h: Int) = DpSize(w.dp, h.dp)

    @Test
    fun `hourly widget grows from a strip to a strip with rain`() {
        val oneRow = WidgetLayout.hourly(size(250, 60))
        assertThat(oneRow.stripOnly).isTrue()
        assertThat(oneRow.strip.count).isEqualTo(4)
        val wide = WidgetLayout.hourly(size(320, 180))
        assertThat(wide.stripOnly).isFalse()
        assertThat(wide.strip.count).isEqualTo(5)
        assertThat(wide.strip.rain).isTrue()
    }

    @Test
    fun `daily rows fill the height but never exceed the forecast`() {
        assertThat(WidgetLayout.dailyRows(size(250, 110), 10)).isEqualTo(1)
        assertThat(WidgetLayout.dailyRows(size(250, 250), 10)).isEqualTo(3)
        assertThat(WidgetLayout.dailyRows(size(250, 500), 2)).isEqualTo(2)
    }

    @Test
    fun `an alert takes a day row from the complete widget`() {
        assertThat(WidgetLayout.complete(size(320, 320), hasAlert = false).dayRows).isEqualTo(2)
        assertThat(WidgetLayout.complete(size(320, 320), hasAlert = true).dayRows).isEqualTo(2)
        assertThat(WidgetLayout.complete(size(320, 280), hasAlert = true).dayRows).isEqualTo(1)
        assertThat(WidgetLayout.complete(size(250, 180), hasAlert = false).dayRows).isEqualTo(0)
    }

    @Test
    fun `a one-row widget shows no list at large fonts`() {
        assertThat(WidgetLayout.largeFont(size(250, 60)).rows).isEqualTo(0)
        assertThat(WidgetLayout.largeFont(size(250, 180)).rows).isEqualTo(2)
    }
}
