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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.sharedui.settings.SheetConfirmRow

/**
 * A multi-select chooser as a modal bottom sheet — the sheet form of [AbMultiSelectDialog] (round
 * 14a, spec §4). Same parameter list as that dialog plus a hoisted [open], so a conversion is a name
 * change at the call site.
 *
 * The confirm/cancel pair stays, unlike [AbChoiceSheet]'s: a multi-select has no per-row commit, so
 * there is nothing else to apply the checked set with. It is drawn by [SheetConfirmRow], the idiom
 * round 12c established, BELOW the scroll region and outside it — which is also what frames that
 * region so a half-cut row cannot read as the end of the list (spec §1.1).
 *
 * [skipPartiallyExpanded] is `true` per spec §7.a, and there is deliberately no
 * `LaunchedEffect(sheetState.isVisible)` re-show — see [AbChoiceSheet]'s kdoc for why copying 13a's
 * effect here would be wrong.
 *
 * ROBORAZZI: never capture this composable; capture [AbMultiSelectSheetContent] instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> AbMultiSelectSheet(
    open: Boolean,
    title: String,
    options: List<T>,
    selectedIds: List<String>,
    idOf: (T) -> String,
    labelOf: (T) -> String,
    confirmText: String,
    dismissText: String,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    selectAllText: String? = null,
    selectNoneText: String? = null,
    footer: String? = null,
    onSelectionChange: ((List<String>) -> Unit)? = null,
) {
    if (!open) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        AbMultiSelectSheetContent(
            title = title,
            options = options,
            selectedIds = selectedIds,
            idOf = idOf,
            labelOf = labelOf,
            confirmText = confirmText,
            dismissText = dismissText,
            onConfirm = onConfirm,
            onCancel = onDismiss,
            onClose = onDismiss,
            selectAllText = selectAllText,
            selectNoneText = selectNoneText,
            footer = footer,
            onSelectionChange = onSelectionChange,
        )
    }
}

/**
 * [AbMultiSelectSheet]'s body. Owns the working checked set exactly as [AbMultiSelectDialog] does —
 * committed to [onConfirm] only on confirm, discarded on cancel.
 */
@Composable
fun <T> AbMultiSelectSheetContent(
    title: String,
    options: List<T>,
    selectedIds: List<String>,
    idOf: (T) -> String,
    labelOf: (T) -> String,
    confirmText: String,
    dismissText: String,
    onConfirm: (List<String>) -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
    selectAllText: String? = null,
    selectNoneText: String? = null,
    footer: String? = null,
    onSelectionChange: ((List<String>) -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
) {
    var current by remember(options, selectedIds) { mutableStateOf(selectedIds) }
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        AbSheetHeader(title = title, onClose = onClose)
        AbSheetScrollBound(canScrollForward = { listState.canScrollForward }) {
            AbMultiSelectContent(
                options = options,
                selectedIds = selectedIds,
                idOf = idOf,
                labelOf = labelOf,
                onCheckedChange = { current = it; onSelectionChange?.invoke(it) },
                selectAllText = selectAllText,
                selectNoneText = selectNoneText,
                modifier = Modifier.padding(horizontal = 16.dp),
                listState = listState,
            )
        }
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        SheetConfirmRow(
            confirmLabel = confirmText,
            cancelLabel = dismissText,
            onConfirm = { onConfirm(current) },
            onCancel = onCancel,
        )
    }
}
