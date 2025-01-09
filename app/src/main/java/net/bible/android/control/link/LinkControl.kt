/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.android.control.link

import android.content.Intent
import android.os.Bundle
import android.util.Log
import net.bible.android.activity.R
import net.bible.android.control.ApplicationScope
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.report.ErrorReportControl
import net.bible.android.control.search.SearchControl
import net.bible.android.control.search.SearchControl.SearchBibleSection
import net.bible.android.database.IdType
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.page.BibleView
import net.bible.android.view.activity.search.SearchIndex
import net.bible.android.view.activity.search.SearchResults
import net.bible.service.common.CommonUtils
import net.bible.service.common.CommonUtils.settings
import net.bible.service.download.FakeBookFactory
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.BookAndKeyList
import net.bible.service.sword.StudyPadKey
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.SwordDocumentFacade
import net.bible.service.sword.bookAndKeyListOf
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.BookException
import org.crosswire.jsword.book.FeatureType
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.index.IndexStatus
import org.crosswire.jsword.index.search.SearchType
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.NoSuchKeyException
import org.crosswire.jsword.passage.Passage
import org.crosswire.jsword.passage.PassageKeyFactory
import org.crosswire.jsword.passage.RestrictionType
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.Versification
import org.crosswire.jsword.versification.system.Versifications
import java.io.FileNotFoundException
import java.net.URLDecoder
import java.util.*
import java.util.regex.Pattern
import javax.inject.Inject


/** Control traversal via links pressed by user in a browser e.g. to Strongs
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */

enum class WindowMode {
    WINDOW_MODE_THIS,
    WINDOW_MODE_SPECIAL,
    WINDOW_MODE_NEW,
    WINDOW_MODE_UNDEFINED,
}


