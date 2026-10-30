package net.bible.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class ComposeLayersTest {
    @Test
    fun windowedPanesNeedWindowLayers() {
        assertEquals("WINDOW", composeLayersTypeFor(windowed = true))
    }

    @Test
    fun offScreenPanesKeepPopupsInWindow() {
        assertEquals(null, composeLayersTypeFor(windowed = false))
    }
}
