package net.bible.sharedlogic.platform.desktop.misc

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.bible.sharedcore.platform.desktop.DesktopDeviceSettings

class DesktopNightModeStateTest {
    @Test fun nightModeChangeNotifiesSignalsOnce() {
        val settings = DesktopDeviceSettings(Files.createTempDirectory("night").resolve("settings"))
        val state = DesktopNightModeState(settings)
        var changes = 0
        val stop = state.observeChanges { changes++ }
        settings.putString("night_mode_pref3", "manual")
        settings.putBoolean("night_mode_pref", true)
        settings.putBoolean("night_mode_pref", true)
        settings.putString("other", "value")
        assertEquals(2, changes)
        stop()
    }

    @Test fun manualNightModePersists() {
        val path = Files.createTempDirectory("night").resolve("settings")
        val settings = DesktopDeviceSettings(path)
        settings.putString("night_mode_pref3", "manual")
        settings.putBoolean("night_mode_pref", true)
        assertTrue(DesktopNightModeState(DesktopDeviceSettings(path)).nightMode)
    }
}
