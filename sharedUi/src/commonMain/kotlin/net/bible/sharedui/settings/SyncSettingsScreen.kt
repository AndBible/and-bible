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

package net.bible.sharedui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import net.bible.sharedcore.docs.DocsLink
import net.bible.sharedcore.settings.SettingsEditorPage
import net.bible.sharedcore.settings.SettingsEditorStack
import net.bible.sharedcore.settings.SyncDialog
import net.bible.sharedcore.settings.SyncSettingsUiState
import net.bible.sharedui.components.AbConfirmDialog
import net.bible.sharedui.components.AbErrorDialog
import net.bible.sharedui.components.AbHelpOverflowMenu
import net.bible.sharedui.components.AbLoadingOverlay
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.components.dialog.AbDialogButton
import net.bible.sharedui.components.dialog.AbDialogDetail
import net.bible.sharedui.components.dialog.AbDialogShell
import net.bible.sharedui.components.dialog.DialogButtonRole
import net.bible.sharedui.strings.LocalStrings

/**
 * The cloud-sync settings screen. Renders the declarative settings list via [AbSettingsContent],
 * whose list-choice/text-input rows now open as [SettingsEditorSheet] pages (via
 * [GenericSettingsEditorSheet]) rather than the dialogs this screen used to render inline — this
 * screen owns the [SettingsEditorStack] driving that sheet, the same way [AbSettingsScreen] does.
 * Overlaid with an [AbLoadingOverlay] while a blocking flow runs, plus the screen's confirm/error
 * modals ([AbConfirmDialog] for the document-enable, reset and forget-certificate confirmations, [AbErrorDialog] for an
 * invalid server URL). All state comes from [uiState]; every user action flows up through the
 * callbacks to the [SyncSettingsController].
 */
@Composable
fun SyncSettingsScreen(
    uiState: SyncSettingsUiState,
    onUp: (() -> Unit)?,
    onSwitch: (String, Boolean) -> Unit,
    onListChoice: (String, String) -> Unit,
    onTextInput: (String, String) -> Unit,
    onNavigate: (String) -> Unit,
    onConfirmReset: () -> Unit,
    onConfirmEnableDocuments: () -> Unit,
    onConfirmForgetCertificate: () -> Unit,
    onDismissDialog: () -> Unit,
    onApply: () -> Unit = {},
    onDiscard: () -> Unit = {},
    onCancelChecking: () -> Unit = {},
    onConfirmLeaveApply: () -> Unit = {},
    onConfirmLeaveDiscard: () -> Unit = {},
) {
    val strings = LocalStrings.current
    val editor = remember { SettingsEditorStack() }
    val editorPages by editor.pages.collectAsState()
    AbScaffold(
        title = uiState.screen.title,
        onNavigateUp = onUp,
        actions = { AbHelpOverflowMenu(DocsLink.CLOUD_SYNC) },
        // Hidden while the leave question is open (its Apply/Discard are the same choice) and while an apply runs
        // (`applying` covers the checking dialog, the overlay and the host's plan dialog after the probe), so neither a
        // second Apply nor a Discard mid-apply is possible.
        bottomBar = {
            if (uiState.pending.isNotEmpty() && !uiState.applying && !uiState.loading &&
                uiState.dialog != SyncDialog.CheckingCloud && uiState.dialog != SyncDialog.LeaveWithPending) {
                SyncApplyBar(
                    summary = strings.syncPendingChanges(uiState.pending.size),
                    discardText = strings.discard,
                    applyText = strings.dialogApply,
                    onDiscard = onDiscard,
                    onApply = onApply,
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AbSettingsContent(
                state = uiState.screen,
                onSwitch = onSwitch,
                onListChoice = onListChoice,
                onTextInput = onTextInput,
                onNavigate = onNavigate,
                onOpenEditor = { key -> editor.open(SettingsEditorPage.Row(key)) },
            )
            if (uiState.loading) AbLoadingOverlay()
        }
    }
    GenericSettingsEditorSheet(
        state = uiState.screen,
        editor = editor,
        page = editorPages.lastOrNull(),
        depth = editor.depth,
        onListChoice = onListChoice,
        onTextInput = onTextInput,
        onMultiSelectChange = { _, _ -> },
    )

    when (val d = uiState.dialog) {
        is SyncDialog.EnableDocuments -> AbConfirmDialog(
            title = d.title,
            message = d.message,
            confirmText = strings.syncStartSyncing,
            dismissText = strings.cancel,
            onConfirm = onConfirmEnableDocuments,
            onDismiss = onDismissDialog,
        )
        is SyncDialog.ResetConfirm -> AbConfirmDialog(
            title = null,
            message = d.message,
            detail = strings.syncResetDetail,
            confirmText = strings.resetSync,
            dismissText = strings.cancel,
            destructive = true,
            onConfirm = onConfirmReset,
            onDismiss = onDismissDialog,
        )
        is SyncDialog.ForgetCertificate -> AbConfirmDialog(
            title = null,
            message = d.message,
            detail = strings.webdavForgetCertificateDetail,
            confirmText = strings.webdavForgetCertificateConfirm,
            dismissText = strings.cancel,
            onConfirm = onConfirmForgetCertificate,
            onDismiss = onDismissDialog,
        )
        is SyncDialog.UrlError -> AbErrorDialog(
            message = d.message,
            confirmText = strings.okay,
            onDismiss = onDismissDialog,
        )
        // Not dismissable: neither back nor an outside tap closes it, only Cancel aborts the apply.
        SyncDialog.CheckingCloud -> AbDialogShell(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            lead = { Text(strings.syncCheckingCloudTitle) },
            content = { AbDialogDetail(strings.syncCheckingCloudText) },
            buttons = { AbDialogButton(strings.cancel, DialogButtonRole.Cancel, onCancelChecking) },
        )
        SyncDialog.LeaveWithPending -> AbDialogShell(
            onDismissRequest = onDismissDialog,
            lead = { Text(strings.syncPendingLeaveTitle) },
            buttons = {
                AbDialogButton(strings.cancel, DialogButtonRole.Cancel, onDismissDialog)
                AbDialogButton(strings.discard, DialogButtonRole.Destructive, onConfirmLeaveDiscard)
                AbDialogButton(strings.dialogApply, DialogButtonRole.Confirm, onConfirmLeaveApply)
            },
        )
        SyncDialog.None -> {}
    }
}

/**
 * The bottom bar shown while category switches are pending: the change count (bold, so it reads without colour in
 * monochrome), Discard and Apply. Tagged `sync-apply-bar`.
 */
@Composable
private fun SyncApplyBar(
    summary: String,
    discardText: String,
    applyText: String,
    onDiscard: () -> Unit,
    onApply: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.testTag("sync-apply-bar")) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(summary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                AbDialogButton(discardText, DialogButtonRole.Cancel, onDiscard)
                AbDialogButton(applyText, DialogButtonRole.Confirm, onApply, filled = true)
            }
        }
    }
}
