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

package net.bible.sharedcore.ai

/** Where a prompt came from — the axis the manager's type filter offers, and the marking its row shows. */
enum class PromptType { BUILT_IN, ADDON, USER }

/** An add-on module is the more specific fact, so it wins over the built-in flag. */
fun promptTypeOf(prompt: PromptVd): PromptType = when {
    prompt.sourceModule != null -> PromptType.ADDON
    prompt.isBuiltIn -> PromptType.BUILT_IN
    else -> PromptType.USER
}

/**
 * The prompt manager's filter state (17f). Each dimension is independent and combines with AND; an
 * EMPTY dimension constrains nothing, it never means "match nothing".
 *
 * [categoryIds] uses `""` for the uncategorized bucket, matching the `""` sentinel the move-to-
 * category picker already uses for "(uncategorized)".
 */
data class PromptListFilter(
    val favoritesOnly: Boolean = false,
    val contexts: Set<String> = emptySet(),
    val categoryIds: Set<String> = emptySet(),
    val types: Set<PromptType> = emptySet(),
) {
    val isActive: Boolean
        get() = favoritesOnly || contexts.isNotEmpty() || categoryIds.isNotEmpty() || types.isNotEmpty()
}

/**
 * Filters the manager's grouped list by [query] and [filter], INSIDE the group structure: a group
 * whose prompts all fall out disappears, but the surviving groups keep their identity and order. A
 * flat result set would throw away the categories the screen is built around.
 *
 * [query] matches name and description, case-insensitively. It deliberately does NOT match the
 * prompt template: a hit on words the row does not show reads as a bug.
 *
 * A blank [query] with an inactive [filter] returns [groups] unchanged — including the empty user
 * categories the service deliberately emits, which only a real constraint may remove.
 */
fun filterPromptGroups(
    groups: List<PromptGroupVd>,
    query: String,
    filter: PromptListFilter,
): List<PromptGroupVd> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty() && !filter.isActive) return groups
    return groups.mapNotNull { group ->
        val kept = group.prompts.filter { matches(it, needle, filter) }
        if (kept.isEmpty()) null else group.copy(prompts = kept)
    }
}

private fun matches(prompt: PromptVd, lowerQuery: String, filter: PromptListFilter): Boolean {
    if (lowerQuery.isNotEmpty() &&
        !prompt.name.lowercase().contains(lowerQuery) &&
        !prompt.description.lowercase().contains(lowerQuery)
    ) return false
    if (filter.favoritesOnly && !prompt.isFavorite) return false
    if (filter.contexts.isNotEmpty() && prompt.contexts.none { it in filter.contexts }) return false
    if (filter.categoryIds.isNotEmpty() && (prompt.categoryId ?: "") !in filter.categoryIds) return false
    if (filter.types.isNotEmpty() && promptTypeOf(prompt) !in filter.types) return false
    return true
}
