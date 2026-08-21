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

package net.bible.android.control.page

import com.nhaarman.mockitokotlin2.mock
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.versification.BibleTraverser
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.junit.Assert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The BibleView reports scroll position as (osisRef of the document in view, ordinal). For
 * commentaries and general books that osisRef decides whether the page has moved to a new entry —
 * and so whether synced windows should follow. See [CurrentPage.updateKeyFromScrolledOsisRef].
 *
 * A real installed module is used as the page's document because both the key parsing under test
 * (`Book.getKey`) and the versification lookup are final methods, so a mock cannot stand in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ScrolledOsisRefKeyUpdateTest {
    private val kjv = Versifications.instance().getVersification("KJV")
    private val heb11v5 = Verse(kjv, BibleBook.HEB, 11, 5)
    private val heb11v7 = Verse(kjv, BibleBook.HEB, 11, 7)
    private val heb11v9 = Verse(kjv, BibleBook.HEB, 11, 9)

    /** A Kingcomments-style entry: one entry covering several verses. */
    private val entry5to8 = VerseRange(kjv, heb11v5, Verse(kjv, BibleBook.HEB, 11, 8))
    private val entry9to10 = VerseRange(kjv, heb11v9, Verse(kjv, BibleBook.HEB, 11, 10))

    private val book: Book get() = Books.installed().getBook("KJV")

    private fun pageManager() = CurrentPageManager(
        mock<BibleTraverser>(),
        mock<BookmarkControl>(),
        mock<WindowControl>(),
    )

    private fun commentaryPageAtEntry5to8(): CurrentCommentaryPage =
        pageManager().currentCommentary.apply {
            onlySetCurrentDocument(book)
            doSetKey(entry5to8)
        }

    /**
     * The two sides of the comparison genuinely differ for a multi-verse entry: the client sends
     * the entry's whole range, the page stores only the verse it starts at. Comparing those two
     * osisRefs directly is what broke window sync in #3866.
     */
    @Test
    fun commentaryPageKeyIsTheEntryStartVerseNotTheEntryRange() {
        val page = commentaryPageAtEntry5to8()

        assertThat(page.key?.osisRef, equalTo(heb11v5.osisRef))
        assertThat(page.key?.osisRef, not(equalTo(entry5to8.osisRef)))
    }

    /**
     * Regression test for #3866: scrolling within a multi-verse commentary entry must not count as
     * a move. It used to, on every 50ms scroll tick, which re-set the key to the entry's first
     * verse and dragged synced Bible windows back there from wherever the user had scrolled them.
     */
    @Test
    fun scrollingWithinMultiVerseCommentaryEntryIsNotAMove() {
        val page = commentaryPageAtEntry5to8()

        assertThat(page.updateKeyFromScrolledOsisRef(entry5to8.osisRef), equalTo(false))
        assertThat(page.key?.osisRef, equalTo(heb11v5.osisRef))
    }

    /** ...but scrolling on into the next entry still is a move, as in 5.0. */
    @Test
    fun scrollingIntoNextCommentaryEntryIsAMove() {
        val page = commentaryPageAtEntry5to8()

        assertThat(page.updateKeyFromScrolledOsisRef(entry9to10.osisRef), equalTo(true))
        assertThat(page.key?.osisRef, equalTo(heb11v9.osisRef))
    }

    /** A move is reported once, not again for each further scroll tick inside the new entry. */
    @Test
    fun repeatedScrollTicksInTheSameEntryReportTheMoveOnlyOnce() {
        val page = commentaryPageAtEntry5to8()

        assertThat(page.updateKeyFromScrolledOsisRef(entry9to10.osisRef), equalTo(true))
        assertThat(page.updateKeyFromScrolledOsisRef(entry9to10.osisRef), equalTo(false))
        assertThat(page.updateKeyFromScrolledOsisRef(entry9to10.osisRef), equalTo(false))
    }

    /**
     * A single verse inside the entry range is a different location than the entry start, so it
     * does move the page — the range is not treated as a catch-all.
     */
    @Test
    fun aVerseInsideTheEntryStillMovesTheCommentaryPage() {
        val page = commentaryPageAtEntry5to8()

        assertThat(page.updateKeyFromScrolledOsisRef(heb11v7.osisRef), equalTo(true))
        assertThat(page.key?.osisRef, equalTo(heb11v7.osisRef))
    }

    /** An osisRef the document cannot resolve leaves the page where it is, without throwing. */
    @Test
    fun unresolvableOsisRefIsIgnored() {
        val page = commentaryPageAtEntry5to8()

        assertThat(page.updateKeyFromScrolledOsisRef("NoSuchBook.1.1"), equalTo(false))
        assertThat(page.key?.osisRef, equalTo(heb11v5.osisRef))
    }

    /**
     * Pages that store the key as given (the base implementation, used by general books) compare
     * keys directly — the behaviour the commentary branch was folded into in b9a8a1bcc even though
     * a commentary does not store the key it is given.
     */
    @Test
    fun pagesThatStoreTheKeyAsGivenMoveOnlyWhenTheKeyChanges() {
        val page = pageManager().currentGeneralBook.apply {
            onlySetCurrentDocument(book)
            doSetKey(book.getKey(heb11v5.osisRef))
        }

        assertThat(page.updateKeyFromScrolledOsisRef(heb11v5.osisRef), equalTo(false))
        assertThat(page.updateKeyFromScrolledOsisRef(heb11v9.osisRef), equalTo(true))
        assertThat(page.key?.osisRef, equalTo(heb11v9.osisRef))
    }
}
