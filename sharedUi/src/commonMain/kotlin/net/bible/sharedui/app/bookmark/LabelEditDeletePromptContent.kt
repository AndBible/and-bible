package net.bible.sharedui.app.bookmark

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import net.bible.sharedcore.bookmark.DeletePrompt
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedui.components.AbOptionsDialog

/**
 * The label editor's delete prompt, pure rendering: every string is pre-resolved by the caller, so this composable
 * touches no platform resource and can be driven directly by a compose-ui test. [orphanedMessage] is only read for
 * [DeletePrompt.Orphaned] (null for [DeletePrompt.Confirm], which needs no body text).
 *
 * `AbOptionsDialog` (spec §6.2) renders [DeletePrompt.Orphaned]'s three answers (delete both, delete label only,
 * cancel), same labels and order as the classic row it replaced. [DeletePrompt.Confirm] (a plain 2-way "delete this
 * label?") keeps its `AlertDialog`.
 */
@Composable
fun LabelEditDeletePromptContent(
    prompt: DeletePrompt,
    title: String,
    orphanedMessage: String?,
    deleteBothLabel: String,
    deleteOnlyLabel: String,
    cancelLabel: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: (deleteOrphaned: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    when (prompt) {
        is DeletePrompt.Orphaned -> AbOptionsDialog(
            title = title,
            message = orphanedMessage,
            options = listOf(
                SettingsItem.Choice(value = "both", label = deleteBothLabel),
                SettingsItem.Choice(value = "labelOnly", label = deleteOnlyLabel),
            ),
            onSelect = { value -> onConfirm(value == "both") },
            onDismissRequest = onDismiss,
            dismissText = cancelLabel,
        )
        DeletePrompt.Confirm -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            confirmButton = { TextButton(onClick = { onConfirm(false) }) { Text(confirmLabel) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
        )
    }
}
