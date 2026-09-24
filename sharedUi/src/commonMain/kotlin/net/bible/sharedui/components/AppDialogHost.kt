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

package net.bible.sharedui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import net.bible.sharedcore.ai.AgentPermissionChoice
import net.bible.sharedcore.ai.AgentPermissionRequest
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedcore.ui.dialog.ShownDialog
import net.bible.sharedui.ai.AgentPermissionDialog

/**
 * Renders the head of `AppDialogController`'s queue, plus the agent permission prompt, per spec §7's
 * dialog-vs-sheet rule. Stateless: every value comes in, every answer goes out through [onRespond].
 *
 * Links in any body go to [onOpenLink] (the host's `CommonUtils.openLink`, which asks first in
 * discrete mode) — [LocalUriHandler] is overridden for everything below.
 *
 * [onSheetOpening] runs once per sheet-shaped request before it is shown, so the host can close its
 * own modal sheets first (two modals must not stack — `ReadingOverlayExclusion`).
 */
@Composable
fun AppDialogHost(
    shown: ShownDialog?,
    permission: AgentPermissionRequest?,
    onRespond: (Long, AppDialogResult) -> Unit,
    onPermissionChoice: (AgentPermissionChoice) -> Unit,
    onPermissionDismiss: () -> Unit,
    onOpenLink: (String) -> Unit,
    onSheetOpening: () -> Unit = {},
) {
    val uriHandler = remember(onOpenLink) { object : UriHandler { override fun openUri(uri: String) = onOpenLink(uri) } }
    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
        if (permission != null) {
            AgentPermissionDialog(request = permission, onChoice = onPermissionChoice, onDismiss = onPermissionDismiss)
        }
        if (shown == null) return@CompositionLocalProvider
        val id = shown.id
        val request = shown.request
        val isSheet = request is AppDialogRequest.SingleChoice || request is AppDialogRequest.MultiChoice ||
            (request is AppDialogRequest.Options && request.asActionSheet)
        if (isSheet) LaunchedEffect(id) { onSheetOpening() }
        val cancel = { onRespond(id, AppDialogResult.Cancel) }
        when (request) {
            is AppDialogRequest.Message -> AbMessageDialog(
                title = request.title, html = request.message, confirmText = request.confirmText,
                onConfirm = { onRespond(id, AppDialogResult.Ok) }, onDismissRequest = cancel,
                dismissText = request.dismissText, neutralText = request.neutralText,
                onNeutral = { onRespond(id, AppDialogResult.Neutral) }, cancellable = request.cancellable,
            )
            is AppDialogRequest.Confirm -> AbMessageDialog(
                title = request.title, html = request.message, confirmText = request.confirmText,
                onConfirm = { onRespond(id, AppDialogResult.Ok) }, onDismissRequest = cancel,
                dismissText = request.dismissText, cancellable = request.cancellable,
            )
            is AppDialogRequest.SingleChoice -> AbChoiceSheet(
                open = true, title = request.title.orEmpty(), choices = request.choices,
                selectedValue = request.selectedValue.orEmpty(),
                onSelect = { onRespond(id, AppDialogResult.Selected(it)) }, onDismiss = cancel,
            )
            is AppDialogRequest.MultiChoice -> AbMultiSelectSheet(
                open = true, title = request.title.orEmpty(), options = request.options,
                selectedIds = request.selectedIds, idOf = SettingsItem.Choice::value, labelOf = SettingsItem.Choice::label,
                confirmText = request.confirmText, dismissText = request.dismissText,
                onConfirm = { onRespond(id, AppDialogResult.SelectedMany(it)) }, onDismiss = cancel,
                selectAllText = request.selectAllText, selectNoneText = request.selectNoneText,
            )
            is AppDialogRequest.TextInput -> key(id) {
                // key(id), not just AbTextInputContent's own remember(initial): two consecutive
                // requests that happen to share the same `initial` (e.g. both "") must still not
                // share typed state -- they are different dialogs. aNewTextRequestStartsFromItsOwnInitialValue
                // passes via remember(initial) alone when the initials differ, so it does not by
                // itself force this guard red; key(id) is kept as the correct behaviour for the
                // same-initial case that test does not exercise.
                AbTextInputDialog(
                    title = request.title.orEmpty(), initial = request.initial,
                    confirmText = request.confirmText, dismissText = request.dismissText,
                    onConfirm = { onRespond(id, AppDialogResult.Text(it)) }, onDismiss = cancel,
                    extraContent = request.message?.let { m -> { AbHtmlText(m) } },
                    numeric = request.numeric, masked = request.masked,
                    neutralText = request.neutralText, onNeutral = { onRespond(id, AppDialogResult.Neutral) },
                    cancellable = request.cancellable,
                )
            }
            is AppDialogRequest.Options -> if (request.asActionSheet) {
                AbActionSheet(open = true, title = request.title.orEmpty(), message = request.message, onDismiss = cancel) {
                    request.options.forEach { option ->
                        AbActionSheetRow(label = option.label, onClick = { onRespond(id, AppDialogResult.Selected(option.value)) })
                    }
                }
            } else {
                AbOptionsDialog(
                    title = request.title, message = request.message, options = request.options,
                    onSelect = { onRespond(id, AppDialogResult.Selected(it)) }, onDismissRequest = cancel,
                    dismissText = request.dismissText, cancellable = request.cancellable,
                )
            }
            is AppDialogRequest.Progress -> AbProgressDialog(message = request.message, title = request.title)
        }
    }
}
