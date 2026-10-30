package net.bible.sharedui.desktop.webview

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import net.bible.sharedcore.platform.desktop.DesktopDeviceSettings
import net.bible.sharedlogic.platform.desktop.misc.DesktopNightModeState
import net.bible.sharedui.desktop.assembly.DesktopNightModeAdapter
import net.bible.sharedui.host.reading.HostNightModeObserver

/** The live night toggle must reach the Compose theme input ([HostNightModeObserver.dark]), through the real desktop sources. */
class DesktopNightModeObserverTest {
    @Test
    fun manualToggleFromTheOverflowMenuFlipsTheThemeInput() {
        val settings = DesktopDeviceSettings(Files.createTempDirectory("night").resolve("prefs.properties"))
        val source = DesktopNightModeAdapter(DesktopNightModeState(settings))
        var changes = 0
        val observer = HostNightModeObserver(source, DesktopDisplayModeSignals(source, runOnMain = { it() }), restored = { null }) { changes++ }
        observer.start()
        try {
            settings.setString("night_mode_pref3", "manual") // Application preferences: Manual
            assertEquals(false, observer.dark.value)
            settings.setBoolean("night_mode_pref", true) // the overflow menu's "Night mode" (HostReadingToolbarHost.setNightMode)
            assertEquals(true, observer.dark.value)
            assertEquals(1, changes)
            settings.setBoolean("night_mode_pref", false)
            assertEquals(false, observer.dark.value)
        } finally {
            observer.stop()
        }
    }
}
