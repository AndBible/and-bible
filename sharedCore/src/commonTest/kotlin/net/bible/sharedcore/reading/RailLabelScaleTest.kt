package net.bible.sharedcore.reading

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RailLabelScaleTest {
    @Test
    fun `labels that already fit are not scaled`() {
        assertEquals(1f, railLabelFontScale(availablePx = 60f, requiredPx = 55f))
    }

    @Test
    fun `labels exactly filling the box are not scaled`() {
        assertEquals(1f, railLabelFontScale(availablePx = 55f, requiredPx = 55f))
    }

    @Test
    fun `labels taller than the box shrink proportionally`() {
        assertEquals(0.5f, railLabelFontScale(availablePx = 30f, requiredPx = 60f))
    }

    @Test
    fun `a zero requirement never divides by zero`() {
        assertEquals(1f, railLabelFontScale(availablePx = 30f, requiredPx = 0f))
        assertEquals(1f, railLabelFontScale(availablePx = 30f, requiredPx = -5f))
    }

    @Test
    fun `a zero-height box clamps to zero rather than going negative`() {
        assertTrue(railLabelFontScale(availablePx = 0f, requiredPx = 60f) == 0f)
        assertTrue(railLabelFontScale(availablePx = -4f, requiredPx = 60f) == 0f)
    }
}
