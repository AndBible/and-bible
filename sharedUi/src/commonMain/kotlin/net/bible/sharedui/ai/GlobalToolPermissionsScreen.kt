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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import net.bible.sharedcore.ai.ToolPermGroupVd
import net.bible.sharedcore.ai.ToolPermission
import net.bible.sharedui.components.AbActionIconSize
import net.bible.sharedui.components.AbConfirmDialog
import net.bible.sharedui.components.AbOverflowMenu
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.strings.LocalStrings

/**
 * The global default tool-permissions screen (mirrors classic
 * [net.bible.android.view.activity.ai.GlobalToolPermissionsActivity] / `ToolPermissionListBuilder.Mode.GLOBAL`).
 * Fully stateless: [groups]/[permissionFor]/[isDirty] come from
 * [net.bible.sharedcore.ai.GlobalToolPermissionsController]'s `state`/`permissions`/`isDirty`
 * flows, and every edit is forwarded straight back to the controller via [onSetPermission] /
 * [onResetAll] / [onSave] — this screen owns no permission state itself, only the
 * discard-confirm dialog's visibility.
 *
 * **GLOBAL mode wiring.** [groups] is mapped to the `List<Pair<ToolCategoryVd, List<ToolVd>>>`
 * shape [ToolPermissionList] expects, and `globalDefaultLabelFor` is passed as `{ null }` — per
 * [ToolPermissionList]'s kdoc, a `null` result is what selects GLOBAL mode per-tool (write tools
 * get the 3-way Ask/Allow/Deny segmented control, no leading "Default (X)" option; read tools get
 * plain Enabled/Disabled). This screen has no PROMPT-mode concept at all, unlike
 * [PromptEditScreen]'s Permissions tab which always resolves a non-null token.
 *
 * **Top bar.** Title: [net.bible.sharedui.strings.Strings.globalToolPermissionsTitle]. A save
 * check-icon action (`enabled = isDirty`, mirrors [PromptEditScreen]'s save icon — shown but
 * inert when there is nothing to save, so the action row never shifts) plus an [AbOverflowMenu]
 * with "Reset all" ([onResetAll], no confirmation — matches classic's `reset_all` menu item,
 * which flips the working state to neutral defaults immediately; nothing is persisted until
 * [onSave] regardless) and "Help" ([onHelp], pure host-navigation callback).
 *
 * **Up / back.** [onUp] is not called directly from the up-navigation icon: mirrors classic's
 * `cancelOrConfirmDiscard()` — tapping it shows an [AbConfirmDialog] ("Discard unsaved changes?")
 * whenever [isDirty], calling [onUp] only on confirm (or immediately, with no dialog, when not
 * dirty).
 *
 * @param groups The tool catalog with its (fixed) category grouping (`GlobalToolPermissionsController.state`).
 * @param permissionFor Current [ToolPermission] for a given tool id (`GlobalToolPermissionsController.permissions`).
 * @param isDirty Whether the working permissions map differs from the loaded baseline (`GlobalToolPermissionsController.isDirty`).
 * @param onUp Requested up-navigation, gated behind the discard-confirm dialog while [isDirty].
 * @param onSetPermission Forwarded 1:1 to `GlobalToolPermissionsController.setPermission`.
 * @param onResetAll Forwarded 1:1 to `GlobalToolPermissionsController.resetAll`.
 * @param onSave Forwarded 1:1 to `GlobalToolPermissionsController.save`.
 * @param onHelp Host callback to show the global-tool-permissions help dialog.
 */
@Composable
fun GlobalToolPermissionsScreen(
    groups: List<ToolPermGroupVd>,
    permissionFor: (toolId: String) -> ToolPermission,
    isDirty: Boolean,
    onUp: () -> Unit,
    onSetPermission: (toolId: String, ToolPermission) -> Unit,
    onResetAll: () -> Unit,
    onSave: () -> Unit,
    onHelp: () -> Unit,
) {
    val strings = LocalStrings.current
    var showDiscardConfirm by remember { mutableStateOf(false) }
    val requestUp: () -> Unit = { if (isDirty) showDiscardConfirm = true else onUp() }
    val categories = remember(groups) { groups.map { it.category to it.tools } }

    AbScaffold(
        title = strings.globalToolPermissionsTitle,
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
        ToolPermissionList(
            categories = categories,
            permissionFor = permissionFor,
            globalDefaultLabelFor = { null },
            onSet = onSetPermission,
            modifier = Modifier.fillMaxSize().padding(padding),
        )
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
