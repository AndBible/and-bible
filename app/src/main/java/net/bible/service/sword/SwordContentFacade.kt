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
import net.bible.android.control.bookmark.BookmarkStyle
import net.bible.android.control.speak.SpeakSettings
import net.bible.android.control.versification.VersificationConverter
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.service.common.CommonUtils.getResourceInteger
import net.bible.service.common.CommonUtils.sharedPreferences
import net.bible.service.common.Constants
import net.bible.service.common.Logger
import net.bible.service.common.ParseException
import net.bible.service.css.CssControl
import net.bible.service.device.speak.SpeakCommand
import net.bible.service.device.speak.SpeakCommandArray
import net.bible.service.font.FontControl
import net.bible.service.format.HtmlMessageFormatter.Companion.format
import net.bible.service.format.Note
import net.bible.service.format.OSISInputStream
import net.bible.service.format.SaxParserPool
import net.bible.service.format.osistohtml.OsisToHtmlParameters
import net.bible.service.format.osistohtml.osishandlers.OsisToBibleSpeak
import net.bible.service.format.osistohtml.osishandlers.OsisToCanonicalTextSaxHandler
import net.bible.service.format.osistohtml.osishandlers.OsisToCopyTextSaxHandler
import net.bible.service.format.osistohtml.osishandlers.OsisToHtmlSaxHandler
import net.bible.service.format.osistohtml.osishandlers.OsisToSpeakTextSaxHandler
import net.bible.service.format.usermarks.BookmarkFormatSupport
import net.bible.service.format.usermarks.MyNoteFormatSupport
import org.crosswire.common.xml.JDOMSAXEventProvider
import org.crosswire.common.xml.SAXEventProvider
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.BookData
import org.crosswire.jsword.book.BookException
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.FeatureType
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.KeyUtil
import org.crosswire.jsword.passage.NoSuchKeyException
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.jdom2.Document
import org.xml.sax.ContentHandler
import java.io.InputStream
import java.util.*
import javax.inject.Inject
import javax.xml.parsers.SAXParser

