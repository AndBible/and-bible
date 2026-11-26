package net.bible.sharedui.app.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedcore.platform.desktop.DesktopDeviceSettings
import net.bible.sharedcore.settings.SettingsScreenState
import net.bible.service.common.DisplayColorMode
import net.bible.sharedui.desktop.settings.PersistentDesktopUiScaleSetting
import net.bible.sharedui.settings.AppSettingsScreen
import net.bible.sharedui.settings.DesktopUiScaleSetting
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.nio.file.Files
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalTestApi::class)
class DesktopUiScaleSettingsTest {
    @Test fun persistsPresetsAndRejectsInvalidValues() {
        val directory = Files.createTempDirectory("ui-scale")
        val path = directory.resolve("settings")
        try {
            val prefs = DesktopDeviceSettings(path)
            val setting = PersistentDesktopUiScaleSetting(prefs)
            assertEquals(100, setting.percent)
            for (percent in listOf(75, 100, 125, 150, 175, 200)) {
                setting.setPercent(percent)
                assertEquals(percent, PersistentDesktopUiScaleSetting(DesktopDeviceSettings(path)).percent)
                val disk = Properties().apply { Files.newBufferedReader(path).use { load(it) } }
                assertEquals(percent.toString(), disk.getProperty("desktop_ui_scale_percent"))
            }
            assertFailsWith<IllegalArgumentException> { setting.setPercent(110) }
            assertEquals(200, setting.percent)
            prefs.putString("desktop_ui_scale_percent", "invalid")
            assertEquals(100, setting.percent)
            prefs.putString("desktop_ui_scale_percent", "110")
            assertEquals(100, setting.percent)
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }
    }

    @Test fun rowRequiresDesktopBindingAndStoresChoice() {
        val empty = koinApplication {}
        val directory = Files.createTempDirectory("ui-scale-row")
        val path = directory.resolve("settings")
        val prefs = DesktopDeviceSettings(path)
        val setting = PersistentDesktopUiScaleSetting(prefs)
        val bound = koinApplication { modules(module { single<DesktopUiScaleSetting> { setting } }) }
        try {
            runComposeUiTest {
                setContent {
                    CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                        AbTheme(darkTheme = false) {
                            AppSettingsScreen(SettingsScreenState("Settings", emptyList()), null, { _, _ -> }, { _, _ -> },
                                { _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, "Reset", settingsKoin = empty.koin)
                        }
                    }
                }
                onNodeWithText("UI scale").assertDoesNotExist()
            }
            for (dark in listOf(false, true)) for (mode in listOf(DisplayColorMode.NORMAL, DisplayColorMode.MONOCHROME)) {
                runComposeUiTest {
                    setContent {
                        CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                            AbTheme(darkTheme = dark, colorMode = mode, disableAnimations = true) {
                                AppSettingsScreen(SettingsScreenState("Settings", emptyList()), null, { _, _ -> }, { _, _ -> },
                                    { _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, "Reset", settingsKoin = bound.koin)
                            }
                        }
                    }
                    onNodeWithText("Restart required.", substring = true).assertExists()
                    onNodeWithText("UI scale").assertExists().performClick()
                    onNodeWithText("150%").assertExists().performClick()
                    runOnIdle {
                        assertEquals(150, setting.percent)
                        val disk = Properties().apply { Files.newBufferedReader(path).use { load(it) } }
                        assertEquals("150", disk.getProperty("desktop_ui_scale_percent"))
                        prefs.putInt("desktop_ui_scale_percent", 175)
                    }
                    onNodeWithText("175%", substring = true).assertExists()
                }
            }
        } finally {
            empty.close()
            bound.close()
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }
    }
}
