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

    @Test fun scaleBindingIsAbsentOnUnsupportedHosts() {
        val keys = listOf("os.name", "compose.layers.type", "sun.java2d.uiScale", "sun.java2d.uiScale.enabled")
        val original = keys.associateWith(System::getProperty)
        val directory = Files.createTempDirectory("ui-scale-binding")
        try {
            System.setProperty("sun.java2d.uiScale.enabled", "true")
            for (os in listOf("Mac OS X", "FreeBSD", "Linux", "GNU/Linux", "Windows 11")) {
                System.setProperty("os.name", os)
                System.setProperty("compose.layers.type", "WINDOW")
                System.setProperty("sun.java2d.uiScale", "2")
                val app = koinApplication {
                    modules(module { single { DesktopDeviceSettings(directory.resolve("settings")) } },
                        net.bible.sharedui.desktop.assembly.desktopMiscModule())
                }
                try {
                    val setting = app.koin.getOrNull<DesktopUiScaleSetting>()
                    assertEquals(os in listOf("Linux", "GNU/Linux", "Windows 11"), setting != null, os)
                    assertEquals(if (os in listOf("Linux", "GNU/Linux")) 200 else null, setting?.effectivePercent)
                    // The detector writes this JDK property too; it is not evidence of a user override.
                    if (setting != null) assertFalse(setting.overriddenThisSession)
                } finally { app.close() }
                System.setProperty("sun.java2d.uiScale.enabled", "false")
                val disabled = koinApplication {
                    modules(module { single { DesktopDeviceSettings(directory.resolve("settings")) } },
                        net.bible.sharedui.desktop.assembly.desktopMiscModule(uiScaleOverridden = true))
                }
                try {
                    val setting = disabled.koin.getOrNull<DesktopUiScaleSetting>()
                    assertEquals(if (os in listOf("Linux", "GNU/Linux")) 100 else null, setting?.effectivePercent)
                    if (setting != null) assertEquals(true, setting.overriddenThisSession)
                } finally { disabled.close() }
                System.setProperty("sun.java2d.uiScale.enabled", "true")
            }
        } finally {
            original.forEach { (key, value) -> if (value == null) System.clearProperty(key) else System.setProperty(key, value) }
            directory.toFile().deleteRecursively()
        }
    }

    @Test fun windowedLinuxSummaryKeepsChosenPresetAlongsideSessionScale() {
        for (dark in listOf(false, true)) for (mode in listOf(DisplayColorMode.NORMAL, DisplayColorMode.MONOCHROME)) {
            checkWindowedSummary(dark, mode)
        }
    }

    private fun checkWindowedSummary(dark: Boolean, mode: DisplayColorMode) {
        val keys = listOf("os.name", "compose.layers.type", "sun.java2d.uiScale", "sun.java2d.uiScale.enabled")
        val original = keys.associateWith(System::getProperty)
        val directory = Files.createTempDirectory("ui-scale-effective")
        System.setProperty("os.name", "Linux")
        System.setProperty("sun.java2d.uiScale.enabled", "true")
        System.setProperty("compose.layers.type", "WINDOW")
        System.setProperty("sun.java2d.uiScale", "2")
        val app = koinApplication {
            modules(module { single { DesktopDeviceSettings(directory.resolve("settings")) } },
                net.bible.sharedui.desktop.assembly.desktopMiscModule())
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
                onNodeWithText("100% (this session: 200%)").assertExists()
                onNodeWithText("UI scale").performClick()
                onNodeWithText("75%").performClick()
                onNodeWithText("75% (this session: 200%)").assertExists()
                assertEquals(75, app.koin.get<DesktopUiScaleSetting>().percent)
                val layouts = mutableListOf<TextLayoutResult>()
                onNodeWithText("75% (this session: 200%)", useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertEquals(1, layouts.size)
                assertFalse(layouts.single().isLineEllipsized(0), "Effective scale must not be ellipsized")
                assertEquals(layouts.single().layoutInput.text.length, layouts.single().getLineEnd(0, visibleEnd = true),
                    "Effective scale must fit on the visible summary line")
                System.getenv("UI_SCALE_EVIDENCE_DIR")?.let { directory ->
                    val file = File(directory, "effective-scale-${if (dark) "dark" else "light"}-${mode.name.lowercase()}.png")
                    file.parentFile.mkdirs()
                    val image = Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap())
                    file.writeBytes(checkNotNull(image.encodeToData()).bytes)
                }
            }
        } finally {
            app.close()
            original.forEach { (key, value) -> if (value == null) System.clearProperty(key) else System.setProperty(key, value) }
            directory.toFile().deleteRecursively()
        }
    }

    @Test fun explicitOverrideIsExplainedWithoutHidingChosenPreset() {
        checkOverrideSummary(null, "125% (overridden this session)", "75% (overridden this session)")
        checkOverrideSummary(200, "125% (overridden this session: 200%)", "75% (overridden this session: 200%)")
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
                onNodeWithText(before).assertExists()
                val initialLayouts = mutableListOf<TextLayoutResult>()
                onNodeWithText(before, useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(initialLayouts) }
                assertFalse(initialLayouts.single().isLineEllipsized(0))
                assertEquals(before.length, initialLayouts.single().getLineEnd(0, visibleEnd = true))
                onNodeWithText("UI scale").performClick()
                onNodeWithText("75%").performClick()
                onNodeWithText(after).assertExists()
                val layouts = mutableListOf<TextLayoutResult>()
                onNodeWithText(after, useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertEquals(75, app.koin.get<DesktopUiScaleSetting>().percent)
                System.getenv("UI_SCALE_EVIDENCE_DIR")?.let { directory ->
                    val file = File(directory, "scale-overridden-${effectivePercent ?: "fractional"}.png")
                    file.parentFile.mkdirs()
                    val image = Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap())
                    file.writeBytes(checkNotNull(image.encodeToData()).bytes)
                }
                val layout = layouts.single()
                assertFalse(layout.isLineEllipsized(0), "Override and session value must not be ellipsized")
                assertEquals(after.length, layout.getLineEnd(0, visibleEnd = true),
                    "Override and session value must fit on the visible summary line")
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
