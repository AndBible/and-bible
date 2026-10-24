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

package net.bible.sharedui.reading.bridge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.bible.android.common.toV11n
import net.bible.android.control.page.OrdinalRange
import net.bible.android.control.progress.ProgressJsActions
import net.bible.android.control.versification.toVerseRange
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.database.progress.ReadingSource
import net.bible.android.view.activity.page.Selection
import net.bible.android.view.activity.page.positiveOrNull
import net.bible.service.common.ReadingProgressSettings
import net.bible.service.sword.BookAndKey
import net.bible.sharedui.reading.session.BibleWindowSession
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.KeyUtil
import org.crosswire.jsword.passage.NoSuchKeyException
import org.crosswire.jsword.passage.RangedPassage
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.system.Versifications

/**
 * The compare, memorization, reading-progress and speak calls of the JS bridge. Progress writes are
 * queued through [ProgressJsActions] under the window's id and are not dropped after the session is
 * disposed (they are keyed by verse range, never by window state); everything that opens UI or
 * starts speech is skipped for a disposed session.
 */
internal class ProgressSpeakBridgeFamily(
    private val session: BibleWindowSession,
    private val progressJsActions: ProgressJsActions,
    private val speaker: ReadingSpeaker,
) {
    private val windowId get() = session.window.id

    fun compare(bookInitials: String, verseOrdinal: Int, endOrdinal: Int) {
        if (session.isDisposed) return
        session.scope.launch(Dispatchers.Main) {
            compareSelection(Selection(bookInitials, verseOrdinal, positiveOrNull(endOrdinal)))
        }
    }

    /** Opens the compare document for [selection] (also the selection menu's Compare action). */
    fun compareSelection(selection: Selection?) {
        val verseRange = selection?.verseRange ?: return
        session.linkControl.openCompare(verseRange)
    }

    /** Opens the memorize document for [selection] (also the selection menu's Memorize action). */
    fun memorizeSelection(selection: Selection?) {
        val verseRange = selection?.verseRange ?: return
        session.linkControl.openMemorize(BookAndKey(verseRange, selection.book))
    }

    fun memorize(bookInitials: String, verseOrdinal: Int, endOrdinal: Int) {
        if (session.isDisposed) return
        val verseRange = verseRangeFromOrdinals(bookInitials, verseOrdinal, endOrdinal)
        progressJsActions.addTargetIfNeededThen(windowId, verseRange, session.scope, Dispatchers.Main) {
            memorizeSelection(Selection(bookInitials, verseOrdinal, positiveOrNull(endOrdinal)))
        }
    }

    private fun verseRangeFromOrdinals(bookInitials: String, startOrdinal: Int, endOrdinal: Int): VerseRange? {
        val book = Books.installed().getBook(bookInitials) ?: return null
        val v11n = (book as? AbstractPassageBook)?.versification ?: return null
        val effectiveEnd = if (endOrdinal > 0) endOrdinal else startOrdinal
        return VerseRange(v11n, Verse(v11n, startOrdinal), Verse(v11n, effectiveEnd))
    }

    fun markAsMemorized(bookInitials: String, startOrdinal: Int, endOrdinal: Int) {
        val verseRange = verseRangeFromOrdinals(bookInitials, startOrdinal, endOrdinal) ?: return
        progressJsActions.markMemorized(windowId, verseRange)
    }

    fun addMemorizationTarget(bookInitials: String, startOrdinal: Int, endOrdinal: Int) {
        val verseRange = verseRangeFromOrdinals(bookInitials, startOrdinal, endOrdinal) ?: return
        progressJsActions.addTarget(windowId, verseRange)
    }

    fun unmarkMemorized(bookInitials: String, startOrdinal: Int, endOrdinal: Int) {
        val verseRange = verseRangeFromOrdinals(bookInitials, startOrdinal, endOrdinal) ?: return
        progressJsActions.unmarkMemorized(windowId, verseRange)
    }

    fun removeMemorizationTarget(bookInitials: String, startOrdinal: Int, endOrdinal: Int) {
        val verseRange = verseRangeFromOrdinals(bookInitials, startOrdinal, endOrdinal) ?: return
        progressJsActions.removeTargetByRange(windowId, verseRange)
    }

    fun setReadingProgressSettings(json: String) {
        ReadingProgressSettings.setBundleFromJson(json)
        ReadingProgressSettings.notifyChanged()
    }

    fun openReadingProgress(tab: Int) {
        if (session.isDisposed) return
        session.navigator.openReadingProgress(tab)
    }

    fun openReadingProgressSettings() {
        if (session.isDisposed) return
        session.navigator.openReadingProgressSettings()
    }

    /**
     * Records a new read-history row for this chapter. Always inserts: JS-side state
     * (autoTrackDone) prevents duplicate auto-track inserts. The `source` parameter is
     * the string name of a [ReadingSource] value; unknown strings fall back to MANUAL.
     */
    fun recordChapterRead(bookInitials: String, startOrdinal: Int, chapter: Int, source: String) {
        val book = Books.installed().getBook(bookInitials) ?: return
        val v11n = (book as? AbstractPassageBook)?.versification ?: return
        val verse = Verse(v11n, startOrdinal)
        val readingSource = try { ReadingSource.valueOf(source) } catch (_: Exception) { ReadingSource.MANUAL }
        progressJsActions.recordChapterRead(windowId, v11n, verse.book, chapter, bookInitials, readingSource)
    }

    fun openChapterReadHistory(bookInitials: String, startOrdinal: Int, chapter: Int) {
        if (session.isDisposed) return
        val book = Books.installed().getBook(bookInitials) ?: return
        val v11n = (book as? AbstractPassageBook)?.versification ?: return
        val kjvBook = Verse(v11n, startOrdinal).toV11n(KJVA).book
        session.scope.launch(Dispatchers.Main) {
            session.hostSheets?.showReadHistory(kjvBook.name, chapter)
        }
    }

    fun speak(bookInitials: String, v11nName: String, ordinal: Int, endOrdinal: Int) {
        if (session.isDisposed) return
        session.scope.launch(Dispatchers.Main) {
            val book = Books.installed().getBook(bookInitials) as SwordBook
            val v11n = Versifications.instance().getVersification(v11nName)
            val verse = Verse(v11n, ordinal).toV11n(book.versification)
            if(speaker.isSpeaking) {
                speaker.pauseForNextSpeech()
            }
            speaker.speakBible(book, verse)
        }
    }

    fun speakMemorizationLoop(bookInitials: String, v11nName: String, ordinal: Int, endOrdinal: Int) {
        if (session.isDisposed) return
        session.scope.launch(Dispatchers.Main) {
            val book = Books.installed().getBook(bookInitials) as SwordBook
            val v11n = Versifications.instance().getVersification(v11nName)
            val startVerse = Verse(v11n, ordinal).toV11n(book.versification)
            val endVerse = Verse(v11n, endOrdinal).toV11n(book.versification)
            if (speaker.isSpeaking) {
                speaker.pauseForNextSpeech()
            }
            speaker.speakMemorizationLoop(book, startVerse, endVerse)
        }
    }

    fun speakGeneric(bookInitials: String, osisRef: String, ordinal: Int, endOrdinal: Int) {
        if (session.isDisposed) return
        session.scope.launch(Dispatchers.Main) {
            val book = Books.installed().getBook(bookInitials)
            val origKey = try {
                book!!.getKey(osisRef)
            } catch (e: NoSuchKeyException) {
                val bookAndKey = session.linkControl.getStrongsKey(book!!, osisRef)
                bookAndKey?.key ?: return@launch
            }
            val key = (origKey as? RangedPassage)?.toVerseRange ?: try { KeyUtil.getVerse(origKey) } catch (e: ClassCastException) { origKey }
            val ordinalRange = OrdinalRange(ordinal, positiveOrNull(endOrdinal))
            val bookAndKey = BookAndKey(key, book, ordinalRange)
            if(speaker.isSpeaking) {
                speaker.pauseForNextSpeech()
            }
            speaker.speakGeneric(bookAndKey)
        }
    }
}
