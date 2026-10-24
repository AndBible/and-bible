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

package net.bible.service.device.speak

import kotlinx.coroutines.runBlocking
import net.bible.android.control.navigation.DocumentBibleBooksFactory
import net.bible.android.control.page.OrdinalRange
import net.bible.android.control.versification.BibleTraverser
import net.bible.android.database.bookmarks.PlaybackSettings
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.android.database.mydocument.MyDocument
import net.bible.android.database.mydocument.MyDocumentContentType
import net.bible.android.database.mydocument.MyDocumentPage
import net.bible.service.common.next
import net.bible.service.common.nextOrNull
import net.bible.service.db.DatabaseContainer
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.mydocument.createMyDocumentBook
import net.bible.test.testAppSettings
import net.bible.test.testCoreStrings
import net.bible.test.testSwordContentFacade
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordGenBook
import org.crosswire.jsword.passage.DefaultLeafKeyList
import org.crosswire.jsword.passage.RangedPassage
import org.crosswire.jsword.passage.VerseRange
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner

/**
 * SP-6: speaking a general book (MyDocument, EPUB, GenBook) ends at the end of the book instead of wrapping to
 * its first page, while navigation (`next`) and the Bible "Repeat passage" loop keep wrapping.
 */
@RunWith(RobolectricTestRunner::class)
class GeneralSpeakEndOfBookTest : SpeakIntegrationTestBase() {
    private val dao get() = DatabaseContainer.instance.myDocumentDb.myDocumentDao()
    private val traverser = BibleTraverser(DocumentBibleBooksFactory())

    private fun myDocument(vararg pages: Pair<String, String>): SwordGenBook = runBlocking {
        dao.allDocuments().forEach { dao.deleteDocumentWithPages(it) }
        val document = MyDocument(name = "Speech", initials = "MyDoc_Speech")
        dao.insert(document)
        pages.forEachIndexed { i, (key, text) ->
            dao.insertPageWithContent(
                MyDocumentPage(
                    documentId = document.id, title = "Title $key", pageKey = key,
                    contentType = MyDocumentContentType.MARKDOWN, orderNumber = i,
                ),
                text,
            )
        }
        createMyDocumentBook(document).also {
            Books.installed().addBook(it)
            registered = it
            SwordContentFacade.clearCaches()
        }
    }

    private var registered: SwordGenBook? = null

    @After
    fun unregisterDocument() {
        registered?.let { Books.installed().removeBook(it) }
        registered = null
        SwordContentFacade.clearCaches()
        runBlocking { dao.allDocuments().forEach { dao.deleteDocumentWithPages(it) } }
    }

    private fun startOf(doc: SwordGenBook, pageKey: String): BookAndKey = runBlocking {
        val key = DefaultLeafKeyList(pageKey, pageKey)
        val first = SwordContentFacade.ordinalRangeFor(doc, key, testCoreStrings()).first
        BookAndKey(key, doc, OrdinalRange(first))
    }

    private fun generalProvider(doc: SwordGenBook, start: BookAndKey): GeneralSpeakTextProvider {
        val provider = GeneralSpeakTextProvider(
            bookmarkControl, doc, fakePlatform, testAppSettings(), GlobalContext.get().get(), GlobalContext.get().get(),
            GlobalContext.get().get(), swordContentFacade = testSwordContentFacade(),
            strings = testCoreStrings(), traverser = traverser,
        )
        provider.setupReading(start)
        return provider
    }

    /** Drives the provider the way the speech manager does; fails if it never reports the end. */
    private fun speakToTheEnd(provider: SpeakTextProvider): String {
        val spoken = StringBuilder()
        var guard = 0
        do {
            check(guard++ < 60) { "Speech did not stop: spoken so far: $spoken" }
            val id = "end-$guard"
            val command = runBlocking { provider.getNextSpeakCommand(id) }
            provider.startUtterance(id)
            if (command is TextCommand) spoken.append(command.text).append(' ')
        } while (provider.isMoreTextToSpeak())
        return spoken.toString()
    }

    private fun count(text: String, word: String) = Regex(word).findAll(text).count()

    @Test
    fun onePageMyDocumentStopsAtTheEnd() {
        val doc = myDocument("only" to "Sentenceone. Sentencetwo. Sentencethree.")
        val provider = generalProvider(doc, startOf(doc, "only"))
        val spoken = speakToTheEnd(provider)
        for (word in listOf("Sentenceone", "Sentencetwo", "Sentencethree")) assertEquals(word, 1, count(spoken, word))
        assertFalse(provider.isMoreTextToSpeak())
    }

    @Test
    fun twoPageMyDocumentSpeaksPageTwoThenStops() {
        val doc = myDocument("page_a" to "Alphaone. Alphatwo.", "page_b" to "Betaone. Betatwo.")
        val provider = generalProvider(doc, startOf(doc, "page_a"))
        val spoken = speakToTheEnd(provider)
        for (word in listOf("Alphaone", "Alphatwo", "Betaone", "Betatwo")) assertEquals(word, 1, count(spoken, word))
        assertFalse(provider.isMoreTextToSpeak())
    }

    @Test
    fun speechStartedOnTheLastPageStopsAfterIt() {
        val doc = myDocument("page_a" to "Alphaone.", "page_b" to "Betaone.")
        val spoken = speakToTheEnd(generalProvider(doc, startOf(doc, "page_b")))
        assertEquals(1, count(spoken, "Betaone"))
        assertEquals(0, count(spoken, "Alphaone"))
    }

    @Test
    fun nextStillWrapsForNavigation() = runBlocking {
        val doc = myDocument("page_a" to "A.", "page_b" to "B.")
        val last = startOf(doc, "page_b")
        assertNull(last.nextOrNull(testCoreStrings(), traverser))
        assertEquals("page_a", last.next(testCoreStrings(), traverser).key.getOsisRef())
        val first = startOf(doc, "page_a")
        assertEquals("page_b", first.nextOrNull(testCoreStrings(), traverser)?.key?.getOsisRef())
    }

    @Test
    fun bibleRepeatRangeStillLoops() {
        val verse = (book.getKey("Rom.1.1") as RangedPassage).getVerseAt(0)
        val last = (book.getKey("Rom.1.2") as RangedPassage).getVerseAt(0)
        val provider = BibleSpeakTextProvider(
            traverser, bookmarkControl, book, fakePlatform, testAppSettings(), GlobalContext.get().get(),
            GlobalContext.get().get(), swordContentFacade = testSwordContentFacade(),
        )
        provider.settings = SpeakSettings(playbackSettings = PlaybackSettings(verseRange = VerseRange(verse.versification, verse, last)))
        provider.setupReading(book, verse)
        val texts = mutableListOf<String>()
        repeat(40) { index ->
            if (texts.size >= 6) return@repeat
            val id = "repeat-$index"
            val command = runBlocking { provider.getNextSpeakCommand(id) }
            provider.startUtterance(id)
            if (command is TextCommand && command.type == TextCommand.TextType.NORMAL) texts += command.text
        }
        assertTrue("Rom.1.1 must be spoken a second time: $texts", texts.drop(1).any { it == texts.first() })
        assertTrue(provider.isMoreTextToSpeak())
    }
}
