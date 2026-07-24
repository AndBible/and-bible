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
package net.bible.android.control.speak

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkWithNotes
import net.bible.android.database.bookmarks.BookmarkEntities.GenericBookmarkWithNotes
import net.bible.android.database.bookmarks.PlaybackSettings
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the PURE [labelOf] formatter (ported verbatim from the classic
 * `SpeakTransportWidget.onBookmarkButtonClick` :190-197). The event-bridge / [SpeakControl] /
 * [net.bible.android.control.bookmark.BookmarkControl] parts of [SpeakTransportServiceImpl] need a
 * live Koin graph and are left to device A/B (same split as `AgentSessionServiceImplTest`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class SpeakTransportServiceImplTest {

    @After
    fun tearDown() {
        DatabaseResetter.resetDatabase()
    }

    // --- Generic branch: "<abbr> - <keyName>" ------------------------------------------------

    @Test
    fun generic_bookmark_label_matches_classic_format() {
        // KJV is installed in the test ~/.sword fixture set; a real, resolvable book+key so
        // b.book/b.bookKey are non-null (a degenerate bookInitials="" fixture would only prove
        // the "null - null" shape, not the real classic format).
        val bookmark = GenericBookmarkWithNotes(
            key = "Gen 1:1",
            bookInitials = "KJV",
            ordinalStart = null,
            ordinalEnd = null,
            startOffset = null,
            endOffset = null,
            playbackSettings = null,
        )

        val abbr = bookmark.book?.abbreviation
        val keyName = bookmark.bookKey?.name
        assertEquals("KJV", abbr) // kjv.conf sets no Abbreviation key -> falls back to initials "KJV"
        assertNotNull(keyName)
        assertTrue(keyName!!.isNotBlank())

        assertEquals("$abbr - $keyName", labelOf(bookmark))
    }

    // --- Bible branch: "<start.name> (<bookId|?>)" -------------------------------------------

    @Test
    fun bible_bookmark_label_matches_classic_format_withBookId() {
        val verseRange = VerseRange(KJV_VERSIFICATION, Verse(KJV_VERSIFICATION, BibleBook.PS, 119, 1))
        val bookmark = BibleBookmarkWithNotes(verseRange, null, true, null)
        bookmark.playbackSettings = PlaybackSettings(bookId = "ESV")

        assertEquals("${verseRange.start.name} (ESV)", labelOf(bookmark))
    }

    @Test
    fun bible_bookmark_label_matches_classic_format_withoutBookId_fallsBackToQuestionMark() {
        val verseRange = VerseRange(KJV_VERSIFICATION, Verse(KJV_VERSIFICATION, BibleBook.PS, 119, 1))
        val bookmark = BibleBookmarkWithNotes(verseRange, null, true, null)
        // Simplified test constructor always leaves playbackSettings null.

        assertEquals("${verseRange.start.name} (?)", labelOf(bookmark))
    }

    companion object {
        private val KJV_VERSIFICATION = Versifications.instance().getVersification("KJV")
    }
}
