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

import android.view.Menu
import net.bible.android.activity.R
import net.bible.android.database.WorkspaceEntities
import net.bible.android.view.activity.journal.Journals
import net.bible.android.view.activity.navigation.genbookmap.ChooseGeneralBookKey
import net.bible.service.download.FakeBookFactory
import net.bible.service.sword.JournalKey
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.SwordDocumentFacade
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.Key

/** Reference to current passage shown by viewer
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class CurrentGeneralBookPage internal constructor(
    swordContentFacade: SwordContentFacade,
    swordDocumentFacade: SwordDocumentFacade,
    pageManager: CurrentPageManager
) : CachedKeyPage(false, swordContentFacade, swordDocumentFacade, pageManager),
    CurrentPage
{

    override val documentCategory = DocumentCategory.GENERAL_BOOK

    override val keyChooserActivity get() =
        if(currentDocument == journalDocument) Journals::class.java
        else ChooseGeneralBookKey::class.java

    override val currentPageContent: Document
        get() {
            val key = key
            return if(key is JournalKey) {
                val bookmarks = pageManager.bookmarkControl.getBookmarksWithLabel(key.label, addData = true)
                val journalTextEntries = pageManager.bookmarkControl.getJournalTextEntriesForLabel(key.label)
                val bookmarkToLabels = bookmarks.map { pageManager.bookmarkControl.getBookmarkToLabel(it.id, key.label.id)!! }
                JournalDocument(key.label, bookmarks, bookmarkToLabels, journalTextEntries)
            } else super.currentPageContent
        }

    /** set key without notification
     *
     * @param key
     */
    override fun doSetKey(key: Key?) {
        _key = key
    }

    override fun next() {
        val key = key
        if(key is JournalKey) {
            val nextLabel = pageManager.bookmarkControl.getNextLabel(key.label)
            setKey(JournalKey(nextLabel))
        } else {
            getKeyPlus(1).let {
                setKey(it)
            }
        }
    }

    override fun previous() {
        val key = key
        if(key is JournalKey) {
            val nextLabel = pageManager.bookmarkControl.getPrevLabel(key.label)
            setKey(JournalKey(nextLabel))
        } else {
            getKeyPlus(-1).let {
                setKey(it)
            }
        }
    }

    override fun updateOptionsMenu(menu: Menu) {
        super.updateOptionsMenu(menu)
        val menuItem = menu.findItem(R.id.bookmarksButton)
        if (menuItem != null) {
            menuItem.isEnabled = false
        }
    }

    override val isSingleKey = true
	override val key: Key? get() = _key

	/** can we enable the main menu search button
     */
    override val isSearchable: Boolean
        get() = false

    val journalDocument: Book get() = FakeBookFactory.journalDocument

    override fun restoreFrom(entity: WorkspaceEntities.Page?) {
        if(entity?.document == journalDocument.initials) {
            val (_, id) = entity!!.key?.split(":")?: return
            val label = pageManager.bookmarkControl.labelById(id.toLong())
            if(label != null) {
                doSetKey(JournalKey(label))
                localSetCurrentDocument(journalDocument)
            }
        } else {
            super.restoreFrom(entity)
        }
    }

    companion object {
        private const val TAG = "CurrentGeneralBookPage"
    }
}
