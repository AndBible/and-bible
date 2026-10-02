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

package net.bible.sharedui.readingplan

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.readingplan.PlanEntry
import net.bible.sharedcore.readingplan.ReadingPlanError
import net.bible.sharedui.components.AbConfirmDialog
import net.bible.sharedui.components.AbErrorDialog
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.strings.LocalStrings
import androidx.compose.foundation.lazy.rememberLazyListState
import net.bible.sharedui.components.volumeScrollTarget

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReadingPlanSelectorScreen(
    title: String,
    plans: List<PlanEntry>,
    duplicateWarning: Boolean,
    error: ReadingPlanError?,
    onSelect: (String) -> Unit,
    onReset: (String) -> Unit,
    onDismissError: () -> Unit,
    onDismissDuplicate: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    val strings = LocalStrings.current
    var confirmResetCode by remember { mutableStateOf<String?>(null) }

    AbScaffold(title = title, onNavigateUp = onNavigateUp) { padding ->
        val listState = rememberLazyListState()
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding).volumeScrollTarget(listState)) {
            items(plans, key = { it.planCode }) { plan ->
                // Long-press opens the reset confirm (classic context menu); tap selects.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onSelect(plan.planCode) },
                            onLongClick = { confirmResetCode = plan.planCode },
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(plan.name, style = MaterialTheme.typography.bodyLarge)
                    Text(plan.description, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    confirmResetCode?.let { code ->
        AbConfirmDialog(
            title = strings.resetGeneric,
            message = strings.resetPlanQuestion,
            confirmText = strings.yes,
            dismissText = strings.no,
            onConfirm = { confirmResetCode = null; onReset(code) },
            onDismiss = { confirmResetCode = null },
        )
    }
    if (error != null) {
        AbErrorDialog(message = strings.errorOccurred, confirmText = strings.okay, onDismiss = onDismissError)
    } else if (duplicateWarning) {
        AbErrorDialog(message = strings.planDuplicateUserPlan, confirmText = strings.okay, onDismiss = onDismissDuplicate)
    }
}
