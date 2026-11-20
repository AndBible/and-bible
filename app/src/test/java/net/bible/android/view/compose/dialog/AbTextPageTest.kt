package net.bible.android.view.compose.dialog

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.material3.Text
import androidx.test.espresso.Espresso
import net.bible.android.TEST_SDK
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedcore.ui.dialog.DialogPresentation
import net.bible.sharedcore.ui.dialog.ShownDialog
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.components.AppDialogHost
import net.bible.sharedui.components.dialog.AbTextPage
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** [AbTextPage]: the full-screen page for long text, and its routing from `AppDialogHost`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbTextPageTest {
    @get:Rule val compose = createComposeRule()

    private var closed = 0
    private var confirmed = 0
    private var back = 0

    private fun show(
        confirmText: String? = null,
        dismissText: String? = null,
        cancellable: Boolean = true,
        onBack: (() -> Unit)? = null,
        topic: String? = "AI disclaimer",
    ) = compose.setContent {
        ProvideAppLocals {
            AbTheme {
                AbTextPage(
                    topic = topic, onClose = { closed++ },
                    confirmText = confirmText, onConfirm = { confirmed++ }, dismissText = dismissText,
                    cancellable = cancellable, onBack = onBack,
                ) { Text("Long text") }
            }
        }
    }

    @Test fun backOnDecisionPageCallsOnClose() {
        show(confirmText = "I accept", dismissText = "Cancel")
        compose.waitForIdle()
        Espresso.pressBack()
        compose.waitForIdle()
        assertEquals(1, closed)
        assertEquals(0, confirmed)
    }

    @Test fun closeButtonCallsOnClose() {
        show()
        compose.onNodeWithTag("page-close").performClick()
        assertEquals(1, closed)
    }

    @Test fun decisionPageShowsBothButtonsAndConfirms() {
        show(confirmText = "I accept", dismissText = "Cancel")
        compose.onNodeWithTag("dialog-dismiss").assertTextEquals("Cancel")
        compose.onNodeWithTag("dialog-confirm").assertTextEquals("I accept").performClick()
        assertEquals(1, confirmed)
        assertEquals(0, closed)
    }

    @Test fun topicIsShownAsHeading() {
        show()
        compose.onNodeWithTag("page-topic").assertTextEquals("AI disclaimer")
    }

    @Test fun nullOrBlankTopicRendersNoTopicNode() {
        show(topic = null)
        compose.onAllNodesWithTag("page-topic").assertCountEquals(0)
        compose.onAllNodesWithTag("page-close").assertCountEquals(1)
    }

    @Test fun blankTopicRendersNoTopicNode() {
        show(topic = "   ")
        compose.onAllNodesWithTag("page-topic").assertCountEquals(0)
    }

    @Test fun readOnlyPageHasSingleClose() {
        show()
        compose.onAllNodesWithTag("dialog-confirm").assertCountEquals(0)
        compose.onNodeWithTag("dialog-dismiss").assertTextEquals("Close")
        compose.onNodeWithTag("dialog-dismiss").performClick()
        assertEquals(1, closed)
    }

    @Test fun nonCancellablePageHidesCloseAndIgnoresBack() {
        show(confirmText = "I accept", dismissText = "Cancel", cancellable = false)
        compose.onAllNodesWithTag("page-close").assertCountEquals(0)
        compose.waitForIdle()
        Espresso.pressBack()
        compose.waitForIdle()
        assertEquals(0, closed)
        assertEquals(0, back)
    }

    @Test fun nonCancellablePageWithOnBackCallsOnBack() {
        show(confirmText = "I accept", dismissText = "Cancel", cancellable = false, onBack = { back++ })
        compose.waitForIdle()
        Espresso.pressBack()
        compose.waitForIdle()
        assertEquals(1, back)
        assertEquals(0, closed)
    }

    @Test fun pageRequestRoutesToTextPage() {
        val answers = mutableListOf<AppDialogResult>()
        val request = AppDialogRequest.Confirm(
            title = "AI disclaimer", message = "<b>Long</b> text", confirmText = "I accept", dismissText = "Cancel",
            presentation = DialogPresentation.Page,
        )
        compose.setContent {
            ProvideAppLocals {
                AbTheme {
                    AppDialogHost(
                        shown = ShownDialog(1, request), permission = null,
                        onRespond = { _, r -> answers += r },
                        onPermissionChoice = {}, onPermissionDismiss = {}, onOpenExternal = {},
                    )
                }
            }
        }
        compose.onAllNodesWithTag("page-close").assertCountEquals(1)
        compose.onNodeWithTag("dialog-confirm").performClick()
        assertEquals(listOf<AppDialogResult>(AppDialogResult.Ok), answers)
    }

    @Test fun dialogPresentationStaysADialog() {
        val request = AppDialogRequest.Confirm("Delete?", null, "Delete", "Cancel")
        compose.setContent {
            ProvideAppLocals {
                AbTheme {
                    AppDialogHost(
                        shown = ShownDialog(1, request), permission = null, onRespond = { _, _ -> },
                        onPermissionChoice = {}, onPermissionDismiss = {}, onOpenExternal = {},
                    )
                }
            }
        }
        compose.onAllNodesWithTag("page-close").assertCountEquals(0)
        compose.onNodeWithTag("dialog-confirm").assertExists()
    }
}
