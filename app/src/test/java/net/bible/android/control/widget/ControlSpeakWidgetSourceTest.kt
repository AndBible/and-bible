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
package net.bible.android.control.widget

import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.speak.AdvancedSpeakSettings
import net.bible.android.control.speak.SpeakChange
import net.bible.android.control.speak.SpeakChanges
import net.bible.android.control.versification.BibleTraverser
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkWithNotes
import net.bible.android.database.bookmarks.PlaybackSettings
import net.bible.android.platform.androidSaxTextPolicy
import net.bible.service.device.speak.TextCommand
import net.bible.test.DatabaseResetter.resetDatabase
import net.bible.test.testAppSettings
import net.bible.test.testCoreStrings
import net.bible.test.testOrderedLauncher
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The real [ControlSpeakWidgetSource]: speak events, saved resume position and speak bookmarks over the test modules. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ControlSpeakWidgetSourceTest {
    private val settings = testAppSettings()
    private var speakingNow = false
    private var pausedNow = false
    private lateinit var bookmarkControl: BookmarkControl
    private lateinit var source: ControlSpeakWidgetSource
    private val kjv get() = Books.installed().getBook("KJV") as SwordBook
    private val v11n get() = Versifications.instance().getVersification(kjv.versification.name)
    private var originalBook: String? = null
    private var originalRef: String? = null

    @Before fun setUp() {
        bookmarkControl = BookmarkControl(mock(WindowControl::class.java), settings, testCoreStrings(), testOrderedLauncher(), androidSaxTextPolicy())
        originalBook = settings.getString("lastSpeakBook", null)
        originalRef = settings.getString("lastSpeakRef", null)
        settings.setString("lastSpeakBook", null)
        settings.setString("lastSpeakRef", null)
        source = ControlSpeakWidgetSource(
            { speakingNow }, { pausedNow }, { null }, bookmarkControl, AdvancedSpeakSettings(settings), GlobalContext.get().get<BibleTraverser>(),
            settings, testCoreStrings(), androidSaxTextPolicy(),
        )
    }

    @After fun tearDown() {
        source.close()
        settings.setString("lastSpeakBook", originalBook)
        settings.setString("lastSpeakRef", originalRef)
        resetDatabase()
    }

    private fun speaking() { speakingNow = true }
    private fun paused() { pausedNow = true }
    private fun progress(verse: Verse, command: TextCommand? = null) =
        SpeakChanges.notifyProgress(SpeakChange.Progress(kjv, verse, command))
    private fun ps(chapter: Int, verse: Int) = Verse(v11n, BibleBook.PS, chapter, verse)

    @Test fun progressWhileSpeakingGivesPositionPercentTextAndNextVerse() = runBlocking {
        speaking()
        progress(ps(23, 2), TextCommand("He maketh me to lie down", TextCommand.TextType.NORMAL))
        val f = source.facts()
        assertEquals(SpeakPhase.SPEAKING, f.phase)
        assertTrue(f.reference!!, f.reference!!.contains("23:2"))
        assertEquals("KJV", f.translation)
        assertNotNull(f.percent)
        assertTrue(f.percent!! in 1..99)
        assertEquals("He maketh me to lie down", f.verseText)
        assertTrue("next verse is Psalm 23:3: ${f.nextVerseText}", f.nextVerseText!!.contains("restoreth"))
    }

    @Test fun titleCommandSetsTheTitleAndNormalTextKeepsIt() = runBlocking {
        speaking()
        progress(ps(23, 1), TextCommand("Psalm 23", TextCommand.TextType.TITLE))
        progress(ps(23, 1), TextCommand("The LORD is my shepherd", TextCommand.TextType.NORMAL))
        val f = source.facts()
        assertEquals("Psalm 23", f.title)
        assertEquals("The LORD is my shepherd", f.verseText)
    }

    @Test fun startingToSpeakClearsTheTitleAndStoppingClearsTheText() = runBlocking {
        speaking()
        progress(ps(23, 1), TextCommand("Psalm 23", TextCommand.TextType.TITLE))
        progress(ps(23, 1), TextCommand("The LORD", TextCommand.TextType.NORMAL))
        SpeakChanges.notifyState(net.bible.android.control.speak.SpeakPlaybackState.SPEAKING)
        assertNull(source.facts().title)
        SpeakChanges.notifyState(net.bible.android.control.speak.SpeakPlaybackState.SILENT)
        assertEquals("", source.facts().verseText)
    }

    @Test fun pausedKeepsThePositionAndText() = runBlocking {
        paused()
        progress(ps(23, 4), TextCommand("Yea, though I walk", TextCommand.TextType.NORMAL))
        SpeakChanges.notifyState(net.bible.android.control.speak.SpeakPlaybackState.PAUSED)
        val f = source.facts()
        assertEquals(SpeakPhase.PAUSED, f.phase)
        assertTrue(f.reference!!.contains("23:4"))
        assertEquals("Yea, though I walk", f.verseText)
    }

    @Test fun stoppedResumesFromTheSavedSpeakPosition() = runBlocking {
        settings.setString("lastSpeakBook", "KJV")
        settings.setString("lastSpeakRef", "Ps.23.1")
        val f = source.facts()
        assertEquals(SpeakPhase.STOPPED, f.phase)
        assertTrue(f.reference!!, f.reference!!.contains("23:1"))
        assertEquals("KJV", f.translation)
        assertNotNull(f.percent)
        assertNull("nothing is being read: no next verse", f.nextVerseText)
    }

    @Test fun stoppedWithoutASavedPositionHasNoReference() = runBlocking {
        val f = source.facts()
        assertEquals(SpeakPhase.STOPPED, f.phase)
        assertNull(f.reference)
        assertNull(f.translation)
    }

    @Test fun stoppedWithAnUnparsableSavedRefHasNoReference() = runBlocking {
        settings.setString("lastSpeakBook", "KJV")
        settings.setString("lastSpeakRef", "not a verse at all")
        assertNull(source.facts().reference)
    }

    @Test fun nonBibleBookBeingSpokenShowsItsKeyNotContinueListening() = runBlocking {
        speaking()
        val dict = Books.installed().getBook("StrongsGreek")!!
        val key = dict.getKey("G1")
        SpeakChanges.notifyProgress(SpeakChange.Progress(dict, key, null))
        val f = source.facts()
        assertEquals(key.getName(), f.reference)
        assertEquals("StrongsGreek", f.translation)
        assertNull(f.percent)
    }

    @Test fun resetForgetsAnEarlierRunsTextAndPosition() = runBlocking {
        speaking()
        progress(ps(23, 1), TextCommand("The LORD", TextCommand.TextType.NORMAL))
        source.reset()
        val f = source.facts()
        assertEquals("", f.verseText)
        assertNull(f.reference)
    }

    private suspend fun speakBookmark(range: VerseRange, bookId: String?): BibleBookmarkWithNotes {
        val b = BibleBookmarkWithNotes(range, null, true, null).apply { playbackSettings = PlaybackSettings(bookId = bookId) }
        return bookmarkControl.addOrUpdateBibleBookmark(b, setOf(bookmarkControl.speakLabel().id))
    }

    @Test fun rowsComeFromTheSpeakLabelAndTheRowBeingReadIsCurrent() = runBlocking {
        val first = speakBookmark(VerseRange(v11n, Verse(v11n, BibleBook.GEN, 1, 1), Verse(v11n, BibleBook.GEN, 1, 3)), "KJV")
        val psalm = speakBookmark(VerseRange(v11n, ps(23, 1), ps(23, 6)), "KJV")
        val otherBible = speakBookmark(VerseRange(v11n, ps(23, 1), ps(23, 6)), "FinRK")
        speaking()
        progress(ps(23, 2), TextCommand("x", TextCommand.TextType.NORMAL))
        val f = source.facts()
        assertEquals(3, f.rows.size)
        assertTrue("rows are in verse order", f.rows.first().reference.contains("Genesis 1:1") || f.rows.first().reference.startsWith("Gen"))
        assertEquals("the Psalm row of KJV is the one being read", psalm.id.toString(), f.currentBookmarkId)
        assertFalse(f.rows.any { it.current })
        assertTrue(f.rows.all { it.translation.isNotEmpty() && it.percent != null && !it.repeats })
        assertTrue(f.rows.map { it.bookmarkId }.containsAll(listOf(first.id.toString(), otherBible.id.toString())))
    }

    @Test fun noRowIsCurrentWhileStopped() = runBlocking {
        speakBookmark(VerseRange(v11n, ps(23, 1), ps(23, 6)), "KJV")
        settings.setString("lastSpeakBook", "KJV")
        settings.setString("lastSpeakRef", "Ps.23.2")
        assertNull(source.facts().currentBookmarkId)
    }

    @Test fun repeatingBookmarkIsMarkedAsRepeats() = runBlocking {
        val b = BibleBookmarkWithNotes(VerseRange(v11n, ps(1, 1), ps(1, 2)), null, true, null).apply {
            playbackSettings = PlaybackSettings(bookId = "KJV", verseRange = VerseRange(v11n, ps(1, 1), ps(1, 2)))
        }
        bookmarkControl.addOrUpdateBibleBookmark(b, setOf(bookmarkControl.speakLabel().id))
        assertTrue(source.facts().rows.single().repeats)
    }
}
