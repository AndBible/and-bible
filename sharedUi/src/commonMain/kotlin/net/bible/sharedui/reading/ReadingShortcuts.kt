package net.bible.sharedui.reading

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/** Maps unhandled native/Compose chords to the same commands as BibleView's keyboard listener. */
internal fun readingShortcut(code: String, ctrl: Boolean, alt: Boolean, meta: Boolean = false, editing: Boolean = false): String? {
    if (meta || ctrl && alt) return null
    if (ctrl) {
        if (editing && code in setOf("KeyB", "KeyC")) return null
        return if (code in setOf("KeyF", "KeyB", "KeyC", "KeyW")) "Ctrl$code" else null
    }
    if (alt && !editing && code in setOf("KeyM", "KeyO", "ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight")) {
        return "Alt$code"
    }
    return null
}

internal fun readingShortcut(event: KeyEvent, editing: Boolean = false): String? {
    if (event.type != KeyEventType.KeyDown) return null
    val code = when (event.key) {
        Key.F -> "KeyF"
        Key.B -> "KeyB"
        Key.C -> "KeyC"
        Key.W -> "KeyW"
        Key.M -> "KeyM"
        Key.O -> "KeyO"
        Key.DirectionUp -> "ArrowUp"
        Key.DirectionDown -> "ArrowDown"
        Key.DirectionLeft -> "ArrowLeft"
        Key.DirectionRight -> "ArrowRight"
        else -> return null
    }
    return readingShortcut(code, event.isCtrlPressed, event.isAltPressed, event.isMetaPressed, editing)
}
