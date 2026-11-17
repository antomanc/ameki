package dev.antomanc.ameki.core.ui.effects

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ApplyOnChangeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun appliesOncePerKeyAndSkipsNull() {
        var key by mutableStateOf<Int?>(null)
        var label by mutableStateOf("a")
        val applied = mutableListOf<String>()
        compose.setContent {
            val l = label
            ApplyOnChange(key) { applied += "$l$it" }
        }
        compose.waitForIdle()
        assertEquals(emptyList<String>(), applied)

        key = 1
        compose.waitForIdle()
        label = "b"
        compose.waitForIdle()
        assertEquals("a recomposition with the same key does not apply again", listOf("a1"), applied)

        key = 2
        compose.waitForIdle()
        assertEquals("a new key applies with the current lambda", listOf("a1", "b2"), applied)
    }

    @Test
    fun aNewKeyOrLeavingCancelsTheRunningApply() {
        var key by mutableStateOf<Int?>(1)
        var shown by mutableStateOf(true)
        val cancelled = mutableListOf<Int>()
        compose.setContent {
            if (shown) ApplyOnChange(key) { k -> try { awaitCancellation() } finally { cancelled += k } }
        }
        compose.waitForIdle()
        assertEquals(emptyList<Int>(), cancelled)

        key = 2
        compose.waitForIdle()
        assertEquals(listOf(1), cancelled)

        shown = false
        compose.waitForIdle()
        assertEquals(listOf(1, 2), cancelled)
    }
}
