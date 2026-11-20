package net.bible.sharedui.settings

import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedui.components.dialog.DialogButtonRole
import net.bible.sharedui.components.dialog.DialogButtonRoleKey
import net.bible.sharedui.settings.nav.MemorizeRemoveConfirmDialog
import kotlin.test.Test
import kotlin.test.assertEquals

/** Removing a memorized passage or goal is destructive: the confirm must carry the destructive role. */
@OptIn(ExperimentalTestApi::class)
class MemorizeRemoveConfirmDialogTest {
    @Test fun confirmShowsLeadHasDestructiveRoleAndAnswers() = runComposeUiTest {
        var confirmed = 0
        setContent {
            MemorizeRemoveConfirmDialog(
                message = "Remove memorized status for “John 3:16”?", confirmText = "Remove", dismissText = "Cancel",
                onConfirm = { confirmed++ }, onDismiss = {},
            )
        }
        onNodeWithText("Remove memorized status for “John 3:16”?").assertExists()
        onNodeWithTag("dialog-confirm").assert(
            SemanticsMatcher("role is Destructive") { it.config.getOrNull(DialogButtonRoleKey) == DialogButtonRole.Destructive },
        )
        onNodeWithTag("dialog-confirm").performClick()
        assertEquals(1, confirmed)
    }
}
