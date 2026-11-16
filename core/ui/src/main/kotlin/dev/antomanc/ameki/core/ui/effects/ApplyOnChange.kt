package dev.antomanc.ameki.core.ui.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Applies a decision made elsewhere (a ViewModel, a pure function like nextStep) once per new [key].
 * A null key does nothing and the same key again does nothing; a new key, or leaving the screen,
 * cancels an [apply] that is still running. Each new key runs the [apply] from that composition.
 */
@Composable
fun <K : Any> ApplyOnChange(key: K?, apply: suspend (K) -> Unit) {
    LaunchedEffect(key) { if (key != null) apply(key) }
}
