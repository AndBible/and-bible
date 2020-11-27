/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */
package net.bible.service.sword

import android.util.Log
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.android.control.ApplicationScope
import net.bible.android.control.link.BookAndKey
import net.bible.android.control.link.BookAndKeyList
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.android.control.versification.toV11n
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.service.common.CommonUtils
import net.bible.service.common.CommonUtils.sharedPreferences
import net.bible.service.common.Logger
import net.bible.service.common.ParseException
import net.bible.service.device.speak.SpeakCommand
import net.bible.service.device.speak.SpeakCommandArray
import net.bible.service.format.OsisMessageFormatter.Companion.format
import net.bible.service.format.Note
import net.bible.service.format.osistohtml.osishandlers.OsisToBibleSpeak
import net.bible.service.format.osistohtml.osishandlers.OsisToCanonicalTextSaxHandler
import net.bible.service.format.osistohtml.osishandlers.OsisToCopyTextSaxHandler
import net.bible.service.format.osistohtml.osishandlers.OsisToSpeakTextSaxHandler
import org.crosswire.common.xml.JDOMSAXEventProvider
import org.crosswire.common.xml.SAXEventProvider
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.BookData
import org.crosswire.jsword.book.BookException
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.NoSuchKeyException
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.VersificationConverter
import org.jdom2.Document
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import org.xml.sax.ContentHandler
import java.util.*
import javax.inject.Inject

