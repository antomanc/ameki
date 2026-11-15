package dev.antomanc.ameki.feature.home

import com.google.common.truth.Truth.assertThat
import dev.antomanc.ameki.core.ui.AppRequest
import org.junit.Test
import java.time.Instant

class ShowRequestTest {
    private val loaded = sampleState().places
    private val rome = loaded[0]
    private val milan = loaded[1]
    private val days = rome.snapshot!!.daily

    @Test
    fun `a place without a day is shown as soon as it is loaded`() {
        assertThat(nextStep(AppRequest.Show(2), loaded, fetched = false)).isEqualTo(ShowStep.Show(2, null))
    }

    @Test
    fun `a day the place has opens with its hour`() {
        val hour = Instant.parse("2026-11-16T09:00:00Z")
        val step = nextStep(AppRequest.Show(1, days[2].date, hour), loaded, fetched = false)
        assertThat(step).isEqualTo(ShowStep.Show(1, DayTarget(days[2].date, hour)))
    }

    @Test
    fun `a removed place leaves the screen where it is`() {
        assertThat(nextStep(AppRequest.Show(99, days[0].date), loaded, fetched = false)).isEqualTo(ShowStep.Show(null, null))
    }

    @Test
    fun `a day outside the forecast shows only the place`() {
        val step = nextStep(AppRequest.Show(1, days.first().date.minusDays(1)), loaded, fetched = false)
        assertThat(step).isEqualTo(ShowStep.Show(1, null))
    }

    @Test
    fun `a place with no forecast is fetched once, then the answer is final`() {
        val request = AppRequest.Show(2, days[1].date)
        val empty = listOf(rome, milan.copy(snapshot = null, lastAttemptAt = null))
        assertThat(nextStep(request, empty, fetched = false)).isEqualTo(ShowStep.Fetch(2))
        assertThat(nextStep(request, listOf(rome, milan), fetched = true)).isEqualTo(ShowStep.Show(2, DayTarget(days[1].date)))
        // The fetch failed or brought no days: show the place, never wait for more.
        assertThat(nextStep(request, empty, fetched = true)).isEqualTo(ShowStep.Show(2, null))
    }
}
