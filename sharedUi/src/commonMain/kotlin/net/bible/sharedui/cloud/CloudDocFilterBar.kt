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
package net.bible.sharedui.cloud

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.cloud.CloudDocFilter
import net.bible.sharedcore.navigation.DocArrangement
import net.bible.sharedcore.navigation.DocCategory
import net.bible.sharedcore.navigation.DocGroupBy
import net.bible.sharedcore.navigation.DocSortKey
import net.bible.sharedui.components.AbArrangementLabels
import net.bible.sharedui.components.AbArrangementSheet
import net.bible.sharedui.components.AbFilterChip
import net.bible.sharedui.components.AbFilterChipBar
import net.bible.sharedui.components.AbSearchableOptionSheet
import net.bible.sharedui.navigation.LocalCategoryIcon
import net.bible.sharedui.navigation.documentGroupKeyLabel
import net.bible.sharedui.navigation.documentSortKeyLabel
import net.bible.sharedui.strings.LocalStrings

/**
 * The cloud documents filter row: mirrors [net.bible.sharedui.navigation.DocumentFilterBar]'s shape
 * (a single [openSheet][CloudFilterSheet] state so only one sheet is open at a time, two
 * [AbFilterChip]s, the result count, and the Tune button) but for the cloud list's own status and
 * category filters. Unlike the document bar's tuned 2/3 : 1/3 language/type split, neither label set
 * here is systematically longer than the other, so both chips share the shortfall evenly.
 *
 * The arrangement sheet reuses [documentSortKeyLabel]/[documentGroupKeyLabel] — the cloud screen's
 * sort/group keys (STATUS/TYPE/NAME/SIZE, NONE/TYPE/STATUS) are a subset of the document list's own,
 * so no cloud-specific label mapping is needed — and passes `repositories = emptyList()` (a cloud
 * listing has no repository concept; [AbArrangementSheetContent][net.bible.sharedui.components.AbArrangementSheetContent]
 * already omits that section when the list is empty). It also carries the "show removed documents"
 * toggle via [AbArrangementSheet]'s `extraContent` slot — moved here from the screen's overflow menu
 * (round 17e-2) so the setting has exactly one home.
 */