/** JSword facade
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
open class SwordContentFacade @Inject constructor(
    private val activeWindowPageManagerProvider: ActiveWindowPageManagerProvider,
) {

    /** top level method to fetch html from the raw document data
     */
    @Throws(ParseException::class)
    fun readOsisFragment(book: Book?, key: Key?,): List<String> {
        var book = book
        var key = key
        if(key is BookAndKey) {
            book = key.document
            key = key.key
        }
        val retVal = when {
            book == null || key == null -> ""
            Books.installed().getBook(book.initials) == null -> {
                Log.w(TAG, "Book may have been uninstalled:$book")
                val errorMsg = application.getString(R.string.document_not_installed, book.initials)
                format(errorMsg)
            }
            key is BookAndKeyList -> {
                return key.map {
                    if(it is BookAndKey) {
                        readXmlTextStandardJSwordMethod(it.document, it.key)
                    } else throw RuntimeException("Not supported")
                }
            }
            !bookContainsAnyOf(book, key) -> {
                Log.w(TAG, "KEY:" + key.osisID + " not found in doc:" + book)
                format(R.string.error_key_not_in_document)
            }
            else -> {
                readXmlTextStandardJSwordMethod(book, key)
            }
        }
        return listOf(retVal)
    }

    /** Get Footnotes and references from specified document page
     */
    @Throws(ParseException::class)
    fun readFootnotesAndReferences(book: Book?, key: Key?, textDisplaySettings: TextDisplaySettings): List<Note> {
        var retVal: List<Note> = ArrayList()
        return try { // based on standard JSword SAX handling method because few performance gains would be gained for the extra complexity of Streaming
            val data = BookData(book, key)
            val osissep = data.saxEventProvider
            if (osissep != null) {
                retVal = emptyList()
                // TODO! Not sure if this is still needed though...
                //val osisToHtml = getSaxHandler(book!!, key!!, true, textDisplaySettings)
                //osissep.provideSAXEvents(osisToHtml)
                //retVal = osisToHtml.notesList
            } else {
                Log.e(TAG, "No osis SEP returned")
            }
            retVal
        } catch (e: Exception) {
            log.error("Parsing error", e)
            throw ParseException("Parsing error", e)
        }
    }

    @Throws(ParseException::class)
    private fun readXmlTextStandardJSwordMethod(book: Book, key: Key): String {
        log.debug("Using standard JSword to fetch document data")
        return try {
            val data = BookData(book, key)
            val outputter = XMLOutputter(Format.getRawFormat())
            outputter.outputString(data.osisFragment)
        } catch (e: Exception) {
            log.error("Parsing error", e)
            throw ParseException("Parsing error", e)
        }
    }

    /**
     * Get just the canonical text of one or more book entries without any
     * markup.
     *
     * @param book
     * the book to use
     * @param key
     * a reference, appropriate for the book, of one or more entries
     */
    @Throws(NoSuchKeyException::class, BookException::class, ParseException::class)
    fun getCanonicalText(book: Book?, key: Key?): String {
        return try {
            val data = BookData(book, key)
            val osissep = data.saxEventProvider
            val osisHandler: ContentHandler = OsisToCanonicalTextSaxHandler()
            osissep.provideSAXEvents(osisHandler)
            osisHandler.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting text from book", e)
            application.getString(R.string.error_occurred)
        }
    }

    @Throws(NoSuchKeyException::class, BookException::class, ParseException::class)
    fun getTextWithVerseNumbers(book: Book?, verseRange: VerseRange): String {
        return try {
            val data = BookData(book, verseRange)
            val osissep = data.saxEventProvider
            val showVerseNumbers = verseRange.toVerseArray().size > 1 &&
                sharedPreferences.getBoolean("show_verseno_pref", true)
            val osisHandler: ContentHandler = OsisToCopyTextSaxHandler(showVerseNumbers)
            osissep.provideSAXEvents(osisHandler)
            osisHandler.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting text from book", e)
            application.getString(R.string.error_occurred)
        }
    }

    private fun getSpeakCommandsForVerse(settings: SpeakSettings, book: Book, key: Key): ArrayList<SpeakCommand> = try {
        val data = BookData(book, key)
        val frag = data.getOsisFragment(false)
        val doc = frag.document ?: Document(frag)
        val osissep: SAXEventProvider = JDOMSAXEventProvider(doc)
        val osisHandler: ContentHandler = OsisToBibleSpeak(settings, book.language.code)
        osissep.provideSAXEvents(osisHandler)
        (osisHandler as OsisToBibleSpeak).speakCommands
    } catch (e: Exception) {
        Log.e(TAG, "Error getting text from book", e)
        ArrayList()
    }

    fun getSpeakCommands(settings: SpeakSettings, book: SwordBook, verse: Verse?): SpeakCommandArray {
        val v11nConverter = VersificationConverter()
        val verse_ = v11nConverter.convert(verse, book.versification)
        val lst = SpeakCommandArray()
        if (verse_.verse == 1) {
            lst.addAll(getSpeakCommandsForVerse(settings, book,
                Verse(book.versification, verse_.book, verse_.chapter, 0)))
        }
        lst.addAll(getSpeakCommandsForVerse(settings, book, verse_))
        return lst
    }

    /**
     * Get text to be spoken without any markup.
     *
     * @param book
     * the book to use
     * @param key
     * a reference, appropriate for the book, of one or more entries
     */
    fun getTextToSpeak(book: Book, key: Key?): String {
        return try {
            val data = BookData(book, key)
            val osissep = data.saxEventProvider
            val sayReferences = BookCategory.GENERAL_BOOK == book.bookCategory
            val osisHandler = OsisToSpeakTextSaxHandler(sayReferences)
            osissep.provideSAXEvents(osisHandler)
            osisHandler.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting text from book", e)
            application.getString(R.string.error_occurred)
        }
    }

    /**
     * Get just the canonical text of one or more book entries without any
     * markup.
     *
     * @param book
     * the book to use
     * @param reference
     * a reference, appropriate for the book, of one or more entries
     */
    @Throws(BookException::class, NoSuchKeyException::class)
    fun getPlainText(book: Book?, reference: String?): String {
        var plainText = ""
        try {
            if (book != null) {
                val key = book.getKey(reference)
                plainText = getPlainText(book, key)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting plain text", e)
        }
        return plainText
    }

    /**
     * Get just the canonical text of one or more book entries without any
     * markup.
     *
     * @param book
     * the book to use
     * @param key
     * a reference, appropriate for the book, of one or more entries
     */
    @Throws(BookException::class, NoSuchKeyException::class)
    fun getPlainText(book: Book?, key: Key?): String {
        var plainText = ""
        try {
            if (book != null) {
                plainText = getCanonicalText(book, key)
                // trim any preceeding spaces that make the final output look uneven
                plainText = plainText.trim { it <= ' ' }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting plain text", e)
        }
        return plainText
    }

    @Throws(BookException::class)
    fun search(bible: Book, searchText: String): Key {
		// example of fetching Strongs ref - only works with downloaded indexes!
		// Book book = getDocumentByInitials("KJV");
		// Key key1 = book.find("strong:h3068");
		// System.out.println("h3068 result count:"+key1.getCardinality());
        Log.d(TAG, "Searching:$bible Search term:$searchText")
	    // This does a standard operator search. See the search
		// documentation for more examples of how to search
        val key = bible.find(searchText) //$NON-NLS-1$
        Log.d(TAG, "There are " + key.cardinality + " verses containing " + searchText)
        return key
    }

    /**
     * When checking a book contains a chapter SwordBook returns false if verse 0 is not in the chapter so this method compensates for that
     *
     * This can be removed if SwordBook.contains is converted to be containsAnyOf as discussed in JS-273
     */
    private fun bookContainsAnyOf(book: Book, key: Key): Boolean {
        if (book.contains(key)) {
            return true
        }
        for (aKey in key) {
            if (book.contains(aKey)) {
                return true
            }
        }
        return false
    }

    fun getBookmarkVerseText(bookmark: BookmarkEntities.Bookmark): String? {
        var verseText: String? = ""
        try {
            val currentBible = activeWindowPageManagerProvider.activeWindowPageManager.currentBible
            val versification = currentBible.versification
            verseText = getPlainText(currentBible.currentDocument, bookmark.verseRange.toV11n(versification))
            verseText = CommonUtils.limitTextLength(verseText)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting verse text", e)
        }
        return verseText
    }

    companion object {
        private const val TAG = "SwordContentFacade"
        // set to false for testing
        private var isAndroid = true //CommonUtils.isAndroid();
        private val log = Logger(SwordContentFacade::class.java.name)
        fun setAndroid(isAndroid: Boolean) {
            Companion.isAndroid = isAndroid
        }
    }

}
