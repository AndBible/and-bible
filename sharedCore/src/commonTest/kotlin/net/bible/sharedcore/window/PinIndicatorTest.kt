package net.bible.sharedcore.window

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PinIndicatorTest {
    @Test
    fun `a pinned window shows the indicator`() {
        assertTrue(shouldShowPinIndicator(isPinMode = true, autoPin = false, isMaximised = false))
    }

    @Test
    fun `an unpinned window shows nothing`() {
        assertFalse(shouldShowPinIndicator(isPinMode = false, autoPin = false, isMaximised = false))
    }

    @Test
    fun `autoPin suppresses the indicator even though every window reports pinned`() {
        assertFalse(shouldShowPinIndicator(isPinMode = true, autoPin = true, isMaximised = false))
    }

    @Test
    fun `a maximised window shows nothing`() {
        assertFalse(shouldShowPinIndicator(isPinMode = true, autoPin = false, isMaximised = true))
    }
}
