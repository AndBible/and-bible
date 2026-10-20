@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import net.bible.sharedcore.ai.AgentPermissionController
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedui.components.AppDialogHost

/** Root-level consumer: feature requests survive navigation and retain drafts until answered. */
@Composable
@kotlin.native.HiddenFromObjC
fun QueuedAppDialogs(
    dialogs: AppDialogController,
    permissions: AgentPermissionController,
    onOpenExternal: (String) -> Unit,
    askBeforeOpeningLink: Boolean,
    onSheetOpening: () -> Unit,
) {
    val shown by dialogs.pending.collectAsState()
    val progress by dialogs.progress.collectAsState()
    val permission by permissions.pending.collectAsState()
    AppDialogHost(
        shown = shown,
        progress = progress,
        permission = permission,
        onRespond = dialogs::respond,
        onPermissionChoice = permissions::respond,
        onPermissionDismiss = permissions::dismiss,
        onOpenExternal = onOpenExternal,
        askBeforeOpeningLink = askBeforeOpeningLink,
        onSheetOpening = onSheetOpening,
        draftFor = dialogs::draft,
        onDraftChange = dialogs::saveDraft,
    )
}
