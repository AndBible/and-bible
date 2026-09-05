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

package net.bible.sharedcore.settings

/**
 * Filters a flat settings list to the rows whose title or summary contain [query] (case-insensitive),
 * keeping a [SettingsItem.Category] header only when at least one of the rows that follow it (up to the
 * next category) survives — the Compose analogue of the classic `PreferenceSearchHelper` action-bar
 * filter, including its empty-category hiding. `PreferenceSearchHelper.kt` was deleted in the
 * "Z-late S12: delete the classic settings cluster" commit; see that commit (and its history) for
 * the classic-parity source of truth this was written against.
 *
 * A blank [query] returns [items] unchanged. Pure; call with an already visibility-filtered list
 * (`SettingsScreenState.visibleItems`) so hidden rows never re-appear via search.
 */
fun filterSettingsItems(items: List<SettingsItem>, query: String): List<SettingsItem> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return items
    val lower = trimmed.lowercase()

    // A category header is emitted lazily — only when the first matching child under it is found —
    // so a category with no surviving children never appears (classic empty-category hiding).
    val result = mutableListOf<SettingsItem>()
    var pendingCategory: SettingsItem.Category? = null
    var categoryHasMatch = false

    for (item in items) {
        if (item is SettingsItem.Category) {
            pendingCategory = item
            categoryHasMatch = false
            continue
        }
        if (matches(item, lower)) {
            if (pendingCategory != null && !categoryHasMatch) {
                result.add(pendingCategory)
                categoryHasMatch = true
            }
            result.add(item)
        }
    }
    return result
}

/** Substring match against the row's title and (when present) summary, both already lowercased query. */
private fun matches(item: SettingsItem, lowerQuery: String): Boolean {
    val title = titleOf(item)?.lowercase() ?: ""
    val summary = summaryOf(item)?.lowercase() ?: ""
    return title.contains(lowerQuery) || summary.contains(lowerQuery)
}

private fun titleOf(item: SettingsItem): String? = when (item) {
    is SettingsItem.Category -> item.title
    is SettingsItem.SwitchRow -> item.title
    is SettingsItem.ListChoiceRow -> item.title
    is SettingsItem.TextInputRow -> item.title
    is SettingsItem.SliderRow -> item.title
    is SettingsItem.MultiSelectRow -> item.title
    is SettingsItem.NavigationRow -> item.title
    is SettingsItem.InfoRow -> item.title
}

private fun summaryOf(item: SettingsItem): String? = when (item) {
    is SettingsItem.Category -> null
    is SettingsItem.SwitchRow -> item.summary
    is SettingsItem.ListChoiceRow -> item.summary
    is SettingsItem.TextInputRow -> item.summary
    is SettingsItem.SliderRow -> null            // SliderRow has no summary field
    is SettingsItem.MultiSelectRow -> item.summary
    is SettingsItem.NavigationRow -> item.summary
    is SettingsItem.InfoRow -> item.summary
}
