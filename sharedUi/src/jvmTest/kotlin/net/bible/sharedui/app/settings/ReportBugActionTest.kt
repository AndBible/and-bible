package net.bible.sharedui.app.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import net.bible.sharedui.settings.AppSettingsScreen
import net.bible.sharedcore.settings.SettingsScreenState
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ReportBugActionTest {
    @Test fun reportActionAbsentByDefaultAndInvokesProvidedCallback() = runComposeUiTest {
        var calls = 0
        var enabled by androidx.compose.runtime.mutableStateOf(false)
        setContent {
            CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                AppSettingsScreen(SettingsScreenState("Settings", emptyList()), null, { _, _ -> }, { _, _ -> }, { _, _ -> },
                    { _, _ -> }, { _, _ -> }, {}, {}, "Reset", onReportBug = if (enabled) ({ calls++ }) else null)
            }
        }
        onNodeWithText(iosStrings("en").reportGeneralBugLabel).assertDoesNotExist()
        runOnIdle { enabled = true }
        onNodeWithText(iosStrings("en").reportGeneralBugLabel).assertExists().performClick()
        runOnIdle { assertEquals(1, calls) }
    }
}