@Composable
fun CloudDocFilterBar(
    statusFilters: List<Pair<CloudDocFilter, String>>,
    selectedStatusFilter: CloudDocFilter,
    onStatusFilterChange: (CloudDocFilter) -> Unit,
    categoryFilters: List<Pair<DocCategory?, String>>,
    selectedCategoryFilter: DocCategory?,
    onCategoryFilterChange: (DocCategory?) -> Unit,
    resultCount: String,
    arrangement: DocArrangement,
    groupKeys: List<DocGroupBy>,
    rememberArrangement: Boolean,
    arrangementIsDefault: Boolean,
    onMoveSort: (from: Int, to: Int) -> Unit,
    onToggleSortDirection: (DocSortKey) -> Unit,
    onGroupByChange: (DocGroupBy) -> Unit,
    onRememberChange: (Boolean) -> Unit,
    onResetArrangement: () -> Unit,
    showRemoved: Boolean,
    onShowRemovedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    var openSheet by remember { mutableStateOf(CloudFilterSheet.None) }

    val selectedStatusPair = statusFilters.firstOrNull { it.first == selectedStatusFilter }
        ?: (selectedStatusFilter to selectedStatusFilter.name)
    val selectedCategoryPair = categoryFilters.firstOrNull { it.first == selectedCategoryFilter }
        ?: (selectedCategoryFilter to strings.all)

    AbFilterChipBar(
        chips = listOf(
            AbFilterChip(
                label = selectedStatusPair.second,
                contentDescription = strings.docSortStatus,
                leadingIcon = null,
                shortfallWeight = 1f / 2f,
                onClick = { openSheet = CloudFilterSheet.Status },
            ),
            AbFilterChip(
                label = selectedCategoryPair.second,
                // On the chip itself, not the leading icon: that slot renders an empty spacer for
                // "All categories", the same trap DocumentFilterBar's type chip hit.
                contentDescription = strings.documentTypeLabel,
                leadingIcon = { CategoryFilterIcon(selectedCategoryFilter, contentDescription = null, size = AssistChipDefaults.IconSize) },
                shortfallWeight = 1f / 2f,
                onClick = { openSheet = CloudFilterSheet.Category },
            ),
        ),
        resultCount = resultCount,
        onMoreFilters = { openSheet = CloudFilterSheet.Arrangement },
        moreFiltersActive = !arrangementIsDefault,
        modifier = modifier,
    )

    when (openSheet) {
        CloudFilterSheet.None -> Unit
        CloudFilterSheet.Status -> AbSearchableOptionSheet(
            options = statusFilters,
            selected = selectedStatusPair,
            optionLabel = { it.second },
            onSelect = { onStatusFilterChange(it.first); openSheet = CloudFilterSheet.None },
            onDismiss = { openSheet = CloudFilterSheet.None },
            searchPlaceholder = null, // a handful of statuses; a search field would be noise
        )
        CloudFilterSheet.Category -> AbSearchableOptionSheet(
            options = categoryFilters,
            selected = selectedCategoryPair,
            optionLabel = { it.second },
            onSelect = { onCategoryFilterChange(it.first); openSheet = CloudFilterSheet.None },
            onDismiss = { openSheet = CloudFilterSheet.None },
            searchPlaceholder = null, // seven items, same as the document type sheet
            leadingIcon = { CategoryFilterIcon(it.first, contentDescription = null) },
        )
        CloudFilterSheet.Arrangement -> AbArrangementSheet(
            labels = AbArrangementLabels(
                title = strings.docArrangeTitle,
                repositoryLabel = strings.docArrangeRepository,
                allRepositories = strings.docArrangeAllRepositories,
                sortLabel = strings.docArrangeSort,
                groupLabel = strings.docArrangeGroupBy,
                rememberLabel = strings.docArrangeRemember,
                resetLabel = strings.docArrangeReset,
                reorderLabel = strings.docArrangeReorder,
                ascending = strings.docSortAscending,
                descending = strings.docSortDescending,
                sortKeyLabel = { documentSortKeyLabel(it, strings) },
                groupKeyLabel = { documentGroupKeyLabel(it, strings) },
            ),
            sort = arrangement.sort,
            groupBy = arrangement.groupBy,
            groupKeys = groupKeys,
            repositories = emptyList(),
            selectedRepository = arrangement.repository,
            rememberSettings = rememberArrangement,
            resultCount = resultCount,
            onMoveSort = onMoveSort,
            onToggleDirection = onToggleSortDirection,
            onGroupByChange = onGroupByChange,
            onRepositoryChange = {}, // unreachable: the cloud controller has no repository concept
            onRememberChange = onRememberChange,
            onReset = onResetArrangement,
            onDismiss = { openSheet = CloudFilterSheet.None },
            extraContent = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = showRemoved, role = Role.Switch, onValueChange = onShowRemovedChange)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.cloudDocShowRemoved, modifier = Modifier.weight(1f))
                    Switch(checked = showRemoved, onCheckedChange = null)
                }
            },
        )
    }
}

private enum class CloudFilterSheet { None, Status, Category, Arrangement }

/**
 * The category chip/sheet leading icon: a reserved [size] slot for every option — including "All
 * categories" ([category] null), which renders an empty spacer rather than a fallback icon — so
 * labels stay aligned. Mirrors [net.bible.sharedui.navigation.TypeFilterIcon]'s reserved-slot rule.
 */
@Composable
private fun CategoryFilterIcon(category: DocCategory?, contentDescription: String?, size: Dp = 24.dp) {
    if (category == null) {
        Spacer(Modifier.size(size))
    } else {
        Icon(
            painter = LocalCategoryIcon.current(category),
            contentDescription = contentDescription,
            modifier = Modifier.size(size),
        )
    }
}
