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

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.activity.R
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.settings.AppSettingsDialog
import net.bible.sharedcore.settings.SettingsScreenState
import net.bible.sharedui.ProvideAppLocals
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
 * right text, answers the right callback, and (C1) routes the discrete-help dialog's inline link
 * through [askBeforeOpeningLink]/[onOpenExternal].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AppSettingsScreenDialogTest {
    @get:Rule val compose = createComposeRule()

    private var confirmCalls = 0
    private var dismissCalls = 0
    private val opened = mutableListOf<String>()

    private fun show(dialog: AppSettingsDialog, askBeforeOpeningLink: Boolean = false) = compose.setContent {
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
                    askBeforeOpeningLink = askBeforeOpeningLink,
                    onOpenExternal = { opened += it },
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

    /** C1: a real click on the discrete-help link, driven through the real `AbLinkRouting`-wrapped
     *  dialog, reaches [onOpenExternal] directly (see `AppDialogHostTest
     *  .linkTapInsideTheDialogWindowReachesTheHost`'s kdoc for why this is now real coverage rather
     *  than the "Dialog-window limitation" this replaces). */
    @Test fun discreteHelp_linkTapReachesOnOpenExternal() {
        // The body is JUST the link (rather than a link inside a longer sentence, as
        // `discreteHelp_bodyCarriesTheLinkAnnotation` used to build it): `performClick()` clicks the
        // CENTER of the whole Text node, which only lands on the link's own glyphs when the link is
        // the only content -- a real click on a link mid-sentence needs its own bounding box (see
        // `EpubSearchScreenHelpDialogTest`'s equivalent test, whose HTML is fixed production content
        // and so can't be simplified this way).
        show(AppSettingsDialog.DiscreteHelp("Settings for the persecuted", """<a href="https://example.org/wiki">here</a>"""))
        compose.onNodeWithText("here").performClick()
        assertEquals(listOf("https://example.org/wiki"), opened)
    }

    /** C1: with [askBeforeOpeningLink], the same tap asks first -- OK opens it exactly once, and the
     *  help dialog stays open underneath, untouched. The help dialog's own confirm button is ALSO
     *  labelled "OK" (both reuse `strings.okay`), so the question's button is picked out by taking
     *  the LAST match: `AbLinkRouting` draws the question's window strictly after the help dialog's
     *  own, same "later window sits on top" mechanism the production fix relies on. */
    @Test fun discreteHelp_askBeforeOpeningLink_asksThenOpensOnce() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        show(
            AppSettingsDialog.DiscreteHelp("Settings for the persecuted", """<a href="https://example.org/wiki">here</a>"""),
            askBeforeOpeningLink = true,
        )
        compose.onNodeWithText("here").performClick()
        compose.onNodeWithText(context.getString(R.string.external_link)).assertExists()
        assertEquals(emptyList<String>(), opened)

        val okNodes = compose.onAllNodesWithText(context.getString(R.string.okay))
        okNodes[okNodes.fetchSemanticsNodes().size - 1].performClick()
        assertEquals(listOf("https://example.org/wiki"), opened)
        assertEquals(0, dismissCalls) // the help dialog itself is untouched by the link question
    }
}
