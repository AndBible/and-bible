package net.bible.sharedcore.window

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Complete truth table over (isPinMode x autoPin x isMaximised), per spec section 9. Every row
// is named for its own combination even where the outcome is unsurprising, so the table stays
// exhaustive rather than pruned to the "interesting" cases.
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

    @Test
    fun `an unpinned maximised window shows nothing`() {
        assertFalse(shouldShowPinIndicator(isPinMode = false, autoPin = false, isMaximised = true))
    }

    @Test
    fun `an unpinned window under autoPin shows nothing`() {
        assertFalse(shouldShowPinIndicator(isPinMode = false, autoPin = true, isMaximised = false))
    }

    @Test
    fun `an unpinned maximised window under autoPin shows nothing`() {
        assertFalse(shouldShowPinIndicator(isPinMode = false, autoPin = true, isMaximised = true))
    }

    @Test
    fun `a pinned maximised window under autoPin shows nothing`() {
        assertFalse(shouldShowPinIndicator(isPinMode = true, autoPin = true, isMaximised = true))
    }
}
