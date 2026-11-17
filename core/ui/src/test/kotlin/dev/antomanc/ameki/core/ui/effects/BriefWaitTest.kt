package dev.antomanc.ameki.core.ui.effects

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BriefWaitTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun readyEndsTheWaitInTheSameFrame() {
        var waiting: Boolean? = null
        compose.setContent { waiting = rememberBriefWait(ready = true, loading = false) }
        compose.waitForIdle()
        assertFalse(checkNotNull(waiting))
    }

    @Test
    fun waitsUntilTimeoutWhenNeverLoading() {
        compose.mainClock.autoAdvance = false
        var waiting: Boolean? = null
        compose.setContent { waiting = rememberBriefWait(ready = false, loading = false, timeoutMs = 2_000) }
        compose.mainClock.advanceTimeByFrame()
        assertTrue(checkNotNull(waiting))

        compose.mainClock.advanceTimeBy(1_500)
        assertTrue(checkNotNull(waiting))

        compose.mainClock.advanceTimeBy(600)
        compose.mainClock.advanceTimeByFrame()
        assertFalse(checkNotNull(waiting))
    }

    @Test
    fun firstLoadSettlingEndsTheWaitBeforeTimeout() {
        var ready by mutableStateOf(false)
        var loading by mutableStateOf(true)
        var waiting: Boolean? = null
        compose.setContent { waiting = rememberBriefWait(ready = ready, loading = loading, timeoutMs = 60_000) }
        compose.waitForIdle()
        assertTrue(checkNotNull(waiting))

        loading = false
        compose.waitForIdle()
        assertFalse(checkNotNull(waiting))

        // Stays false even if loading starts again later.
        loading = true
        compose.waitForIdle()
        assertFalse(checkNotNull(waiting))
        ready = true
        compose.waitForIdle()
        assertFalse(checkNotNull(waiting))
    }

    @Test
    fun readyArrivingEndsWaitWithoutNeedingSettle() {
        var ready by mutableStateOf(false)
        var waiting: Boolean? = null
        compose.setContent { waiting = rememberBriefWait(ready = ready, loading = true, timeoutMs = 60_000) }
        compose.waitForIdle()
        assertTrue(checkNotNull(waiting))

        ready = true
        compose.waitForIdle()
        assertFalse(checkNotNull(waiting))
    }
}
