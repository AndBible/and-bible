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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * A list of ACTIONS as a modal bottom sheet: no selection state, no radio buttons, no confirm button
 * (round 14a, spec §4). Tapping a row performs its action; the sheet closes because the action
 * clears whatever state gated [open]. This is M3's canonical bottom-sheet use, and it is the shape
 * both export-destination sites (share / save) actually have — they were `AlertDialog`s whose
 * "buttons" were the choices.
 *
 * There is deliberately no Cancel row of its own — a sheet is dismissed by swipe, scrim tap or back,
 * and the ✕ in the header is the explicit affordance. [dismissText] (run 3 final-review fix wave, I2)
 * is the one exception: when the caller names an explicit dismiss label (`AppDialogRequest.Options`'s
 * `dismissText`), it is drawn as a trailing [AbActionSheetRow] that answers the same as the ✕/swipe/
 * scrim/back — needed because [cancellable] = false blocks every one of THOSE, and a sheet with no
 * options-independent way out would trap the user (`ErrorReportControl.checkCrash`'s crash-report
 * sheet is the one production caller of both together: `dismissText = error_skip`, `cancellable =
 * false`). [onDismiss] must therefore carry whatever "the user chose nothing" means to the caller —
 * for the export sites, completing their `CompletableDeferred` with `null`.
 *
 * [cancellable] = false (default `true`, so every existing caller is unaffected) refuses swipe-to-
 * dismiss (`confirmValueChange` rejects `SheetValue.Hidden`), back press
 * (`ModalBottomSheetProperties.shouldDismissOnBackPress`) and hides the ✕ ([AbSheetHeader]'s
 * `showClose`) — the platform `setCancelable(false)` dialog this sheet replaces blocked exactly
 * those three, leaving only its explicit buttons ([dismissText] here) as a way out.
 *
 * Rows are supplied as content rather than as a data list so a caller can build them from platform
 * resources: [AbActionSheetRow]'s `icon` is a composable slot, the same shape as `AbMenuItem`'s
 * (`AbMenu.kt:91`), which is what lets `:app` pass a `painterResource` drawable from `commonMain`-safe
 * code.
 *
 * [skipPartiallyExpanded] is `true` per spec §7.a. No `LaunchedEffect(sheetState.isVisible)` re-show
 * — see [AbChoiceSheet]'s kdoc.
 *
 * ROBORAZZI: never capture this composable; capture [AbActionSheetContent] instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbActionSheet(
    open: Boolean,
    title: String,
    message: String? = null,
    dismissText: String? = null,
    cancellable: Boolean = true,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!open) return
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value -> cancellable || value != SheetValue.Hidden },
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = cancellable),
    ) {
        AbActionSheetContent(
            title = title, message = message, onClose = onDismiss,
            dismissText = dismissText, showClose = cancellable, content = content,
        )
    }
}

/**
 * [AbActionSheet]'s body. Not scroll-bounded and carries no fade: an action sheet is a handful of
 * rows by construction (three at the widest call site in this port), so there is nothing below the
 * clip for a fade to advertise. A caller that grows one past a screenful should wrap its rows in
 * [AbSheetScrollBound] itself rather than have every action sheet pay for a viewport it never fills.
 *
 * [dismissText]/[showClose]: see [AbActionSheet]'s kdoc (run 3 final-review fix wave, I2). Both
 * default to their old behaviour (no dismiss row, ✕ shown), so every existing caller of this content
 * composable renders byte-identically to before.
 */
@Composable
fun AbActionSheetContent(
    title: String,
    message: String? = null,
    onClose: () -> Unit,
    dismissText: String? = null,
    showClose: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        AbSheetHeader(title = title, onClose = onClose, showClose = showClose)
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        content()
        if (dismissText != null) {
            AbActionSheetRow(label = dismissText, onClick = onClose)
        }
    }
}

/**
 * One action row: optional leading icon, then the label. The whole row is the target.
 * `role = Role.Button` on the clickable so TalkBack announces it as a button, matching the
 * `TextButton`s these rows replaced (final-review fix wave, M1) -- same idiom as
 * `AbExpandableSection.kt`'s toggle row and `AbListChoiceDialog.kt`'s choice row (there
 * `Role.RadioButton`, since that one carries selection state; this row performs an action with no
 * selection, so `Role.Button` is correct here, not `Role.RadioButton`).
 */
@Composable
fun AbActionSheetRow(
    label: String,
    onClick: () -> Unit,
    icon: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Row(modifier = Modifier.padding(end = 16.dp)) { icon() }
        }
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
