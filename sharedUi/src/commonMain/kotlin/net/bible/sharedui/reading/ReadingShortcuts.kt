package net.bible.sharedui.reading

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/** Maps native/Compose chords to BibleView's supported commands; keep keyboard.ts in sync. */
internal fun readingShortcut(
    code: String, ctrl: Boolean, alt: Boolean, meta: Boolean = false, editing: Boolean = false,
    iosCommandKeys: Boolean = false,
): String? {
    if (meta) {
        if (!iosCommandKeys || ctrl || alt || code !in setOf("KeyF", "KeyB")) return null
        if (editing && code == "KeyB") return null
        return "Ctrl$code"
    }
    if (ctrl && alt) return null
    if (ctrl) {
        if (editing && code in setOf("KeyB", "KeyC")) return null
        return if (code in setOf("KeyF", "KeyB", "KeyC", "KeyW")) "Ctrl$code" else null
    }
    if (alt && !editing && code in setOf("KeyM", "KeyO", "ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight")) {
        return "Alt$code"
    }
    return null
}

/** Only called with codes accepted by [readingShortcut], never arbitrary page text. */
internal fun readingShortcutScript(code: String, ctrl: Boolean, alt: Boolean): String =
    "window.dispatchEvent(new CustomEvent('andbible-native-reading-key', {detail:{code:'$code',ctrlKey:$ctrl,altKey:$alt}}));"

internal fun readingShortcut(event: KeyEvent, editing: Boolean = false, iosCommandKeys: Boolean = false): String? {
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
    return readingShortcut(code, event.isCtrlPressed, event.isAltPressed, event.isMetaPressed, editing, iosCommandKeys)
}
