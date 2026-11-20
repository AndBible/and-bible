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

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties
import net.bible.sharedui.components.dialog.AbDialogButton
import net.bible.sharedui.components.dialog.AbDialogDetail
import net.bible.sharedui.components.dialog.AbDialogShell
import net.bible.sharedui.components.dialog.AbDialogTokens
import net.bible.sharedui.components.dialog.DialogButtonRole

/**
 * A message or a question whose body is HTML (spec §6.1): the replacement for every platform
 * `AlertDialog` whose message went through `htmlToSpan`. Up to three buttons — confirm, optional
 * dismiss, optional neutral — in the platform dialog's order (neutral at the start).
 *
 * The lead line is [title] only; the HTML [html] is never the lead (it would show raw markup there). Without a
 * title the HTML is body text with no lead line. [detail] (plain text) sits between the lead and the body.
 * [destructive] makes the confirm button a destructive one.
 *
 * [cancellable] = false is `setCancelable(false)`: back and scrim do nothing and [onDismissRequest]
 * is never called. With [onBack] also set, a non-cancellable dialog still ignores the scrim but Back
 * calls [onBack] (e.g. Back = Cancel on a confirmation); [onBack] is ignored while [cancellable].
 */
@Composable
fun AbMessageDialog(
    title: String?,
    html: String?,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    dismissText: String? = null,
    onDismiss: () -> Unit = onDismissRequest,
    neutralText: String? = null,
    onNeutral: () -> Unit = {},
    cancellable: Boolean = true,
    onBack: (() -> Unit)? = null,
    detail: String? = null,
    destructive: Boolean = false,
) {
    AbDialogShell(
        // With dismissOnClickOutside = false only Back reaches onDismissRequest.
        onDismissRequest = { if (cancellable) onDismissRequest() else onBack?.invoke() },
        properties = DialogProperties(
            dismissOnBackPress = cancellable || onBack != null,
            dismissOnClickOutside = cancellable,
        ),
        lead = title?.let { t -> { Text(t) } },
        content = if (detail != null || html != null) {
            {
                if (detail != null) AbDialogDetail(detail)
                if (detail != null && html != null) Spacer(Modifier.height(AbDialogTokens.GapLeadToBody))
                if (html != null) AbHtmlText(html)
            }
        } else null,
        buttons = {
            if (neutralText != null) AbDialogButton(neutralText, DialogButtonRole.Neutral, onNeutral)
            if (dismissText != null) AbDialogButton(dismissText, DialogButtonRole.Cancel, onDismiss)
            AbDialogButton(confirmText, if (destructive) DialogButtonRole.Destructive else DialogButtonRole.Confirm, onConfirm)
        },
    )
}
