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
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.page.BibleView
import net.bible.android.view.activity.search.SearchIndex
import net.bible.android.view.activity.search.SearchResults
import net.bible.service.common.CommonUtils.sharedPreferences
import net.bible.service.download.FakeBookFactory
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.BookAndKeyList
import net.bible.service.sword.StudyPadKey
import net.bible.service.sword.SwordDocumentFacade
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookException
import org.crosswire.jsword.book.FeatureType
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.index.IndexStatus
import org.crosswire.jsword.index.search.SearchType
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.NoSuchKeyException
import org.crosswire.jsword.passage.PassageKeyFactory
import org.crosswire.jsword.versification.Versification
import java.net.URLDecoder
import java.util.regex.Pattern
import javax.inject.Inject


/** Control traversal via links pressed by user in a browser e.g. to Strongs
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
class LinkControl @Inject constructor(
    private val windowControl: WindowControl,
	private val bookmarkControl: BookmarkControl,
	private val searchControl: SearchControl,
	private val swordDocumentFacade: SwordDocumentFacade,
	private val errorReportControl: ErrorReportControl)
{
    private var windowMode = WINDOW_MODE_UNDEFINED

    fun openMulti(links: List<BibleView.BibleLink>): Boolean {
        val key = BookAndKeyList()
        val bookKeys = links.map { getBookAndKey(it.url) }.filterNotNull()
        for(k in bookKeys) {
            key.addAll(k)
        }
        key.name = bookKeys.map { it.key.name }.joinToString(", ")
        showLink(FakeBookFactory.multiDocument, key)
        return true
    }

    fun loadApplicationUrl(link: BibleView.BibleLink): Boolean {
        return loadApplicationUrl(link.url)
    }

    fun errorLink() {
        errorReportControl.sendErrorReportEmail(Exception("Error in webview-js"))
    }

    private fun getBookAndKey(uriStr: String): BookAndKey? {
        Log.d(TAG, "Loading: $uriStr")
        val uriAnalyzer = UriAnalyzer()
        if (uriAnalyzer.analyze(uriStr)) {
            return when (uriAnalyzer.docType) {
                UriAnalyzer.DocType.BIBLE -> getBibleKey(uriAnalyzer.key)
                UriAnalyzer.DocType.GREEK_DIC -> getStrongsKey(swordDocumentFacade.defaultStrongsGreekDictionary, uriAnalyzer.key)
                UriAnalyzer.DocType.HEBREW_DIC -> getStrongsKey(swordDocumentFacade.defaultStrongsHebrewDictionary, uriAnalyzer.key)
                UriAnalyzer.DocType.ROBINSON -> getRobinsonMorphologyKey(uriAnalyzer.key)
                UriAnalyzer.DocType.SPECIFIC_DOC -> getSpecificDocRefKey(uriAnalyzer.book, uriAnalyzer.key)
                else -> null
            }
        }
        return null
    }

    private fun loadApplicationUrl(uriStr: String): Boolean {
        val bookAndKey = try {getBookAndKey(uriStr)} catch (e: NoSuchKeyException) {return false} ?: return false
        showLink(bookAndKey.document, bookAndKey.key)
        return true
	}

    @Throws(NoSuchKeyException::class)
    private fun getSpecificDocRefKey(initials: String?, ref: String): BookAndKey? {
        var ref = ref
        if (StringUtils.isEmpty(initials)) {
            return getBibleKey(ref)
        } else {
            val document = swordDocumentFacade.getDocumentByInitials(initials)
            if (document == null) { // tell user to install book
                Dialogs.instance.showErrorMsg(R.string.document_not_installed, initials)
            } else { //Foreign language keys may have been URLEncoded so need to URLDecode them e.g. UZV module at Matthew 1. The first link is "David" (looks a bit like DOBYA)
                ref = URLDecoder.decode(ref)
                //According to the OSIS schema, the osisRef attribute can contain letters and "_", but NOT punctuation and NOT spaces
				//IBT dictionary entries sometimes contain spaces but osisrefs can't so _32_ is used
				// e.g.  UZV Matthew 1:18: The link to "Holy Spirit" (Muqaddas Ruhdan)
                ref = replaceIBTSpecialCharacters(ref)
                val bookKey = document.getKey(ref)
                return BookAndKey(document, bookKey)
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
    private fun getBibleKey(keyText: String): BookAndKey {
        val pageManager = currentPageManager
        val bible = pageManager.currentBible.currentDocument!!
        // get source versification
        val sourceDocumentVersification: Versification
        val currentDoc = pageManager.currentPage.currentDocument
        sourceDocumentVersification = if (currentDoc is AbstractPassageBook) {
            currentDoc.versification
        } else { // default to v11n of current Bible.
			//TODO av11n issue.  GenBooks have no v11n and this default would be used for links from GenBooks which would only sometimes be correct
            (bible as AbstractPassageBook).versification
        }
        val key: Key = PassageKeyFactory.instance().getKey(sourceDocumentVersification, keyText)

        // Bible not specified so use the default Bible version
        return BookAndKey(windowControl.defaultBibleDoc, key)
    }

    /** user has selected a Strong's Number link so show Strong's page for key in link
     */

    @Throws(NoSuchKeyException::class)
    private fun getStrongsKey(book: Book?, key: String): BookAndKey? { // valid Strongs uri but Strongs refs not installed
        if (book == null) {
            Dialogs.instance.showErrorMsg(R.string.strongs_not_installed)
            // this uri request was handled by showing an error message
            return null
        }
        val sanitizedKey = sanitizeStrongsKey(key) ?: return null
        val k = book.getKey(sanitizedKey)
        return BookAndKey(book, k)
    }

    private fun sanitizeStrongsKey(key: String): String? =
        Regex("^([0-9]+).*").find(key)?.groups?.get(1)?.value?.padStart(5, '0')

    /** user has selected a morphology link so show morphology page for key in link
     */
    @Throws(NoSuchKeyException::class)
    private fun getRobinsonMorphologyKey(key: String): BookAndKey? {
        val robinson = swordDocumentFacade.getDocumentByInitials("robinson")
        // valid Strongs uri but Strongs refs not installed
        if (robinson == null) {
            Dialogs.instance.showErrorMsg(R.string.morph_robinson_not_installed)
            // this uri request was handled by showing an error message
            return null
        }
        val robinsonNumberKey = robinson.getKey(key)
        return BookAndKey(robinson, robinsonNumberKey)
    }

    fun showAllOccurrences(ref: String, biblesection: SearchBibleSection, refPrefix: String) {
        val currentBible = currentPageManager.currentBible.currentDocument!!
        var strongsBible: Book? = null
        // if current bible has no Strongs refs then try to find one that has
        strongsBible = if (currentBible.hasFeature(FeatureType.STRONGS_NUMBERS)) {
            currentBible
        } else {
            swordDocumentFacade.defaultBibleWithStrongs
        }
        // possibly no Strong's bible or it has not been indexed
        var needToDownloadIndex = false
        if (strongsBible == null) {
            Dialogs.instance.showErrorMsg(R.string.no_indexed_bible_with_strongs_ref)
            return
        } else if (currentBible == strongsBible && !checkStrongs(currentBible)) {
            Log.d(TAG, "Index status is NOT DONE")
            needToDownloadIndex = true
        }
        // The below uses ANY_WORDS because that does not add anything to the search string
		//String noLeadingZeroRef = StringUtils.stripStart(ref, "0");
        val searchText = searchControl.decorateSearchString("strong:$refPrefix$ref", SearchType.ANY_WORDS, biblesection, null)
        Log.d(TAG, "Search text:$searchText")
        val activity = CurrentActivityHolder.getInstance().currentActivity
        val searchParams = Bundle()
        searchParams.putString(SearchControl.SEARCH_TEXT, searchText)
        searchParams.putString(SearchControl.SEARCH_DOCUMENT, strongsBible.initials)
        searchParams.putString(SearchControl.TARGET_DOCUMENT, currentBible.initials)
        var intent: Intent? = null
        intent = if (needToDownloadIndex) {
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
        }
    }

    private fun showLink(document: Book?, key: Key) { // ask window controller to open link in desired window
        val currentPageManager = currentPageManager
        val defaultDocument = currentPageManager.currentBible.currentDocument!!
        if (windowMode == WINDOW_MODE_NEW) {
            windowControl.addNewWindow(document?: defaultDocument, key)
        } else if (checkIfOpenLinksInDedicatedWindow()) {
            if (document == null) {
                windowControl.showLinkUsingDefaultBible(key)
            } else {
                windowControl.showLink(document, key)
            }
        } else { // old style - open links in current window
            currentPageManager.setCurrentDocumentAndKey(document ?: defaultDocument, key)
        }
    }

    private fun checkIfOpenLinksInDedicatedWindow(): Boolean {
        if(windowControl.windowRepository.isMaximized) return false
        return when (windowMode) {
            WINDOW_MODE_SPECIAL -> true
            WINDOW_MODE_THIS -> false
            WINDOW_MODE_UNDEFINED -> sharedPreferences.getBoolean("open_links_in_special_window_pref", true)
            else -> sharedPreferences.getBoolean("open_links_in_special_window_pref", true)
        }
    }

    private val currentPageManager: CurrentPageManager
        get() = windowControl.activeWindowPageManager

    fun setWindowMode(windowMode: String) {
        this.windowMode = windowMode
    }

    fun openMyNotes(id: Long): Boolean {
        val bookmark = bookmarkControl.bookmarksByIds(listOf(id)).firstOrNull() ?: return false
        val key = bookmark.verseRange
        showLink(currentPageManager.currentMyNotePage.currentDocument, key)
        return true
    }

    fun openJournal(id: Long): Boolean {
        val label = bookmarkControl.labelById(id) ?: return false
        val key = StudyPadKey(label)
        showLink(FakeBookFactory.journalDocument, key)
        return true
    }

    companion object {
        private val IBT_SPECIAL_CHAR_RE = Pattern.compile("_(\\d+)_")
        private const val TAG = "LinkControl"
        const val WINDOW_MODE_THIS = "this"
        const val WINDOW_MODE_SPECIAL = "special"
        const val WINDOW_MODE_NEW = "new"
        const val WINDOW_MODE_UNDEFINED = "undefined"
    }

}
