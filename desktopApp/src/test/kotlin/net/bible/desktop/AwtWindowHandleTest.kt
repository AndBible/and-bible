package net.bible.desktop

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals

class AwtWindowHandleTest {
    /**
     * Found live on Windows (W1): the 1200 x 800 dp default overflowed the work area. At 150 % a 1920 x 1080 laptop has
     * 1280 x ~690 dp above the taskbar, so the window bottom (and bottom sheets) went under the taskbar or off screen.
     */
    @Test fun initialSizeFitsTheWorkArea() {
        // Plenty of room: the default.
        assertEquals(IntSize(1200, 800), AwtWindowHandle.initialSizeDp(Rectangle(0, 0, 2560, 1400)))
        // 1920 x 1080 at 150 %, taskbar 48 px (32 dp): 1280 x 688 dp.
        assertEquals(IntSize(1200, 688), AwtWindowHandle.initialSizeDp(Rectangle(0, 0, 1280, 688)))
        // 1280 x 800 at 150 %: 853 x 501 dp; never below the window's minimum size.
        assertEquals(IntSize(853, AwtWindowHandle.MIN_HEIGHT_DP), AwtWindowHandle.initialSizeDp(Rectangle(0, 0, 853, 501)))
        // Unknown work area (headless): the default.
        assertEquals(IntSize(1200, 800), AwtWindowHandle.initialSizeDp(null))
    }

    /** Centred on the work area, but the title bar never goes above or left of it (it must stay draggable). */
    @Test fun initialPositionKeepsTheTitleBarOnScreen() {
        assertEquals(IntOffset(40, 0), AwtWindowHandle.initialPositionDp(Rectangle(0, 0, 1280, 688), IntSize(1200, 688)))
        assertEquals(IntOffset(0, 0), AwtWindowHandle.initialPositionDp(Rectangle(0, 0, 853, 501), IntSize(853, 600)))
        // A work area that does not start at 0 (taskbar on top or left, second screen).
        assertEquals(IntOffset(110, 50), AwtWindowHandle.initialPositionDp(Rectangle(100, 40, 1220, 820), IntSize(1200, 800)))
        assertEquals(null, AwtWindowHandle.initialPositionDp(null, IntSize(1200, 800)))
    }
}
