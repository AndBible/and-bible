package net.bible.sharedui.reading

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint

internal actual val searchFieldUsesCommandKeys: Boolean = false

internal actual fun searchResultKeyCharacter(event: KeyEvent): String? {
    if (event.type != KeyEventType.KeyDown || event.isCtrlPressed || event.isMetaPressed) return null
    val code = event.utf16CodePoint
    return if (code in 32 until 0xffff && code != 127) code.toChar().toString() else null
}
