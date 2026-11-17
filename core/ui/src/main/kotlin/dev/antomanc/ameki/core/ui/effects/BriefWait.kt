package dev.antomanc.ameki.core.ui.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * True only for the brief post-open wait for first content.
 * Becomes false (and stays false) when [ready] is true, [settled] while not [loading],
 * the first [loading] cycle settles, or [timeoutMs] elapses — whichever comes first.
 * [ready] / [settled] end the wait in the same frame so finished loads never flash mute UI.
 */
@Composable
fun rememberBriefWait(
    ready: Boolean,
    loading: Boolean,
    settled: Boolean = false,
    timeoutMs: Long = 2_000L,
): Boolean {
    var expired by remember { mutableStateOf(false) }
    var sawLoading by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(timeoutMs)
        expired = true
    }
    LaunchedEffect(loading) {
        if (loading) {
            sawLoading = true
        } else if (sawLoading) {
            expired = true
        }
    }
    LaunchedEffect(settled, loading) {
        if (settled && !loading) expired = true
    }
    // Same-frame exit when already settled; `expired` latches so a later refresh cannot revive mute.
    val alreadySettled = settled && !loading
    return !ready && !expired && !alreadySettled
}
