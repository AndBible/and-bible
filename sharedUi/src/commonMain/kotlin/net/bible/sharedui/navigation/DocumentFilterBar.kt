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
package net.bible.sharedui.navigation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.navigation.DocArrangement
import net.bible.sharedcore.navigation.DocGroupBy
import net.bible.sharedcore.navigation.DocSortKey
import net.bible.sharedcore.navigation.DocTypeFilter
import net.bible.sharedcore.navigation.LangOption
import net.bible.sharedcore.navigation.iconCategory
import net.bible.sharedui.components.AbArrangementLabels
import net.bible.sharedui.components.AbArrangementSheet
import net.bible.sharedui.components.AbFilterChip
import net.bible.sharedui.components.AbFilterChipBar
import net.bible.sharedui.components.AbSearchableOptionSheet
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.Strings

/**
 * The document-selection filter row: two chips that each open their own bottom sheet, plus the
 * result count. Replaces the previous tappable "Finnish · All types" summary row and the two
 * full-width controls it expanded to; the chips carry the current value, so nothing is hidden.
 *
 * Exactly one sheet can be open at a time (a single [openSheet] state), which preserves the
 * screen's no-nested-bottom-sheets invariant.
 *
 * Search-agnostic: it takes no query state. The controller decides what
 * [selectedTypeFilter] means under a live query (F72: `DocumentSelectionController.shownTypeFilter`),
 * so this bar always draws the filter that actually applies.
 */
@Composable
fun DocumentFilterBar(
    languages: List<LangOption>,
    selectedLanguage: LangOption?,
    onLanguageChange: (LangOption?) -> Unit,
    typeFilters: List<Pair<DocTypeFilter, String>>,
    selectedTypeFilter: DocTypeFilter,
    onTypeFilterChange: (DocTypeFilter) -> Unit,
    resultCount: String,
    arrangement: DocArrangement,
    groupKeys: List<DocGroupBy>,
    repositories: List<String>,
    rememberArrangement: Boolean,
    arrangementIsDefault: Boolean,
    onMoveSort: (from: Int, to: Int) -> Unit,
    onToggleSortDirection: (DocSortKey) -> Unit,
    onGroupByChange: (DocGroupBy) -> Unit,
    onRepositoryChange: (String?) -> Unit,
    onRememberChange: (Boolean) -> Unit,
    onResetArrangement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    var openSheet by remember { mutableStateOf(FilterSheet.None) }

    val languageOptions: List<LangOption?> = remember(languages) { listOf<LangOption?>(null) + languages }
    val selectedTypePair = typeFilters.firstOrNull { it.first == selectedTypeFilter }
        ?: (selectedTypeFilter to selectedTypeFilter.name)

    AbFilterChipBar(
        chips = listOf(
            AbFilterChip(
                label = selectedLanguage?.displayName ?: strings.all,
                contentDescription = strings.languageLabel,
                leadingIcon = {
                    Icon(Icons.Filled.Language, contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize))
                },
                // Language names are the long, variable labels; the type set is short and
                // bounded — the same 2/3 : 1/3 split ShrinkingChipPair was tuned to.
                shortfallWeight = 2f / 3f,
                onClick = { openSheet = FilterSheet.Language },
            ),
            AbFilterChip(
                label = selectedTypePair.second,
                // On the chip itself, not on the leading icon slot: that slot disappears
                // entirely when the filter is ALL, and a description hung off it went silent
                // in the chip's default state.
                contentDescription = strings.documentTypeLabel,
                leadingIcon = if (selectedTypeFilter.iconCategory == null) null else {
                    { TypeFilterIcon(selectedTypeFilter, contentDescription = null, size = AssistChipDefaults.IconSize) }
                },
                shortfallWeight = 1f / 3f,
                onClick = { openSheet = FilterSheet.Type },
            ),
        ),
        resultCount = resultCount,
        onMoreFilters = { openSheet = FilterSheet.Arrangement },
        moreFiltersActive = !arrangementIsDefault,
        modifier = modifier,
    )

    when (openSheet) {
        FilterSheet.None -> Unit
        FilterSheet.Language -> AbSearchableOptionSheet(
            options = languageOptions,
            selected = selectedLanguage,
            optionLabel = { it?.displayName ?: strings.all },
            onSelect = { onLanguageChange(it); openSheet = FilterSheet.None },
            onDismiss = { openSheet = FilterSheet.None },
            searchPlaceholder = strings.search,
        )
        FilterSheet.Type -> AbSearchableOptionSheet(
            options = typeFilters,
            selected = selectedTypePair,
            optionLabel = { it.second },
            onSelect = { onTypeFilterChange(it.first); openSheet = FilterSheet.None },
            onDismiss = { openSheet = FilterSheet.None },
            searchPlaceholder = null, // seven items; a search field would be noise
            leadingIcon = { TypeFilterIcon(it.first, contentDescription = null) },
        )
        FilterSheet.Arrangement -> AbArrangementSheet(
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
            repositories = repositories,
            selectedRepository = arrangement.repository,
            rememberSettings = rememberArrangement,
            resultCount = resultCount,
            onMoveSort = onMoveSort,
            onToggleDirection = onToggleSortDirection,
            onGroupByChange = onGroupByChange,
            onRepositoryChange = onRepositoryChange,
            onRememberChange = onRememberChange,
            onReset = onResetArrangement,
            onDismiss = { openSheet = FilterSheet.None },
        )
    }
}

