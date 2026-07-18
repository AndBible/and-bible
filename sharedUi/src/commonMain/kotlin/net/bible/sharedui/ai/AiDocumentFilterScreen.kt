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

package net.bible.sharedui.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.ai.AiDocGroupVd
import net.bible.sharedcore.ai.AiDocVd
import net.bible.sharedui.components.AbActionIconSize
import net.bible.sharedui.components.AbConfirmDialog
import net.bible.sharedui.components.AbOverflowMenu
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.strings.LocalStrings

/**
 * The per-document AI access filter screen (mirrors classic
 * [net.bible.android.view.activity.ai.AiDocumentFilterActivity]). Fully stateless: [groups] /
 * [isDirty] come from [net.bible.sharedcore.ai.AiDocumentFilterController]'s `state`/`isDirty`
 * flows, and every edit is forwarded straight back to the controller via [onToggle] /
 * [onResetAll] / [onSave] — this screen owns no filter state itself, only the discard-confirm
 * dialog's visibility (same shape as [GlobalToolPermissionsScreen]).
 *
 * **Blacklist semantics.** A document's checkbox reflects [AiDocVd.allowed]: checked = allowed
 * (the default — nothing is excluded until the user unchecks it), unchecked = excluded. Tapping a
 * row (or its checkbox) calls [onToggle] with that document's [AiDocVd.initials]; the controller
 * flips its membership in the working excluded set.
 *
 * **Grouping.** [groups] is already ordered/filtered by the controller (one [AiDocGroupVd] per
 * non-empty `BookCategory`, in Bible/Commentary/Dictionary/General-Book order — see
 * [AiDocGroupVd]'s kdoc); this screen just renders each group's `categoryLabel` as a section
 * header above its documents, with a divider between groups (mirrors [ToolPermissionList]'s
 * category-divider layout).
 *
 * **Top bar.** Title: [net.bible.sharedui.strings.Strings.aiDocumentFilterTitle]. A save
 * check-icon action (`enabled = isDirty`, mirrors [GlobalToolPermissionsScreen]'s save icon —
 * shown but inert when there is nothing to save) plus an [AbOverflowMenu] with "Reset all"
 * ([onResetAll] — clears the working excluded set, i.e. allows everything again; no confirmation,
 * matches classic's `reset_all` menu item; nothing is persisted until [onSave] regardless) and
 * "Help" ([onHelp], pure host-navigation callback).
 *
 * **Up / back.** [onUp] is not called directly from the up-navigation icon: mirrors classic's
 * `cancelOrConfirmDiscard()` — tapping it shows an [AbConfirmDialog] ("Discard unsaved changes?")
 * whenever [isDirty], calling [onUp] only on confirm (or immediately, with no dialog, when not
 * dirty).
 *
 * @param groups Document groups with their (already category-ordered) documents (`AiDocumentFilterController.state`).
 * @param isDirty Whether the working excluded set differs from the loaded baseline (`AiDocumentFilterController.isDirty`).
 * @param onUp Requested up-navigation, gated behind the discard-confirm dialog while [isDirty].
 * @param onToggle Forwarded 1:1 to `AiDocumentFilterController.toggle`, given a document's `initials`.
 * @param onResetAll Forwarded 1:1 to `AiDocumentFilterController.resetAll`.
 * @param onSave Forwarded 1:1 to `AiDocumentFilterController.save`.
 * @param onHelp Host callback to show the AI document filter help dialog.
 */
@Composable
fun AiDocumentFilterScreen(
    groups: List<AiDocGroupVd>,
    isDirty: Boolean,
    onUp: () -> Unit,
    onToggle: (initials: String) -> Unit,
    onResetAll: () -> Unit,
    onSave: () -> Unit,
    onHelp: () -> Unit,
) {
    val strings = LocalStrings.current
    var showDiscardConfirm by remember { mutableStateOf(false) }
    val requestUp: () -> Unit = { if (isDirty) showDiscardConfirm = true else onUp() }

    AbScaffold(
        title = strings.aiDocumentFilterTitle,
        onNavigateUp = requestUp,
        actions = {
            IconButton(onClick = onSave, enabled = isDirty) {
                Icon(Icons.Filled.Check, contentDescription = strings.okay, modifier = Modifier.size(AbActionIconSize))
            }
            AbOverflowMenu(contentDescription = null) { close ->
                DropdownMenuItem(text = { Text(strings.resetToolPermissionsLabel) }, onClick = { close(); onResetAll() })
                DropdownMenuItem(text = { Text(strings.helpLabel) }, onClick = { close(); onHelp() })
            }
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            groups.forEachIndexed { index, group ->
                if (index > 0) {
                    item(key = "divider-${group.categoryId}") { HorizontalDivider() }
                }
                item(key = "header-${group.categoryId}") { AiDocGroupHeader(group.categoryLabel) }
                items(group.docs, key = { "${group.categoryId}:${it.initials}" }) { doc ->
                    AiDocRow(doc = doc, onToggle = { onToggle(doc.initials) })
                }
            }
        }
    }

    if (showDiscardConfirm) {
        AbConfirmDialog(
            title = null,
            message = strings.discardChangesConfirmation,
            confirmText = strings.yes,
            dismissText = strings.no,
            onConfirm = { showDiscardConfirm = false; onUp() },
            onDismiss = { showDiscardConfirm = false },
        )
    }
}

@Composable
private fun AiDocGroupHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun AiDocRow(doc: AiDocVd, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = doc.allowed, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(8.dp))
        Text("${doc.initials} — ${doc.name}")
    }
}
