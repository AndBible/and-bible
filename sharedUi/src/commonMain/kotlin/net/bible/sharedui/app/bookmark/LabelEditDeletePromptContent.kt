@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.app.bookmark

import net.bible.sharedcore.ui.dialog.DialogAnswer
import androidx.compose.runtime.Composable
import net.bible.sharedcore.bookmark.DeletePrompt
import net.bible.sharedui.components.AbConfirmDialog
import net.bible.sharedui.components.AbOptionsDialog

/**
 * The label editor's delete prompt, pure rendering: every string is pre-resolved by the caller, so this composable
 * touches no platform resource and can be driven directly by a compose-ui test.
 *
 * [DeletePrompt.Confirm] (a plain 2-way question) is an `AbConfirmDialog` whose destructive verb is [confirmLabel].
 * [DeletePrompt.Orphaned] is an `AbOptionsDialog` with two answer cards (`both`, `labelOnly`) and Cancel; [detail]
 * says how many bookmarks have no other label, and each answer carries its own consequence line.
 */
@Composable
@kotlin.native.HiddenFromObjC
fun LabelEditDeletePromptContent(
    prompt: DeletePrompt,
    lead: String,
    detail: String,
    deleteBothLabel: String?,
    deleteBothConsequence: String,
    deleteOnlyLabel: String,
    deleteOnlyConsequence: String,
    cancelLabel: String,
    confirmLabel: String,
    onConfirm: (deleteOrphaned: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    when (prompt) {
        is DeletePrompt.Orphaned -> AbOptionsDialog(
            title = lead,
            message = detail,
            options = listOf(
                DialogAnswer("both", checkNotNull(deleteBothLabel) { "deleteBothLabel is required for DeletePrompt.Orphaned" }, deleteBothConsequence, destructive = true),
                DialogAnswer("labelOnly", deleteOnlyLabel, deleteOnlyConsequence),
            ),
            onSelect = { value -> onConfirm(value == "both") },
            onDismissRequest = onDismiss,
            dismissText = cancelLabel,
        )
        DeletePrompt.Confirm -> AbConfirmDialog(
            title = null,
            message = lead,
            detail = detail,
            confirmText = confirmLabel,
            dismissText = cancelLabel,
            destructive = true,
            onConfirm = { onConfirm(false) },
            onDismiss = onDismiss,
        )
    }
}
