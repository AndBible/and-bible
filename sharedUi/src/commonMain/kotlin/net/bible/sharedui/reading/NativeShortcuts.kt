package net.bible.sharedui.reading

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * Reading shortcuts that need a native action instead of a BibleView key (#4177): the iOS counterparts of the desktop's
 * `DesktopShortcuts` table (Command in place of Ctrl). They run the shared `HostShortcutActions` the desktop uses.
 */
internal enum class NativeShortcut {
    SyncNow, OpenSettings, FontLarger, FontSmaller, FontReset, NextWindow, PreviousWindow, ToggleFullScreen,

    /** Cmd+B: bookmarks the page's text selection; without one it opens the key chooser like Ctrl+B. */
    BookmarkSelection,
}

/**
 * Decides what a DOM-style [key] ("s", ",", "=", "Tab", "F11") with these modifiers means on iOS. Nothing is mapped
 * while [editing] (a text field or the StudyPad editor keeps its keys), with Alt, or with Command and Control together.
 */
internal fun nativeShortcut(
    key: String, ctrl: Boolean, alt: Boolean, shift: Boolean, meta: Boolean, editing: Boolean,
): NativeShortcut? {
    if (editing || alt || (ctrl && meta)) return null
    val k = if (key.length == 1) key.lowercase() else key
    if (meta) return when (k) {
        "s" -> NativeShortcut.SyncNow.takeUnless { shift }
        "," -> NativeShortcut.OpenSettings
        "+", "=" -> NativeShortcut.FontLarger
        "-", "_" -> NativeShortcut.FontSmaller
        "0" -> NativeShortcut.FontReset
        "b" -> NativeShortcut.BookmarkSelection.takeUnless { shift }
        else -> null
    }
    if (ctrl) return if (k == "Tab") {
        if (shift) NativeShortcut.PreviousWindow else NativeShortcut.NextWindow
    } else null
    return if (k == "F11") NativeShortcut.ToggleFullScreen else null
}

internal fun nativeShortcut(event: KeyEvent, editing: Boolean): NativeShortcut? {
    if (event.type != KeyEventType.KeyDown) return null
    val key = when (event.key) {
        Key.S -> "s"
        Key.B -> "b"
        Key.Comma -> ","
        Key.Equals, Key.Plus, Key.NumPadAdd -> "="
        Key.Minus, Key.NumPadSubtract -> "-"
        Key.Zero, Key.NumPad0 -> "0"
        Key.Tab -> "Tab"
        Key.F11 -> "F11"
        else -> return null
    }
    return nativeShortcut(key, event.isCtrlPressed, event.isAltPressed, event.isShiftPressed, event.isMetaPressed, editing)
}
