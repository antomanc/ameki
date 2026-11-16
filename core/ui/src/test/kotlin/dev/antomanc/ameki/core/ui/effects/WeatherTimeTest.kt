package dev.antomanc.ameki.core.ui.effects

import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class WeatherTimeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun startsAtNowAndTicksOnceAMinute() {
        compose.mainClock.autoAdvance = false
        val before = Instant.now()
        var latest: Instant? = null
        compose.setContent { latest = rememberWeatherTime() }
        compose.mainClock.advanceTimeByFrame()
        val first = checkNotNull(latest)
        assertFalse(first.isBefore(before))
        assertFalse(first.isAfter(Instant.now()))

        Thread.sleep(5)
        compose.mainClock.advanceTimeBy(59_000)
        assertEquals("no tick before a minute", first, latest)

        compose.mainClock.advanceTimeBy(2_000)
        compose.mainClock.advanceTimeByFrame()
        assertTrue("ticks after a minute", checkNotNull(latest).isAfter(first))
    }
}
