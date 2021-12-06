/*
 * Copyright (c) 2021 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

import android.os.Build
import android.text.Html
import android.text.TextUtils
import android.util.LayoutDirection
import android.util.Log
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.android.view.activity.page.Selection
import net.bible.service.common.Logger
import net.bible.service.common.ParseException
import net.bible.service.device.speak.SpeakCommand
import net.bible.service.device.speak.SpeakCommandArray
import net.bible.service.format.osistohtml.osishandlers.OsisToBibleSpeak
import net.bible.service.format.osistohtml.osishandlers.OsisToCanonicalTextSaxHandler
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
import org.crosswire.jsword.versification.BookName
import org.crosswire.jsword.versification.VersificationConverter
import org.jdom2.Document
import org.jdom2.Element
import org.jdom2.input.SAXBuilder
import org.xml.sax.ContentHandler
import java.io.StringReader
import java.lang.RuntimeException
import java.util.*
import kotlin.math.min

open class OsisError(xmlMessage: String): Exception(xmlMessage) {
    val xml: Element = SAXBuilder().build(StringReader("<div>$xmlMessage</div>")).rootElement
}
class DocumentNotFound(xmlMessage: String): OsisError(xmlMessage)

/** JSword facade
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */

object SwordContentFacade {

    /** top level method to fetch html from the raw document data
     */
    @Throws(ParseException::class, OsisError::class)
    fun readOsisFragment(book: Book?, key: Key?,): Element = when {
        book == null || key == null -> {
            Log.e(TAG, "Key or book was null")
            throw OsisError(application.getString(R.string.error_no_content))
        }
        Books.installed().getBook(book.initials) == null -> {
            Log.w(TAG, "Book may have been uninstalled:$book")
            val link = "<AndBibleLink href='download://?initials=${book.initials}'>${book.initials}</AndBibleLink>"
            val errorMsg = application.getString(R.string.document_not_installed, link)
            throw DocumentNotFound(errorMsg)
        }
        !bookContainsAnyOf(book, key) -> {
            Log.w(TAG, "KEY:" + key.osisID + " not found in doc:" + book)
            throw DocumentNotFound(application.getString(R.string.error_key_not_in_document2, key.name, book.initials))
        }
        else -> {
            readXmlTextStandardJSwordMethod(book, key)
        }
    }

