package net.bible.sharedui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Text for a field whose value lives in a controller `StateFlow` that reaches the screen through `collectAsState`.
 *
 * That echo arrives a dispatch after the keystroke. A field that displays the echoed value is redrawn with stale text
 * in between, which on iOS discarded the first key typed after a focus switch. This state keeps the typed text locally
 * (updated at once, see [edit]) and still forwards every edit. The controller's value is taken over only when it is a
 * genuine external write, a value this field never sent (paste, a validation result, a reload or a normalising save);
 * the late echo of an edit already shown is recognised and ignored, so it cannot overwrite newer input.
 */
internal class ControllerTextState(initial: String) {
    var text by mutableStateOf(initial)
        private set
    private val sent = ArrayDeque<String>()

    /** Shows [value] at once and forwards it to the controller. */
    fun edit(value: String, forward: (String) -> Unit) {
        text = value
        sent.addLast(value)
        while (sent.size > MAX_PENDING) sent.removeFirst()
        forward(value)
    }

    /** Called when the controller's value changes. */
    fun controllerChanged(value: String) {
        if (value == text) {
            sent.clear()
            return
        }
        val index = sent.indexOf(value)
        if (index >= 0) {
            repeat(index + 1) { sent.removeFirst() }
            return
        }
        sent.clear()
        text = value
    }

    private companion object {
        const val MAX_PENDING = 64
    }
}

/** A [ControllerTextState] seeded from [controllerValue] and re-seeded whenever [key] changes. */
@Composable
internal fun rememberControllerText(controllerValue: String, key: Any? = null): ControllerTextState {
    val state = remember(key) { ControllerTextState(controllerValue) }
    LaunchedEffect(state, controllerValue) { state.controllerChanged(controllerValue) }
    return state
}
