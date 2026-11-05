package net.bible.sharedui.reading

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(androidx.compose.ui.InternalComposeUiApi::class)
class ReadingShortcutsTest {
    @Test fun controlCommandsUseTheExistingBridgeProtocol() {
        for ((code, command) in listOf("KeyF" to "CtrlKeyF", "KeyB" to "CtrlKeyB", "KeyC" to "CtrlKeyC", "KeyW" to "CtrlKeyW")) {
            assertEquals(command, readingShortcut(code, ctrl = true, alt = false))
        }
    }

    @Test fun altCommandsIncludeBothWindowNavigationAxes() {
        for (code in listOf("KeyM", "KeyO", "ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight")) {
            assertEquals("Alt$code", readingShortcut(code, ctrl = false, alt = true))
        }
    }

    @Test fun textEditingKeepsCopyAndBoldButSearchRemainsAvailable() {
        assertNull(readingShortcut("KeyC", ctrl = true, alt = false, editing = true))
        assertNull(readingShortcut("KeyB", ctrl = true, alt = false, editing = true))
        assertEquals("CtrlKeyF", readingShortcut("KeyF", ctrl = true, alt = false, editing = true))
    }

    @Test fun unsupportedModifierCombinationsAreNotConsumed() {
        assertNull(readingShortcut("KeyF", ctrl = false, alt = false))
        assertNull(readingShortcut("KeyF", ctrl = true, alt = true))
        assertNull(readingShortcut("KeyF", ctrl = true, alt = false, meta = true))
        assertNull(readingShortcut("KeyV", ctrl = true, alt = false))
    }

    @Test fun composeKeyDownAndUIKitCodeUseTheSameMapping() {
        assertEquals("CtrlKeyF", readingShortcut(KeyEvent(Key.F, KeyEventType.KeyDown, isCtrlPressed = true)))
        assertNull(readingShortcut(KeyEvent(Key.F, KeyEventType.KeyUp, isCtrlPressed = true)))
        assertNull(readingShortcut(KeyEvent(Key.F, KeyEventType.KeyDown)))
    }
}
