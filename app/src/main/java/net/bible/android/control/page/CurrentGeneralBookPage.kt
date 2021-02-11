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
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.BookAndKeyList
import net.bible.service.sword.JournalKey
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.SwordDocumentFacade
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.VerseRangeFactory
import java.lang.Exception

/** Reference to current passage shown by viewer
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class CurrentGeneralBookPage internal constructor(
    val swordContentFacade: SwordContentFacade,
    swordDocumentFacade: SwordDocumentFacade,
    pageManager: CurrentPageManager
) : CachedKeyPage(false, swordContentFacade, swordDocumentFacade, pageManager),
    CurrentPage
{

    override val documentCategory = DocumentCategory.GENERAL_BOOK

    override val keyChooserActivity get() =
        when (currentDocument) {
            FakeBookFactory.journalDocument -> Journals::class.java
            FakeBookFactory.multiDocument -> null
            else -> ChooseGeneralBookKey::class.java
        }

    override val currentPageContent: Document
        get() {
            val key = key
            return when(key) {
                is JournalKey -> {
                    val bookmarks = pageManager.bookmarkControl.getBookmarksWithLabel(key.label, addData = true)
                    val journalTextEntries = pageManager.bookmarkControl.getJournalTextEntriesForLabel(key.label)
                    val bookmarkToLabels = bookmarks.map { pageManager.bookmarkControl.getBookmarkToLabel(it.id, key.label.id)!! }
                    JournalDocument(key.label, bookmarks, bookmarkToLabels, journalTextEntries)
                }
                is BookAndKeyList -> {
                    val frags = key.filterIsInstance<BookAndKey>().map {
                        OsisFragment(swordContentFacade.readOsisFragment(it.document, it.key), it.key, it.document)
                    }
                    MultiFragmentDocument(frags)
                }
                else -> super.currentPageContent
            }
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
        when (currentDocument) {
            FakeBookFactory.journalDocument -> {
                val nextLabel = pageManager.bookmarkControl.getNextLabel((key as JournalKey).label)
                setKey(JournalKey(nextLabel))
            }
            FakeBookFactory.multiDocument -> {}
            else -> {
                getKeyPlus(1).let {
                    setKey(it)
                }
            }
        }
    }

    override fun previous() {
        val key = key
        when (currentDocument) {
            FakeBookFactory.journalDocument -> {
                val nextLabel = pageManager.bookmarkControl.getPrevLabel((key as JournalKey).label)
                setKey(JournalKey(nextLabel))
            }
            FakeBookFactory.multiDocument -> {}
            else -> {
                getKeyPlus(-1).let {
                    setKey(it)
                }
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

    override fun restoreFrom(entity: WorkspaceEntities.Page?) {
        when (entity?.document) {
            FakeBookFactory.journalDocument.initials -> {
                val (_, id) = entity!!.key?.split(":") ?: return
                val label = pageManager.bookmarkControl.labelById(id.toLong())
                if (label != null) {
                    doSetKey(JournalKey(label))
                    localSetCurrentDocument(FakeBookFactory.journalDocument)
                }
            }
            FakeBookFactory.multiDocument.initials -> {
                val refs = entity!!.key!!.split("||").map { it.split(":") }.map {
                    try {
                        val book = Books.installed().getBook(it[0])
                        val key = if(book is SwordBook) {
                            VerseRangeFactory.fromString(book.versification, it[1])
                        } else {
                            book.getKey(it[1])
                        }
                        BookAndKey(book, key)

                    } catch (e: Exception) {
                        null
                    }
                }.filterNotNull()

                val key = BookAndKeyList()
                for (ref in refs) {
                    key.addAll(ref)
                }
                doSetKey(key)
                localSetCurrentDocument(FakeBookFactory.multiDocument)
            }
            else -> {
                super.restoreFrom(entity)
            }
        }
    }

    companion object {
        private const val TAG = "CurrentGeneralBookPage"
    }
}
