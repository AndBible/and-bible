package net.bible.android.view.activity.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemBarPolicyTest {
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()
    private val midGrey = 0xFF808080.toInt()

    @Test fun defaultsShowBothBars() =
        assertEquals(SystemBarState(true, true, null), decideSystemBars(true, false, false, null, null))

    // F77: the setting hides the status bar on EVERY destination, not only on reading.
    @Test fun hideStatusBarAppliesEverywhere() {
        for (onReading in listOf(true, false)) {
            val s = decideSystemBars(onReading, hideStatusBar = true, fullScreen = false, topBarArgb = null, pageBackgroundArgb = null)
            assertFalse("onReading=$onReading", s.statusVisible)
            assertTrue(s.navVisible)
            assertTrue("swipe reveals it transiently", s.transientBySwipe)
        }
    }

    // Fullscreen is a reading-view mode: it must not leak a hidden nav bar into other screens.
    @Test fun fullScreenAppliesOnlyOnReading() {
        val reading = decideSystemBars(true, false, fullScreen = true, topBarArgb = null, pageBackgroundArgb = null)
        assertFalse(reading.statusVisible); assertFalse(reading.navVisible); assertTrue(reading.transientBySwipe)
        val elsewhere = decideSystemBars(false, false, fullScreen = true, topBarArgb = null, pageBackgroundArgb = null)
        assertEquals(SystemBarState(true, true, null), elsewhere)
        assertFalse(elsewhere.transientBySwipe)
    }

    // F71 (verifiable half): icons follow what is actually under the bar.
    @Test fun iconAppearanceFollowsTheSurfaceUnderTheBar() {
        assertEquals(true, decideSystemBars(false, false, false, topBarArgb = white, pageBackgroundArgb = black).lightStatusIcons)
        assertEquals(false, decideSystemBars(false, false, false, topBarArgb = black, pageBackgroundArgb = white).lightStatusIcons)
        // fullscreen reading: the page is under the (transient) bar, not the toolbar
        assertEquals(true, decideSystemBars(true, false, true, topBarArgb = black, pageBackgroundArgb = white).lightStatusIcons)
        assertEquals(false, decideSystemBars(true, false, true, topBarArgb = white, pageBackgroundArgb = black).lightStatusIcons)
        // nothing known: leave the appearance alone
        assertNull(decideSystemBars(true, false, true, topBarArgb = white, pageBackgroundArgb = null).lightStatusIcons)
    }

    // Same 0.45 threshold as SystemBarSync; monochrome is pure black/white, which must stay decisive.
    @Test fun luminanceThreshold() {
        assertTrue(backgroundIsLight(white)); assertFalse(backgroundIsLight(black)); assertFalse(backgroundIsLight(midGrey))
    }
}
