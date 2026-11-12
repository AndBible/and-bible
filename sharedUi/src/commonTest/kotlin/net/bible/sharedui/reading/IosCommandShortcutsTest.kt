package net.bible.sharedui.reading

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosCommandShortcutsTest {
    @Test fun commandFindAndBookmarkReuseControlCommandsOnIosOnly() {
        for ((code, expected) in listOf("KeyF" to "CtrlKeyF", "KeyB" to "CtrlKeyB")) {
            assertEquals(expected, readingShortcut(code, ctrl = false, alt = false, meta = true, iosCommandKeys = true))
            assertNull(readingShortcut(code, ctrl = false, alt = false, meta = true))
            assertEquals(expected, readingShortcut(code, ctrl = true, alt = false))
        }
    }

    @Test fun commandDoesNotClaimOtherCommandsOrMixedModifiers() {
        for (code in listOf("KeyG", "BracketLeft", "BracketRight", "KeyD", "Comma", "KeyC", "KeyW")) {
            assertNull(readingShortcut(code, ctrl = false, alt = false, meta = true, iosCommandKeys = true))
        }
        assertNull(readingShortcut("KeyF", ctrl = true, alt = false, meta = true, iosCommandKeys = true))
        assertNull(readingShortcut("KeyF", ctrl = false, alt = true, meta = true, iosCommandKeys = true))
    }

    @Test fun commandBoldRemainsWithTheEditor() {
        assertNull(readingShortcut("KeyB", ctrl = false, alt = false, meta = true, editing = true, iosCommandKeys = true))
        assertEquals("CtrlKeyF", readingShortcut("KeyF", ctrl = false, alt = false, meta = true, editing = true, iosCommandKeys = true))
    }
}
