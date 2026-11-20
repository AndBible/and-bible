package net.bible.android.view.compose.dialog

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.espresso.Espresso
import net.bible.android.TEST_SDK
import net.bible.sharedui.components.AbConfirmDialog
import net.bible.sharedui.components.AbErrorDialog
import net.bible.sharedui.components.AbMessageDialog
import net.bible.sharedui.components.dialog.DialogButtonRole
import net.bible.sharedui.components.dialog.DialogButtonRoleKey
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** [AbConfirmDialog], [AbErrorDialog] and [AbMessageDialog] on the dialog shell: lead/detail mapping and roles. */
/** The lead box has no text of its own; its Text child does. */
private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertLeadText(expected: String) =
    onChild().onChild().assertTextEquals(expected)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ConfirmDialogsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun confirmWithoutTitleUsesMessageAsLead() {
        compose.setContent {
            AbTheme { AbConfirmDialog(null, "Discard unsaved changes?", "Discard", "Cancel", {}, {}) }
        }
        compose.onNodeWithTag("dialog-lead", useUnmergedTree = true).assertLeadText("Discard unsaved changes?")
        compose.onAllNodesWithTag("dialog-detail").assertCountEquals(0)
    }

    @Test fun titleAndMessageBecomeLeadAndDetail() {
        compose.setContent {
            AbTheme { AbConfirmDialog("Reset", "Are you sure you want to reset this plan?", "Reset", "Cancel", {}, {}) }
        }
        compose.onNodeWithTag("dialog-lead", useUnmergedTree = true).assertLeadText("Reset")
        compose.onNodeWithTag("dialog-detail").assertTextEquals("Are you sure you want to reset this plan?")
    }

    @Test fun explicitDetailWinsOverMessage() {
        compose.setContent {
            AbTheme { AbConfirmDialog("Reset", "msg", "Reset", "Cancel", {}, {}, detail = "Explicit detail") }
        }
        compose.onNodeWithTag("dialog-detail").assertTextEquals("Explicit detail")
    }

    @Test fun destructiveConfirmHasDestructiveRole() {
        compose.setContent {
            AbTheme { AbConfirmDialog("Delete", null.let { "Gone for good" }, "Delete", "Cancel", {}, {}, destructive = true) }
        }
        compose.onNodeWithTag("dialog-confirm").assert(
            SemanticsMatcher("role is Destructive") { it.config.getOrNull(DialogButtonRoleKey) == DialogButtonRole.Destructive },
        )
    }

    @Test fun nonDestructiveConfirmHasConfirmRole() {
        compose.setContent { AbTheme { AbConfirmDialog(null, "Go on?", "Go", "Cancel", {}, {}) } }
        compose.onNodeWithTag("dialog-confirm").assert(
            SemanticsMatcher("role is Confirm") { it.config.getOrNull(DialogButtonRoleKey) == DialogButtonRole.Confirm },
        )
    }

    @Test fun errorDialogLeadIsMessageAndConfirmDismisses() {
        var dismissed = 0
        compose.setContent { AbTheme { AbErrorDialog("It broke", "OK", { dismissed++ }, detail = "Try again later") } }
        compose.onNodeWithTag("dialog-lead", useUnmergedTree = true).assertLeadText("It broke")
        compose.onNodeWithTag("dialog-detail").assertTextEquals("Try again later")
        compose.onNodeWithTag("dialog-confirm").performClick()
        assertEquals(1, dismissed)
    }

    @Test fun messageDialogBackCallsOnBackWhenNotCancellable() {
        var back = 0
        var dismissed = 0
        compose.setContent {
            AbTheme {
                AbMessageDialog(
                    title = "T", html = "Body", confirmText = "OK", onConfirm = {}, onDismissRequest = { dismissed++ },
                    cancellable = false, onBack = { back++ },
                )
            }
        }
        compose.waitForIdle()
        Espresso.pressBack()
        compose.waitForIdle()
        assertEquals(1, back)
        assertEquals(0, dismissed)
    }
}
