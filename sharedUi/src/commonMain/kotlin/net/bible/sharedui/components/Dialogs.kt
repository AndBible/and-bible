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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
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
 *
 * [inputGuardMillis] (opt-in, default off): ignore every answer for that long after the dialog first
 * composes. A question that replaces another one in the same place would otherwise take the second tap of a
 * double tap on the first (#4192). Key the dialog by its question so a new question restarts the guard.
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
    inputGuardMillis: Long = 0L,
) {
    var armed by remember { mutableStateOf(inputGuardMillis <= 0L) }
    if (!armed) LaunchedEffect(Unit) { delay(inputGuardMillis); armed = true }
    val confirm = { if (armed) onConfirm() }
    val dismiss = { if (armed) onDismiss() }
    val lead = title ?: message
    val shownDetail = detail ?: message.takeIf { title != null && it.isNotEmpty() }
    AbDialogShell(
        onDismissRequest = dismiss,
        buttons = {
            AbDialogButton(dismissText, DialogButtonRole.Cancel, dismiss)
            AbDialogButton(confirmText, if (destructive) DialogButtonRole.Destructive else DialogButtonRole.Confirm, confirm)
        },
        lead = { Text(lead) },
        content = shownDetail?.let { d -> { AbDialogDetail(d) } },
    )
}
