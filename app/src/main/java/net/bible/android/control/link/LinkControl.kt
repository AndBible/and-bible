/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
import net.bible.android.control.page.OrdinalRange
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
import org.crosswire.jsword.book.Books
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

    fun openMemorize(verseRange: BookAndKey): Boolean {
        showLink(FakeBookFactory.memorizeDocument, verseRange)
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
            val key = when (uriAnalyzer.docType) {
                UriAnalyzer.DocType.BIBLE -> getBibleKey(uriAnalyzer.key, versification, book)
                UriAnalyzer.DocType.GREEK_DIC -> getStrongsKey(SwordDocumentFacade.defaultStrongsGreekDictionary, uriAnalyzer.key, StrongsKeyType.GREEK)
                UriAnalyzer.DocType.HEBREW_DIC -> getStrongsKey(SwordDocumentFacade.defaultStrongsHebrewDictionary, uriAnalyzer.key, StrongsKeyType.HEBREW)
                UriAnalyzer.DocType.ROBINSON -> getRobinsonMorphologyKey(uriAnalyzer.key)
                UriAnalyzer.DocType.SPECIFIC_DOC -> getSpecificDocRefKey(uriAnalyzer.book, uriAnalyzer.key, versification, book)
                else -> null
            }
            // If a fragment was present (e.g. #o5 or #o5-10), parse ordinal range
            // and attach it for scroll + highlight in the frontend
            val fragment = uriAnalyzer.fragment
            if (key is BookAndKey && fragment != null) {
                val rangeParts = fragment.removePrefix("o").split("-", limit = 2)
                val start = rangeParts[0].toIntOrNull()
                if (start != null) {
                    val end = rangeParts.getOrNull(1)?.toIntOrNull() ?: start
                    return BookAndKey(key.key, key.document,
                        ordinal = OrdinalRange(start, end),
                        htmlId = "o-$start"
                    )
                }
            }
            return key
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
                    // Pass the full BookAndKey (not just .key) to preserve htmlId for anchor navigation
                    showLink(bookAndKeys.document, bookAndKeys)
                }
            }
            is BookAndKeyList -> {
                showLink(FakeBookFactory.multiDocument, bookAndKeys)
            }
        }

        return true
	}

    @Throws(NoSuchKeyException::class)
    private fun getSpecificDocRefKey(initials: String?, reference: String, versification: Versification, book: Book?): Key? {
        var ref = reference
        if (StringUtils.isEmpty(initials)) {
            return getBibleKey(ref, versification, book)
        } else {
            val document = SwordDocumentFacade.getDocumentByInitials(initials)
            if (document == null) {
                val strongsMatch = Regex("^([GH])0*[0-9]+").find(reference)
                if (strongsMatch != null) {
                    return when (strongsMatch.groupValues[1]) {
                        "G" -> getStrongsKey(SwordDocumentFacade.defaultStrongsGreekDictionary, reference, StrongsKeyType.GREEK)
                        "H" -> getStrongsKey(SwordDocumentFacade.defaultStrongsHebrewDictionary, reference, StrongsKeyType.HEBREW)
                        else -> { Dialogs.showErrorMsg(R.string.document_not_installed, initials); null }
                    }
                }
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
        val currentBible = currentPageManager.currentBible.currentDocument ?: return
        // if current bible has no Strongs refs then try to find one that has
        val strongsBible = if (currentBible.hasFeature(FeatureType.STRONGS_NUMBERS)) {
            currentBible
        } else {
            SwordDocumentFacade.defaultBibleWithStrongs
        }
        // possibly no Strong's bible
        if (strongsBible == null) {
            Dialogs.showErrorMsg(R.string.no_indexed_bible_with_strongs_ref)
            return
        }

        // Restore the document(s) chosen last time in the search-results document selector so
        // find-all remembers the user's preferred Strong's translation(s). Keep only installed,
        // Strong's-enabled books; fall back to the auto-selected strongsBible when nothing usable
        // is remembered.
        val remembered = settings.getString(SearchControl.STRONGS_SEARCH_TRANSLATIONS_PREF, null)
            ?.split(",").orEmpty()
            .filter { it.isNotBlank() }
            .mapNotNull { SwordDocumentFacade.getDocumentByInitials(it) as? SwordBook }
            .filter { it.hasFeature(FeatureType.STRONGS_NUMBERS) }
        val selection = remembered.ifEmpty { listOf(strongsBible) }
        val searchBible = selection.first()

        // The document that will actually be searched must be indexed with Strong's numbers;
        // otherwise route to SearchIndex to build the index (rather than silently returning
        // "0 verses").
        val needToIndex = !checkStrongs(searchBible)
        if (needToIndex) {
            Log.i(TAG, "Index status is NOT DONE")
        }
        // The below uses ANY_WORDS because that does not add anything to the search string
		//String noLeadingZeroRef = StringUtils.stripStart(ref, "0");
        val searchText = searchControl.decorateSearchString("strong:$ref", SearchType.ANY_WORDS, bibleSection, null)
        Log.i(TAG, "Search text:$searchText")
        val activity = CurrentActivityHolder.currentActivity!!
        val searchParams = Bundle()
        searchParams.putString(SearchControl.SEARCH_TEXT, searchText)
        searchParams.putString(SearchControl.SEARCH_DOCUMENT, searchBible.initials)
        searchParams.putBoolean(SearchControl.IS_STRONGS_SEARCH, true)
        val intent = if (needToIndex) {
            Intent(activity, SearchIndex::class.java)
        } else { //If an indexed Strong's module is in place then do the search - the normal situation
            Intent(activity, SearchResults::class.java)
        }
        intent.putExtras(searchParams)
        intent.putStringArrayListExtra(SearchControl.SELECTED_TRANSLATIONS, ArrayList(selection.map { it.initials }))
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
        val defaultDocument = currentPageManager.currentBible.currentDocument
        if (defaultDocument == null) {
            Log.w(TAG, "No current Bible document available for link navigation")
            return
        }
        if (windowMode == WindowMode.WINDOW_MODE_NEW) {
            windowControl.addNewWindow(document?: defaultDocument, key)
        } else if (checkIfOpenLinksInDedicatedWindow() && !forceOpenHere) {
            // Pass document through (it may be null for non-specific links, e.g. cross
            // references in Bibles or in EPUBs) so the links window keeps its current
            // Bible version instead of being forced to a specific one (#2502).
            // WindowControl.showLink substitutes a default Bible only when the links
            // window has no Bible document of its own yet.
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

    fun openStudyPad(labelId: IdType, entryId: IdType?): Boolean {
        val label = bookmarkControl.labelById(labelId) ?: return false
        val key = StudyPadKey(label, entryId)
        showLink(FakeBookFactory.journalDocument, key)
        return true
    }

    /**
     * Open an AI Document page in the dedicated links window.
     *
     * @param documentInitials The initials of the AI Document (e.g., "AIDocuments")
     * @param pageKey The page key within the document
     * @return True if the page was successfully opened, false otherwise
     */
    fun openAIDocument(documentInitials: String, pageKey: String): Boolean {
        val book = Books.installed().getBook(documentInitials)
        if (book == null) {
            Log.w(TAG, "AI Document not found: $documentInitials")
            return false
        }
        val key = try {
            book.getKey(pageKey)
        } catch (e: NoSuchKeyException) {
            Log.w(TAG, "Page key not found: $pageKey in $documentInitials", e)
            return false
        }
        if (key == null) {
            Log.w(TAG, "Page key returned null: $pageKey")
            return false
        }
        showLink(book, key)
        return true
    }

    /**
     * Look up a word/phrase in configured dictionaries.
     * Uses efficient binary search via book.getKey() for exact matching.
     * Returns true if results were found, false otherwise.
     */
    fun lookupInDictionaries(text: String): Boolean {
        val dictionaries = SwordDocumentFacade.wordLookupDictionaries
        if (dictionaries.isEmpty()) {
            Dialogs.showErrorMsg(R.string.word_not_found_in_dictionaries)
            return false
        }

        val searchText = normalizeSearchText(text)
        if (searchText.isBlank()) {
            Dialogs.showErrorMsg(R.string.word_not_found_in_dictionaries)
            return false
        }

        val bookAndKeys = mutableListOf<BookAndKey>()

        for (dict in dictionaries) {
            // Use getKey() which internally uses binary search - efficient O(log n)
            // JSword handles case normalization internally (converts to uppercase by default)
            val key = try {
                dict.getKey(searchText)
            } catch (_: NoSuchKeyException) {
                null
            }
            if (key != null) {
                bookAndKeys.add(BookAndKey(key, dict))
            }
        }

        if (bookAndKeys.isEmpty()) {
            Dialogs.showErrorMsg(R.string.word_not_found_in_dictionaries)
            return false
        }

        val keyList = bookAndKeyListOf(bookAndKeys)
        keyList.name = text
        showLink(FakeBookFactory.multiDocument, keyList)
        return true
    }

    private fun normalizeSearchText(text: String): String {
        // Trim and remove trailing punctuation
        return text.trim()
            .replace(Regex("[.,;:!?\"'()\\[\\]]+$"), "")
    }

    companion object {
        private val IBT_SPECIAL_CHAR_RE = Pattern.compile("_(\\d+)_")
        private const val TAG = "LinkControl"
    }

}

val Book.isHebrewDef get() = bookMetaData.getValues("Feature")?.contains("HebrewDef") == true
val Book.isGreekDef get() = bookMetaData.getValues("Feature")?.contains("GreekDef") == true
