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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
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
import net.bible.sharedui.components.AppDialogHost
import net.bible.sharedui.components.LocalNoticeIcons
import net.bible.sharedui.components.NoticeIcons
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Behaviour coverage for [AppDialogHost]: every branch of `AppDialogRequest` answers the right
 * [AppDialogResult] to the right id, back/scrim honour [AppDialogRequest.Message.cancellable] and
 * [AppDialogRequest.Confirm.cancellable], a link in the body reaches [onOpenExternal] (asking first
 * over the open dialog when [askBeforeOpeningLink]) rather than answering the dialog, and a new text
 * request does not inherit the previous one's typed value. Task 28 adds
 * [AppDialogRequest.Notice]'s button mapping and its `Html`/`IconLine` blocks' link routing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AppDialogHostTest {
    @get:Rule val compose = createComposeRule()

    private val answers = mutableListOf<Pair<Long, AppDialogResult>>()
    private val opened = mutableListOf<String>()
    private val choices = mutableListOf<AgentPermissionChoice>()

    private fun show(
        shown: ShownDialog?,
        permission: AgentPermissionRequest? = null,
        progress: ShownDialog? = null,
        askBeforeOpeningLink: Boolean = false,
        provideNoticeIcons: Boolean = false,
    ) = compose.setContent {
        ProvideAppLocals {
            AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                val content: @Composable () -> Unit = {
                    AppDialogHost(
                        shown = shown, permission = permission,
                        onRespond = { id, r -> answers += id to r },
                        onPermissionChoice = { choices += it }, onPermissionDismiss = { choices += AgentPermissionChoice.DENY },
                        onOpenExternal = { opened += it },
                        askBeforeOpeningLink = askBeforeOpeningLink,
                        progress = progress,
                    )
                }
                if (provideNoticeIcons) {
                    // A stand-in NoticeIcons -- honest, drawn colour swatches, never the real
                    // drawables (:sharedUi stays resource-free) -- so a Notice's InlineTextContent
                    // icon codepath is genuinely exercised rather than silently skipped (null
                    // painters, as the default `show()` leaves it).
                    val stub = NoticeIcons(ColorPainter(Color.Magenta), ColorPainter(Color.Cyan))
                    CompositionLocalProvider(LocalNoticeIcons provides stub, content = content)
                } else {
                    content()
                }
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
     * C1: a real click on a link inside the real [AppDialogHost]/`AbMessageDialog` `Dialog` window
     * now reaches the host, because the listener lives on the `LinkAnnotation` itself (via
     * `LocalAbLinkOpener`, a custom composition local the platform's `Dialog` window does not
     * re-provide) rather than on whatever `LocalUriHandler` that window's own child composition
     * happens to install. Before the C1 fix, this failed both ways at once: `opened` stayed empty
     * AND a real `ACTION_VIEW` activity was started (`AndroidUriHandler`, the platform's bare
     * fallback) -- proving Task 4's "harness limitation" write-off wrong; the click was always
     * deliverable, only the wiring was broken.
     */
    @Test fun linkTapInsideTheDialogWindowReachesTheHost() {
        show(ShownDialog(4, AppDialogRequest.Message(null, "<a href=\"https://x.org\">here</a>", "OK")))
        compose.onNodeWithText("here").performClick()
        assertEquals(listOf("https://x.org"), opened)
        assertNull(
            "no ACTION_VIEW activity was started directly -- the click went through onOpenExternal",
            Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity,
        )
        assertEquals(emptyList<Pair<Long, AppDialogResult>>(), answers)
    }

    /**
     * C1: with [askBeforeOpeningLink], tapping the link does NOT open it -- it draws the "open
     * external link?" question ON TOP of the still-open Message (whose own confirm text, "Close", is
     * deliberately distinct from the question's OK/Cancel so the two can't be confused). OK opens the
     * link exactly once and leaves the Message open and unanswered; Cancel opens nothing.
     */
    @Test fun discreteLinkAsksOverTheOpenDialog_okOpensOnce_leavesTheMessageOpen() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        show(ShownDialog(4, AppDialogRequest.Message(null, "<a href=\"https://x.org\">here</a>", "Close")), askBeforeOpeningLink = true)

        compose.onNodeWithText("here").performClick()
        compose.onNodeWithText(context.getString(R.string.external_link)).assertExists()
        compose.onNodeWithText("Close").assertExists() // the Message is still shown, underneath
        assertEquals(emptyList<String>(), opened)

        compose.onNodeWithText(context.getString(R.string.okay)).performClick()
        assertEquals(listOf("https://x.org"), opened)
        compose.onNodeWithText("Close").assertExists() // still shown and still unanswered
        assertEquals(emptyList<Pair<Long, AppDialogResult>>(), answers)
    }

    @Test fun discreteLinkAsksOverTheOpenDialog_cancelOpensNothing() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        show(ShownDialog(4, AppDialogRequest.Message(null, "<a href=\"https://x.org\">here</a>", "Close")), askBeforeOpeningLink = true)

        compose.onNodeWithText("here").performClick()
        compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
        assertEquals(emptyList<String>(), opened)
        compose.onNodeWithText("Close").assertExists()
    }

    /** M6 (run-1 minor, now load-bearing per Ruling 8): a sheet-shaped request calls
     *  [net.bible.sharedui.components.AppDialogHost]'s `onSheetOpening` exactly once for its id. */
    @Test fun multiChoiceCallsOnSheetOpeningExactlyOnceForItsId() {
        var sheetOpenings = 0
        val options = listOf(SettingsItem.Choice("a", "A"))
        compose.setContent {
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    AppDialogHost(
                        shown = ShownDialog(1, AppDialogRequest.MultiChoice(null, options, emptyList(), "OK", "Cancel")),
                        permission = null,
                        onRespond = { _, _ -> },
                        onPermissionChoice = {},
                        onPermissionDismiss = {},
                        onOpenExternal = {},
                        onSheetOpening = { sheetOpenings++ },
                    )
                }
            }
        }
        compose.waitForIdle()
        assertEquals(1, sheetOpenings)
    }

    /** Task 25: [AppDialogRequest.MultiChoice.footerFor] renders under the option list and
     *  recomputes live as the user toggles rows -- it does not reset the checked set on every
     *  recomposition, even though [AppDialogRequest.MultiChoice] now carries a function-typed
     *  property (equals/hashCode implications noted in the task brief). */
    @Test fun multiChoiceFooterReflectsTheLiveSelectionAndDoesNotResetTheChecklist() {
        val options = listOf(SettingsItem.Choice("a", "A"), SettingsItem.Choice("b", "B"))
        show(
            ShownDialog(
                1,
                AppDialogRequest.MultiChoice(
                    title = null, options = options, selectedIds = listOf("a"), confirmText = "OK", dismissText = "Cancel",
                    footerFor = { ids -> "Selected: ${ids.size}" },
                ),
            ),
        )
        compose.onNodeWithText("Selected: 1").assertExists()
        compose.onNodeWithText("B").performClick() // check "b" too
        compose.onNodeWithText("Selected: 2").assertExists()
        compose.onNodeWithText("A").performClick() // uncheck "a"
        compose.onNodeWithText("Selected: 1").assertExists()
        compose.onNodeWithText("OK").performClick()
        assertEquals(listOf(1L to AppDialogResult.SelectedMany(listOf("b"))), answers)
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

    /** I2 (run 3 final review): an action sheet's `dismissText` is drawn as a trailing row and
     *  answers [AppDialogResult.Cancel], same as `ErrorReportControl.showErrorDialog`'s "Skip". */
    @Test fun actionSheetDismissTextRendersAsARowAndAnswersCancel() {
        show(
            ShownDialog(
                7,
                AppDialogRequest.Options(
                    "Crash!", null,
                    listOf(SettingsItem.Choice("report", "Send report"), SettingsItem.Choice("backup", "Backup & restore")),
                    dismissText = "Skip", asActionSheet = true, cancellable = false,
                ),
            ),
        )
        compose.onNodeWithText("Skip").performClick()
        assertEquals(listOf(7L to AppDialogResult.Cancel), answers)
    }

    /** I2: `cancellable = false` on an action sheet blocks back (the ModalBottomSheet must not
     *  answer Cancel through `onDismissRequest`) and hides the ✕ header close -- `dismissText`'s
     *  row is the one way out left, matching the non-cancellable platform dialog this replaces. */
    @Test fun nonCancellableActionSheetIgnoresBackAndHidesClose() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        show(
            ShownDialog(
                8,
                AppDialogRequest.Options(
                    "Crash!", null,
                    listOf(SettingsItem.Choice("report", "Send report")),
                    dismissText = "Skip", asActionSheet = true, cancellable = false,
                ),
            ),
        )
        compose.waitForIdle()
        compose.onNodeWithContentDescription(context.getString(R.string.settings_editor_close)).assertDoesNotExist()
        Espresso.pressBack()
        compose.waitForIdle()
        assertEquals(emptyList<Pair<Long, AppDialogResult>>(), answers)
        compose.onNodeWithText("Send report").assertExists()
    }

    /** I2: a cancellable action sheet (the default, e.g. `BackupControl.classicDestinationPrompt`)
     *  keeps the ✕ header close, unaffected by the I2 fix. */
    @Test fun cancellableActionSheetKeepsHeaderClose() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        show(
            ShownDialog(
                9,
                AppDialogRequest.Options(
                    "Save or share?", null,
                    listOf(SettingsItem.Choice("share", "Share"), SettingsItem.Choice("save", "Save")),
                    dismissText = null, asActionSheet = true,
                ),
            ),
        )
        compose.onNodeWithContentDescription(context.getString(R.string.settings_editor_close)).performClick()
        assertEquals(listOf(9L to AppDialogResult.Cancel), answers)
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
                    AppDialogHost(shown = state.value, permission = null, onRespond = { _, _ -> }, onPermissionChoice = {}, onPermissionDismiss = {}, onOpenExternal = {})
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
                    AppDialogHost(shown = state.value, permission = null, onRespond = { id, r -> answers += id to r }, onPermissionChoice = {}, onPermissionDismiss = {}, onOpenExternal = {})
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

    // ——— Task 28: AppDialogRequest.Notice / AbNoticeDialog ————————————————————————————————————————

    private val notice = AppDialogRequest.Notice(
        title = "Notice title", showTitleLogo = true,
        blocks = listOf(
            AppDialogRequest.NoticeBlock.Html("Some notice text"),
            AppDialogRequest.NoticeBlock.Logo,
            AppDialogRequest.NoticeBlock.IconLine(AppDialogRequest.NoticeIcon.Money, "Support us"),
        ),
        confirmText = "OK", dismissText = "Cancel", neutralText = "Later",
    )

    @Test fun noticeConfirmAnswersOk() {
        show(ShownDialog(20, notice))
        compose.onNodeWithText("OK").performClick()
        assertEquals(listOf(20L to AppDialogResult.Ok), answers)
    }

    @Test fun noticeNeutralAnswersNeutral() {
        show(ShownDialog(21, notice))
        compose.onNodeWithText("Later").performClick()
        assertEquals(listOf(21L to AppDialogResult.Neutral), answers)
    }

    @Test fun noticeDismissAnswersCancel() {
        show(ShownDialog(22, notice))
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(listOf(22L to AppDialogResult.Cancel), answers)
    }

    /** [AppDialogRequest.Notice] has no `cancellable` flag -- back/scrim always answers Cancel
     *  (none of today's three ported notices calls `setCancelable(false)`). */
    @Test fun noticeBackAlwaysAnswersCancel() {
        show(ShownDialog(23, notice.copy(dismissText = null, neutralText = null)))
        compose.waitForIdle()
        Espresso.pressBack()
        compose.waitForIdle()
        assertEquals(listOf(23L to AppDialogResult.Cancel), answers)
    }

    @Test fun noticeWithNoLogoPainterSkipsTheLogoBlockRatherThanCrashing() {
        // `show()` provides no LocalNoticeIcons, so logoPainter/moneyPainter are both null here --
        // exactly the render path a golden/preview without :app's AppDialogOverlay takes.
        show(ShownDialog(26, notice))
        compose.onNodeWithText("Some notice text").assertExists()
        compose.onNodeWithText("Support us").assertExists()
    }

    /**
     * C1/Task 28: a link in a [AppDialogRequest.NoticeBlock.Html] body goes through the same
     * [net.bible.sharedui.components.LocalAbLinkOpener] routing as [AppDialogRequest.Message] --
     * [AppDialogHost] wraps every request, `Notice` included, in `AbLinkRouting`. Template:
     * `discreteLinkAsksOverTheOpenDialog_okOpensOnce_leavesTheMessageOpen` above.
     */
    @Test
    fun noticeDiscreteLinkAsksOverTheOpenDialog_okOpensOnce_leavesTheNoticeOpen() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val linkNotice = AppDialogRequest.Notice(
            title = null, showTitleLogo = false,
            blocks = listOf(AppDialogRequest.NoticeBlock.Html("<a href=\"https://x.org\">here</a>")),
            confirmText = "Close",
        )
        show(ShownDialog(24, linkNotice), askBeforeOpeningLink = true)

        compose.onNodeWithText("here").performClick()
        compose.onNodeWithText(context.getString(R.string.external_link)).assertExists()
        compose.onNodeWithText("Close").assertExists() // the Notice is still shown, underneath
        assertEquals(emptyList<String>(), opened)

        compose.onNodeWithText(context.getString(R.string.okay)).performClick()
        assertEquals(listOf("https://x.org"), opened)
        compose.onNodeWithText("Close").assertExists() // still shown and still unanswered
        assertEquals(emptyList<Pair<Long, AppDialogResult>>(), answers)
    }

    /** Same routing, for an [AppDialogRequest.NoticeBlock.IconLine] body -- brief: "including
     *  IconLine bodies". IconLine's HTML renders through `AbHtmlText`'s `leadingIcon` overload,
     *  which must still carry the link through [net.bible.sharedui.components.LocalAbLinkOpener]. */
    @Test
    fun noticeIconLineLinkTapReachesTheHost() {
        val iconNotice = AppDialogRequest.Notice(
            title = null, showTitleLogo = false,
            blocks = listOf(
                AppDialogRequest.NoticeBlock.IconLine(
                    AppDialogRequest.NoticeIcon.Money, "<a href=\"https://sponsor.example\">sponsor</a>",
                ),
            ),
            confirmText = "OK",
        )
        show(ShownDialog(25, iconNotice), provideNoticeIcons = true)
        compose.onNodeWithText("sponsor", substring = true).performClick()
        assertEquals(listOf("https://sponsor.example"), opened)
        assertEquals(emptyList<Pair<Long, AppDialogResult>>(), answers)
    }
}
