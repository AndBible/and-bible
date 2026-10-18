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
package net.bible.android.view.compose

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.startup.StartupWelcomeState
import net.bible.sharedcore.startup.StartupWelcomeTab
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.startup.StartupWelcomeScreen
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class StartupWelcomeScreenTest {
    @get:Rule val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun state(
        tabs: Boolean = true,
        tab: StartupWelcomeTab = StartupWelcomeTab.EASY,
        prev: Boolean = false,
        progress: String? = null,
    ) = StartupWelcomeState(
        versionText = "Version: 5.1",
        supportedFormatsText = "Supported formats: AndBible zip, MyBible, MySword, EPUB",
        showTabs = tabs,
        selectedTab = if (tabs) tab else StartupWelcomeTab.ADVANCED,
        showRedownload = prev,
        showRedownloadHint = tabs && prev,
        progressText = progress,
    )

    private var scheme: ColorScheme? = null

    private fun show(
        s: StartupWelcomeState,
        dark: Boolean = false,
        mode: DisplayColorMode = DisplayColorMode.NORMAL,
    ) = compose.setContent {
        ProvideAppLocals {
            AbTheme(darkTheme = dark, colorMode = mode, disableAnimations = true) {
                scheme = MaterialTheme.colorScheme
                StartupWelcomeScreen(
                    state = s, appName = "AndBible", logo = null,
                    onSelectTab = { calls += "tab:$it" },
                    onDownload = { calls += "download" }, onImport = { calls += "import" },
                    onRestore = { calls += "restore" }, onRedownload = { calls += "redownload" },
                    onEasyStart = { calls += "easy" },
                    onOpenHomepage = { calls += "home" }, onOpenGithub = { calls += "github" },
                )
            }
        }
    }

    @Test fun easy_quickStart_fires_onEasyStart() {
        show(state())
        compose.onNodeWithText("Download and start").performScrollTo().performClick()
        assertEquals(listOf("easy"), calls)
    }

    @Test fun easy_hides_advanced_rows() {
        show(state())
        assertEquals(0, compose.onAllNodesWithText("Restore Database File").fetchSemanticsNodes().size)
    }

    @Test fun easyHint_redownload_fires_onRedownload() {
        show(state(prev = true))
        compose.onNodeWithText("Previous documents found").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Redownload").performScrollTo().performClick()
        assertEquals(listOf("redownload"), calls)
    }

    @Test fun advancedTab_click_fires_onSelectTab() {
        show(state())
        compose.onNodeWithText("Advanced").performClick()
        assertEquals(listOf("tab:ADVANCED"), calls)
    }

    @Test fun advanced_rows_fire_their_callbacks() {
        show(state(tab = StartupWelcomeTab.ADVANCED, prev = true))
        for ((label, call) in listOf(
            "Redownload Documents" to "redownload",
            "Download Documents" to "download",
            "Load Documents From Files" to "import",
            "Restore Database File" to "restore",
        )) {
            compose.onNodeWithText(label).performScrollTo().performClick()
            assertEquals(call, calls.last())
        }
    }

    @Test fun nonEnglish_shows_no_tabs_no_quickStart_and_restore() {
        show(state(tabs = false, prev = false))
        assertEquals(0, compose.onAllNodesWithText("Easy").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("Download and start").fetchSemanticsNodes().size)
        compose.onNodeWithText("Restore Database File").performScrollTo().assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Redownload Documents").fetchSemanticsNodes().size)
    }

    @Test fun progress_visible_on_advanced() {
        show(state(tab = StartupWelcomeTab.ADVANCED, progress = "Installing document 1 of 3…"))
        compose.onNodeWithText("Installing document 1 of 3…").assertIsDisplayed()
    }

    private fun textColor(text: String): androidx.compose.ui.graphics.Color {
        val node = compose.onNodeWithText(text).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)!!.action!!(results)
        return results.single().layoutInput.style.color
    }

    private fun assertProgressAndVersionUseThemeColour(dark: Boolean, mode: DisplayColorMode) {
        show(state(tab = StartupWelcomeTab.ADVANCED, progress = "Extracting Zip file now…"), dark, mode)
        val expected = scheme!!.onSurfaceVariant
        assertEquals("progress", expected, textColor("Extracting Zip file now…"))
        assertEquals("version", expected, textColor("Version: 5.1"))
    }

    /** Regression (E6 F-2): progress and version text set no colour and rendered dark-on-dark. */
    @Test fun progress_and_version_text_use_theme_colour_dark() =
        assertProgressAndVersionUseThemeColour(true, DisplayColorMode.NORMAL)

    @Test fun progress_and_version_text_use_theme_colour_light() =
        assertProgressAndVersionUseThemeColour(false, DisplayColorMode.NORMAL)

    @Test fun progress_and_version_text_use_theme_colour_monochrome() =
        assertProgressAndVersionUseThemeColour(true, DisplayColorMode.MONOCHROME)
}
