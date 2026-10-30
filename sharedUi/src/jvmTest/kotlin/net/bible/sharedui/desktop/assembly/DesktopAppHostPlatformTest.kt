/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.sharedui.desktop.assembly

import androidx.compose.ui.unit.IntSize
import net.bible.service.cloudsync.desktop.desktopSyncModule
import net.bible.sharedcore.platform.desktop.DesktopPaths
import net.bible.sharedcore.settings.DeviceLocalPrefs
import net.bible.sharedlogic.platform.NightModeState
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopAppHostPlatformTest {
    private class FakeWindow(override var sizeDp: IntSize, override val density: Float = 2f) : DesktopWindowHandle {
        override fun toFront() = Unit
    }

    private fun <T> withKoin(block: (Koin) -> T): T {
        val root: Path = Files.createTempDirectory("desktop-host-platform")
        val paths = DesktopPaths({ name -> if (name.startsWith("XDG_")) root.resolve(name).toString() else null }, "Linux", root)
        val app = koinApplication {
            modules(
                desktopPlatformModule(paths, FakeWindow(IntSize(1200, 800)), uiLanguage = "en"),
                desktopReadingModule(), desktopDocumentModule(), desktopMiscModule(), desktopSyncModule(),
            )
        }
        try {
            return block(app.koin)
        } finally {
            app.close()
            root.toFile().deleteRecursively()
        }
    }

    @Test fun windowHandleIsNotFocusedAndVisibleByDefault() {
        val window = object : DesktopWindowHandle {
            override val sizeDp = IntSize(1200, 800)
            override val density = 1f
            override fun toFront() = Unit
        }

        assertFalse(window.isFocusedAndVisible)
    }

    @Test fun toolbarReadsTheNightModeStoreOfTheKoinGraph() = withKoin { koin ->
        val toolbar = DesktopReadingHostPlatform(FakeWindow(IntSize(1200, 800))) { koin }.newToolbarDeviceState()
        val prefs = koin.get<DeviceLocalPrefs>()
        val night = koin.get<NightModeState>()
        prefs.setString("night_mode_pref3", "manual")
        prefs.setBoolean("night_mode_pref", true)
        assertTrue(night.nightMode)
        assertTrue(toolbar.nightModeOn())
        assertTrue(toolbar.nightModeManual())
        // The toolbar writes through the same store the Koin graph reads.
        toolbar.deviceSettings.setBoolean("night_mode_pref", false)
        assertFalse(night.nightMode)
        assertFalse(toolbar.nightModeOn())
        prefs.setString("night_mode_pref3", "system")
        assertFalse(toolbar.nightModeManual())
    }

    @Test fun windowFactsComeFromTheWindow() = withKoin { koin ->
        val window = FakeWindow(IntSize(1200, 800))
        val host = DesktopReadingHostPlatform(window) { koin }
        assertFalse(host.isPortrait())
        window.sizeDp = IntSize(500, 900)
        assertTrue(host.isPortrait())
        assertEquals(2f, host.screenScale())
        assertFalse(host.isRightToLeft())
        assertFalse(host.tiltAvailable())
        assertEquals("dev", host.versionName())
    }

    @Test fun noBackgroundTaskIsEverGranted() = withKoin { koin ->
        val host = DesktopReadingHostPlatform(FakeWindow(IntSize(1, 1))) { koin }
        assertEquals(host.invalidBackgroundTaskId, host.beginBackgroundTask { error("never expires") })
    }
}
