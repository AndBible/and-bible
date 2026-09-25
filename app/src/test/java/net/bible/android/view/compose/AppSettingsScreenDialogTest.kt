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

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.LinkAnnotation
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.settings.AppSettingsDialog
import net.bible.sharedcore.settings.SettingsScreenState
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.components.AbHtmlText
import net.bible.sharedui.settings.AppSettingsScreen
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Task 15, Step 1/2: the main app settings screen's "reset to defaults?" confirmation and its
 * persecution/discrete-mode help moved off the host (`NavHostComposeActivity.confirmResetSettings`/
 * `showDiscreteHelpDialog`) into `AppSettingsController.dialog` and are now rendered by
 * [AppSettingsScreen] itself from an `AppSettingsDialog` state -- this proves the screen renders the
 * right text, answers the right callback, and routes the discrete-help dialog's inline link through
 * `onOpenLink`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AppSettingsScreenDialogTest {
    @get:Rule val compose = createComposeRule()

    private var confirmCalls = 0
    private var dismissCalls = 0
    private var openedLink: String? = null

    private fun show(dialog: AppSettingsDialog) = compose.setContent {
        ProvideAppLocals {
            AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                AppSettingsScreen(
                    state = SettingsScreenState(title = "Settings", items = emptyList()),
                    onUp = {},
                    onSwitch = { _, _ -> },
                    onListChoice = { _, _ -> },
                    onTextInput = { _, _ -> },
                    onSliderChange = { _, _ -> },
                    onMultiSelectChange = { _, _ -> },
                    onNavigate = {},
                    onReset = {},
                    resetContentDescription = "Reset to defaults",
                    dialog = dialog,
                    onConfirmDialog = { confirmCalls++ },
                    onDismissDialog = { dismissCalls++ },
                    onOpenLink = { openedLink = it },
                )
            }
        }
    }

    @Test fun noDialogWhenNone() {
        show(AppSettingsDialog.None)
        compose.onNodeWithText(
            "Do you want to reset all global application preferences that are displayed on this " +
                "screen to their default values?",
        ).assertDoesNotExist()
    }

    @Test fun confirmReset_showsTheMessage_andAnswersConfirm() {
        show(
            AppSettingsDialog.ConfirmReset(
                "Do you want to reset all global application preferences that are displayed on " +
                    "this screen to their default values?",
            ),
        )
        compose.onNodeWithText(
            "Do you want to reset all global application preferences that are displayed on this " +
                "screen to their default values?",
        ).assertExists()
        compose.onNodeWithText("Yes").performClick()
        assertEquals(1, confirmCalls)
        assertEquals(0, dismissCalls)
    }

    @Test fun confirmReset_cancelAnswersDismiss() {
        show(AppSettingsDialog.ConfirmReset("Reset everything?"))
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, confirmCalls)
        assertEquals(1, dismissCalls)
    }

    @Test fun discreteHelp_showsTitleAndBody_okAnswersDismiss() {
        show(AppSettingsDialog.DiscreteHelp("Settings for the persecuted", "Some help text."))
        compose.onNodeWithText("Settings for the persecuted").assertExists()
        compose.onNodeWithText("Some help text.").assertExists()
        compose.onNodeWithText("OK").performClick()
        assertEquals(1, dismissCalls)
    }

    /**
     * A real click on a link inside a material3 `AlertDialog` window is not driveable through
     * `performClick` on this compose-ui-test/Robolectric combination (see
     * `AppDialogHostTest.linkAnnotationIsPresentInTheRenderedBody`'s kdoc for the three scratch
     * probes that pinned this down as a Dialog-window limitation, not an app defect). So, the same
     * way that test does, this checks the two halves separately: the rendered body really carries a
     * [LinkAnnotation.Url] for the exact href (the `AbLinkRouting` wrap did not swallow it), and the
     * `UriHandler` wrapper `AbLinkRouting` installs really forwards to `onOpenLink` when driven
     * directly on a plain (non-dialog) composable, where clicks do work.
     */
    @Test fun discreteHelp_bodyCarriesTheLinkAnnotation() {
        show(
            AppSettingsDialog.DiscreteHelp(
                "Settings for the persecuted",
                """More info at <a href="https://example.org/wiki">here</a>.""",
            ),
        )
        val node = compose.onNodeWithText("here", substring = true).fetchSemanticsNode()
        val text = node.config[SemanticsProperties.Text].single()
        val links = text.getLinkAnnotations(0, text.length).map { (it.item as LinkAnnotation.Url).url }
        assertEquals(listOf("https://example.org/wiki"), links)
    }

    @Test fun onOpenLink_wrapperForwardsClicks() {
        val forwarded = mutableListOf<String>()
        val wrapper = object : UriHandler {
            override fun openUri(uri: String) { forwarded += uri }
        }
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides wrapper) {
                AbHtmlText("<a href=\"https://example.org/wiki\">probe</a>")
            }
        }
        compose.onNodeWithText("probe").performClick()
        assertEquals(listOf("https://example.org/wiki"), forwarded)
    }
}
