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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.ai.PromptCategoryVd
import net.bible.sharedcore.ai.PromptGroupVd
import net.bible.sharedcore.ai.PromptVd
import net.bible.sharedui.components.AbActionIcon
import net.bible.sharedui.components.AbConfirmDialog
import net.bible.sharedui.components.AbOverflowMenu
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.components.AbTextInputDialog
import net.bible.sharedui.components.TwoLineListItem
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.Strings

/**
 * The AI prompt manager (mirrors classic `AiSettingsActivity`'s prompt-list half + `PromptRepository`
 * category/prompt actions). Two states by [configured]:
 * - **not configured**: a centered CTA (title/description/button, mirrors `manage_prompts.xml`'s
 *   child 0) → [onOpenConnectionSettings].
 * - **configured**: a collapsible grouped list — virtual Favorites group first (only when non-empty),
 *   then categories in [groups] order (already resolved by the controller/service, including
 *   respecting [showHidden] — hidden prompts/categories are simply absent from [groups] when
 *   [showHidden] is false), then the uncategorized bucket. Each row is a [TwoLineListItem]
 *   (name + description) with a leading-free trailing ★ favorite toggle and a click that opens the
 *   prompt ([onOpenPrompt]).
 *
 * **Reorder — up/down actions, not drag-and-drop.** [AbReorderableColumn][net.bible.sharedui.components.AbReorderableColumn]
 * wraps its own `LazyColumn`, so nesting one per collapsible group inside this screen's outer list
 * would nest lazy layouts (unsupported/unmeasurable) and still couldn't express "reorder categories"
 * in the same gesture space as "reorder prompts within a category". The controller surface is also
 * shaped for it: [onMovePrompt]/[onMoveCategory] take `(id, up: Boolean)`, exactly the classic
 * Activity's `swapPromptOrder`/`swapCategoryOrder` contract — there is no drag-target API. So this
 * screen uses the same up/down affordance as classic, exposed per-row via the overflow menu (see
 * below), with adjacency computed locally from the already-ordered [groups] list (mirrors
 * `AiSettingsActivity`'s index-in-siblings logic):
 * - Prompt move: only for non-read-only prompts (`isReadOnly` — built-in and add-on prompts are
 *   never reorderable) and never inside the Favorites virtual group (its membership is derived, not
 *   a real category — moving "within Favorites" has no persisted order to change). Adjacency is
 *   computed among the non-read-only siblings of the SAME group's [PromptGroupVd.prompts].
 * - Category move: only for non-built-in categories (`isBuiltIn`). Adjacency is computed among all
 *   `category != null` groups in [groups] (matches `AiSettingsActivity`'s `categoryGroups` list,
 *   which is scanned in full display order regardless of built-in-ness, only gating the *action*
 *   on `!isBuiltInCat`).
 *
 * **Per-row actions** live in a per-row overflow (3-dot `IconButton` + `DropdownMenu`, the same
 * pattern as `MyDocumentsScreen`'s `RowOverflow` — not [AbOverflowMenu], which is sized for the top
 * bar) rather than a long-press menu: it's discoverable without a hidden gesture and composes
 * cleanly with the row's own click (open) and the trailing favorite-star tap target. A prompt/
 * category with literally no available action (e.g. a read-only, non-built-in add-on prompt — no
 * hide, no move, no delete) omits the overflow button entirely rather than show an empty menu.
 *
 * **String reuse (no new resource strings, per task brief):** the show/hide-hidden overflow toggle
 * reuses `R.string.ai_restore_hidden_prompts` ("Restore hidden prompts") as a *checkable* menu item
 * (leading [Checkbox] reflecting [showHidden], same pattern as `GridPassageScreen`'s `CheckItem`)
 * rather than a one-shot action — classic's "restore ALL hidden prompts at once" no longer exists as
 * a controller action ([net.bible.sharedcore.ai.AiPromptsController] only has [onSetShowHidden]), so
 * the closest-meaning existing string is repurposed. The un-hide action on an individual hidden
 * built-in prompt reuses the generic `R.string.restore` ("Restore"). A future strings-only pass could
 * add a precise "Show hidden" string; noted as a follow-up, not blocking here.
 */