/** JSword facade
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
open class SwordContentFacade @Inject constructor(
    private val bookmarkFormatSupport: BookmarkFormatSupport,
    private val myNoteFormatSupport: MyNoteFormatSupport
) {
    private val documentParseMethod = DocumentParseMethod()
    private val saxParserPool = SaxParserPool()
    private val cssControl = CssControl()
    /** top level method to fetch html from the raw document data
     */
    @Throws(ParseException::class)
    fun readHtmlText(book: Book?, key: Key?, asFragment: Boolean, textDisplaySettings: TextDisplaySettings): String {
        var retVal = ""
        if (book == null || key == null) {
            retVal = ""
        } else if (Books.installed().getBook(book.initials) == null) {
            Log.w(TAG, "Book may have been uninstalled:$book")
            val errorMsg = application.getString(R.string.document_not_installed, book.initials)
            retVal = format(errorMsg)
        } else if (!bookContainsAnyOf(book, key)) {
            Log.w(TAG, "KEY:" + key.osisID + " not found in doc:" + book)
            retVal = format(R.string.error_key_not_in_document)
        } else {
			// we have a fast way of handling OSIS zText docs but some docs need the superior JSword error recovery for mismatching tags
			// try to parse using optimised method first if a suitable document and it has not failed previously
            var isParsedOk = false
            if ("OSIS" == book.bookMetaData.getProperty("SourceType") && "zText" == book.bookMetaData.getProperty("ModDrv") &&
                documentParseMethod.isFastParseOkay(book, key)) {
                try {
                    retVal = readHtmlTextOptimizedZTextOsis(book, key, asFragment, textDisplaySettings)
                    isParsedOk = true
                } catch (pe: ParseException) {
                    documentParseMethod.failedToParse(book, key)
                }
            }
            // fall back to slightly slower JSword method with JSword's fallback approach of removing all tags
            if (!isParsedOk) {
                retVal = readHtmlTextStandardJSwordMethod(book, key, asFragment, textDisplaySettings)
            }
        }
        return retVal
    }

    /** Get Footnotes and references from specified document page
     */
    @Throws(ParseException::class)
    fun readFootnotesAndReferences(book: Book?, key: Key?, textDisplaySettings: TextDisplaySettings): List<Note?> {
        var retVal: List<Note?> = ArrayList()
        return try { // based on standard JSword SAX handling method because few performance gains would be gained for the extra complexity of Streaming
            val data = BookData(book, key)
            val osissep = data.saxEventProvider
            if (osissep != null) {
                val osisToHtml = getSaxHandler(book!!, key!!, true, textDisplaySettings)
                osissep.provideSAXEvents(osisToHtml)
                retVal = osisToHtml.notesList
            } else {
                Log.e(TAG, "No osis SEP returned")
            }
            retVal
        } catch (e: Exception) {
            log.error("Parsing error", e)
            throw ParseException("Parsing error", e)
        }
    }

    /**
     * Use OSISInputStream which loads a single verse at a time as required.
     * This reduces memory requirements compared to standard JDom SaxEventProvider
     */
    @Throws(ParseException::class)
    internal fun readHtmlTextOptimizedZTextOsis(book: Book, key: Key, asFragment: Boolean, textDisplaySettings: TextDisplaySettings): String {
        log.debug("Using fast method to fetch document data")
        /*
		  When you supply an InputStream, the SAX implementation wraps the stream in an InputStreamReader;
		  then SAX automatically detects the correct character encoding from the stream. You can then omit the setEncoding() step,
		  reducing the method invocations once again. The result is an application that is faster, and always has the correct character encoding.
		 */
        val `is`: InputStream = OSISInputStream(book, key)
        val osisToHtml = getSaxHandler(book, key, asFragment, textDisplaySettings)
        var parser: SAXParser? = null
        try {
            parser = saxParserPool.obtain()
            parser.parse(`is`, osisToHtml)
        } catch (e: Exception) {
            log.error("Parsing error", e)
            throw ParseException("Parsing error", e)
        } finally {
            saxParserPool.recycle(parser)
        }
        return osisToHtml.toString()
    }

    @Throws(ParseException::class)
    private fun readHtmlTextStandardJSwordMethod(book: Book, key: Key, asFragment: Boolean, textDisplaySettings: TextDisplaySettings): String {
        log.debug("Using standard JSword to fetch document data")
        val retVal: String
        return try {
            val data = BookData(book, key)
            val osissep = data.saxEventProvider
            retVal = if (osissep == null) {
                Log.e(TAG, "No osis SEP returned")
                "Error fetching osis SEP"
            } else {
                val osisToHtml = getSaxHandler(book, key, asFragment, textDisplaySettings)
                osissep.provideSAXEvents(osisToHtml)
                osisToHtml.toString()
            }
            retVal
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

    private fun getSpeakCommandsForVerse(settings: SpeakSettings, book: Book, key: Key): ArrayList<SpeakCommand> {
        return try {
            val data = BookData(book, key)
            val frag = data.getOsisFragment(false)
            var doc = frag.document
            if (doc == null) {
                doc = Document(frag)
            }
            val osissep: SAXEventProvider = JDOMSAXEventProvider(doc)
            val osisHandler: ContentHandler = OsisToBibleSpeak(settings, book.language.code)
            osissep.provideSAXEvents(osisHandler)
            (osisHandler as OsisToBibleSpeak).speakCommands
        } catch (e: Exception) {
            Log.e(TAG, "Error getting text from book", e)
            ArrayList()
        }
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
            val osisHandler: ContentHandler = OsisToSpeakTextSaxHandler(sayReferences)
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

    private fun getSaxHandler(book: Book, key: Key, asFragment: Boolean, textDisplaySettings: TextDisplaySettings): OsisToHtmlSaxHandler {
        val osisToHtmlParameters = OsisToHtmlParameters()
        val bookCategory = book.bookCategory
        val bmd = book.bookMetaData
        osisToHtmlParameters.isAsFragment = asFragment
        osisToHtmlParameters.isLeftToRight = bmd.isLeftToRight
        osisToHtmlParameters.languageCode = book.language.code
        osisToHtmlParameters.moduleBasePath = book.bookMetaData.location
        // If Bible or Commentary then set Basis for partial references to current Key/Verse
        if (BookCategory.BIBLE == bookCategory || BookCategory.COMMENTARY == bookCategory) {
            osisToHtmlParameters.setBasisRef(key)
            osisToHtmlParameters.documentVersification = (book as AbstractPassageBook).versification
            // only show chapter divider in Bibles
            osisToHtmlParameters.isShowChapterDivider = BookCategory.BIBLE == bookCategory
            // but commentaries also have a verse tag which requires a chapter part
            osisToHtmlParameters.chapter = KeyUtil.getVerse(key).chapter
        }
        if (isAndroid) { // HunUj has an error in that refs are not wrapped so automatically add notes around refs
            osisToHtmlParameters.isAutoWrapUnwrappedRefsInNote = "HunUj" == book.initials
            val preferences = sharedPreferences
			// prefs applying to any doc type
			osisToHtmlParameters.isShowNotes = textDisplaySettings.showMyNotes!!
			osisToHtmlParameters.isRedLetter = textDisplaySettings.showRedLetters!!
			osisToHtmlParameters.cssStylesheetList = cssControl.allStylesheetLinks
			// show verse numbers if user has selected to show verse numbers AND the book is a bible (so don't even try to show verses in a Dictionary)
			if (BookCategory.BIBLE == bookCategory) {
				osisToHtmlParameters.isShowVerseNumbers = textDisplaySettings.showVerseNumbers!! && BookCategory.BIBLE == bookCategory
				osisToHtmlParameters.isVersePerline = textDisplaySettings.showVersePerLine!!
				osisToHtmlParameters.isShowMyNotes = textDisplaySettings.showMyNotes!!
				osisToHtmlParameters.isShowBookmarks = textDisplaySettings.showBookmarks!!
				osisToHtmlParameters.setDefaultBookmarkStyle(BookmarkStyle.valueOf(preferences.getString("default_bookmark_style_pref", BookmarkStyle.YELLOW_STAR.name)))
				osisToHtmlParameters.isShowTitles = textDisplaySettings.showSectionTitles!!
				osisToHtmlParameters.versesWithNotes = myNoteFormatSupport.getVersesWithNotesInPassage(key)
				osisToHtmlParameters.bookmarkStylesByBookmarkedVerse = bookmarkFormatSupport.getVerseBookmarkStylesInPassage(key)
				// showMorphology depends on showStrongs to allow the toolbar toggle button to affect both strongs and morphology
				val showStrongs = textDisplaySettings.showStrongs!!
				osisToHtmlParameters.isShowStrongs = showStrongs
				osisToHtmlParameters.isShowMorphology = showStrongs && textDisplaySettings.showMorphology!!
			}
			if (BookCategory.DICTIONARY == bookCategory) {
				if (book.hasFeature(FeatureType.HEBREW_DEFINITIONS)) { //add allHebrew refs link
					val prompt = application.getString(R.string.all_hebrew_occurrences)
					osisToHtmlParameters.extraFooter = "<br /><a href='" + Constants.ALL_HEBREW_OCCURRENCES_PROTOCOL + ":" + key.name + "' class='allStrongsRefsLink'>" + prompt + "</a>"
					//convert text refs to links
					osisToHtmlParameters.isConvertStrongsRefsToLinks = true
				} else if (book.hasFeature(FeatureType.GREEK_DEFINITIONS)) { //add allGreek refs link
					val prompt = application.getString(R.string.all_greek_occurrences)
					osisToHtmlParameters.extraFooter = "<br /><a href='" + Constants.ALL_GREEK_OCCURRENCES_PROTOCOL + ":" + key.name + "' class='allStrongsRefsLink'>" + prompt + "</a>"
					//convert text refs to links
					osisToHtmlParameters.isConvertStrongsRefsToLinks = true
				}
			}
			// which font, if any
			osisToHtmlParameters.font = FontControl.getInstance().getFontForBook(book)
			osisToHtmlParameters.cssClassForCustomFont = FontControl.getInstance().getCssClassForCustomFont(book)
			// indent depth - larger screens have a greater indent
			osisToHtmlParameters.indentDepth = getResourceInteger(R.integer.poetry_indent_chars)
		}
        return OsisToHtmlSaxHandler(osisToHtmlParameters)
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
