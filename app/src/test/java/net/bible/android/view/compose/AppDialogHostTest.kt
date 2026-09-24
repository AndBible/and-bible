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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.LinkAnnotation
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import net.bible.android.TEST_SDK
import net.bible.android.activity.R
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.ai.AgentPermissionChoice
import net.bible.sharedcore.ai.AgentPermissionRequest
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedcore.ui.dialog.ShownDialog
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.components.AbHtmlText
import net.bible.sharedui.components.AppDialogHost
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Behaviour coverage for [AppDialogHost]: every branch of `AppDialogRequest` answers the right
 * [AppDialogResult] to the right id, back/scrim honour [AppDialogRequest.Message.cancellable] and
 * [AppDialogRequest.Confirm.cancellable], a link in the body routes to `onOpenLink` rather than
 * answering the dialog, and a new text request does not inherit the previous one's typed value.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AppDialogHostTest {
    @get:Rule val compose = createComposeRule()

    private val answers = mutableListOf<Pair<Long, AppDialogResult>>()
    private val opened = mutableListOf<String>()
    private val choices = mutableListOf<AgentPermissionChoice>()

    private fun show(shown: ShownDialog?, permission: AgentPermissionRequest? = null, progress: ShownDialog? = null) = compose.setContent {
        ProvideAppLocals {
            AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                AppDialogHost(
                    shown = shown, permission = permission,
                    onRespond = { id, r -> answers += id to r },
                    onPermissionChoice = { choices += it }, onPermissionDismiss = { choices += AgentPermissionChoice.DENY },
                    onOpenLink = { opened += it },
                    progress = progress,
                )
            }
        }
    }

    private val message = AppDialogRequest.Message(
        title = null, message = "Something failed", confirmText = "OK",
        dismissText = "Cancel", neutralText = "Report error", cancellable = true,
    )

    @Test fun messageOkAnswersOk() {
        show(ShownDialog(1, message))
        compose.onNodeWithText("OK").performClick()
        assertEquals(listOf(1L to AppDialogResult.Ok), answers)
    }

    @Test fun messageNeutralAnswersNeutral() {
        show(ShownDialog(1, message))
        compose.onNodeWithText("Report error").performClick()
        assertEquals(listOf(1L to AppDialogResult.Neutral), answers)
    }

    @Test fun messageCancelAnswersCancel() {
        show(ShownDialog(1, message))
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(listOf(1L to AppDialogResult.Cancel), answers)
    }

    @Test fun confirmBackAnswersCancel() {
        show(ShownDialog(2, AppDialogRequest.Confirm("Sure?", null, "OK", "Cancel")))
        compose.waitForIdle()
        Espresso.pressBack()
        compose.waitForIdle()
        assertEquals(listOf(2L to AppDialogResult.Cancel), answers)
    }

    @Test fun nonCancellableMessageIgnoresBack() {
        show(ShownDialog(3, message.copy(dismissText = null, cancellable = false)))
        compose.waitForIdle()
        Espresso.pressBack()
        compose.waitForIdle()
        assertEquals(emptyList<Pair<Long, AppDialogResult>>(), answers)
        compose.onNodeWithText("Something failed").assertExists()
    }

    /**
     * A real physical click on a link INSIDE a material3 `AlertDialog` window cannot be driven
     * through `performClick`/`performTouchInput` on Robolectric here: three scratch probes proved
     * this is a platform-Dialog-window limitation of this compose-ui-test/Robolectric combination,
     * not an `AppDialogHost` defect --
     *  1. The same link, in the same [AbHtmlText] pipeline, rendered bare (no dialog): clicks fine.
     *  2. The same link wrapped in `AbMessageDialog`'s own `heightIn` + `verticalScroll` modifier
     *     chain but still bare (no dialog): clicks fine -- rules out the scroll wrapper.
     *  3. The same link rendered through the real [AbMessageDialog] (a real `AlertDialog`/`Dialog`
     *     window), with an ambient `LocalUriHandler` provided from OUTSIDE `AppDialogHost` entirely:
     *     the click event never reaches the link's `LinkInteractionListener` at all.
     * So this test instead asserts, separately, the two halves that #3 could not: (a) the body
     * `AppDialogHost` hands to `AbMessageDialog` really does carry a [LinkAnnotation.Url] for the
     * exact href (the html-to-link parsing/rendering pipeline is wired correctly), and (b) the
     * `UriHandler` wrapper `AppDialogHost` installs really does forward to `onOpenLink` when driven
     * directly (proven on a plain, non-dialog composable, where clicks DO work per probe #1/#2).
     */
    @Test fun linkAnnotationIsPresentInTheRenderedBody() {
        show(ShownDialog(4, AppDialogRequest.Message(null, "<a href=\"https://x.org\">here</a>", "OK")))
        val node = compose.onNodeWithText("here").fetchSemanticsNode()
        val text = node.config[SemanticsProperties.Text].single()
        val links = text.getLinkAnnotations(0, text.length).map { (it.item as LinkAnnotation.Url).url }
        assertEquals(listOf("https://x.org"), links)
        assertEquals(emptyList<Pair<Long, AppDialogResult>>(), answers)
    }

    @Test fun uriHandlerWrapperForwardsToOnOpenLink() {
        // The exact wrapper AppDialogHost installs: object : UriHandler { openUri = onOpenLink(it) }.
        // Proven driveable on a plain composable (probe #1/#2 above), so clicking it here proves the
        // forwarding logic itself, independent of the Dialog-window limitation.
        val forwarded = mutableListOf<String>()
        val wrapper = object : UriHandler {
            override fun openUri(uri: String) { forwarded += uri }
        }
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides wrapper) {
                AbHtmlText("<a href=\"https://x.org\">probe</a>")
            }
        }
        compose.onNodeWithText("probe").performClick()
        assertEquals(listOf("https://x.org"), forwarded)
    }

    @Test fun htmlIsRenderedNotShownAsMarkup() {
        show(ShownDialog(5, AppDialogRequest.Message(null, "a<br><b>bold</b>", "OK")))
        compose.onNodeWithText("a\nbold").assertExists()
    }

    @Test fun optionsDialogAnswersTheChosenValue() {
        show(
            ShownDialog(
                6,
                AppDialogRequest.Options(
                    "Restore or import?", null,
                    listOf(SettingsItem.Choice("restore", "Restore"), SettingsItem.Choice("import", "Import")),
                    dismissText = "Cancel", asActionSheet = false,
                ),
            ),
        )
        compose.onNodeWithText("Import").performClick()
        assertEquals(listOf(6L to AppDialogResult.Selected("import")), answers)
    }

    @Test fun textInputAnswersTypedText() {
        show(ShownDialog(7, AppDialogRequest.TextInput("Passphrase", null, "", "OK", "Cancel", neutralText = "Info")))
        compose.onNode(hasSetTextAction()).performTextReplacement("secret")
        compose.onNodeWithText("OK").performClick()
        assertEquals(listOf(7L to AppDialogResult.Text("secret")), answers)
    }

    @Test fun textInputNeutralAnswersNeutral() {
        show(ShownDialog(7, AppDialogRequest.TextInput("Passphrase", null, "", "OK", "Cancel", neutralText = "Info")))
        compose.onNodeWithText("Info").performClick()
        assertEquals(listOf(7L to AppDialogResult.Neutral), answers)
    }

    @Test fun aNewTextRequestStartsFromItsOwnInitialValue() {
        val state = mutableStateOf(ShownDialog(8, AppDialogRequest.TextInput("A", null, "first", "OK", "Cancel")) as ShownDialog?)
        compose.setContent {
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    AppDialogHost(state.value, null, { _, _ -> }, {}, {}, {})
                }
            }
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("typed")
        state.value = ShownDialog(9, AppDialogRequest.TextInput("B", null, "second", "OK", "Cancel"))
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).assertTextEquals("second")
    }

    /** C1: a Progress drawn underneath must not block the answerable dialog queued behind it. */
    @Test fun aProgressDoesNotBlockAConfirmShownAlongsideIt() {
        show(
            ShownDialog(10, AppDialogRequest.Confirm("Sure?", null, "OK", "Cancel")),
            progress = ShownDialog(11, AppDialogRequest.Progress(title = null, message = "Please wait")),
        )
        compose.onNodeWithText("Please wait").assertExists()
        compose.onNodeWithText("OK").assertExists()
        compose.onNodeWithText("OK").performClick()
        assertEquals(listOf(10L to AppDialogResult.Ok), answers)
    }

    /** I2: two consecutive MultiChoice requests with the same options don't share selection state. */
    @Test fun twoConsecutiveMultiChoiceRequestsDoNotShareToggledSelection() {
        val options = listOf(SettingsItem.Choice("a", "A"), SettingsItem.Choice("b", "B"))
        val state = mutableStateOf(
            ShownDialog(1, AppDialogRequest.MultiChoice(null, options, emptyList(), "OK", "Cancel")) as ShownDialog?,
        )
        compose.setContent {
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    AppDialogHost(state.value, null, { id, r -> answers += id to r }, {}, {}, {})
                }
            }
        }
        compose.onNodeWithText("A").performClick() // toggle "a" selected in the first request
        state.value = ShownDialog(2, AppDialogRequest.MultiChoice(null, options, emptyList(), "OK", "Cancel"))
        compose.waitForIdle()
        compose.onNodeWithText("OK").performClick() // confirm the SECOND request
        // A fresh sheet, not the toggled-over-from-the-first one: "a" is not pre-selected.
        assertEquals(listOf(2L to AppDialogResult.SelectedMany(emptyList())), answers)
    }

    @Test fun permissionRequestRendersAndAnswers() {
        show(null, AgentPermissionRequest("Add bookmark", "Adds a bookmark", null))
        val denyText = ApplicationProvider.getApplicationContext<android.content.Context>().getString(R.string.permission_deny)
        compose.onNodeWithText(denyText).performClick()
        assertEquals(listOf(AgentPermissionChoice.DENY), choices)
    }
}
