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

@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class, ExperimentalMaterial3Api::class)

package net.bible.sharedui.reading.activity

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.bible.android.control.activity.ActivityItem
import net.bible.sharedui.components.AbAlertDialog
import net.bible.sharedui.components.AbModalBottomSheet
import net.bible.sharedui.components.AbSheetHeader
import net.bible.sharedui.strings.LocalStrings

/** Width from which the list is a centred popover instead of a bottom sheet. */
internal val ACTIVITY_LIST_WIDE_BREAKPOINT = 600.dp

/** Test tags telling the two containers apart. */
@kotlin.native.HiddenFromObjC
const val ACTIVITY_LIST_SHEET_TAG = "activityListSheet"
@kotlin.native.HiddenFromObjC
const val ACTIVITY_LIST_POPOVER_TAG = "activityListPopover"

internal fun activityListIsWide(width: Dp): Boolean = width >= ACTIVITY_LIST_WIDE_BREAKPOINT

/**
 * Shows the full activity list (#4007) while [visible]: a popover dialog when the available width is at least
 * 600 dp, otherwise a bottom sheet. Emits nothing when not visible. The host owns [visible] and what
 * [onOpenItem] does with a tapped row.
 */
@kotlin.native.HiddenFromObjC
@Composable
fun ActivityListHost(
    visible: Boolean,
    items: List<ActivityItem>,
    onDismissItem: (String) -> Unit,
    onClearFinished: () -> Unit,
    onOpenItem: (ActivityItem) -> Unit,
    onClose: () -> Unit,
) {
    if (!visible) return
    val strings = LocalStrings.current
    BoxWithConstraints {
        if (activityListIsWide(maxWidth)) {
            AbAlertDialog(
                onDismissRequest = onClose,
                title = { Text(strings.activityTitle) },
                text = {
                    ActivityListContent(
                        items, onDismissItem, onClearFinished, onOpenItem,
                        modifier = Modifier.testTag(ACTIVITY_LIST_POPOVER_TAG),
                    )
                },
                confirmButton = { TextButton(onClick = onClose) { Text(strings.settingsEditorClose) } },
            )
        } else {
            AbModalBottomSheet(
                onDismissRequest = onClose,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                showDragHandle = false,
            ) {
                Column(Modifier.testTag(ACTIVITY_LIST_SHEET_TAG)) {
                    AbSheetHeader(title = strings.activityTitle, onClose = onClose)
                    ActivityListContent(items, onDismissItem, onClearFinished, onOpenItem)
                }
            }
        }
    }
}