@Composable
fun AiPromptsScreen(
    configured: Boolean,
    groups: List<PromptGroupVd>,
    showHidden: Boolean,
    onUp: () -> Unit,
    onOpenPrompt: (String) -> Unit,
    onNewPrompt: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onSetPromptHidden: (String, Boolean) -> Unit,
    onSetCategoryHidden: (String, Boolean) -> Unit,
    onDeletePrompt: (String) -> Unit,
    onDeleteCategory: (String, Boolean) -> Unit,
    onMovePrompt: (String, Boolean) -> Unit,
    onMoveCategory: (String, Boolean) -> Unit,
    onCreateCategory: (String) -> Unit,
    onRenameCategory: (String, String) -> Unit,
    onSetShowHidden: (Boolean) -> Unit,
    onOpenConnectionSettings: () -> Unit,
    onImportCsv: () -> Unit,
    onExportCsv: () -> Unit,
    onHelp: () -> Unit,
) {
    val strings = LocalStrings.current

    var showNewCategoryDialog by remember { mutableStateOf(false) }
    var renameCategoryTarget by remember { mutableStateOf<PromptCategoryVd?>(null) }
    var deleteCategoryTarget by remember { mutableStateOf<PromptCategoryVd?>(null) }
    var deletePromptTarget by remember { mutableStateOf<PromptVd?>(null) }

    AbScaffold(
        title = strings.aiPromptsTitle,
        onNavigateUp = onUp,
        actions = {
            if (configured) {
                AbActionIcon(Icons.Filled.Add, contentDescription = strings.newPrompt, onClick = onNewPrompt)
                AbOverflowMenu(contentDescription = null) { close ->
                    DropdownMenuItem(
                        text = { Text(strings.newCategory) },
                        onClick = { close(); showNewCategoryDialog = true },
                    )
                    DropdownMenuItem(
                        text = { Text(strings.restoreHiddenPromptsLabel) },
                        onClick = { close(); onSetShowHidden(!showHidden) },
                        leadingIcon = {
                            Checkbox(checked = showHidden, onCheckedChange = { close(); onSetShowHidden(it) })
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(strings.connectionSettingsMenuLabel) },
                        onClick = { close(); onOpenConnectionSettings() },
                    )
                    DropdownMenuItem(text = { Text(strings.exportPromptsCsv) }, onClick = { close(); onExportCsv() })
                    DropdownMenuItem(text = { Text(strings.importPromptsCsv) }, onClick = { close(); onImportCsv() })
                    DropdownMenuItem(text = { Text(strings.helpLabel) }, onClick = { close(); onHelp() })
                }
            }
        },
    ) { padding ->
        if (!configured) {
            AiSetupCta(
                strings = strings,
                onOpenConnectionSettings = onOpenConnectionSettings,
                modifier = Modifier.padding(padding),
            )
        } else {
            val totalPrompts = groups.sumOf { it.prompts.size }
            if (totalPrompts == 0) {
                Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        strings.managePromptsSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                PromptGroupsList(
                    groups = groups,
                    strings = strings,
                    onOpenPrompt = onOpenPrompt,
                    onToggleFavorite = onToggleFavorite,
                    onSetPromptHidden = onSetPromptHidden,
                    onSetCategoryHidden = onSetCategoryHidden,
                    onMovePrompt = onMovePrompt,
                    onMoveCategory = onMoveCategory,
                    onDeletePromptRequest = { deletePromptTarget = it },
                    onDeleteCategoryRequest = { deleteCategoryTarget = it },
                    onRenameCategoryRequest = { renameCategoryTarget = it },
                    modifier = Modifier.padding(padding).fillMaxSize(),
                )
            }
        }
    }

    if (showNewCategoryDialog) {
        AbTextInputDialog(
            title = strings.newCategory,
            initial = "",
            confirmText = strings.okay,
            dismissText = strings.cancel,
            onConfirm = { name ->
                showNewCategoryDialog = false
                if (name.isNotBlank()) onCreateCategory(name.trim())
            },
            onDismiss = { showNewCategoryDialog = false },
        )
    }
    renameCategoryTarget?.let { cat ->
        AbTextInputDialog(
            title = strings.rename,
            initial = cat.name,
            confirmText = strings.okay,
            dismissText = strings.cancel,
            onConfirm = { name ->
                renameCategoryTarget = null
                if (name.isNotBlank()) onRenameCategory(cat.id, name.trim())
            },
            onDismiss = { renameCategoryTarget = null },
        )
    }
    deleteCategoryTarget?.let { cat ->
        // Mirrors classic AiSettingsActivity's delete-category chooser (an AlertDialog#setItems
        // pick-list, not a plain yes/no): the category can be deleted either keeping its prompts
        // (moved to the uncategorized bucket) or cascading the delete to its prompts too.
        AlertDialog(
            onDismissRequest = { deleteCategoryTarget = null },
            text = { Text(strings.deleteCategoryConfirm(cat.name)) },
            confirmButton = {
                TextButton(onClick = { deleteCategoryTarget = null; onDeleteCategory(cat.id, true) }) {
                    Text(strings.deleteCategoryAndPromptsLabel)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { deleteCategoryTarget = null; onDeleteCategory(cat.id, false) }) {
                        Text(strings.deleteCategoryKeepPromptsLabel)
                    }
                    TextButton(onClick = { deleteCategoryTarget = null }) { Text(strings.cancel) }
                }
            },
        )
    }
    deletePromptTarget?.let { prompt ->
        AbConfirmDialog(
            title = prompt.name,
            message = strings.deletePromptConfirmMessage,
            confirmText = strings.yes,
            dismissText = strings.no,
            onConfirm = { deletePromptTarget = null; onDeletePrompt(prompt.id) },
            onDismiss = { deletePromptTarget = null },
        )
    }
}

