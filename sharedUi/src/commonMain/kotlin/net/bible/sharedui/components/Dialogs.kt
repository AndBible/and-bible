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

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import net.bible.sharedui.components.dialog.AbDialogButton
import net.bible.sharedui.components.dialog.AbDialogDetail
import net.bible.sharedui.components.dialog.AbDialogShell
import net.bible.sharedui.components.dialog.DialogButtonRole

/**
 * A simple error dialog on [AbDialogShell]: [message] is the lead line, an optional [detail] says what the
 * user can do, and one confirm button dismisses. The shell scrolls a long body and keeps the button visible.
 */
@Composable
fun AbErrorDialog(message: String, confirmText: String, onDismiss: () -> Unit, detail: String? = null) {
    AbDialogShell(
        onDismissRequest = onDismiss,
        buttons = { AbDialogButton(confirmText, DialogButtonRole.Confirm, onDismiss) },
        lead = { Text(message) },
        content = detail?.let { d -> { AbDialogDetail(d) } },
    )
}

/**
 * A confirm/cancel dialog on [AbDialogShell]. Plain text. Without a [title] the [message] is the lead line;
 * with both, [title] is the lead and [message] becomes the detail, unless an explicit [detail] is given.
 * [destructive] makes the confirm button a [DialogButtonRole.Destructive] one.
 */
@Composable
fun AbConfirmDialog(
    title: String?,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    detail: String? = null,
    destructive: Boolean = false,
) {
    val lead = title ?: message
    val shownDetail = detail ?: message.takeIf { title != null && it.isNotEmpty() }
    AbDialogShell(
        onDismissRequest = onDismiss,
        buttons = {
            AbDialogButton(dismissText, DialogButtonRole.Cancel, onDismiss)
            AbDialogButton(confirmText, if (destructive) DialogButtonRole.Destructive else DialogButtonRole.Confirm, onConfirm)
        },
        lead = { Text(lead) },
        content = shownDetail?.let { d -> { AbDialogDetail(d) } },
    )
}
