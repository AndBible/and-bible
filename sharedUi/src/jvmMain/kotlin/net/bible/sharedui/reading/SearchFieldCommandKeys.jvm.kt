package net.bible.sharedui.reading

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint

internal actual val searchFieldUsesCommandKeys: Boolean = false

internal actual fun searchResultKeyCharacter(event: KeyEvent): String? {
    if (event.type != KeyEventType.Unknown || event.isMetaPressed) return null
    // AltGr may report Ctrl+Alt on Windows; its typed event still contains real text.
    if (event.isCtrlPressed && !event.isAltPressed) return null
    val char = event.utf16CodePoint
    return if (char in 32 until 0xffff && char != 127) char.toChar().toString() else null
}