private enum class FilterSheet { None, Language, Type, Arrangement }

/**
 * The leading icon for a document-type filter. [DocTypeFilter.ALL] spans every category and so
 * has no icon of its own — it renders an EMPTY slot of exactly [size], so the labels in the type
 * sheet stay aligned instead of shifting left on one row.
 *
 * [size] is caller-supplied rather than fixed: the sheet's `ListItem` leading slot and the chip's
 * `AssistChip` leading slot want different sizes ([TypeFilterIconSize] vs
 * [AssistChipDefaults.IconSize]), and using the larger, list-appropriate size inside the chip was
 * exactly what starved the chip of width for its own label.
 *
 * Public (not `private`) so [net.bible.android.view.compose.golden.DocumentFilterBarGoldenTest] —
 * in the separate `:app` module — can capture the type sheet through the SAME leading-icon lambda
 * production uses, rather than a hand-copied re-implementation of this reserved-slot rule that
 * could silently drift from it. `internal` is not enough here: Kotlin `internal` visibility is
 * scoped to the compilation module, and `:app` is a different Gradle module from `:sharedUi`.
 */
@Composable
fun TypeFilterIcon(filter: DocTypeFilter, contentDescription: String?, size: Dp = TypeFilterIconSize) {
    val category = filter.iconCategory
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

private val TypeFilterIconSize = 24.dp

/**
 * Criterion labels. Type, language and repository deliberately reuse strings that already exist
 * and are already translated, rather than adding near-duplicates for the same concepts.
 */
fun documentSortKeyLabel(key: DocSortKey, strings: Strings): String = when (key) {
    DocSortKey.STATUS -> strings.docSortStatus
    DocSortKey.RECOMMENDED -> strings.docSortRecommended
    DocSortKey.TYPE -> strings.documentTypeLabel
    DocSortKey.NAME -> strings.docSortName
    DocSortKey.LANGUAGE -> strings.languageLabel
    DocSortKey.REPOSITORY -> strings.docArrangeRepository
    DocSortKey.SIZE -> strings.docSortSize
}

fun documentGroupKeyLabel(key: DocGroupBy, strings: Strings): String = when (key) {
    DocGroupBy.NONE -> strings.docGroupNone
    DocGroupBy.TYPE -> strings.documentTypeLabel
    DocGroupBy.LANGUAGE -> strings.languageLabel
    DocGroupBy.REPOSITORY -> strings.docArrangeRepository
    DocGroupBy.STATUS -> strings.docSortStatus
}
