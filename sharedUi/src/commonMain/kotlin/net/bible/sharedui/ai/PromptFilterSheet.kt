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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.ai.PromptCategoryVd
import net.bible.sharedcore.ai.PromptContextIds
import net.bible.sharedcore.ai.PromptListFilter
import net.bible.sharedcore.ai.PromptType
import net.bible.sharedui.components.AbSheetHeader
import net.bible.sharedui.components.AbSheetScrollBound
import net.bible.sharedui.settings.SheetConfirmRow
import net.bible.sharedui.strings.LocalStrings

/**
 * The prompt manager's filter sheet (17f): four independent dimensions as chip groups. Bespoke
 * rather than `AbMultiSelectSheet`, which models exactly ONE dimension — four of them stacked would
 * be four sheets or four confirm rows.
 *
 * The sheet edits a working copy and commits on OK, so a half-built filter can be abandoned with
 * back/✕ — the same contract `AbMultiSelectSheet` has.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PromptFilterSheet(
    open: Boolean,
    filter: PromptListFilter,
    categories: List<PromptCategoryVd>,
    onApply: (PromptListFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!open) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        PromptFilterSheetContent(
            filter = filter,
            categories = categories,
            onApply = { onApply(it); onDismiss() },
            onClose = onDismiss,
        )
    }
}

/**
 * [PromptFilterSheet]'s body — no `ModalBottomSheet` of its own, so a golden test can capture it.
 * PUBLIC for that reason, following `AbChoiceSheetContent`'s precedent: `:app`'s golden tests are a
 * different module and cannot see an `internal` composable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PromptFilterSheetContent(
    filter: PromptListFilter,
    categories: List<PromptCategoryVd>,
    onApply: (PromptListFilter) -> Unit,
    onClose: () -> Unit,
    scrollState: ScrollState = rememberScrollState(),
) {
    val strings = LocalStrings.current
    var working by remember(filter) { mutableStateOf(filter) }
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        AbSheetHeader(title = strings.promptFilterTitle, onClose = onClose)
        AbSheetScrollBound(canScrollForward = { scrollState.canScrollForward }) {
            Column(Modifier.verticalScroll(scrollState).padding(horizontal = 16.dp)) {
                FilterGroupLabel(strings.promptCategoryFavorites)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = working.favoritesOnly,
                        onClick = { working = working.copy(favoritesOnly = !working.favoritesOnly) },
                        label = { Text(strings.promptCategoryFavorites) },
                    )
                }

                FilterGroupLabel(strings.promptShowInLabel)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PromptContextIds.ordered.forEach { id ->
                        FilterChip(
                            selected = id in working.contexts,
                            onClick = { working = working.copy(contexts = working.contexts.toggle(id)) },
                            label = { Text(promptContextLabel(id, strings)) },
                        )
                    }
                }

                FilterGroupLabel(strings.promptCategoryLabel)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // "" is the uncategorized bucket, the same sentinel the move-to-category
                    // picker uses.
                    FilterChip(
                        selected = "" in working.categoryIds,
                        onClick = { working = working.copy(categoryIds = working.categoryIds.toggle("")) },
                        label = { Text(strings.promptCategoryUncategorized) },
                    )
                    categories.forEach { category ->
                        FilterChip(
                            selected = category.id in working.categoryIds,
                            onClick = { working = working.copy(categoryIds = working.categoryIds.toggle(category.id)) },
                            label = { Text(category.name) },
                        )
                    }
                }

                FilterGroupLabel(strings.promptFilterType)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        PromptType.BUILT_IN to strings.builtInPrompt,
                        PromptType.ADDON to strings.promptTypeAddon,
                        PromptType.USER to strings.promptTypeUser,
                    ).forEach { (type, label) ->
                        FilterChip(
                            selected = type in working.types,
                            onClick = { working = working.copy(types = working.types.toggle(type)) },
                            label = { Text(label) },
                        )
                    }
                }

                TextButton(
                    onClick = { working = PromptListFilter() },
                    enabled = working.isActive,
                ) { Text(strings.promptFilterClear) }
            }
        }
        SheetConfirmRow(
            confirmLabel = strings.okay,
            cancelLabel = strings.cancel,
            onConfirm = { onApply(working) },
            onCancel = onClose,
        )
    }
}

@Composable
private fun FilterGroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

/** Adds [value] if absent, removes it if present. */
private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
