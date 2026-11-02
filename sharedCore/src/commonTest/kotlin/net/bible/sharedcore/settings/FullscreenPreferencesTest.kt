package net.bible.sharedcore.settings

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FullscreenPreferencesTest {
    @Test fun desktopAndMacDefaultsAreOff() {
        assertFalse(doubleTapToFullscreenDefaultFor(isDesktop = true, isIosAppOnMac = false))
        assertFalse(doubleTapToFullscreenDefaultFor(isDesktop = false, isIosAppOnMac = true))
    }

    @Test fun androidIphoneAndIpadDefaultIsOn() {
        assertTrue(doubleTapToFullscreenDefaultFor(isDesktop = false, isIosAppOnMac = false))
    }
}
