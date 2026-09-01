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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PromptListFilterTest {

    private fun prompt(
        id: String,
        name: String = id,
        description: String = "",
        categoryId: String? = null,
        builtIn: Boolean = false,
        favorite: Boolean = false,
        contexts: Set<String> = emptySet(),
        sourceModule: String? = null,
    ) = PromptVd(
        id = id, name = name, description = description, categoryId = categoryId,
        isBuiltIn = builtIn, isReadOnly = builtIn || sourceModule != null,
        isFavorite = favorite, isHidden = false, contexts = contexts, sourceModule = sourceModule,
    )

    private val cat = PromptCategoryVd(id = "C1", name = "Study", isBuiltIn = false, isHidden = false)

    private val groups = listOf(
        PromptGroupVd(category = null, isFavorites = true, prompts = listOf(prompt("fav", favorite = true))),
        PromptGroupVd(category = null, isFavorites = false, prompts = listOf(prompt("loose", name = "Summarize"))),
        PromptGroupVd(
            category = cat, isFavorites = false,
            prompts = listOf(
                prompt("a", name = "Explain verse", categoryId = "C1", builtIn = true, contexts = setOf("VERSE_SELECTION")),
                prompt("b", name = "Translate", description = "into plain English", categoryId = "C1", sourceModule = "Extras"),
            ),
        ),
    )

    private val none = PromptListFilter()

    @Test fun blankQueryAndInactiveFilterReturnTheGroupsUnchanged() {
        assertEquals(groups, filterPromptGroups(groups, "  ", none))
        assertTrue(!none.isActive)
    }

    @Test fun queryMatchesNameAndDescription_caseInsensitively() {
        val byName = filterPromptGroups(groups, "TRANSL", none)
        assertEquals(listOf("b"), byName.flatMap { g -> g.prompts.map { it.id } })

        val byDescription = filterPromptGroups(groups, "plain english", none)
        assertEquals(listOf("b"), byDescription.flatMap { g -> g.prompts.map { it.id } })
    }

    @Test fun emptiedGroupsDisappear_butTheStructureSurvives() {
        val result = filterPromptGroups(groups, "Explain", none)
        assertEquals(1, result.size)
        assertEquals(cat, result.single().category)
    }

    @Test fun favoritesOnly_keepsOnlyFavourites() {
        val result = filterPromptGroups(groups, "", PromptListFilter(favoritesOnly = true))
        assertEquals(listOf("fav"), result.flatMap { g -> g.prompts.map { it.id } })
    }

    @Test fun contextFilterMatchesAnySelectedTarget() {
        val result = filterPromptGroups(groups, "", PromptListFilter(contexts = setOf("VERSE_SELECTION")))
        assertEquals(listOf("a"), result.flatMap { g -> g.prompts.map { it.id } })
    }

    @Test fun categoryFilterUsesEmptyStringForTheUncategorizedBucket() {
        val inCategory = filterPromptGroups(groups, "", PromptListFilter(categoryIds = setOf("C1")))
        assertEquals(listOf("a", "b"), inCategory.flatMap { g -> g.prompts.map { it.id } })

        val uncategorized = filterPromptGroups(groups, "", PromptListFilter(categoryIds = setOf("")))
        assertEquals(listOf("fav", "loose"), uncategorized.flatMap { g -> g.prompts.map { it.id } })
    }

    @Test fun typeFilterSeparatesBuiltInAddonAndUser() {
        assertEquals(PromptType.BUILT_IN, promptTypeOf(prompt("x", builtIn = true)))
        assertEquals(PromptType.ADDON, promptTypeOf(prompt("y", sourceModule = "Extras")))
        assertEquals(PromptType.USER, promptTypeOf(prompt("z")))
        // An add-on prompt that is ALSO flagged built-in is an add-on: the module is the more
        // specific fact, and it is what the row shows.
        assertEquals(PromptType.ADDON, promptTypeOf(prompt("w", builtIn = true, sourceModule = "Extras")))

        val result = filterPromptGroups(groups, "", PromptListFilter(types = setOf(PromptType.ADDON)))
        assertEquals(listOf("b"), result.flatMap { g -> g.prompts.map { it.id } })
    }

    @Test fun dimensionsCombineWithAnd_andAnEmptyDimensionConstrainsNothing() {
        val result = filterPromptGroups(
            groups, "e",
            PromptListFilter(types = setOf(PromptType.BUILT_IN), contexts = setOf("VERSE_SELECTION")),
        )
        assertEquals(listOf("a"), result.flatMap { g -> g.prompts.map { it.id } })
    }

    @Test fun isActiveReportsAnyConstraint() {
        assertTrue(PromptListFilter(favoritesOnly = true).isActive)
        assertTrue(PromptListFilter(contexts = setOf("X")).isActive)
        assertTrue(PromptListFilter(categoryIds = setOf("")).isActive)
        assertTrue(PromptListFilter(types = setOf(PromptType.USER)).isActive)
    }
}