@ApplicationScope
class LinkControl @Inject constructor(
    private val windowControl: WindowControl,
	private val bookmarkControl: BookmarkControl,
	private val searchControl: SearchControl,
)  {
    var windowMode: WindowMode = WindowMode.WINDOW_MODE_UNDEFINED

    fun openMulti(links: List<BibleView.BibleLink>): Boolean {
        val key = BookAndKeyList()
        val bookKeys = links.mapNotNull {
            try {
                getBookAndKey(it.url, it.versification, null)
            } catch (e: NoSuchKeyException) {
                null
            }
        }
        for(k in bookKeys) {
            when(k) {
                is BookAndKeyList -> {
                    for(kk in k) {
                        key.addAll(kk)
                    }
                }
                is BookAndKey -> key.addAll(k)
                else -> throw Exception("Unknown key type: ${k.javaClass}")
            }
        }
        key.name = key.joinToString(", ") { (it as BookAndKey).key.name }
        showLink(FakeBookFactory.multiDocument, key)
        return true
    }

    fun openCompare(verseRange: VerseRange): Boolean {
        showLink(FakeBookFactory.compareDocument, verseRange)
        return true
    }

    fun loadApplicationUrl(link: BibleView.BibleLink, book: Book? = null): Boolean = loadApplicationUrl(link.url, link.versification, link.forceDoc, book)

    fun errorLink() {
        ErrorReportControl.sendErrorReportEmail(Exception("Error in webview-js"), "webview")
    }

    /**
     *  Returns either BookAndKey or BookAndKeyList
     */
    private fun getBookAndKey(uriStr: String, versification: Versification, book: Book?): Key? {
        Log.i(TAG, "Loading: $uriStr")
        val uriAnalyzer = UriAnalyzer()
        if (uriAnalyzer.analyze(uriStr)) {
            return when (uriAnalyzer.docType) {
                UriAnalyzer.DocType.BIBLE -> getBibleKey(uriAnalyzer.key, versification, book)
                UriAnalyzer.DocType.GREEK_DIC -> getStrongsKey(SwordDocumentFacade.defaultStrongsGreekDictionary, uriAnalyzer.key, StrongsKeyType.GREEK)
                UriAnalyzer.DocType.HEBREW_DIC -> getStrongsKey(SwordDocumentFacade.defaultStrongsHebrewDictionary, uriAnalyzer.key, StrongsKeyType.HEBREW)
                UriAnalyzer.DocType.ROBINSON -> getRobinsonMorphologyKey(uriAnalyzer.key)
                UriAnalyzer.DocType.SPECIFIC_DOC -> getSpecificDocRefKey(uriAnalyzer.book, uriAnalyzer.key, versification, book)
                else -> null
            }
        }
        return null
    }

    private fun loadApplicationUrl(uriStr: String, versification: Versification, forceDoc: Boolean, book: Book?): Boolean {
        val bookAndKeys =
            try {getBookAndKey(uriStr, versification, if(forceDoc) book else null)}
            catch (e: NoSuchKeyException) {return false} ?: return false

        when(bookAndKeys) {
            is BookAndKey -> {
                val key = bookAndKeys.key
                if(key is Passage && key.countRanges(RestrictionType.NONE) > 1) {
                    val keyList = BookAndKeyList()
                    for( range in (0 until key.countRanges(RestrictionType.NONE)).map { key.getRangeAt(it, RestrictionType.NONE) }) {
                        keyList.addAll(BookAndKey(range, bookAndKeys.document))
                    }
                    showLink(FakeBookFactory.multiDocument, keyList)
                } else {
                    showLink(bookAndKeys.document, bookAndKeys.key)
                }
            }
            is BookAndKeyList -> {
                showLink(FakeBookFactory.multiDocument, bookAndKeys)
            }
        }

        return true
	}

    @Throws(NoSuchKeyException::class)
    private fun getSpecificDocRefKey(initials: String?, reference: String, versification: Versification, book: Book?): BookAndKey? {
        var ref = reference
        if (StringUtils.isEmpty(initials)) {
            return getBibleKey(ref, versification, book)
        } else {
            val document = SwordDocumentFacade.getDocumentByInitials(initials)
            if (document == null) { // tell user to install book
                Dialogs.showErrorMsg(R.string.document_not_installed, initials)
            } else if(document.bookCategory == BookCategory.BIBLE && book == null) {
                return getBibleKey(ref, versification, book)
            } else if(document.isGreekDef || document.isHebrewDef) {
                return getStrongsKey(document, reference)
            }
            else { //Foreign language keys may have been URLEncoded so need to URLDecode them e.g. UZV module at Matthew 1. The first link is "David" (looks a bit like DOBYA)
                ref = URLDecoder.decode(ref)
                //According to the OSIS schema, the osisRef attribute can contain letters and "_", but NOT punctuation and NOT spaces
				//IBT dictionary entries sometimes contain spaces but osisrefs can't so _32_ is used
				// e.g.  UZV Matthew 1:18: The link to "Holy Spirit" (Muqaddas Ruhdan)
                ref = replaceIBTSpecialCharacters(ref)
                val bookKey = document.getKey(ref)
                return BookAndKey(bookKey, document)
            }
        }
        return null
    }

    /**
     * IBT use _nn_ for punctuation chars in references to dictionaries e.g. _32_ represents a space so 'Holy_32_Spirit' should be converted to 'Holy Spirit'
     * @param ref Key e.g. dictionary key
     * @return ref with _nn_ replaced by punctuation
     */
    private fun replaceIBTSpecialCharacters(ref: String): String {
        val refIBTSpecialCharMatcher = IBT_SPECIAL_CHAR_RE.matcher(ref)
        val output = StringBuffer()
        while (refIBTSpecialCharMatcher.find()) {
            val specialChar = Character.toString(refIBTSpecialCharMatcher.group(1).toInt().toChar())
            refIBTSpecialCharMatcher.appendReplacement(output, specialChar)
        }
        refIBTSpecialCharMatcher.appendTail(output)
        return output.toString()
    }

    /** user has selected a Bible verse link
     */
    @Throws(NoSuchKeyException::class)
    private fun getBibleKey(keyText: String, versification: Versification, book: Book?): BookAndKey {
        val key: Passage = PassageKeyFactory.instance().getKey(versification, keyText)
        return BookAndKey(key, book)
    }

    enum class KeyType {
        KEY,
        ZERO_PADDED_KEY,
        ZERO_PADDED_KEY_R,
        CATEGORY;

        companion object {
            val ALL_TYPES = listOf(KEY, ZERO_PADDED_KEY, ZERO_PADDED_KEY_R, CATEGORY)
        }
    }

    private val preferredKeyType = hashMapOf<String, KeyType>()

    enum class StrongsKeyType {HEBREW, GREEK}

    fun getStrongsKey(book: Book, key: String): BookAndKey? {
        val match = Regex("^([GH])(0*)([0-9]+).*").find(key)
        val category = match?.groups?.get(1)?.value
            ?: if(book.isHebrewDef) "H"
            else if(book.isGreekDef) "G"
            else return null

        val lst = getStrongsKey(listOf(book), key, when(category) {
            "H" -> StrongsKeyType.HEBREW
            "G" -> StrongsKeyType.GREEK
            else -> return null
        })
        return lst?.firstOrNull() as BookAndKey?
    }

    @Throws(NoSuchKeyException::class)
    private fun getStrongsKey(books: List<Book>, key: String, strongsKeyType: StrongsKeyType): BookAndKeyList? {
        val match = Regex("^([GH]?)(0*)([0-9]+).*").find(key)
        val match2 = Regex("^(0*)([0-9]+).*").find(key)

        val category = when(strongsKeyType) {
            StrongsKeyType.HEBREW -> "H"
            StrongsKeyType.GREEK -> "G"
        }

        val sanitizedKeyBase = match?.groups?.get(3)?.value ?: match2?.groups?.get(2)?.value

        val zeroPaddedKey = sanitizedKeyBase?.padStart(5, '0') ?: ""

        val keyOptions = mapOf(
            KeyType.KEY to key,
            KeyType.ZERO_PADDED_KEY to zeroPaddedKey,
            KeyType.ZERO_PADDED_KEY_R to zeroPaddedKey + "\r",

            // MyBible dictionaries
            KeyType.CATEGORY to category + sanitizedKeyBase
        )

        val bookAndKeys = books.mapNotNull { book ->
            val preferred = preferredKeyType[book.initials] ?: KeyType.KEY

            val keyTypes = mutableListOf(preferred)
            keyTypes.addAll(KeyType.ALL_TYPES.filterNot { it == preferred })

            val k = run {
                for (keyType in keyTypes) {
                    val opt = keyOptions[keyType]
                    val candidate = try {
                        book.getKey(opt)
                    } catch (e: NoSuchKeyException) {
                        null
                    }
                    if (candidate != null) {
                        preferredKeyType[book.initials] = keyType
                        return@run candidate
                    }
                }
                null
            }

            if (k == null) null else BookAndKey(k, book)
        }
        if(bookAndKeys.isEmpty()) return null
        return bookAndKeyListOf(bookAndKeys)
    }

    @Throws(NoSuchKeyException::class)
    private fun getRobinsonMorphologyKey(key: String): BookAndKeyList {
        val robinsonBooks = SwordDocumentFacade.defaultRobinsonGreekMorphology
        return bookAndKeyListOf(robinsonBooks.mapNotNull {
            val k = try { it.getKey(key) } catch (e: NoSuchKeyException) { null }
            if(k != null) BookAndKey(k, it) else null
        })
    }

    fun showAllOccurrences(ref: String, bibleSection: SearchBibleSection) {
        val currentBible = currentPageManager.currentBible.currentDocument!!
        // if current bible has no Strongs refs then try to find one that has
        val strongsBible = if (currentBible.hasFeature(FeatureType.STRONGS_NUMBERS)) {
            currentBible
        } else {
            SwordDocumentFacade.defaultBibleWithStrongs
        }
        // possibly no Strong's bible or it has not been indexed
        var needToIndex = false
        if (strongsBible == null) {
            Dialogs.showErrorMsg(R.string.no_indexed_bible_with_strongs_ref)
            return
        } else if (currentBible == strongsBible && !checkStrongs(currentBible)) {
            Log.i(TAG, "Index status is NOT DONE")
            needToIndex = true
        }
        // The below uses ANY_WORDS because that does not add anything to the search string
		//String noLeadingZeroRef = StringUtils.stripStart(ref, "0");
        val searchText = searchControl.decorateSearchString("strong:$ref", SearchType.ANY_WORDS, bibleSection, null)
        Log.i(TAG, "Search text:$searchText")
        val activity = CurrentActivityHolder.currentActivity!!
        val searchParams = Bundle()
        searchParams.putString(SearchControl.SEARCH_TEXT, searchText)
        searchParams.putString(SearchControl.SEARCH_DOCUMENT, strongsBible.initials)
        searchParams.putString(SearchControl.TARGET_DOCUMENT, currentBible.initials)
        var intent: Intent? = null
        intent = if (needToIndex) {
            Intent(activity, SearchIndex::class.java)
        } else { //If an indexed Strong's module is in place then do the search - the normal situation
            Intent(activity, SearchResults::class.java)
        }
        intent.putExtras(searchParams)
        activity.startActivity(intent)
    }

    /** ensure a book is indexed and the index contains typical Greek or Hebrew Strongs Numbers
     */
    private fun checkStrongs(bible: Book): Boolean {
        return try {
            bible.indexStatus == IndexStatus.DONE &&
                (bible.find("+[Gen 1:1] strong:h7225").cardinality > 0 || bible.find("+[John 1:1] strong:g746").cardinality > 0 || bible.find("+[Gen 1:1] strong:g746").cardinality > 0)
        } catch (be: BookException) {
            Log.e(TAG, "Error checking strongs numbers", be)
            false
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "Error checking strongs numbers", e)
            false
        }
    }
    fun resolveRef(searchRef: String, doc: SwordBook? = null): Key? {
        val searchDoc = doc ?: windowControl.defaultBibleDoc(useLinks = true)
        return SwordContentFacade.resolveRef(searchRef, searchDoc.language.code, searchDoc.versification)
    }

    fun tryToOpenRef(searchRef: String, doc: SwordBook? = null): Boolean {
        val key = resolveRef(searchRef, doc)
        if (key != null) {
            showLink(doc, key, forceOpenHere = true)
            return true
        }
        return false
    }

    fun showLink(document: Book?, key: Key, forceOpenHere: Boolean = false) {
        val currentPageManager = currentPageManager
        val defaultDocument = currentPageManager.currentBible.currentDocument!!
        if (windowMode == WindowMode.WINDOW_MODE_NEW) {
            windowControl.addNewWindow(document?: defaultDocument, key)
        } else if (checkIfOpenLinksInDedicatedWindow() && !forceOpenHere) {
            windowControl.showLink(document, key)
        } else { // old style - open links in current window
            currentPageManager.setCurrentDocumentAndKey(document ?: defaultDocument, key)
        }
    }

    private fun checkIfOpenLinksInDedicatedWindow(): Boolean {
        if(windowControl.windowRepository.isMaximized) return false
        return when (windowMode) {
            WindowMode.WINDOW_MODE_SPECIAL -> true
            WindowMode.WINDOW_MODE_THIS -> false
            WindowMode.WINDOW_MODE_UNDEFINED -> settings.getBoolean("open_links_in_special_window_pref", true)
            else -> settings.getBoolean("open_links_in_special_window_pref", true)
        }
    }

    private val currentPageManager: CurrentPageManager
        get() = windowControl.activeWindowPageManager

    fun openMyNotes(v11nName: String, ordinal: Int): Boolean {
        val v11n = Versifications.instance().getVersification(v11nName)
        val verse = Verse(v11n, ordinal)
        showLink(currentPageManager.currentMyNotePage.currentDocument, verse)
        return true
    }

    fun openStudyPad(labelId: IdType, bookmarkId: IdType?): Boolean {
        val label = bookmarkControl.labelById(labelId) ?: return false
        val key = StudyPadKey(label, bookmarkId)
        showLink(FakeBookFactory.journalDocument, key)
        return true
    }

    companion object {
        private val IBT_SPECIAL_CHAR_RE = Pattern.compile("_(\\d+)_")
        private const val TAG = "LinkControl"
    }

}

val Book.isHebrewDef get() = bookMetaData.getValues("Feature")?.contains("HebrewDef") == true
val Book.isGreekDef get() = bookMetaData.getValues("Feature")?.contains("GreekDef") == true
