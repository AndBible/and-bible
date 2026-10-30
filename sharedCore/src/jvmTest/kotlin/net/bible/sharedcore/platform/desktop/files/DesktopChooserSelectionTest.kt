package net.bible.sharedcore.platform.desktop.files

import net.bible.sharedcore.platform.desktop.dbus.DesktopDbus
import net.bible.sharedcore.platform.desktop.dbus.PortalFileChooserBackend
import kotlin.test.Test
import kotlin.test.assertIs

class DesktopChooserSelectionTest {
    @Test
    fun portalChosenWhenAvailable() {
        val dbus = object : DesktopDbus() { override fun portalAvailable() = true }
        assertIs<PortalFileChooserBackend>(desktopFileChooser(dbus))
    }

    @Test
    fun awtWhenNoPortal() {
        val dbus = object : DesktopDbus() { override fun portalAvailable() = false }
        assertIs<AwtFileChooserBackend>(desktopFileChooser(dbus))
    }
}
