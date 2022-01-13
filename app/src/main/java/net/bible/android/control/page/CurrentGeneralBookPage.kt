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

import android.app.Activity
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.bible.android.common.toV11n
import net.bible.android.database.WorkspaceEntities
import net.bible.android.misc.OsisFragment
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.IntentHelper
import net.bible.android.view.activity.bookmark.ManageLabels
import net.bible.android.view.activity.bookmark.updateFrom
import net.bible.android.view.activity.navigation.ChooseDocument
import net.bible.android.view.activity.navigation.genbookmap.ChooseGeneralBookKey
import net.bible.android.view.activity.page.MainBibleActivity.Companion._mainBibleActivity
import net.bible.service.common.firstBibleDoc
import net.bible.service.download.FakeBookFactory
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.BookAndKeyList
import net.bible.service.sword.OsisError
import net.bible.service.sword.StudyPadKey
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.SwordDocumentFacade
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.Passage
import org.crosswire.jsword.passage.VerseRangeFactory
import java.lang.Exception

/** Reference to current passage shown by viewer
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class CurrentGeneralBookPage internal constructor(
    swordDocumentFacade: SwordDocumentFacade,
    pageManager: CurrentPageManager
) : CachedKeyPage(false, swordDocumentFacade, pageManager),
    CurrentPage
{

    override val documentCategory = DocumentCategory.GENERAL_BOOK

    val isSpecialDoc get() = setOf(FakeBookFactory.journalDocument, FakeBookFactory.multiDocument).contains(currentDocument)
    val isStudyPad get() = FakeBookFactory.journalDocument == currentDocument

    override val isSpeakable: Boolean get() = !isSpecialDoc

    override fun startKeyChooser(context: ActivityBase) {
        GlobalScope.launch(Dispatchers.Main) {
            when (currentDocument) {
                FakeBookFactory.journalDocument -> {
                    val result = context.awaitIntent(Intent(context, ManageLabels::class.java)
                        .putExtra("data", ManageLabels.ManageLabelsData(mode = ManageLabels.Mode.STUDYPAD)
                            .applyFrom(_mainBibleActivity?.workspaceSettings)
                            .toJSON())
                    )
                    if(result?.resultCode == Activity.RESULT_OK) {
                        val resultData = ManageLabels.ManageLabelsData.fromJSON(result.resultData.getStringExtra("data")!!)
                        _mainBibleActivity?.workspaceSettings?.updateFrom(resultData)
                    }
                }
                FakeBookFactory.multiDocument -> {
                    context.startActivityForResult(
                        Intent(context, ChooseDocument::class.java),
                        IntentHelper.UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH
                    )
                }
                else -> context.startActivity(Intent(context, ChooseGeneralBookKey::class.java))
            }
        }
    }


    private val defaultBibleDoc get() = pageManager.currentBible.currentDocument ?: firstBibleDoc

    override val currentPageContent: Document
        get() {
            val key = key
            return when(key) {
                is StudyPadKey -> {
                    val bookmarks = pageManager.bookmarkControl.getBookmarksWithLabel(key.label, addData = true)
                    val journalTextEntries = pageManager.bookmarkControl.getJournalTextEntriesForLabel(key.label)
                    val bookmarkToLabels = bookmarks.mapNotNull { pageManager.bookmarkControl.getBookmarkToLabel(it.id, key.label.id) }
                    val bookmarkId = key.bookmarkId
                    StudyPadDocument(key.label, bookmarkId, bookmarks, bookmarkToLabels, journalTextEntries)
                }
                is BookAndKeyList -> {
                    val frags = key.filterIsInstance<BookAndKey>().map {
                        val doc = it.document ?: defaultBibleDoc
                        var k = it.key
                        try {
                            if(doc is SwordBook && k is Passage) {
                                k = k.toV11n(doc.versification)
                            }
                            OsisFragment(SwordContentFacade.readOsisFragment(doc, k), k, doc)
                        } catch (e: OsisError) {
                            Log.e(TAG, "Fragment could not be read")
                            OsisFragment(e.xml, k, doc)
                        }
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
                val nextLabel = pageManager.bookmarkControl.getNextLabel((key as StudyPadKey).label)
                setKey(StudyPadKey(nextLabel))
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
                val nextLabel = pageManager.bookmarkControl.getPrevLabel((key as StudyPadKey).label)
                setKey(StudyPadKey(nextLabel))
            }
            FakeBookFactory.multiDocument -> {}
            else -> {
                getKeyPlus(-1).let {
                    setKey(it)
                }
            }
        }
    }

    override val isSingleKey = true
	override val key: Key? get() = _key

	/** can we enable the main menu search button
     */
    override val isSearchable: Boolean
        get() = false

    override val isSyncable: Boolean
        get() = false

    override fun restoreFrom(entity: WorkspaceEntities.Page?) {
        when (entity?.document) {
            FakeBookFactory.journalDocument.initials -> {
                val (_, id) = entity!!.key?.split(":") ?: return
                val label = pageManager.bookmarkControl.labelById(id.toLong())
                if (label != null) {
                    doSetKey(StudyPadKey(label))
                    localSetCurrentDocument(FakeBookFactory.journalDocument)
                    anchorOrdinal = entity.anchorOrdinal
                }
            }
            FakeBookFactory.multiDocument.initials -> {
                val refs = entity!!.key!!.split("||").map { it.split(":") }.mapNotNull {
                    try {
                        val isUnspecifiedDoc = it[0] == "null"
                        val book: Book? = if(isUnspecifiedDoc) pageManager.currentBible.currentDocument else Books.installed().getBook(it[0])
                        val key = if (book is SwordBook) {
                            VerseRangeFactory.fromString(book.versification, it[1])
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
