package net.bible.sharedui.desktop.assembly

import kotlin.test.Test
import kotlin.test.assertFalse

/** The desktop has no speech backend, and says so through the one capability seam every speak entry reads. */
class DesktopSpeakCapabilityTest {
    @Test fun desktopSpeakPlatformIsNotSupported() {
        assertFalse(DesktopNoSpeakPlatform().isSupported)
    }
}
