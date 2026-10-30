package net.bible.sharedui.desktop.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopDistributionTest {
    @Test fun packageVersionRequiresValidBuildNumber() {
        assertEquals(1232, desktopBuildNumber("6.0.1232"))
        assertEquals(null, desktopBuildNumber("dev"))
        assertEquals(null, desktopBuildNumber("6.0.invalid"))
        assertEquals(null, desktopBuildNumber("6.0.0"))
    }

    @Test fun detectsEachDistribution() {
        assertEquals(DesktopDistribution.FLATPAK, detectDistribution(mapOf("FLATPAK_ID" to "x", "APPIMAGE" to "x"), "resources", null))
        assertEquals(DesktopDistribution.APPIMAGE, detectDistribution(mapOf("APPIMAGE" to "x"), "resources", null))
        assertEquals(DesktopDistribution.MSIX, detectDistribution(emptyMap(), "resources", "C:\\Program Files\\WindowsApps\\AndBible"))
        assertEquals(DesktopDistribution.GITHUB_PACKAGE, detectDistribution(emptyMap(), "resources", null))
        assertEquals(DesktopDistribution.DEV, detectDistribution(emptyMap(), null, null))
        assertTrue(DesktopDistribution.APPIMAGE.checksForUpdates)
        assertTrue(DesktopDistribution.GITHUB_PACKAGE.checksForUpdates)
        assertFalse(DesktopDistribution.FLATPAK.checksForUpdates)
        assertFalse(DesktopDistribution.MSIX.checksForUpdates)
        assertFalse(DesktopDistribution.DEV.checksForUpdates)
    }
}
