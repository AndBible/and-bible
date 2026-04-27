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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionMenuRulesTest {
    private fun context(
        hasSelection: Boolean = true,
        hasText: Boolean = true,
        hasVerseRange: Boolean = false,
        hasResolvableRef: Boolean = false,
        hasDictionaries: Boolean = false,
        llmConfigured: Boolean = false,
        paragraphBreakEnabled: Boolean = true,
        disableTwoStepBookmarking: Boolean = false,
        isBibleDocument: Boolean = false,
        supportsProcessTextActions: Boolean = true,
        currentSelectionText: String? = if (hasText) "sample text" else null,
    ) = SelectionMenuContext(
        hasSelection = hasSelection,
        hasText = hasText,
        hasVerseRange = hasVerseRange,
        hasResolvableRef = hasResolvableRef,
        hasDictionaries = hasDictionaries,
        llmConfigured = llmConfigured,
        paragraphBreakEnabled = paragraphBreakEnabled,
        disableTwoStepBookmarking = disableTwoStepBookmarking,
        isBibleDocument = isBibleDocument,
        supportsProcessTextActions = supportsProcessTextActions,
        currentSelectionText = currentSelectionText,
    )

    @Test
    fun memorizeHiddenForTextOnlySelection() {
        assertFalse(SelectionMenuRules.isVisible(R.id.memorize, context(isBibleDocument = true, hasVerseRange = false)))
    }

    @Test
    fun memorizeVisibleForBibleVerseSelection() {
        assertTrue(SelectionMenuRules.isVisible(R.id.memorize, context(isBibleDocument = true, hasVerseRange = true)))
    }

    @Test
    fun shareVersesVisibleForBibleDocument() {
        assertFalse(SelectionMenuRules.isVisible(R.id.share_verses, context(isBibleDocument = false)))
        assertTrue(SelectionMenuRules.isVisible(R.id.share_verses, context(isBibleDocument = true)))
    }

    @Test
    fun compareVisibleForBibleDocument() {
        assertFalse(SelectionMenuRules.isVisible(R.id.compare, context(isBibleDocument = false)))
        assertTrue(SelectionMenuRules.isVisible(R.id.compare, context(isBibleDocument = true)))
    }

    @Test
    fun searchAndOpenRefStayMutuallyExclusive() {
        assertTrue(SelectionMenuRules.isVisible(R.id.search, context(hasResolvableRef = false)))
        assertFalse(SelectionMenuRules.isVisible(R.id.search, context(hasResolvableRef = true)))
        assertFalse(SelectionMenuRules.isVisible(R.id.open_ref, context(hasResolvableRef = false)))
        assertTrue(SelectionMenuRules.isVisible(R.id.open_ref, context(hasResolvableRef = true)))
    }

    @Test
    fun addParagraphBreakFollowsExistingSetting() {
        assertTrue(SelectionMenuRules.isVisible(R.id.add_paragraph_break, context(paragraphBreakEnabled = true)))
        assertFalse(SelectionMenuRules.isVisible(R.id.add_paragraph_break, context(paragraphBreakEnabled = false)))
    }

    @Test
    fun dictionaryAndLlmActionsRequireTheirCapabilities() {
        assertFalse(SelectionMenuRules.isVisible(R.id.lookup_dictionary, context(hasDictionaries = false)))
        assertTrue(SelectionMenuRules.isVisible(R.id.lookup_dictionary, context(hasDictionaries = true)))
        assertFalse(SelectionMenuRules.isVisible(R.id.llm_action, context(llmConfigured = false)))
        assertTrue(SelectionMenuRules.isVisible(R.id.llm_action, context(llmConfigured = true)))
    }

    @Test
    fun copyRequiresProcessTextSupport() {
        assertTrue(SelectionMenuRules.isVisible(R.id.copy, context(isBibleDocument = false, supportsProcessTextActions = true)))
        assertFalse(SelectionMenuRules.isVisible(R.id.copy, context(isBibleDocument = false, supportsProcessTextActions = false)))
    }

    @Test
    fun bookmarkItemsRespectBibleTwoStepMode() {
        assertTrue(SelectionMenuRules.isVisible(R.id.add_bookmark, context(isBibleDocument = false, disableTwoStepBookmarking = false)))
        assertTrue(SelectionMenuRules.isVisible(R.id.add_bookmark, context(isBibleDocument = true, disableTwoStepBookmarking = false)))
        assertFalse(SelectionMenuRules.isVisible(R.id.add_bookmark, context(isBibleDocument = true, disableTwoStepBookmarking = true)))
        assertTrue(SelectionMenuRules.isVisible(R.id.add_bookmark_selection, context(isBibleDocument = true, disableTwoStepBookmarking = true)))
        assertTrue(SelectionMenuRules.isVisible(R.id.add_bookmark_whole_verse, context(isBibleDocument = true, disableTwoStepBookmarking = true)))
    }
}
