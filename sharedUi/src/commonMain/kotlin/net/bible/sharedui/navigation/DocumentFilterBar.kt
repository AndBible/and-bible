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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.navigation.DocTypeFilter
import net.bible.sharedcore.navigation.LangOption
import net.bible.sharedcore.navigation.iconCategory
import net.bible.sharedui.components.AbSearchableOptionSheet
import net.bible.sharedui.strings.LocalStrings

/**
 * The document-selection filter row: two chips that each open their own bottom sheet, plus the
 * result count. Replaces the previous tappable "Finnish · All types" summary row and the two
 * full-width controls it expanded to; the chips carry the current value, so nothing is hidden.
 *
 * Exactly one sheet can be open at a time (a single [openSheet] state), which preserves the
 * screen's no-nested-bottom-sheets invariant.
 *
 * Deliberately search-agnostic: it takes no query state. Moving the search field into the
 * toolbar is a separate, later change that must not have to touch this file.
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
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    var openSheet by remember { mutableStateOf(FilterSheet.None) }

    val languageOptions: List<LangOption?> = remember(languages) { listOf<LangOption?>(null) + languages }
    val selectedTypePair = typeFilters.firstOrNull { it.first == selectedTypeFilter }
        ?: (selectedTypeFilter to selectedTypeFilter.name)

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The chips live in their own weighted row so THEY absorb the overflow. A trailing
        // weighted Spacer would instead make the LAST child — the result count — the casualty.
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(
                onClick = { openSheet = FilterSheet.Language },
                label = {
                    Text(
                        selectedLanguage?.displayName ?: strings.all,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Language,
                        contentDescription = strings.languageLabel,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
                trailingIcon = {
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
                },
                // Language display names are the long ones, so this chip yields first.
                modifier = Modifier.weight(1f, fill = false),
            )
            AssistChip(
                onClick = { openSheet = FilterSheet.Type },
                label = { Text(selectedTypePair.second, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = if (selectedTypeFilter.iconCategory == null) {
                    // No reserved-slot spacer here: that's only needed in the sheet's LIST, to keep
                    // every row's label aligned. A single chip has nothing to align against, so
                    // dropping the slot entirely reclaims real width for the label under overflow.
                    null
                } else {
                    { TypeFilterIcon(selectedTypeFilter, contentDescription = strings.documentTypeLabel) }
                },
                trailingIcon = {
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
                },
            )
        }
        Text(
            text = resultCount,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(start = 8.dp),
        )
    }

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
    }
}

private enum class FilterSheet { None, Language, Type }

/**
 * The leading icon for a document-type filter. [DocTypeFilter.ALL] spans every category and so
 * has no icon of its own — it renders an EMPTY slot of exactly the icon's size, so the labels in
 * the type sheet stay aligned instead of shifting left on one row.
 */
@Composable
private fun TypeFilterIcon(filter: DocTypeFilter, contentDescription: String?) {
    val category = filter.iconCategory
    if (category == null) {
        Spacer(Modifier.size(TypeFilterIconSize))
    } else {
        Icon(
            painter = LocalCategoryIcon.current(category),
            contentDescription = contentDescription,
            modifier = Modifier.size(TypeFilterIconSize),
        )
    }
}

private val TypeFilterIconSize = 24.dp