    @Throws(ParseException::class)
    private fun readXmlTextStandardJSwordMethod(book: Book, key: Key): Element {
        log.debug("Using standard JSword to fetch document data")
        return try {
            val data = BookData(book, key)
            if(book.bookCategory == BookCategory.COMMENTARY && key.cardinality == 1) {
                val div = data.osisFragment
                val verse = div.getChild("verse")
                    ?: throw DocumentNotFound(application.getString(R.string.error_key_not_in_document2, key.name, book.initials))
                val verseContent = verse.content.toList()
                verse.removeContent()
                div.removeContent()
                div.addContent(verseContent)
                div
            } else {
                data.osisFragment
            }
        }
        catch (e: OsisError) {
            throw e
        }
        catch (e: Exception) {
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
    open fun getCanonicalText(book: Book?, key: Key?, compatibleOffsets: Boolean = false): String {
        return try {
            val data = BookData(book, key)
            val osissep = data.saxEventProvider
            val osisHandler: ContentHandler = OsisToCanonicalTextSaxHandler(compatibleOffsets)
            osissep.provideSAXEvents(osisHandler)
            osisHandler.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting text from book", e)
            application.getString(R.string.error_occurred)
        }
    }

    fun getSelectionText(selection: Selection,
                         showVerseNumbers: Boolean,
                         advertiseApp: Boolean,
                         showReference: Boolean = true,
                         abbreviateReference: Boolean = true,
                         showNotes: Boolean = true,
                         showVersion: Boolean = true,
                         showSelectionOnly: Boolean = true,
                         showEllipsis: Boolean = true
    ): String {

        class VerseAndText(val verse: Verse, val text: String)

        val book = selection.book
        val verseTexts = selection.verseRange.map {
            VerseAndText(it as Verse, getCanonicalText(book, it, true).trimEnd())
        }
        val startOffset = selection.startOffset ?: 0
        var startVerse = verseTexts.first().text
        val endOffset = selection.endOffset ?: verseTexts.last().text.length

        val start = startVerse.slice(0 until min(startOffset, startVerse.length))

        var startVerseNumber = ""
        if(showVerseNumbers && verseTexts.size > 1) {
            startVerseNumber = "${selection.verseRange.start.verse}. "
        }
        if (showSelectionOnly && startOffset > 0 && showEllipsis) {
            startVerseNumber = "$startVerseNumber..."
        }
        val bookLocale = Locale(selection.book.language.code)
        val isRtl = TextUtils.getLayoutDirectionFromLocale(bookLocale) == LayoutDirection.RTL

        val versionText = if (showVersion) (", ${selection.book.abbreviation}") else ""

        val reference = if(showReference) {
            if(abbreviateReference) {
                synchronized(BookName::class) {
                    val oldValue = BookName.isFullBookName()
                    BookName.setFullBookName(false)
                    val verseRangeName = selection.verseRange.getNameInLocale(null, bookLocale)
                    BookName.setFullBookName(oldValue)
                    " ($verseRangeName$versionText)"
                }
            } else {
                val verseRangeName = selection.verseRange.getNameInLocale(null, bookLocale)
                " ($verseRangeName$versionText)"
            }
        }
        else
            ""

        val advertise = if(advertiseApp) "\n\n${application.getString(R.string.verse_share_advertise, application.getString(R.string.app_name_long))} (https://andbible.github.io)" else ""
        val notesOrig = selection.notes
        val notes =
            if(showNotes && notesOrig != null)
                "\n\n" + if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Html.fromHtml(notesOrig, Html.FROM_HTML_MODE_LEGACY)
                } else {
                    Html.fromHtml(notesOrig)
                }.toString()
            else
                ""

        return when {
            verseTexts.size == 1 -> {
                val end = startVerse.slice(endOffset until startVerse.length)
                val text = startVerse.slice(startOffset until min(endOffset, startVerse.length))
                val post = if(showSelectionOnly && end.isNotEmpty() && showEllipsis) "..." else ""
                if(!showSelectionOnly) """“$startVerseNumber$start${text}$end”""" else "“$startVerseNumber$text$post”"
            }
            verseTexts.size > 1 -> {
                startVerse = startVerse.slice(startOffset until startVerse.length)
                val lastVerse = verseTexts.last()
                val endVerseNum = if(showVerseNumbers) "${lastVerse.verse.verse}. " else ""
                val endVerse = lastVerse.text.slice(0 until min(lastVerse.text.length, endOffset))
                val end = lastVerse.text.slice(endOffset until lastVerse.text.length)
                var middleVerses = if(verseTexts.size > 2) {
                    verseTexts.slice(1 until verseTexts.size-1).joinToString(" ") {
                        if(showVerseNumbers && it.verse.verse != 0) "${it.verse.verse}. ${it.text}" else it.text
                    }
                } else ""
                if(middleVerses.isNotEmpty()) {
                    middleVerses += " "
                }
                val text = "${startVerse.trimEnd()} ${middleVerses.trimStart()}$endVerseNum$endVerse"
                val post = if(showSelectionOnly && end.isNotEmpty() && showEllipsis) "..." else ""

                if(!showSelectionOnly) """“$startVerseNumber$start${text}$end$post”""" else "“$startVerseNumber$text$post”"
            }
            else -> throw RuntimeException("what")
        } + reference + notes + advertise
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
    fun search(bible: Book, searchText: String?): Key {
		// example of fetching Strongs ref - only works with downloaded indexes!
		// Book book = getDocumentByInitials("KJV");
		// Key key1 = book.find("strong:h3068");
		// System.out.println("h3068 result count:"+key1.getCardinality());
        Log.i(TAG, "Searching:$bible Search term:$searchText")
	    // This does a standard operator search. See the search
		// documentation for more examples of how to search
        val key = bible.find(searchText) //$NON-NLS-1$
        Log.i(TAG, "There are " + key.cardinality + " verses containing " + searchText)
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

    private const val TAG = "SwordContentFacade"
    // set to false for testing
    private var isAndroid = true //CommonUtils.isAndroid();
    private val log = Logger(SwordContentFacade::class.java.name)
    fun setAndroid(isAndroid: Boolean) {
        this.isAndroid = isAndroid
    }

}
