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
 * Becomes false (and stays false) when [ready] is true, the first [loading] cycle settles,
 * or [timeoutMs] elapses — whichever comes first. [ready] ends the wait immediately in the
 * same frame, so content that is already present never flashes the waiting UI.
 */
@Composable
fun rememberBriefWait(
    ready: Boolean,
    loading: Boolean,
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
    return !ready && !expired
}
