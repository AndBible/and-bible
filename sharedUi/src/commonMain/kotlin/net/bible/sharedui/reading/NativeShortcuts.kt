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
import androidx.compose.ui.input.key.utf16CodePoint

/**
 * Reading shortcuts that need a native action instead of a BibleView key (#4177): the iOS counterparts of the desktop's
 * `DesktopShortcuts` table (Command in place of Ctrl). They run the shared `HostShortcutActions` the desktop uses.
 */
internal enum class NativeShortcut {
    SyncNow, OpenSettings, FontLarger, FontSmaller, FontReset, NextWindow, PreviousWindow, ToggleFullScreen,

    /** Cmd+B: bookmarks the page's text selection; without one it opens the key chooser like Ctrl+B. */
    BookmarkSelection,

    /** Cmd+Y (Safari's History, #4204): the History sheet, like desktop's Ctrl+H / Ctrl+Y. */
    ShowHistory,
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
        "y" -> NativeShortcut.ShowHistory.takeUnless { shift }
        else -> null
    }
    if (ctrl) return if (k == "Tab") {
        if (shift) NativeShortcut.PreviousWindow else NativeShortcut.NextWindow
    } else null
    return if (k == "F11") NativeShortcut.ToggleFullScreen else null
}

/**
 * The DOM-style key a chord means, from the PRINTED character, not the key position: a Finnish/Swedish keyboard has "+"
 * where a US one has "-", so Cmd++ must follow the character (desktop reads the character too). [codePoint] is the
 * event's character (0 or a control code when unknown). [key] names Tab/F11, and is the fallback only when no printable
 * character is known, for the numpad, or for Cmd+S/B on a layout whose letters are not Latin (Cyrillic).
 */
internal fun nativeDomKey(key: Key, codePoint: Int): String? {
    if (key == Key.Tab) return "Tab"
    if (key == Key.F11) return "F11"
    val positionLetter = when (key) { Key.S -> "s"; Key.B -> "b"; else -> null }
    if (codePoint in 32..0xFFFF) {
        return nativeDomKeyOfCharacter(codePoint.toChar().toString(), key == Key.S, key == Key.B)
    }
    // No printable character known: only layout-independent keys (numpad) and the S/B letters map. Punctuation and the
    // digit row are never mapped from a position (a Nordic "+" sits on the US Key.Minus position), so no action beats the wrong one.
    return positionLetter ?: when (key) {
        Key.NumPadAdd -> "+"
        Key.NumPadSubtract -> "-"
        Key.NumPad0 -> "0"
        else -> null
    }
}

/**
 * The shortcut key for a typed [character] (UIKey.charactersIgnoringModifiers on iOS). A non-Latin letter falls back to
 * the S/B key position ([onKeyS], [onKeyB]); other punctuation is never mapped from a position.
 */
internal fun nativeDomKeyOfCharacter(character: String, onKeyS: Boolean, onKeyB: Boolean): String? {
    if (character.length != 1) return null
    val c = character[0].lowercaseChar()
    return when {
        c in 'a'..'z' || c in NATIVE_PUNCTUATION -> c.toString()
        c.isLetter() -> if (onKeyS) "s" else if (onKeyB) "b" else null
        else -> null
    }
}

private const val NATIVE_PUNCTUATION = ",+=-_0"

internal fun nativeShortcut(event: KeyEvent, editing: Boolean): NativeShortcut? {
    if (event.type != KeyEventType.KeyDown) return null
    val key = nativeDomKey(event.key, event.utf16CodePoint) ?: return null
    return nativeShortcut(key, event.isCtrlPressed, event.isAltPressed, event.isShiftPressed, event.isMetaPressed, editing)
}
