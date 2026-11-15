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
        assertThat(nextStep(AppRequest.Show(2), loaded, emptySet(), null)).isEqualTo(ShowStep.Show(2, null))
    }

    @Test
    fun `a day the place has opens with its hour`() {
        val hour = Instant.parse("2026-11-16T09:00:00Z")
        val step = nextStep(AppRequest.Show(1, days[2].date, hour), loaded, emptySet(), null)
        assertThat(step).isEqualTo(ShowStep.Show(1, DayTarget(days[2].date, hour)))
    }

    @Test
    fun `a removed place leaves the screen where it is`() {
        assertThat(nextStep(AppRequest.Show(99, days[0].date), loaded, emptySet(), null)).isEqualTo(ShowStep.Show(null, null))
    }

    @Test
    fun `a day outside the forecast shows only the place`() {
        val step = nextStep(AppRequest.Show(1, days.first().date.minusDays(1)), loaded, emptySet(), null)
        assertThat(step).isEqualTo(ShowStep.Show(1, null))
    }

    @Test
    fun `a place with no forecast is fetched before the day is decided`() {
        val request = AppRequest.Show(2, days[1].date)
        val empty = listOf(rome, milan.copy(snapshot = null, lastAttemptAt = null))
        assertThat(nextStep(request, empty, emptySet(), null)).isEqualTo(ShowStep.Fetch(2))

        val started = Attempt(lastAttemptAt = null)
        assertThat(nextStep(request, empty, setOf(2), started)).isEqualTo(ShowStep.Wait(2))
        // Refresh flag already cleared, but the attempt has not reached the data yet.
        assertThat(nextStep(request, empty, emptySet(), started)).isEqualTo(ShowStep.Wait(2))

        val arrived = listOf(rome, milan)
        assertThat(nextStep(request, arrived, emptySet(), started)).isEqualTo(ShowStep.Show(2, DayTarget(days[1].date)))
        val failed = listOf(rome, milan.copy(snapshot = null, lastAttemptAt = Instant.now()))
        assertThat(nextStep(request, failed, emptySet(), started)).isEqualTo(ShowStep.Show(2, null))
    }
}
