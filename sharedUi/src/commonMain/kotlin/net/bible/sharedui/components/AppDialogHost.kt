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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * Links in any body go through [AbLinkRouting] ([askBeforeOpeningLink]/[onOpenExternal] — C1 fix:
 * a plain `LocalUriHandler provides …` here does not survive into the `Dialog`/`Popup`/sheet window
 * each request below draws into, since the platform re-provides its own `LocalUriHandler` INSIDE
 * that window. [AbLinkRouting]'s [LocalAbLinkOpener] does survive, and
 * [askBeforeOpeningLink] additionally draws its own "open external link?" question on top when a
 * link is tapped, instead of going through `CommonUtils.openLink`'s FIFO (which would queue behind
 * -- and so hide behind -- the very Message/Confirm/… that contains the link).
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
    onOpenExternal: (String) -> Unit,
    askBeforeOpeningLink: Boolean = false,
    onSheetOpening: () -> Unit = {},
    progress: ShownDialog? = null,
    draftFor: (Long) -> String? = { null },
    onDraftChange: (Long, String) -> Unit = { _, _ -> },
) {
    AbLinkRouting(askFirst = askBeforeOpeningLink, onOpenExternal = onOpenExternal) {
        if (permission != null) {
            AgentPermissionDialog(request = permission, onChoice = onPermissionChoice, onDismiss = onPermissionDismiss)
        }
        // Drawn FIRST, so its window is created before the answerable `shown` dialog's -- the
        // question's window then sits on top and stays clickable (C1: a Progress must never block
        // an answerable request queued behind it).
        if (progress != null) {
            val p = progress.request as AppDialogRequest.Progress
            AbProgressDialog(message = p.message, title = p.title)
        }
        if (shown != null) {
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
                is AppDialogRequest.SingleChoice -> key(id) {
                    // key(id): two consecutive requests must not share remembered sheet/selection state.
                    AbChoiceSheet(
                        open = true, title = request.title.orEmpty(), choices = request.choices,
                        selectedValue = request.selectedValue.orEmpty(),
                        onSelect = { onRespond(id, AppDialogResult.Selected(it)) }, onDismiss = cancel,
                    )
                }
                is AppDialogRequest.MultiChoice -> key(id) {
                    // key(id): two consecutive requests (even with identical options) must not share a
                    // toggled-selection remembered inside AbMultiSelectSheet (I2).
                    // currentIds mirrors the sheet's own checked set purely to feed footerFor -- the
                    // committed selection itself still lives (and is keyed by id, per the comment
                    // above) inside AbMultiSelectSheetContent, unaffected by this local state.
                    var currentIds by remember(id) { mutableStateOf(request.selectedIds) }
                    AbMultiSelectSheet(
                        open = true, title = request.title.orEmpty(), options = request.options,
                        selectedIds = request.selectedIds, idOf = SettingsItem.Choice::value, labelOf = SettingsItem.Choice::label,
                        confirmText = request.confirmText, dismissText = request.dismissText,
                        onConfirm = { onRespond(id, AppDialogResult.SelectedMany(it)) }, onDismiss = cancel,
                        selectAllText = request.selectAllText, selectNoneText = request.selectNoneText,
                        footer = request.footerFor?.invoke(currentIds),
                        onSelectionChange = if (request.footerFor != null) { { ids: List<String> -> currentIds = ids } } else null,
                    )
                }
                is AppDialogRequest.TextInput -> key(id) {
                    // key(id), not just AbTextInputContent's own remember(initial): two consecutive
                    // requests that happen to share the same `initial` (e.g. both "") must still not
                    // share typed state -- they are different dialogs. aNewTextRequestStartsFromItsOwnInitialValue
                    // passes via remember(initial) alone when the initials differ, so it does not by
                    // itself force this guard red; key(id) is kept as the correct behaviour for the
                    // same-initial case that test does not exercise.
                    // Fix batch 1 §2.9: read ONCE per composition (remember), so the field's own
                    // remember(initial) is not reset on every keystroke; the composition is rebuilt
                    // after onStop, which is exactly when the draft must be read again.
                    val start = remember { draftFor(id) ?: request.initial }
                    AbTextInputDialog(
                        title = request.title.orEmpty(), initial = start,
                        confirmText = request.confirmText, dismissText = request.dismissText,
                        onConfirm = { onRespond(id, AppDialogResult.Text(it)) }, onDismiss = cancel,
                        extraContent = request.message?.let { m -> { AbHtmlText(m) } },
                        numeric = request.numeric, masked = request.masked,
                        neutralText = request.neutralText, onNeutral = { onRespond(id, AppDialogResult.Neutral) },
                        cancellable = request.cancellable,
                        onValueChange = { onDraftChange(id, it) },
                    )
                }
                is AppDialogRequest.Options -> if (request.asActionSheet) {
                    key(id) {
                        // key(id): same reason as SingleChoice/MultiChoice above (I2).
                        // dismissText/cancellable (run 3 final-review fix wave, I2): both were
                        // previously dropped here, silently ignoring what the request asked for --
                        // see AbActionSheet's kdoc for what each one now does.
                        AbActionSheet(
                            open = true, title = request.title.orEmpty(), message = request.message,
                            dismissText = request.dismissText, cancellable = request.cancellable, onDismiss = cancel,
                        ) {
                            request.options.forEach { option ->
                                AbActionSheetRow(label = option.label, onClick = { onRespond(id, AppDialogResult.Selected(option.value)) })
                            }
                        }
                    }
                } else {
                    AbOptionsDialog(
                        title = request.title, message = request.message, options = request.options,
                        onSelect = { onRespond(id, AppDialogResult.Selected(it)) }, onDismissRequest = cancel,
                        dismissText = request.dismissText, cancellable = request.cancellable,
                    )
                }
                is AppDialogRequest.Notice -> {
                    val icons = LocalNoticeIcons.current
                    AbNoticeDialog(
                        title = request.title, showTitleLogo = request.showTitleLogo, blocks = request.blocks,
                        confirmText = request.confirmText, onConfirm = { onRespond(id, AppDialogResult.Ok) },
                        onDismissRequest = cancel,
                        dismissText = request.dismissText, neutralText = request.neutralText,
                        onNeutral = { onRespond(id, AppDialogResult.Neutral) },
                        logoPainter = icons?.logo, moneyPainter = icons?.money,
                    )
                }
                // Unreachable in production: AppDialogController.pending (what feeds `shown`) is the
                // first NON-Progress entry -- a Progress only ever arrives via the `progress` parameter
                // above. Kept as a defensive render (never as Unit) so a caller that builds a `shown`
                // ShownDialog directly around a Progress (as some tests still do) still shows something
                // sane rather than silently nothing.
                is AppDialogRequest.Progress -> AbProgressDialog(message = request.message, title = request.title)
            }
        }
    }
}
