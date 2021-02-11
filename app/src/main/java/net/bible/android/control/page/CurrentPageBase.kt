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
import android.view.Menu
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.android.control.PassageChangeMediator
import net.bible.android.database.WorkspaceEntities
import net.bible.service.common.CommonUtils
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.BookAndKeyList
import net.bible.service.sword.OsisError
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.SwordDocumentFacade
import org.crosswire.common.activate.Activator
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.NoSuchKeyException

/** Common functionality for different document page types
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
abstract class CurrentPageBase protected constructor(
	shareKeyBetweenDocs: Boolean,
	swordContentFacade: SwordContentFacade,
	swordDocumentFacade: SwordDocumentFacade,
    override val pageManager: CurrentPageManager
) : CurrentPage {

    override var isInhibitChangeNotifications: Boolean = false

    override var _key: Key? = null

    // just pretend we are at the top of the page if error occurs
    // if key has changed then offsetRatio must be reset because user has changed page

    /** how far down the page was the user - allows Back to go to correct line on non-Bible pages (Bibles use verse number for positioning)
     */
    override var currentYOffsetRatio = 0f
        get() {
            try { // if key has changed then offsetRatio must be reset because user has changed page
                if (key == null || key != keyWhenYOffsetRatioSet || currentDocument != docWhenYOffsetRatioSet) {
                    field = 0f
                }
            } catch (e: Exception) {
                // cope with occasional NPE thrown by above if statement
                // just pretend we are at the top of the page if error occurs
                field = 0f
                Log.w(TAG, "NPE getting currentYOffsetRatio")
            }
            return field
        }
        set(currentYOffsetRatio) {
            key ?: return
            docWhenYOffsetRatioSet = currentDocument
            keyWhenYOffsetRatioSet = key
            field = currentYOffsetRatio
        }
    private var keyWhenYOffsetRatioSet: Key? = null
    private var docWhenYOffsetRatioSet: Book? = null

    // all bibles and commentaries share the same key
    override var isShareKeyBetweenDocs: Boolean = false

    private val swordContentFacade: SwordContentFacade
    val swordDocumentFacade: SwordDocumentFacade

    /** notify mediator that page has changed and a lot of things need to update themselves
     */
    protected fun beforePageChange() {
        if (!isInhibitChangeNotifications) {
            PassageChangeMediator.getInstance().onBeforeCurrentPageChanged()
        }
    }

    /** notify mediator that page has changed and a lot of things need to update themselves
     */
    protected fun pageChange() {
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

    override fun getPageContent(key: Key): Document {
        val currentDocument = currentDocument!!
        return try {
            OsisDocument(
                book = currentDocument,
                key = key,
                osisFragments = synchronized(currentDocument) {
                    val frag = swordContentFacade.readOsisFragment(currentDocument, key)
                    listOf (if (frag.isEmpty()) {
                        throw OsisError(application.getString(R.string.error_no_content))
                    } else
                        OsisFragment(frag, key, currentDocument)
                    )
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting bible text", e)
            if(e is OsisError) ErrorDocument(e.message) else errorDocument
        }
    }

    private val errorDocument: ErrorDocument get() =
        ErrorDocument(application.getString(R.string.error_occurred))

    override fun checkCurrentDocumentStillInstalled(): Boolean {
        if (_currentDocument != null) {
            Log.d(TAG, "checkCurrentDocumentStillInstalled:$currentDocument")
            // this sets currentDoc to null if it does not exist
            _currentDocument = swordDocumentFacade.getDocumentByInitials(_currentDocument!!.initials)
        }
        return _currentDocument != null
    }

    private var _currentDocument: Book? = null

    override val currentDocument: Book?
        get() {
            if (_currentDocument == null) {
                _currentDocument = getDefaultBook()
            }
            return _currentDocument
        }

    private fun getDefaultBook(): Book? {
        // see net.bible.android.view.activity.page.MainBibleActivity.setCurrentDocument
        val savedDefaultBook = swordDocumentFacade.getDocumentByInitials(
            CommonUtils.sharedPreferences.getString("default-${documentCategory.bookCategory.name}", ""))

        return savedDefaultBook ?: {
            val books = swordDocumentFacade.getBooks(documentCategory.bookCategory)
            if (books.size > 0) books[0] else null
        }()
    }

    override fun setCurrentDocument(doc: Book?) {
        Log.d(TAG, "Set current doc to $doc")
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

    override fun updateOptionsMenu(menu: Menu) { // these are fine for Bible and commentary
        var menuItem = menu.findItem(R.id.searchButton)
        if (menuItem != null) {
            menuItem.isEnabled = isSearchable
        }
        menuItem = menu.findItem(R.id.bookmarksButton)
        if (menuItem != null) {
            menuItem.isEnabled = true
        }
        menuItem = menu.findItem(R.id.speakButton)
        if (menuItem != null) {
            menuItem.isEnabled = isSpeakable
        }
    }

    val pageEntity: WorkspaceEntities.Page get() {
            return WorkspaceEntities.Page(
                currentDocument?.initials,
                key?.osisID,
                currentYOffsetRatio
            )
        }

    open fun restoreFrom(entity: WorkspaceEntities.Page?) {
        if(entity == null) return
        val document = entity.document
        Log.d(TAG, "State document:$document")
        val book = swordDocumentFacade.getDocumentByInitials(document)
        if (book != null) {
            Log.d(TAG, "Restored document:" + book.name)
            // bypass setter to avoid automatic notifications
            localSetCurrentDocument(book)
            val keyName = entity.key
            if(!keyName.isNullOrEmpty()) {
                try {
                    doSetKey(book.getKey(keyName))
                } catch (e: NoSuchKeyException) {
                    Log.e(TAG, "Key ${keyName} not found in book ${document}")
                }
            }
        }
        currentYOffsetRatio = entity.currentYOffsetRatio ?: 0f
    }

    /** can we enable the main menu Speak button
     */
    override val isSpeakable: Boolean = true

    companion object {
        private const val TAG = "CurrentPage"
    }

    init {
        isShareKeyBetweenDocs = shareKeyBetweenDocs
        this.swordContentFacade = swordContentFacade
        this.swordDocumentFacade = swordDocumentFacade
    }
}
