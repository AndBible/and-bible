/*
 * Copyright (c) 2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.view.activity.page

import net.bible.android.activity.R

/**
 * Derived state used to decide whether a selection menu item belongs in the current context.
 */
data class SelectionMenuContext(
    val hasSelection: Boolean,
    val hasText: Boolean,
    val hasVerseRange: Boolean,
    val hasResolvableRef: Boolean,
    val hasDictionaries: Boolean,
    val llmConfigured: Boolean,
    val paragraphBreakEnabled: Boolean,
    val disableTwoStepBookmarking: Boolean,
    val isBibleDocument: Boolean,
    val supportsProcessTextActions: Boolean,
    val currentSelectionText: String?,
)

/**
 * Centralises visibility rules for the built-in text selection menu items so the menu and
 * handlers stay in sync.
 */
object SelectionMenuRules {
    val managedItemIds = setOf(
        R.id.add_bookmark,
        R.id.add_bookmark_selection,
        R.id.add_bookmark_whole_verse,
        R.id.add_paragraph_break,
        R.id.compare,
        R.id.memorize,
        R.id.share_verses,
        R.id.copy,
        R.id.search,
        R.id.open_ref,
        R.id.web_search,
        R.id.lookup_dictionary,
        R.id.llm_action,
    )

    fun isManagedItem(itemId: Int): Boolean = itemId in managedItemIds

    fun isVisible(itemId: Int, context: SelectionMenuContext): Boolean {
        val hasAnyText = context.currentSelectionText != null
        return when (itemId) {
            R.id.add_bookmark -> context.hasSelection && !(context.isBibleDocument && context.disableTwoStepBookmarking)
            R.id.add_bookmark_selection,
            R.id.add_bookmark_whole_verse -> context.hasSelection && context.isBibleDocument && context.disableTwoStepBookmarking
            R.id.add_paragraph_break -> context.paragraphBreakEnabled
            R.id.compare,
            R.id.share_verses -> context.isBibleDocument
            R.id.memorize -> context.isBibleDocument && context.hasVerseRange
            R.id.copy -> context.supportsProcessTextActions && !context.isBibleDocument && hasAnyText
            R.id.search -> hasAnyText && !context.hasResolvableRef
            R.id.open_ref -> context.hasResolvableRef
            R.id.web_search -> hasAnyText
            R.id.lookup_dictionary -> hasAnyText && context.hasDictionaries
            R.id.llm_action -> hasAnyText && context.llmConfigured
            else -> false
        }
    }
}