@Composable
private fun AiSetupCta(strings: Strings, onOpenConnectionSettings: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            strings.aiSetupTitle,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            strings.aiSetupDescription,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpenConnectionSettings) { Text(strings.aiConfigureButton) }
    }
}

/** Stable per-group key: favorites/uncategorized are singletons, real categories key by id. */
private fun groupKey(group: PromptGroupVd): String = when {
    group.isFavorites -> " favorites"
    group.category != null -> group.category!!.id
    else -> " uncategorized"
}

@Composable
private fun PromptGroupsList(
    groups: List<PromptGroupVd>,
    strings: Strings,
    onOpenPrompt: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onSetPromptHidden: (String, Boolean) -> Unit,
    onSetCategoryHidden: (String, Boolean) -> Unit,
    onMovePrompt: (String, Boolean) -> Unit,
    onMoveCategory: (String, Boolean) -> Unit,
    onDeletePromptRequest: (PromptVd) -> Unit,
    onDeleteCategoryRequest: (PromptCategoryVd) -> Unit,
    onRenameCategoryRequest: (PromptCategoryVd) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Collapse/expand state is component-local (mirrors ToolPermissionList): all groups start
    // expanded, absent-from-map means expanded.
    val collapsed = remember { mutableStateMapOf<String, Boolean>() }
    val categorized = groups.filter { it.category != null }

    LazyColumn(modifier = modifier) {
        groups.forEachIndexed { index, group ->
            val key = groupKey(group)
            if (index > 0) item(key = "divider-$key") { HorizontalDivider() }
            item(key = "header-$key") {
                val isCollapsed = collapsed[key] == true
                val category = group.category
                when {
                    group.isFavorites -> CategoryHeader(
                        title = strings.promptCategoryFavorites,
                        count = group.prompts.size,
                        dimmed = false,
                        expanded = !isCollapsed,
                        onToggle = { collapsed[key] = !isCollapsed },
                    )
                    category == null -> CategoryHeader(
                        title = strings.promptCategoryUncategorized,
                        count = group.prompts.size,
                        dimmed = false,
                        expanded = !isCollapsed,
                        onToggle = { collapsed[key] = !isCollapsed },
                    )
                    else -> {
                        val catIdx = categorized.indexOfFirst { it.category?.id == category.id }
                        CategoryHeader(
                            title = if (category.isHidden) "${category.name} (${strings.hiddenSuffix})" else category.name,
                            count = group.prompts.size,
                            dimmed = category.isHidden,
                            expanded = !isCollapsed,
                            onToggle = { collapsed[key] = !isCollapsed },
                            overflow = {
                                CategoryRowOverflow(
                                    category = category,
                                    canMoveUp = catIdx > 0,
                                    canMoveDown = catIdx in 0 until categorized.size - 1,
                                    strings = strings,
                                    onSetHidden = { hidden -> onSetCategoryHidden(category.id, hidden) },
                                    onMove = { up -> onMoveCategory(category.id, up) },
                                    onRenameRequest = { onRenameCategoryRequest(category) },
                                    onDeleteRequest = { onDeleteCategoryRequest(category) },
                                )
                            },
                        )
                    }
                }
            }
            if (collapsed[key] != true) {
                val movableSiblings = group.prompts.filter { !it.isReadOnly }
                items(group.prompts, key = { "prompt-$key-${it.id}" }) { prompt ->
                    val idx = movableSiblings.indexOf(prompt)
                    PromptRow(
                        prompt = prompt,
                        canMoveUp = !group.isFavorites && idx > 0,
                        canMoveDown = !group.isFavorites && idx in 0 until movableSiblings.size - 1,
                        strings = strings,
                        onOpenPrompt = { onOpenPrompt(prompt.id) },
                        onToggleFavorite = { onToggleFavorite(prompt.id) },
                        onSetHidden = { hidden -> onSetPromptHidden(prompt.id, hidden) },
                        onMove = { up -> onMovePrompt(prompt.id, up) },
                        onDeleteRequest = { onDeletePromptRequest(prompt) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(
    title: String,
    count: Int,
    dimmed: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    overflow: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).alpha(if (dimmed) 0.5f else 1f),
        )
        Text(
            count.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 4.dp).alpha(if (dimmed) 0.5f else 1f),
        )
        overflow?.invoke()
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
        )
    }
}

@Composable
private fun CategoryRowOverflow(
    category: PromptCategoryVd,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    strings: Strings,
    onSetHidden: (Boolean) -> Unit,
    onMove: (Boolean) -> Unit,
    onRenameRequest: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Filled.MoreVert, contentDescription = null) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(if (category.isHidden) strings.showCategoryLabel else strings.hideCategoryLabel) },
                onClick = { expanded = false; onSetHidden(!category.isHidden) },
            )
            if (!category.isBuiltIn) {
                if (canMoveUp) {
                    DropdownMenuItem(text = { Text(strings.moveUpLabel) }, onClick = { expanded = false; onMove(true) })
                }
                if (canMoveDown) {
                    DropdownMenuItem(text = { Text(strings.moveDownLabel) }, onClick = { expanded = false; onMove(false) })
                }
                DropdownMenuItem(text = { Text(strings.rename) }, onClick = { expanded = false; onRenameRequest() })
                DropdownMenuItem(text = { Text(strings.deleteCategoryLabel) }, onClick = { expanded = false; onDeleteRequest() })
            }
        }
    }
}

