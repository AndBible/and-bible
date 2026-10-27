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
package net.bible.android.view.activity.page

import net.bible.android.view.activity.page.SelectionMenuItem.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The text-selection menu's item set, pinned to what Android's `BibleView.onPrepareActionMenu` decides. */
class SelectionMenuPolicyTest {
    private fun input(
        isBible: Boolean = true,
        twoStepDisabled: Boolean = false,
        hasVerseSelection: Boolean = true,
        selectionText: String? = "In the beginning",
        resolvedRef: Boolean = false,
        paragraphBreakEnabled: Boolean = false,
        hasLookupDictionaries: Boolean = false,
        llmConfigured: Boolean = false,
    ) = SelectionMenuInput(isBible, twoStepDisabled, hasVerseSelection, selectionText, resolvedRef, paragraphBreakEnabled, hasLookupDictionaries, llmConfigured)

    @Test fun oneStepShowsSelectionAndWholeVerse() {
        val items = selectionMenuItems(input(twoStepDisabled = true), step2 = false)
        assertEquals(listOf(AddBookmarkSelection, AddBookmarkWholeVerse), items.filter { it in BOOKMARK_ROWS })
    }

    @Test fun twoStepShowsAddBookmarkThenStep2() {
        val step1 = selectionMenuItems(input(), step2 = false)
        assertEquals(listOf(AddBookmark), step1.filter { it in BOOKMARK_ROWS })
        assertEquals(listOf(AddBookmarkSelection, AddBookmarkWholeVerse), selectionMenuItems(input(), step2 = true))
    }

    @Test fun twoStepOnlyForBibles() {
        // A non-Bible document ignores the "disable two-step" preference: Android shows the single "Bookmark" row either way.
        for (disabled in listOf(false, true)) {
            val items = selectionMenuItems(input(isBible = false, twoStepDisabled = disabled), step2 = false)
            assertEquals(listOf(AddBookmark), items.filter { it in BOOKMARK_ROWS })
        }
    }

    @Test fun noVerseSelectionHidesBookmarkRows() {
        val noVerse = input(hasVerseSelection = false, twoStepDisabled = true, paragraphBreakEnabled = true)
        val items = selectionMenuItems(noVerse, step2 = false)
        assertEquals(emptyList(), items.filter { it in BOOKMARK_ROWS })
        assertFalse(AddParagraphBreak in items)
        assertFalse(AddBookmark in selectionMenuItems(noVerse.copy(twoStepDisabled = false), step2 = false))
        // Text-bound rows stay.
        assertTrue(WebSearch in items)
        assertTrue(Search in items)
    }

    @Test fun bibleFootnoteTextOffersCopyInsteadOfVerseSharing() {
        // A teleported footnote popup reports plain text, without verse context.
        val items = selectionMenuItems(
            input(hasVerseSelection = false, selectionText = "An alternative reading"), step2 = false,
        )
        assertTrue(Copy in items, "Footnote text must be copyable even in a Bible window")
        assertFalse(ShareVerses in items, "Verse sharing cannot handle a plain-text selection")
    }

    @Test fun bibleWithoutSelectionDoesNotOfferCopyOrVerseSharing() {
        val items = selectionMenuItems(input(hasVerseSelection = false, selectionText = null), step2 = false)
        assertFalse(Copy in items)
        assertFalse(ShareVerses in items)
    }

    @Test fun nonBibleHidesCompareAndShare() {
        val bible = selectionMenuItems(input(), step2 = false)
        assertTrue(Compare in bible && ShareVerses in bible && Memorize in bible)
        assertFalse(Copy in bible)
        val other = selectionMenuItems(input(isBible = false), step2 = false)
        assertFalse(Compare in other)
        assertFalse(ShareVerses in other)
        assertFalse(Memorize in other)
        assertTrue(Copy in other, "Copy is the non-Bible row")
    }

    @Test fun memorizeIsOfferedForBibleVerses() {
        assertTrue(Memorize in selectionMenuItems(input(), step2 = false))
    }

    @Test fun commentaryVerseSelectionDoesNotOfferMemorize() {
        val items = selectionMenuItems(input(isBible = false, hasVerseSelection = true), step2 = false)
        assertFalse(Memorize in items)
        assertTrue(AddBookmark in items)
    }

    @Test fun dictionaryTextSelectionDoesNotOfferMemorize() {
        val items = selectionMenuItems(input(isBible = false, hasVerseSelection = false), step2 = false)
        assertFalse(Memorize in items)
        assertTrue(Copy in items)
    }

    @Test fun refSelectionShowsOpenRefNotSearch() {
        val ref = selectionMenuItems(input(resolvedRef = true), step2 = false)
        assertTrue(OpenRef in ref)
        assertFalse(Search in ref)
        val plain = selectionMenuItems(input(resolvedRef = false), step2 = false)
        assertTrue(Search in plain)
        assertFalse(OpenRef in plain)
    }

    @Test fun llmRowOnlyWhenConfigured() {
        assertFalse(LlmAction in selectionMenuItems(input(llmConfigured = false), step2 = false))
        assertTrue(LlmAction in selectionMenuItems(input(llmConfigured = true), step2 = false))
        assertFalse(LlmAction in selectionMenuItems(input(llmConfigured = true, selectionText = null), step2 = false))
    }

    @Test fun textBoundRowsNeedSelectionText() {
        val none = selectionMenuItems(input(selectionText = null, isBible = false), step2 = false)
        assertEquals(listOf(AddBookmark), none)
    }

    @Test fun dictionaryParagraphBreakAndOrder() {
        val all = selectionMenuItems(
            input(paragraphBreakEnabled = true, hasLookupDictionaries = true, llmConfigured = true), step2 = false,
        )
        assertEquals(
            listOf(AddBookmark, Compare, Memorize, ShareVerses, Search, WebSearch, LookupDictionary, AddParagraphBreak, LlmAction),
            all,
        )
        assertFalse(LookupDictionary in selectionMenuItems(input(hasLookupDictionaries = false), step2 = false))
        assertFalse(AddParagraphBreak in selectionMenuItems(input(paragraphBreakEnabled = false), step2 = false))
    }

    private companion object {
        val BOOKMARK_ROWS = setOf(AddBookmark, AddBookmarkSelection, AddBookmarkWholeVerse)
    }
}
