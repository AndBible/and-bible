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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
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
import net.bible.sharedcore.ai.PromptContextIds
import net.bible.sharedcore.ai.PromptGroupVd
import net.bible.sharedcore.ai.PromptListFilter
import net.bible.sharedcore.ai.PromptType
import net.bible.sharedcore.ai.PromptVd
import net.bible.sharedcore.ai.filterPromptGroups
import net.bible.sharedcore.ai.promptTypeOf
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedui.components.AbActionIcon
import net.bible.sharedui.components.AbActionIconSize
import net.bible.sharedui.components.AbChoiceSheet
import net.bible.sharedui.components.AbConfirmDialog
import net.bible.sharedui.components.AbHelpMenuIcon
import net.bible.sharedui.components.AbInfoDialog
import net.bible.sharedui.components.AbMenuItem
import net.bible.sharedui.components.AbOverflowMenu
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.components.AbSearchImeRequest
import net.bible.sharedui.components.AbTextInputDialog
import net.bible.sharedui.components.AbTopBarSearchCallbacks
import net.bible.sharedui.components.AbTopBarSearchState
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
 *   [showHidden] is false), then the uncategorized bucket. Each row leads with a ★ favorite toggle
 *   (17f: moved from trailing to leading), then name + description + an optional third meta line
 *   (type marking and/or target contexts), and a click on that body opens the prompt
 *   ([onOpenPrompt]).
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
 * cleanly with the row's own click (open) and the leading favorite-star tap target. **F40:** Copy
 * is available for every prompt (built-in/add-on/user, mirrors classic `showPromptContextMenu`), so
 * every prompt row's overflow is now always shown (previously a read-only, non-built-in add-on
 * prompt — no hide/move/delete — omitted the button entirely; Copy means there's always something).
 * Move-to-category (opens an [net.bible.sharedui.components.AbChoiceSheet] picker, mirrors
 * classic `showMoveToCategoryDialog`) and reorder/delete stay gated to non-read-only (user) prompts.
 *
 * **Search + filter (17f).** The top bar's search icon opens an inline search field
 * ([net.bible.sharedui.components.AbTopBarSearchState]) plus a filter action ([FilterAction], its icon
 * swapping between [androidx.compose.material.icons.filled.FilterAlt]/[androidx.compose.material.icons.filled.FilterAltOff]
 * to show whether any filter is active) that opens [PromptFilterSheet]. Both the query and the
 * [net.bible.sharedcore.ai.PromptListFilter] are SCREEN-LOCAL state (`query`/`filter` below), not
 * hoisted to the host — filtering is a pure function over the already-resolved [groups]
 * ([net.bible.sharedcore.ai.filterPromptGroups]), so there is nothing for a host controller to own.
 *
 * **String reuse (no new resource strings, per task brief):** the show/hide-hidden overflow toggle
 * reuses `R.string.ai_restore_hidden_prompts` ("Restore hidden prompts") as a *checkable*
 * [net.bible.sharedui.components.AbMenuItem] (a trailing check reflecting [showHidden]) rather than
 * a one-shot action — classic's "restore ALL hidden prompts at once" no longer exists as a
 * controller action ([net.bible.sharedcore.ai.AiPromptsController] only has [onSetShowHidden]), so
 * the closest-meaning existing string is repurposed. The un-hide action on an individual hidden
 * built-in prompt reuses the generic `R.string.restore` ("Restore"). A future strings-only pass could
 * add a precise "Show hidden" string; noted as a follow-up, not blocking here.
 *
 * **F38 fix — toggle gating.** [hasHiddenPrompts] gates the toggle item's very presence (mirrors
 * classic `AiSettingsActivity.onPrepareOptionsMenu`'s
 * `restore_hidden_prompts.isVisible = hiddenBuiltInPrompts.isNotEmpty()`; the controller derives it
 * from [net.bible.sharedcore.ai.AiPromptsController.hasHiddenPrompts], independent of [showHidden]
 * since [groups] itself only filters hidden items in/out — it can't tell you whether any exist once
 * they're filtered out). **17f-B6:** every item in this overflow now goes through
 * [net.bible.sharedui.components.AbMenuItem] and carries a real leading icon, so the old hand-rolled
 * equal-width leading-icon slot (`OVERFLOW_LEADING_SLOT`) is gone — `AbMenuItem` reserves that box
 * itself, and the toggle's trailing check replaces its old leading [androidx.compose.material3.Checkbox].
 */

