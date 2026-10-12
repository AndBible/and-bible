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
package net.bible.android.control.page

import net.bible.sharedcore.log.Log
import net.bible.android.common.toV11n
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.misc.OsisFragment
import net.bible.service.common.firstBibleDoc
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.BookAndKeyList
import net.bible.service.sword.DocumentNotFound
import net.bible.service.sword.OsisError
import net.bible.service.sword.StudyPadKey
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.epub.isEpub
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.Passage
import org.crosswire.jsword.passage.PassageKeyFactory
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import java.lang.Exception

/** Reference to current passage shown by viewer
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class CurrentGeneralBookPage internal constructor(
    pageManager: CurrentPageManager
) : CachedKeyPage(false, pageManager),
    CurrentPage
{

    override val documentCategory = DocumentCategory.GENERAL_BOOK

    val isSpecialDoc get() = setOf(fakeBookFactory.journalDocument, fakeBookFactory.multiDocument).contains(currentDocument)
    val isStudyPad get() = fakeBookFactory.journalDocument == currentDocument

    override val isSpeakable: Boolean get() = !isSpecialDoc

    private val defaultBibleDoc get() = pageManager.currentBible.currentDocument ?: firstBibleDoc

    override suspend fun currentPageContent(): Document {
        return when(val key = key) {
            is StudyPadKey -> {
                val bookmarks = pageManager.bookmarkControl.getBibleBookmarksWithLabel(key.label, addData = true)
                val genericBookmarks = pageManager.bookmarkControl.getGenericBookmarksWithLabel(key.label, addData = true)
                val journalTextEntries = pageManager.bookmarkControl.getStudyPadTextEntriesForLabel(key.label)
                val bookmarkToLabels = bookmarks.mapNotNull { pageManager.bookmarkControl.getBookmarkToLabel(it, key.label.id) as BookmarkEntities.BibleBookmarkToLabel? }
                val genericBookmarkToLabels = genericBookmarks.mapNotNull { pageManager.bookmarkControl.getBookmarkToLabel(it, key.label.id) as BookmarkEntities.GenericBookmarkToLabel? }
                val entryId = key.entryId
                StudyPadDocument(key.label, entryId, bookmarks, genericBookmarks, bookmarkToLabels, genericBookmarkToLabels, journalTextEntries)
            }
            is BookAndKeyList -> {
                // A multi-document key is ASSEMBLED, not asked for: a Strong's tap builds one
                // Robinson entry per installed morphology document (LinkControl.kt:329). A
                // document that is not installed therefore contributes an error card with its
                // own download link that the user never requested -- drop it instead.
                //
                // A key genuinely missing from an INSTALLED document is different: that is
                // information, not noise, and must still produce its card.
                //
                // Both cases throw the SAME exception class, DocumentNotFound
                // (SwordContentFacade.kt:170 for "not installed", :174 and :359 for "key not in
                // document") -- so the two cannot be told apart by catch-type alone. dropUninstalled
                // re-checks Books.installed() for the specific document inside the catch instead.
                fun fragmentFor(bookAndKey: BookAndKey, dropUninstalled: Boolean): OsisFragment? {
                    val doc = bookAndKey.document ?: defaultBibleDoc
                    var k = bookAndKey.key
                    return try {
                        if(doc is SwordBook) {
                            k = when(k) {
                                is Passage -> k.toV11n(doc.versification)
                                is VerseRange -> k.toV11n(doc.versification)
                                is Verse -> k.toV11n(doc.versification)
                                else -> k
                            }
                        }
                        OsisFragment(SwordContentFacade.readOsisFragment(doc, k, strings = org.koin.core.context.GlobalContext.get().get<net.bible.sharedcore.platform.CoreStrings>()), k, doc)
                    } catch (e: OsisError) {
                        if(dropUninstalled && e is DocumentNotFound && Books.installed().getBook(doc.initials) == null) {
                            Log.i(TAG, "Dropping an uninstalled document from an assembled key: ${doc.initials}")
                            null
                        } else {
                            Log.e(TAG, "Fragment could not be read")
                            OsisFragment(e.xml, k, doc)
                        }
                    }
                }
                val bookAndKeys = key.filterIsInstance<BookAndKey>()
                val frags = bookAndKeys.mapNotNull { fragmentFor(it, dropUninstalled = true) }
                // If everything dropped (every document in the assembled key is uninstalled),
                // fall back to today's behaviour -- showing every card -- rather than an empty page.
                val shown = frags.ifEmpty { bookAndKeys.mapNotNull { fragmentFor(it, dropUninstalled = false) } }
                MultiFragmentDocument(shown, state = pageManager.jsState)
            }
            else -> super.currentPageContent()
        }
    }

    /** set key without notification
     *
     * @param key
     */
    override fun doSetKey(key: Key?) {
        _key = key
    }

    override suspend fun next() {
        val key = key
        when (currentDocument) {
            fakeBookFactory.journalDocument -> {
                val document = currentDocument
                val nextLabel = pageManager.bookmarkControl.getNextLabel((key as StudyPadKey).label)
                // A manual selection can supersede this step while its label lookup is suspended.
                if (pageManager.currentPage === this && currentDocument === document && this.key === key) {
                    setKey(StudyPadKey(nextLabel))
                }
            }
            fakeBookFactory.multiDocument -> {}
            else -> {
                setKey(getKeyPlus(1))
            }
        }
    }

    override suspend fun previous() {
        val key = key
        when (currentDocument) {
            fakeBookFactory.journalDocument -> {
                val document = currentDocument
                val nextLabel = pageManager.bookmarkControl.getPrevLabel((key as StudyPadKey).label)
                if (pageManager.currentPage === this && currentDocument === document && this.key === key) {
                    setKey(StudyPadKey(nextLabel))
                }
            }
            fakeBookFactory.multiDocument -> {}
            else -> {
                setKey(getKeyPlus(-1))
            }
        }
    }

    override val isSingleKey = true
	override val key: Key? get() = _key

	/** can we enable the main menu search button
     */
    override val isSearchable: Boolean get() = currentDocument?.isEpub == true

    override val isSyncable: Boolean = false

    override fun restoreFrom(entity: WorkspaceEntities.Page?) = restoreFrom(entity, null)

    fun restoreFrom(entity: WorkspaceEntities.Page?, studyPadLabel: BookmarkEntities.Label?) {
        when (entity?.document) {
            fakeBookFactory.journalDocument.initials -> {
                val splitted = entity!!.key?.split(":")?: return
                if(splitted.size != 2) return
                val label = studyPadLabel
                if (label != null) {
                    doSetKey(StudyPadKey(label))
                    localSetCurrentDocument(fakeBookFactory.journalDocument)
                    anchorOrdinal = entity.anchorOrdinal?.let { OrdinalRange(it) }
                }
            }
            fakeBookFactory.multiDocument.initials -> {
                val refs = entity!!.key!!.split("||").map { it.split(":") }.mapNotNull {
                    try {
                        val isUnspecifiedDoc = it[0] == "null"
                        val book: Book? = if(isUnspecifiedDoc) pageManager.currentBible.currentDocument else Books.installed().getBook(it[0])
                        val key = if (book is SwordBook) {
                            PassageKeyFactory.instance().getKey(book.versification, it[1])
                        } else {
                            book?.getKey(it[1])
                        }
                        if(book == null || key == null) null else BookAndKey(key, if(isUnspecifiedDoc) null else book)
                    } catch (e: Exception) {
                        null
                    }
                }

                val key = BookAndKeyList()
                for (ref in refs) {
                    key.addAll(ref)
                }
                doSetKey(key)
                localSetCurrentDocument(fakeBookFactory.multiDocument)
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
