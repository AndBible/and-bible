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
package net.bible.sharedui.workspaces

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.theme.accentArgbFor
import net.bible.sharedcore.workspaces.CopySettingsState
import net.bible.sharedcore.workspaces.WorkspaceRowVd
import net.bible.sharedui.components.AbActionIcon
import net.bible.sharedui.components.AbMenuItem
import net.bible.sharedui.components.AbMultiSelectDialog
import net.bible.sharedui.components.AbReorderableColumn
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.components.AbSearchImeRequest
import net.bible.sharedui.components.AbTextInputDialog
import net.bible.sharedui.components.AbTopBarSearchCallbacks
import net.bible.sharedui.components.AbTopBarSearchState
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.theme.LocalDisplayColorMode

/**
 * Stateless workspace-selector list editor. Persistence + navigation are hoisted (the host wires
 * [net.bible.sharedcore.workspaces.WorkspaceSelectorController] to these callbacks); only which
 * dialog is currently open (new/rename/clone) is local UI state — everything else (order, dirty,
 * filtering, the copy-settings staging state, the dirty-select prompt) is driven by the host.
 *
 * Delete has no separate confirm dialog (parity with the classic activity): it stages immediately,
 * applied together with every other pending edit on Save.
 */
@Composable
fun WorkspaceSelectorScreen(
    title: String,
    workspaces: List<WorkspaceRowVd>,
    dirty: Boolean,
    canDelete: Boolean,
    filtering: Boolean,
    query: String,
    searchModeActive: Boolean,
    copySettingsState: CopySettingsState?,
    pendingSelectId: String?,
    onQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onSelect: (id: String) -> Unit,
    onRename: (id: String, name: String) -> Unit,
    onClone: (id: String, name: String) -> Unit,
    onDelete: (id: String) -> Unit,
    onEditSettings: (id: String) -> Unit,
    onCopySettings: (id: String) -> Unit,
    onCopySettingsToGlobal: (id: String) -> Unit,
    onChooseCopyTypes: (List<Int>) -> Unit,
    onChooseCopyTargets: (List<String>) -> Unit,
    onCancelCopySettings: () -> Unit,
    onCreate: (name: String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onConfirmPendingSelect: (save: Boolean) -> Unit,
    onDismissPendingSelect: () -> Unit,
    onHelp: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    val s = LocalStrings.current
    // Local dialog state (which text-input dialog is open + its target item). Not hoisted (pure UI).
    var createOpen by remember { mutableStateOf(false) }
    var renameFor by remember { mutableStateOf<WorkspaceRowVd?>(null) }
    var cloneFor by remember { mutableStateOf<WorkspaceRowVd?>(null) }

    // Round 6. Classic has the same three action-bar icons and NO overflow — New and Help are
    // declared in workspace_options_menu.xml; the search action is added programmatically by
    // RecyclerViewSearchHelper.setupRecyclerViewSearch, not by that XML. Our order here (search,
    // new, help) differs deliberately from classic's render order and was approved from a rendered
    // preview — an approved arrangement outweighs matching classic's icon order exactly. The port
    // had buried New and Help in a 3-dot menu and pinned the search field permanently below the
    // bar; both are restored here.
    AbScaffold(
        title = title,
        // Belt-and-braces: AbTopAppBar already suppresses onNavigateUp/actions itself in search
        // mode (search replaces the whole bar), so this is redundant with that contract, not a
        // disagreement with it — kept because it is behaviour-identical either way and removing it
        // would need fresh golden verification for no gain.
        onNavigateUp = if (searchModeActive) null else onNavigateUp,
        actions = {
            if (!searchModeActive) {
                AbActionIcon(Icons.Filled.Search, s.search, onOpenSearch)
                AbActionIcon(Icons.Filled.AddCircleOutline, s.newItem, { createOpen = true })
                // Icons.Filled.HelpOutline (not AutoMirrored) is deliberate: classic's
                // ic_help_white_24dp.xml has no android:autoMirrored, and Material Icons Extended
                // only ships an AutoMirrored variant for HelpOutline here, not for Search or
                // AddCircleOutline — switching this one would REGRESS RTL parity on a screen that
                // does have an RTL golden. Don't "fix" this in a later cross-screen icon pass.
                AbActionIcon(Icons.Filled.HelpOutline, s.helpLabel, onHelp)
            }
        },
        search = if (searchModeActive) {
            AbTopBarSearchState(
                query = query,
                // Focus on entering search mode, release on leaving. Recomputed from
                // searchModeActive rather than held as state: the bar acks each instruction back to
                // null itself, and the only transitions that matter are the two edges.
                imeRequest = AbSearchImeRequest.Focus,
            )
        } else null,
        searchCallbacks = if (searchModeActive) {
            AbTopBarSearchCallbacks(
                onQueryChange = onQueryChange,
                onClose = onCloseSearch,
                onImeRequestHandled = {},
            )
        } else null,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AbReorderableColumn(items = workspaces, key = { it.id }, onMove = onMove) { item, handle ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(item.id) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Drag handle hidden while filtering (parity: no reorder over a filtered/partial list).
                        if (!filtering) {
                            Icon(
                                Icons.Filled.DragHandle, contentDescription = null,
                                modifier = handle.padding(horizontal = 12.dp),
                            )
                        } else {
                            Spacer(Modifier.size(48.dp))
                        }
                        // The per-workspace color is a decorative-but-meaningful identifier, not a scheme
                        // color, so it isn't grayscaled by AbTheme automatically (CategoryPalette idiom):
                        // grays out in BW, stays colored in COLOR_EINK.
                        Surface(
                            color = Color(accentArgbFor(item.colorArgb, LocalDisplayColorMode.current)),
                            shape = CircleShape,
                            modifier = Modifier.size(16.dp),
                        ) {}
                        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(
                                if (item.isCurrent) s.workspaceListingWithCurrent(item.name) else item.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (item.isCurrent) FontWeight.Bold else FontWeight.Normal,
                            )
                            item.summary?.let { summary ->
                                Text(
                                    summary, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        RowOverflow(
                            canDelete = canDelete,
                            onEditSettings = { onEditSettings(item.id) },
                            onRename = { renameFor = item },
                            onClone = { cloneFor = item },
                            onDelete = { onDelete(item.id) },
                            onCopySettings = { onCopySettings(item.id) },
                            onCopySettingsToGlobal = { onCopySettingsToGlobal(item.id) },
                        )
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(s.dismiss) }
                TextButton(onClick = onSave, enabled = dirty, modifier = Modifier.weight(1f)) { Text(s.saveAndExit) }
            }
        }
    }

    if (createOpen) {
        AbTextInputDialog(
            title = s.giveNameWorkspace, initial = s.workspaceNumber(workspaces.size + 1),
            confirmText = s.okay, dismissText = s.cancel,
            onConfirm = { createOpen = false; if (it.isNotBlank()) onCreate(it.trim()) },
            onDismiss = { createOpen = false },
        )
    }
    renameFor?.let { item ->
        AbTextInputDialog(
            title = s.giveNameWorkspace, initial = item.name, confirmText = s.okay, dismissText = s.cancel,
            onConfirm = { renameFor = null; if (it.isNotBlank()) onRename(item.id, it.trim()) },
            onDismiss = { renameFor = null },
        )
    }
    cloneFor?.let { item ->
        AbTextInputDialog(
            title = s.giveNameWorkspace, initial = s.copyOfWorkspace(item.name),
            confirmText = s.okay, dismissText = s.cancel,
            onConfirm = { cloneFor = null; if (it.isNotBlank()) onClone(item.id, it.trim()) },
            onDismiss = { cloneFor = null },
        )
    }
    when (val cs = copySettingsState) {
        is CopySettingsState.ChooseTypes -> AbMultiSelectDialog(
            title = s.copySettingsTitle,
            options = cs.typeLabels.mapIndexed { i, label -> IndexedLabel(i, label) },
            selectedIds = emptyList(), idOf = { it.index.toString() }, labelOf = { it.label },
            confirmText = s.okay, dismissText = s.cancel,
            onConfirm = { ids -> onChooseCopyTypes(ids.map { it.toInt() }) },
            onDismiss = onCancelCopySettings, selectAllText = s.selectAll, selectNoneText = s.selectNone,
        )
        is CopySettingsState.ToGlobal -> AbMultiSelectDialog(
            title = s.copySettingsTitle,
            options = cs.typeLabels.mapIndexed { i, label -> IndexedLabel(i, label) },
            selectedIds = emptyList(), idOf = { it.index.toString() }, labelOf = { it.label },
            confirmText = s.okay, dismissText = s.cancel,
            onConfirm = { ids -> onChooseCopyTypes(ids.map { it.toInt() }) },
            onDismiss = onCancelCopySettings, selectAllText = s.selectAll, selectNoneText = s.selectNone,
        )
        is CopySettingsState.ChooseTargets -> AbMultiSelectDialog(
            title = s.copySettingsWorkspacesTitle,
            options = cs.targets, selectedIds = emptyList(), idOf = { it.id }, labelOf = { it.name },
            confirmText = s.okay, dismissText = s.cancel,
            onConfirm = onChooseCopyTargets, onDismiss = onCancelCopySettings,
            selectAllText = s.selectAll, selectNoneText = s.selectNone,
        )
        null -> {}
    }
    // Dirty-select prompt = classic 3-branch (yes=save+go, no=discard+go, tap-outside/back=stay). A
    // plain M3 AlertDialog is used (not AbConfirmDialog) so onDismissRequest (stay) is distinct from
    // the No button (discard) — AbConfirmDialog only has a 2-way confirm/dismiss shape.
    pendingSelectId?.let {
        AlertDialog(
            onDismissRequest = onDismissPendingSelect,
            text = { Text(s.workspaceSaveChanges) },
            confirmButton = { TextButton(onClick = { onConfirmPendingSelect(true) }) { Text(s.yes) } },
            dismissButton = { TextButton(onClick = { onConfirmPendingSelect(false) }) { Text(s.no) } },
        )
    }
}

private data class IndexedLabel(val index: Int, val label: String)

@Composable
private fun RowOverflow(
    canDelete: Boolean,
    onEditSettings: () -> Unit, onRename: () -> Unit, onClone: () -> Unit, onDelete: () -> Unit,
    onCopySettings: () -> Unit, onCopySettingsToGlobal: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var submenuOpen by remember { mutableStateOf(false) }
    // Reset to the root whenever the menu closes, so the next open never starts inside the submenu
    // (WindowPaneMenu.kt:64-66 does the same with its path stack).
    LaunchedEffect(expanded) { if (!expanded) submenuOpen = false }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Filled.MoreVert, contentDescription = null) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            WorkspaceRowMenuRows(
                canDelete = canDelete,
                submenuOpen = submenuOpen,
                onEnterSubmenu = { submenuOpen = true },
                onBack = { submenuOpen = false },
                onEditSettings = { expanded = false; onEditSettings() },
                onRename = { expanded = false; onRename() },
                onClone = { expanded = false; onClone() },
                onDelete = { expanded = false; onDelete() },
                onCopySettings = { expanded = false; onCopySettings() },
                onCopySettingsToGlobal = { expanded = false; onCopySettingsToGlobal() },
            )
        }
    }
}

