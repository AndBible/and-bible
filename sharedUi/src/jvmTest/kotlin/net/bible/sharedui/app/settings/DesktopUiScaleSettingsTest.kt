package net.bible.sharedui.app.settings

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import kotlin.test.assertFalse
import org.jetbrains.skia.Image
import java.io.File
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedcore.platform.desktop.DesktopDeviceSettings
import net.bible.sharedcore.settings.SettingsScreenState
import net.bible.service.common.DisplayColorMode
import net.bible.sharedui.desktop.settings.PersistentDesktopUiScaleSetting
import net.bible.sharedui.settings.AppSettingsScreen
import net.bible.sharedui.settings.DesktopUiScaleSetting
import net.bible.sharedui.settings.DESKTOP_UI_SCALE_PRESETS
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
            for (percent in listOf(50, 60, 67, 75, 80, 90, 100, 110, 125, 150, 175, 200)) {
                setting.setPercent(percent)
                assertEquals(percent, PersistentDesktopUiScaleSetting(DesktopDeviceSettings(path)).percent)
                val disk = Properties().apply { Files.newBufferedReader(path).use { load(it) } }
                assertEquals(percent.toString(), disk.getProperty("desktop_ui_scale_percent"))
            }
            assertFailsWith<IllegalArgumentException> { setting.setPercent(55) }
            assertEquals(200, setting.percent)
            prefs.putString("desktop_ui_scale_percent", "invalid")
            assertEquals(100, setting.percent)
            prefs.putString("desktop_ui_scale_percent", "55")
            assertEquals(100, setting.percent)
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }
    }

    @Test fun presetsAreOrderedAndDistinct() {
        assertEquals(listOf(50, 60, 67, 75, 80, 90, 100, 110, 125, 150, 175, 200), DESKTOP_UI_SCALE_PRESETS)
    }

    @Test fun scaleBindingIsAbsentOnUnsupportedHosts() {
        val properties = mapOf("compose.layers.type" to "WINDOW", "sun.java2d.uiScale" to "2")
        val directory = Files.createTempDirectory("ui-scale-binding")
        try {
            for (os in listOf("Mac OS X", "FreeBSD", "Linux", "GNU/Linux", "Windows 11")) {
                val app = koinApplication {
                    modules(module { single { DesktopDeviceSettings(directory.resolve("settings")) } },
                        net.bible.sharedui.desktop.assembly.desktopMiscModule(os = os, property = properties::get))
                }
                try {
                    val setting = app.koin.getOrNull<DesktopUiScaleSetting>()
                    assertEquals(os in listOf("Linux", "GNU/Linux", "Windows 11"), setting != null, os)
                    assertEquals(if (os in listOf("Linux", "GNU/Linux")) 200 else null, setting?.effectivePercent)
                    // The detector writes this JDK property too; it is not evidence of a user override.
                    if (setting != null) assertFalse(setting.overriddenThisSession)
                } finally { app.close() }
                val disabledProperties = properties + ("sun.java2d.uiScale.enabled" to "false")
                val disabled = koinApplication {
                    modules(module { single { DesktopDeviceSettings(directory.resolve("settings")) } },
                        net.bible.sharedui.desktop.assembly.desktopMiscModule(
                            uiScaleOverridden = true, os = os, property = disabledProperties::get))
                }
                try {
                    val setting = disabled.koin.getOrNull<DesktopUiScaleSetting>()
                    assertEquals(if (os in listOf("Linux", "GNU/Linux")) 100 else null, setting?.effectivePercent)
                    if (setting != null) assertEquals(true, setting.overriddenThisSession)
                } finally { disabled.close() }
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test fun windowedLinuxSummaryKeepsChosenPresetAlongsideSessionScale() {
        for (dark in listOf(false, true)) for (mode in listOf(DisplayColorMode.NORMAL, DisplayColorMode.MONOCHROME)) {
            checkWindowedSummary(dark, mode)
        }
    }

    private fun checkWindowedSummary(dark: Boolean, mode: DisplayColorMode) {
        val properties = mapOf("compose.layers.type" to "WINDOW", "sun.java2d.uiScale" to "2")
        val directory = Files.createTempDirectory("ui-scale-effective")
        val app = koinApplication {
            modules(module { single { DesktopDeviceSettings(directory.resolve("settings")) } },
                net.bible.sharedui.desktop.assembly.desktopMiscModule(os = "Linux", property = properties::get))
        }
        try {
            runComposeUiTest {
                setContent {
                    CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                        AbTheme(darkTheme = dark, colorMode = mode, disableAnimations = true) {
                            AppSettingsScreen(SettingsScreenState("Settings", emptyList()), null, { _, _ -> }, { _, _ -> },
                                { _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, "Reset", settingsKoin = app.koin)
                        }
                    }
                }
                onNode(hasText("UI scale") and hasText("Restart required.", substring = true) and
                    hasText("Current value: 100% (this session: 200%)", substring = true)).assertExists()
                onNodeWithText("UI scale").performClick()
                onNodeWithText("75%").performClick()
                onNodeWithText("Current value: 75% (this session: 200%)", substring = true).assertExists()
                assertEquals(75, app.koin.get<DesktopUiScaleSetting>().percent)
                val layouts = mutableListOf<TextLayoutResult>()
                onNodeWithText("Current value: 75% (this session: 200%)", substring = true, useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertEquals(1, layouts.size)
                assertFalse((0 until layouts.single().lineCount).any { layouts.single().isLineEllipsized(it) },
                    "Effective scale must not be ellipsized")
                System.getenv("UI_SCALE_EVIDENCE_DIR")?.let { directory ->
                    val file = File(directory, "effective-scale-${if (dark) "dark" else "light"}-${mode.name.lowercase()}.png")
                    file.parentFile.mkdirs()
                    val image = Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap())
                    file.writeBytes(checkNotNull(image.encodeToData()).bytes)
                }
            }
        } finally {
            app.close()
            directory.toFile().deleteRecursively()
        }
    }

    @Test fun explicitOverrideIsExplainedWithoutHidingChosenPreset() {
        checkOverrideSummary(null, "Current value: 125% (overridden this session)", "Current value: 75% (overridden this session)")
        checkOverrideSummary(200, "Current value: 125% (overridden this session: 200%)", "Current value: 75% (overridden this session: 200%)")
    }

    private fun checkOverrideSummary(effectivePercent: Int?, before: String, after: String) {
        val directory = Files.createTempDirectory("ui-scale-override")
        val app = koinApplication {
            modules(module {
                single<DesktopUiScaleSetting> {
                    PersistentDesktopUiScaleSetting(DesktopDeviceSettings(directory.resolve("settings")),
                        effectivePercent = effectivePercent, overriddenThisSession = true)
                }
            })
        }
        app.koin.get<DesktopUiScaleSetting>().setPercent(125)
        try {
            runComposeUiTest {
                setContent {
                    CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                        AbTheme(darkTheme = false, disableAnimations = true) {
                            AppSettingsScreen(SettingsScreenState("Settings", emptyList()), null, { _, _ -> }, { _, _ -> },
                                { _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, "Reset", settingsKoin = app.koin)
                        }
                    }
                }
                onNodeWithText(before, substring = true).assertExists()
                val initialLayouts = mutableListOf<TextLayoutResult>()
                onNodeWithText(before, substring = true, useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(initialLayouts) }
                assertFalse((0 until initialLayouts.single().lineCount).any { initialLayouts.single().isLineEllipsized(it) })
                onNodeWithText("UI scale").performClick()
                onNodeWithText("75%").performClick()
                onNodeWithText(after, substring = true).assertExists()
                val layouts = mutableListOf<TextLayoutResult>()
                onNodeWithText(after, substring = true, useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertEquals(75, app.koin.get<DesktopUiScaleSetting>().percent)
                System.getenv("UI_SCALE_EVIDENCE_DIR")?.let { directory ->
                    val file = File(directory, "scale-overridden-${effectivePercent ?: "fractional"}.png")
                    file.parentFile.mkdirs()
                    val image = Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap())
                    file.writeBytes(checkNotNull(image.encodeToData()).bytes)
                }
                val layout = layouts.single()
                assertFalse((0 until layout.lineCount).any { layout.isLineEllipsized(it) },
                    "Override and session value must not be ellipsized")
            }
        } finally {
            app.close()
            directory.toFile().deleteRecursively()
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
                    onNode(hasText("UI scale") and hasText("Restart required.", substring = true) and
                        hasText("Current value: ${setting.percent}%", substring = true)).assertExists()
                    onAllNodesWithText("Restart required.", substring = true).assertCountEquals(1)
                    for (percent in listOf(50, 60)) {
                        onNodeWithText("UI scale").assertExists().performClick()
                        onNodeWithText("$percent%").assertExists().performClick()
                        onNodeWithText("Current value: $percent%", substring = true).assertExists()
                        assertEquals(percent, setting.percent)
                    }
                    onNodeWithText("UI scale").performClick()
                    onNodeWithText("150%").performScrollTo().assertIsDisplayed().performClick()
                    runOnIdle {
                        assertEquals(150, setting.percent)
                        val disk = Properties().apply { Files.newBufferedReader(path).use { load(it) } }
                        assertEquals("150", disk.getProperty("desktop_ui_scale_percent"))
                        prefs.putInt("desktop_ui_scale_percent", 175)
                    }
                    onNodeWithText("Current value: 175%", substring = true).assertExists()
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
