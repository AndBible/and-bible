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
package net.bible.android.control.page

import android.util.Log
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.android.control.PassageChangeMediator
import net.bible.android.database.WorkspaceEntities
import net.bible.android.misc.OsisFragment
import net.bible.android.view.activity.base.Dialogs
import net.bible.service.common.CommonUtils
import net.bible.service.download.FakeBookFactory
import net.bible.service.download.doesNotExist
import net.bible.service.sword.DocumentNotFound
import net.bible.service.sword.OsisError
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.SwordDocumentFacade
import org.crosswire.common.activate.Activator
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.NoSuchKeyException
import org.crosswire.jsword.passage.VerseRange

/** Common functionality for different document page types
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
abstract class CurrentPageBase protected constructor(
	shareKeyBetweenDocs: Boolean,
	swordDocumentFacade: SwordDocumentFacade,
    override val pageManager: CurrentPageManager
) : CurrentPage {

    override var isInhibitChangeNotifications: Boolean = false

    override var _key: Key? = null

    // just pretend we are at the top of the page if error occurs
    // if key has changed then offsetRatio must be reset because user has changed page

    var _anchorOrdinal: Int? = 0

    /** how far down the page was the user - allows Back to go to correct line on non-Bible pages (Bibles use verse number for positioning)
     */
    override var anchorOrdinal: Int?
        get() {
            try { // if key has changed then offsetRatio must be reset because user has changed page
                if (key == null || key != keyWhenAnchorOrdinalSet || currentDocument != docWhenAnchorOrdinalSet) {
                    return 0
                }
            } catch (e: Exception) {
                // cope with occasional NPE thrown by above if statement
                // just pretend we are at the top of the page if error occurs
                Log.w(TAG, "NPE getting currentYOffsetRatio")
                return 0
            }
            return _anchorOrdinal
        }
        set(newValue) {
            key ?: return
            docWhenAnchorOrdinalSet = currentDocument
            keyWhenAnchorOrdinalSet = key
            _anchorOrdinal = newValue
        }

    private var keyWhenAnchorOrdinalSet: Key? = null
    private var docWhenAnchorOrdinalSet: Book? = null

    // all bibles and commentaries share the same key
    override var isShareKeyBetweenDocs: Boolean = false

    val swordDocumentFacade: SwordDocumentFacade

    /** notify mediator that page has changed and a lot of things need to update themselves
     */
    private fun beforePageChange() {
        PassageChangeMediator.getInstance().onBeforeCurrentPageChanged()
    }

    /** notify mediator that page has changed and a lot of things need to update themselves
     */
    private fun pageChange() {
        if (!isInhibitChangeNotifications) {
            PassageChangeMediator.getInstance().onCurrentPageChanged()
        }
    }

    override val singleKey: Key? get() = key

    override fun setKey(key: Key) {
        beforePageChange()
        doSetKey(key)
        pageChange()
    }

    override fun next() {}
    override fun previous() {}

    /** add or subtract a number of pages from the current position and return Page
     * default is one key per page - all except bible use this default
     */
    override fun getPagePlus(num: Int): Key { // If 1 key per page then same as getKeyPlus
        return getKeyPlus(num)
    }

    override val isSingleKey: Boolean = false

    override val currentPageContent: Document get() {
        val key = key
        return if(key == null) errorDocument else getPageContent(key)
    }

    var annotateKey: VerseRange? = null

    override val displayKey get() = annotateKey ?: key

    override fun getPageContent(key: Key): Document = try {
        val currentDocument = currentDocument!!

        val frag = synchronized(currentDocument) {
            val frag = SwordContentFacade.readOsisFragment(currentDocument, key)
            OsisFragment(frag, key, currentDocument)
        }

        annotateKey = frag.annotateRef

        OsisDocument(
            book = currentDocument,
            key = key,
            osisFragment = frag
        )
    } catch (e: Exception) {
        Log.e(TAG, "Error getting bible text", e)
        when (e) {
            is DocumentNotFound -> ErrorDocument(e.message, ErrorSeverity.NORMAL)
            is OsisError -> ErrorDocument(e.message, ErrorSeverity.WARNING)
            else -> errorDocument
        }
    }

    private val errorDocument: ErrorDocument get() =
        ErrorDocument(application.getString(R.string.error_occurred), ErrorSeverity.ERROR)

    override fun checkCurrentDocumenInstalled(): Boolean {
        if(_currentDocument?.doesNotExist == true) {
            val doc = swordDocumentFacade.getDocumentByInitials(_currentDocument!!.initials)
            if(doc != null)
            _currentDocument = doc
        }
        if (_currentDocument == null) {
            Log.i(TAG, "checkCurrentDocumentStillInstalled:$currentDocument")
            _currentDocument =  FakeBookFactory.giveDoesNotExist(_currentDocument!!.initials)
        }
        return _currentDocument != null && !_currentDocument!!.doesNotExist
    }

    private var _currentDocument: Book? = null

    override val currentDocument: Book?
        get() {
            if (_currentDocument == null) {
                _currentDocument = getDefaultBook()
            }
            if(_currentDocument?.doesNotExist == true) {
                val pseudo = _currentDocument!!
                val real = Books.installed().getBook(pseudo.initials)
                if(real != null) {
                    _currentDocument = real
                }
            }
            return _currentDocument
        }

    private fun getDefaultBook(): Book? {
        // see net.bible.android.view.activity.page.MainBibleActivity.setCurrentDocument
        val savedDefaultBook = swordDocumentFacade.getDocumentByInitials(
            CommonUtils.settings.getString("default-${documentCategory.bookCategory.name}", ""))

        return savedDefaultBook ?: {
            val books = swordDocumentFacade.getBooks(documentCategory.bookCategory).filter { !it.isLocked }
            if (books.isNotEmpty()) books[0] else null
        }()
    }

    override fun setCurrentDocument(doc: Book?) {
        Log.i(TAG, "Set current doc to $doc")
        val prevDoc = _currentDocument
        if (doc != _currentDocument && !isShareKeyBetweenDocs && key != null && !doc!!.contains(key)) {
            doSetKey(null)
        }
        localSetCurrentDocument(doc)
        // try to clear memory to prevent OutOfMemory errors
        if (_currentDocument != prevDoc) {
            Activator.deactivate(prevDoc)
        }
    }

    val isCurrentDocumentSet: Boolean get() = _currentDocument != null


    /* Set new doc and if possible show new doc
	 * @see net.bible.android.control.CurrentPage#setCurrentDocument(org.crosswire.jsword.book.Book)
	 */
    override fun setCurrentDocumentAndKey(doc: Book, key: Key) {
        doSetKey(key)
        localSetCurrentDocument(doc)
    }

    protected open fun localSetCurrentDocument(doc: Book?) {
        _currentDocument = doc
    }

    fun onlySetCurrentDocument(doc: Book?) {
        _currentDocument = doc
    }

    protected open fun localSetCurrentDocument(doc: Book?, isMyNote: Boolean = false) {
        localSetCurrentDocument(doc)
    }

    val pageEntity: WorkspaceEntities.Page get() {
            return WorkspaceEntities.Page(
                currentDocument?.initials,
                key?.osisRef,
                anchorOrdinal
            )
        }

    open fun restoreFrom(entity: WorkspaceEntities.Page?) {
        if(entity == null) return
        val document = entity.document
        Log.i(TAG, "State document:$document")
        val book = swordDocumentFacade.getDocumentByInitials(document)
            ?: if(document != null) FakeBookFactory.giveDoesNotExist(document) else null
        if (book != null) {
            Log.i(TAG, "Restored document: ${book.name} ${book.initials}")
            // bypass setter to avoid automatic notifications
            localSetCurrentDocument(book)
            val keyName = entity.key
            if(!keyName.isNullOrEmpty()) {
                try {
                    doSetKey(book.getKey(keyName))
                } catch (e: Exception) {
                    Log.e(TAG, "Key $keyName not be loaded from $document", e)
                    if(e !is NoSuchKeyException) {
                        Dialogs.instance.showErrorMsg(R.string.error_occurred, e)
                    }
                }
            }
        }
        anchorOrdinal = entity.anchorOrdinal
    }

    /** can we enable the main menu Speak button
     */
    override val isSpeakable: Boolean = true

    /** Can we sync between windows
     */
    override val isSyncable: Boolean = true
    companion object {
        private const val TAG = "CurrentPage"
    }

    init {
        isShareKeyBetweenDocs = shareKeyBetweenDocs
        this.swordDocumentFacade = swordDocumentFacade
    }
}