@Composable
fun AiPromptsScreen(
    configured: Boolean,
    groups: List<PromptGroupVd>,
    showHidden: Boolean,
    hasHiddenPrompts: Boolean,
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
    helpBody: String,
    helpReadMoreUrl: String,
    // F40: all prompts can be copied; only non-read-only (user) prompts can be moved to another
    // category (mirrors classic AiSettingsActivity.showPromptContextMenu/showMoveToCategoryDialog).
    onCopyPrompt: (String) -> Unit = {},
    onMovePromptToCategory: (String, String?) -> Unit = { _, _ -> },
    categoriesProvider: () -> List<PromptCategoryVd> = { emptyList() },
    initiallyHelpDialogOpen: Boolean = false,
    initiallyOverflowMenuOpen: Boolean = false,
    initiallySearchOpen: Boolean = false,
    initiallyFilter: PromptListFilter = PromptListFilter(),
) {
    val strings = LocalStrings.current

    var showNewCategoryDialog by remember { mutableStateOf(false) }
    var renameCategoryTarget by remember { mutableStateOf<PromptCategoryVd?>(null) }
    var deleteCategoryTarget by remember { mutableStateOf<PromptCategoryVd?>(null) }
    var deletePromptTarget by remember { mutableStateOf<PromptVd?>(null) }
    var moveToCategoryTarget by remember { mutableStateOf<PromptVd?>(null) }
    var showHelp by remember { mutableStateOf(initiallyHelpDialogOpen) }
    // 17f: search + filter are SCREEN-LOCAL — filtering is pure over the already-resolved [groups],
    // so hoisting to the host (as MyDocumentsScreen does for its DB-backed query) would add wiring
    // that buys nothing here.
    var searchOpen by remember { mutableStateOf(initiallySearchOpen) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(initiallyFilter) }
    var filterSheetOpen by remember { mutableStateOf(false) }
    val visibleGroups = remember(groups, query, filter) { filterPromptGroups(groups, query, filter) }

    AbScaffold(
        title = strings.aiPromptsTitle,
        onNavigateUp = onUp,
        actions = {
            if (configured) {
                if (!searchOpen) {
                    AbActionIcon(Icons.Filled.Search, contentDescription = strings.search) { searchOpen = true }
                }
                AbActionIcon(Icons.Filled.Add, contentDescription = strings.newPrompt, onClick = onNewPrompt)
                // F41: Connection settings holds important settings — surfaced as a top-bar action
                // (Android showAsAction="ifRoom" parity) instead of being buried in the overflow.
                AbActionIcon(
                    Icons.Filled.Settings,
                    contentDescription = strings.connectionSettingsMenuLabel,
                    onClick = onOpenConnectionSettings,
                )
                AbOverflowMenu(contentDescription = null, initiallyExpanded = initiallyOverflowMenuOpen) { close ->
                    AbMenuItem(
                        text = strings.newCategory,
                        onClick = { close(); showNewCategoryDialog = true },
                        icon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) },
                    )
                    if (hasHiddenPrompts) {
                        AbMenuItem(
                            text = strings.restoreHiddenPromptsLabel,
                            onClick = { close(); onSetShowHidden(!showHidden) },
                            icon = { Icon(Icons.Filled.VisibilityOff, contentDescription = null) },
                            checkable = true,
                            checked = showHidden,
                        )
                    }
                    AbMenuItem(
                        text = strings.exportPromptsCsv,
                        onClick = { close(); onExportCsv() },
                        icon = { Icon(Icons.Filled.FileUpload, contentDescription = null) },
                    )
                    AbMenuItem(
                        text = strings.importPromptsCsv,
                        onClick = { close(); onImportCsv() },
                        icon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
                    )
                    AbMenuItem(text = strings.helpLabel, onClick = { close(); showHelp = true }, icon = AbHelpMenuIcon)
                }
            }
        },
        search = if (searchOpen) AbTopBarSearchState(query = query, imeRequest = AbSearchImeRequest.Focus) else null,
        searchCallbacks = if (searchOpen) {
            AbTopBarSearchCallbacks(
                onQueryChange = { query = it },
                // Leaving search mode clears BOTH the query and the filters: a constraint the user
                // can no longer see is indistinguishable from missing data.
                onClose = { searchOpen = false; query = ""; filter = PromptListFilter() },
                onImeRequestHandled = {},
            )
        } else null,
        searchActions = {
            if (searchOpen) {
                FilterAction(filter.isActive, strings.promptFilterTitle) { filterSheetOpen = true }
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
            // Based on visibleGroups (not groups): an over-narrow search/filter combination must
            // show the empty-state message rather than a blank list.
            val totalPrompts = visibleGroups.sumOf { it.prompts.size }
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
                    groups = visibleGroups,
                    strings = strings,
                    onOpenPrompt = onOpenPrompt,
                    onToggleFavorite = onToggleFavorite,
                    onSetPromptHidden = onSetPromptHidden,
                    onSetCategoryHidden = onSetCategoryHidden,
                    onMovePrompt = onMovePrompt,
                    onMoveCategory = onMoveCategory,
                    onCopyPrompt = onCopyPrompt,
                    onMoveToCategoryRequest = { moveToCategoryTarget = it },
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
    moveToCategoryTarget?.let { prompt ->
        // Mirrors classic showMoveToCategoryDialog's picker: "(uncategorized)" first, then every
        // category, single-choice, pre-selecting the prompt's current category. Categories are
        // fetched FRESH each time the dialog opens (keyed on the target prompt), so a category
        // created earlier in the same session appears without re-entering the screen (classic
        // re-queries PromptRepository.allCategories() on every open; a one-time snapshot would miss it).
        val freshCategories = remember(prompt.id) { categoriesProvider() }
        val choices = listOf(SettingsItem.Choice(value = "", label = strings.categoryNoneLabel)) +
            freshCategories.map { SettingsItem.Choice(value = it.id, label = it.name) }
        AbChoiceSheet(
            // The `moveToCategoryTarget?.let` above IS the gate; `open` exists for the call sites
            // whose state is a plain Boolean.
            open = true,
            title = strings.moveToCategoryLabel,
            choices = choices,
            selectedValue = prompt.categoryId ?: "",
            onSelect = { value -> onMovePromptToCategory(prompt.id, value.ifEmpty { null }) },
            onDismiss = { moveToCategoryTarget = null },
        )
    }
    if (showHelp) {
        AbInfoDialog(
            title = strings.helpLabel,
            body = helpBody,
            onDismiss = { showHelp = false },
            readMoreLabel = strings.helpReadMoreLink,
            readMoreUrl = helpReadMoreUrl,
        )
    }
    PromptFilterSheet(
        open = filterSheetOpen,
        filter = filter,
        categories = remember(filterSheetOpen) { categoriesProvider() },
        onApply = { filter = it },
        onDismiss = { filterSheetOpen = false },
    )
}

/** The filter affordance in the search bar's action slot. The icon differs when a filter is active:
 *  a constraint the user cannot see is indistinguishable from missing data. */
@Composable
private fun FilterAction(active: Boolean, contentDescription: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            if (active) Icons.Filled.FilterAlt else Icons.Filled.FilterAltOff,
            contentDescription = contentDescription,
            modifier = Modifier.size(AbActionIconSize),
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
    onCopyPrompt: (String) -> Unit,
    onMoveToCategoryRequest: (PromptVd) -> Unit,
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
                        onCopy = { onCopyPrompt(prompt.id) },
                        onMoveToCategoryRequest = { onMoveToCategoryRequest(prompt) },
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
            AbMenuItem(
                text = if (category.isHidden) strings.showCategoryLabel else strings.hideCategoryLabel,
                onClick = { expanded = false; onSetHidden(!category.isHidden) },
                icon = {
                    Icon(
                        if (category.isHidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = null,
                    )
                },
            )
            if (!category.isBuiltIn) {
                if (canMoveUp) {
                    AbMenuItem(
                        text = strings.moveUpLabel,
                        onClick = { expanded = false; onMove(true) },
                        icon = { Icon(Icons.Filled.ArrowUpward, contentDescription = null) },
                    )
                }
                if (canMoveDown) {
                    AbMenuItem(
                        text = strings.moveDownLabel,
                        onClick = { expanded = false; onMove(false) },
                        icon = { Icon(Icons.Filled.ArrowDownward, contentDescription = null) },
                    )
                }
                AbMenuItem(
                    text = strings.rename,
                    onClick = { expanded = false; onRenameRequest() },
                    icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                )
                AbMenuItem(
                    text = strings.deleteCategoryLabel,
                    onClick = { expanded = false; onDeleteRequest() },
                    icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                )
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
    onCopy: () -> Unit,
    onMoveToCategoryRequest: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    // 17f: the star LEADS the row (the maintainer's ask — the choosing affordance on the leading
    // edge), leaving the ⋮ alone on the trailing edge.
    val meta = remember(prompt, strings) {
        val type = when (promptTypeOf(prompt)) {
            PromptType.BUILT_IN -> strings.builtInPrompt
            PromptType.ADDON -> strings.addonPromptBadge(prompt.sourceModule.orEmpty())
            PromptType.USER -> null
        }
        val targets = prompt.contexts
            .filter { it in PromptContextIds.ordered }
            .sortedBy { PromptContextIds.ordered.indexOf(it) }
            .joinToString(", ") { promptContextLabel(it, strings) }
            .ifBlank { null }
        listOfNotNull(type, targets).joinToString(" · ")
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
        Column(
            modifier = Modifier
                .weight(1f)
                .alpha(if (prompt.isHidden) 0.5f else 1f)
                .clickable(onClick = onOpenPrompt)
                .padding(vertical = 12.dp, horizontal = 4.dp),
        ) {
            Text(
                if (prompt.isHidden) "${prompt.name} (${strings.hiddenSuffix})" else prompt.name,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (prompt.description.isNotBlank()) {
                Text(prompt.description, style = MaterialTheme.typography.bodySmall)
            }
            // The third line classic had and the port dropped: the type marking and the targets.
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // F40: Copy is available for EVERY prompt (built-in, add-on, user), so the overflow
        // affordance is now always shown — there is no longer a "no available action" prompt.
        PromptRowOverflow(
            prompt = prompt,
            canMoveUp = canMoveUp,
            canMoveDown = canMoveDown,
            strings = strings,
            onSetHidden = onSetHidden,
            onMove = onMove,
            onCopy = onCopy,
            onMoveToCategoryRequest = onMoveToCategoryRequest,
            onDeleteRequest = onDeleteRequest,
        )
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
    onCopy: () -> Unit,
    onMoveToCategoryRequest: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Filled.MoreVert, contentDescription = null) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (prompt.isBuiltIn) {
                AbMenuItem(
                    text = if (prompt.isHidden) strings.restoreLabel else strings.hidePromptLabel,
                    onClick = { expanded = false; onSetHidden(!prompt.isHidden) },
                    icon = {
                        Icon(
                            if (prompt.isHidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = null,
                        )
                    },
                )
            }
            // F40: Copy mirrors classic showPromptContextMenu — available for ALL prompts
            // (built-in/add-on/user), unlike the reorder/move-to-category/delete actions below.
            AbMenuItem(
                text = strings.copyLabel,
                onClick = { expanded = false; onCopy() },
                icon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
            )
            if (!prompt.isReadOnly) {
                if (canMoveUp) {
                    AbMenuItem(
                        text = strings.moveUpLabel,
                        onClick = { expanded = false; onMove(true) },
                        icon = { Icon(Icons.Filled.ArrowUpward, contentDescription = null) },
                    )
                }
                if (canMoveDown) {
                    AbMenuItem(
                        text = strings.moveDownLabel,
                        onClick = { expanded = false; onMove(false) },
                        icon = { Icon(Icons.Filled.ArrowDownward, contentDescription = null) },
                    )
                }
                AbMenuItem(
                    text = strings.moveToCategoryLabel,
                    onClick = { expanded = false; onMoveToCategoryRequest() },
                    icon = { Icon(Icons.Filled.DriveFileMove, contentDescription = null) },
                )
                AbMenuItem(
                    text = strings.deleteLabel,
                    onClick = { expanded = false; onDeleteRequest() },
                    icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                )
            }
        }
    }
}