@Composable
private fun PromptRow(
    prompt: PromptVd,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    strings: Strings,
    onOpenPrompt: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSetHidden: (Boolean) -> Unit,
    onMove: (Boolean) -> Unit,
    onDeleteRequest: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TwoLineListItem(
            title = if (prompt.isHidden) "${prompt.name} (${strings.hiddenSuffix})" else prompt.name,
            subtitle = prompt.description,
            onClick = onOpenPrompt,
            modifier = Modifier.weight(1f).alpha(if (prompt.isHidden) 0.5f else 1f),
        )
        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (prompt.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = null,
                tint = if (prompt.isFavorite) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                },
            )
        }
        // Only a built-in prompt can be hidden/restored, and only a non-read-only one can be
        // reordered/deleted — an add-on (read-only, non-built-in) prompt has no available action
        // here, so the overflow affordance itself is omitted rather than showing an empty menu.
        val hasAnyAction = prompt.isBuiltIn || !prompt.isReadOnly
        if (hasAnyAction) {
            PromptRowOverflow(
                prompt = prompt,
                canMoveUp = canMoveUp,
                canMoveDown = canMoveDown,
                strings = strings,
                onSetHidden = onSetHidden,
                onMove = onMove,
                onDeleteRequest = onDeleteRequest,
            )
        }
    }
}

@Composable
private fun PromptRowOverflow(
    prompt: PromptVd,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    strings: Strings,
    onSetHidden: (Boolean) -> Unit,
    onMove: (Boolean) -> Unit,
    onDeleteRequest: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Filled.MoreVert, contentDescription = null) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (prompt.isBuiltIn) {
                DropdownMenuItem(
                    text = { Text(if (prompt.isHidden) strings.restoreLabel else strings.hidePromptLabel) },
                    onClick = { expanded = false; onSetHidden(!prompt.isHidden) },
                )
            }
            if (!prompt.isReadOnly) {
                if (canMoveUp) {
                    DropdownMenuItem(text = { Text(strings.moveUpLabel) }, onClick = { expanded = false; onMove(true) })
                }
                if (canMoveDown) {
                    DropdownMenuItem(text = { Text(strings.moveDownLabel) }, onClick = { expanded = false; onMove(false) })
                }
                DropdownMenuItem(text = { Text(strings.deleteLabel) }, onClick = { expanded = false; onDeleteRequest() })
            }
        }
    }
}