/**
 * One level of the per-workspace row menu.
 *
 * Public and factored out of [RowOverflow] for the same reason `WindowPaneMenuRows` is: an expanded
 * `DropdownMenu` cannot be photographed — it hangs Roborazzi, and two open popups on one page hang
 * the whole `:app` suite — so the golden renders this directly instead of opening the real popup.
 *
 * Row order follows classic `workspace_popup_menu.xml`. The last two classic rows are folded into a
 * "Copy settings…" submenu: "Global defaults" named an action ("copy these into the global
 * defaults") as though it were a destination, which read as a mystery in both UIs.
 */
@Composable
fun WorkspaceRowMenuRows(
    canDelete: Boolean,
    submenuOpen: Boolean,
    onEnterSubmenu: () -> Unit,
    onBack: () -> Unit,
    onEditSettings: () -> Unit, onRename: () -> Unit, onClone: () -> Unit, onDelete: () -> Unit,
    onCopySettings: () -> Unit, onCopySettingsToGlobal: () -> Unit,
) {
    val s = LocalStrings.current
    if (submenuOpen) {
        AbMenuItem(
            text = s.menuBack,
            onClick = onBack,
            icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) },
        )
        AbMenuItem(
            text = s.copySettingsToWorkspaces,
            onClick = onCopySettings,
            icon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
        )
        AbMenuItem(
            text = s.copySettingsToGlobalDefaults,
            onClick = onCopySettingsToGlobal,
            icon = { Icon(Icons.Filled.Public, contentDescription = null) },
        )
        return
    }
    AbMenuItem(
        text = s.deleteWorkspaceLabel,
        onClick = onDelete,
        icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
        enabled = canDelete,
    )
    AbMenuItem(
        text = s.rename,
        onClick = onRename,
        icon = { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null) },
    )
    AbMenuItem(
        text = s.newCopiedWorkspace,
        onClick = onClone,
        icon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
    )
    AbMenuItem(
        text = s.workspaceSettingsLabel,
        onClick = onEditSettings,
        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
    )
    AbMenuItem(
        text = s.copyWorkspaceSettings,
        onClick = onEnterSubmenu,
        icon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
        trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
    )
}
