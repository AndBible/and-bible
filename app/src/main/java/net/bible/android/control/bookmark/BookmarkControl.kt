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
package net.bible.android.control.bookmark

import android.app.Activity
import android.content.Intent
import android.util.Log
import android.view.View
import com.google.android.material.snackbar.Snackbar
import net.bible.android.activity.R
import net.bible.android.common.resource.ResourceProvider
import net.bible.android.control.ApplicationScope
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.android.database.bookmarks.BookmarkEntities.Bookmark
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import net.bible.android.database.bookmarks.BookmarkEntities.BookmarkToLabel
import net.bible.android.database.bookmarks.BookmarkSortOrder
import net.bible.android.database.bookmarks.BookmarkStyle
import net.bible.android.database.bookmarks.PlaybackSettings
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.bookmark.BookmarkLabels
import net.bible.service.common.CommonUtils.getResourceColor
import net.bible.service.common.CommonUtils.getResourceString
import net.bible.service.common.CommonUtils.getSharedPreference
import net.bible.service.common.CommonUtils.saveSharedPreference
import net.bible.service.db.DatabaseContainer
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import java.lang.IllegalArgumentException
import java.lang.RuntimeException
import java.util.*
import javax.inject.Inject

abstract class BookmarkEvent

// TODO: implement listeners and add arguments
class BookmarkAddedOrUpdatedEvent(val bookmark: Bookmark, val labels: List<Long>? = null): BookmarkEvent()
class BookmarksDeletedEvent(val bookmarks: List<Bookmark>): BookmarkEvent()
class LabelAddedOrUpdatedEvent(val label: Label): BookmarkEvent()

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
open class BookmarkControl @Inject constructor(
	private val activeWindowPageManagerProvider: ActiveWindowPageManagerProvider,
    resourceProvider: ResourceProvider
) {
    // TODO: proper styles!!!
    val LABEL_ALL = Label(-999L, resourceProvider.getString(R.string.all)?: "all", BookmarkStyle.GREEN_HIGHLIGHT)
    val LABEL_UNLABELLED = Label(-998L, resourceProvider.getString(R.string.label_unlabelled)?: "unlabeled", BookmarkStyle.BLUE_HIGHLIGHT)

    private val dao get() = DatabaseContainer.db.bookmarkDao()

	fun updateBookmarkSettings(settings: PlaybackSettings) {
        val pageManager = activeWindowPageManagerProvider.activeWindowPageManager
        if (pageManager.currentPage.bookCategory == BookCategory.BIBLE) {
            updateBookmarkSettings(pageManager.currentBible.singleKey, settings)
        }
    }

    private fun updateBookmarkSettings(v: Verse, settings: PlaybackSettings) {
        val verse = if (v.verse == 0) Verse(v.versification, v.book, v.chapter, 1) else v

        // TODO: what if there are more?
        val bookmark = dao.bookmarksForVerseStartWithLabel(verse, speakLabel).firstOrNull()
        if (bookmark?.playbackSettings != null) {
            bookmark.playbackSettings = settings
            addOrUpdateBookmark(bookmark)
            Log.d("SpeakBookmark", "Updated bookmark settings " + bookmark + settings.speed)
        }
    }

    fun addBookmarkForVerseRange(book: Book?, verseRange: VerseRange) {
        if (!isCurrentDocumentBookmarkable) return
        // TODO: allow having many bookmarks in same verse
        var bookmark = dao.bookmarksStartingAtVerse(verseRange.start).firstOrNull()
        val currentActivity = CurrentActivityHolder.getInstance().currentActivity
        val currentView = currentActivity.findViewById<View>(R.id.coordinatorLayout)
        var message: Int? = null
        if (bookmark == null) { // prepare new bookmark and add to db
            bookmark = Bookmark(verseRange, null, book)
            bookmark = addOrUpdateBookmark(bookmark, doNotSync = true)
            message = R.string.bookmark_added
        } else {
            bookmark = dao.updateBookmarkDate(bookmark)
            message = R.string.bookmark_date_updated
        }
        val actionTextColor = getResourceColor(R.color.snackbar_action_text)
        Snackbar.make(currentView, message, Snackbar.LENGTH_LONG)
            .setActionTextColor(actionTextColor)
            .setAction(R.string.assign_labels) { showBookmarkLabelsActivity(currentActivity, bookmark) }.show()
        ABEventBus.getDefault().post(BookmarkAddedOrUpdatedEvent(bookmark))
    }

    fun deleteBookmarkForVerseRange(verseRange: VerseRange) {
        if (!isCurrentDocumentBookmarkable) return
        // TODO: allow having many bookmarks in same verse
        val bookmark = dao.bookmarksStartingAtVerse(verseRange.start).firstOrNull()
        val currentActivity = CurrentActivityHolder.getInstance().currentActivity
        val currentView = currentActivity.findViewById<View>(android.R.id.content)
        if (bookmark != null) {
            deleteBookmark(bookmark, true)
            Snackbar.make(currentView, R.string.bookmark_deleted, Snackbar.LENGTH_SHORT).show()
            ABEventBus.getDefault().post(BookmarksDeletedEvent(listOf(bookmark)))
        }
    }

    fun editBookmarkLabelsForVerseRange(verseRange: VerseRange) {
        if (!isCurrentDocumentBookmarkable) return
        // TODO: allow having many bookmarks in same verse
        val bookmark = dao.bookmarksStartingAtVerse(verseRange.start).firstOrNull()?: return
        val currentActivity = CurrentActivityHolder.getInstance().currentActivity
        showBookmarkLabelsActivity(currentActivity, bookmark)
    }

    val allBookmarks: List<Bookmark> get() = dao.allBookmarks()

    /** create a new bookmark  */
    fun addOrUpdateBookmark(bookmark: Bookmark, labels: List<Long>?=null, doNotSync: Boolean=false): Bookmark {
        if(bookmark.id != 0L) {
            dao.update(bookmark)
        } else {
            bookmark.id = dao.insert(bookmark)
        }

        if(labels != null) {
            for(l in labels.filter { it > 0 }) {
                dao.insert(BookmarkToLabel(bookmark.id, l))
            }
        }

        if(!doNotSync) {
            ABEventBus.getDefault().post(BookmarkAddedOrUpdatedEvent(bookmark, labels)) // TODO: make sure this talks with bibleview.js properly
        }
        return bookmark
    }

    fun bookmarksByIds(ids: List<Long>): List<Bookmark> = dao.bookmarksByIds(ids)

    fun hasBookmarksForVerse(verse: Verse): Boolean = dao.hasBookmarksForVerse(verse)

    fun firstBookmarkStartingAtVerse(key: Verse): Bookmark? = dao.bookmarksStartingAtVerse(key).firstOrNull()

    fun deleteBookmark(bookmark: Bookmark, doNotSync: Boolean = false) {
        dao.delete(bookmark)
        if(!doNotSync) {
            ABEventBus.getDefault().post(BookmarksDeletedEvent(listOf(bookmark)))
        }
    }

    fun deleteBookmarks(bookmarks: List<Bookmark>, doNotSync: Boolean = false) {
        dao.deleteBookmarks(bookmarks)
        if(!doNotSync) {
            ABEventBus.getDefault().post(BookmarksDeletedEvent(bookmarks))
        }
    }

    fun getBookmarksWithLabel(label: Label): List<Bookmark> = getSortedBookmarks(
        when {
            LABEL_ALL == label -> dao.allBookmarks(bookmarkSortOrder)
            LABEL_UNLABELLED == label -> dao.unlabelledBookmarks(bookmarkSortOrder)
            else -> dao.bookmarksWithLabel(label, bookmarkSortOrder)
        })

    // Not sure if this is really needed (or if per-ordinal sorting by DB is enough). Leaving this for now.
    private fun getSortedBookmarks(bookmarkList: List<Bookmark>): List<Bookmark> {
        if(bookmarkSortOrder == BookmarkSortOrder.CREATED_AT) return bookmarkList

        val comparator = BookmarkBibleOrderComparator(bookmarkList)

        // the new Java 7 sort is stricter and occasionally generates errors, so prevent total crash on listing bookmarks
        try {
            Collections.sort(bookmarkList, comparator)
        } catch (e: Exception) {
            Dialogs.instance.showErrorMsg(R.string.error_occurred, e)
        }
        return bookmarkList
    }

    fun labelsForBookmark(bookmark: Bookmark): List<Label> {
        return dao.labelsForBookmark(bookmark.id)
    }

    fun setLabelsForBookmark(bookmark: Bookmark, labels: List<Label>, doNotSync: Boolean = false) {
		val lbls = labels.toMutableList()
        lbls.remove(LABEL_ALL)
        lbls.remove(LABEL_UNLABELLED)

        val prevLabels = dao.labelsForBookmark(bookmark.id)

        //find those which have been deleted and remove them
        val deleted = HashSet(prevLabels)
        deleted.removeAll(lbls)

        dao.delete(deleted.map { BookmarkToLabel(bookmark.id, it.id) })

        //find those which are new and persist them
        val added = HashSet(lbls)
        added.removeAll(prevLabels)

        dao.insert(added.map { BookmarkToLabel(bookmark.id, it.id) })

        if(!doNotSync) {
            ABEventBus.getDefault().post(BookmarkAddedOrUpdatedEvent(bookmark, lbls.map { it.id }))
        }
    }

    fun insertOrUpdateLabel(label: Label): Label {
        if(label.id < 0) throw RuntimeException("Illegal negative label.id")
        if(label.id > 0L) {
            dao.update(label)
        } else {
            label.id = dao.insert(label)
        }
        ABEventBus.getDefault().post(LabelAddedOrUpdatedEvent(label))
        return label
    }

    fun deleteLabel(label: Label) = dao.delete(label)

    // add special label that is automatically associated with all-bookmarks
    val allLabels: List<Label>
        get() {
            val labelList = assignableLabels.toMutableList()
            // add special label that is automatically associated with all-bookmarks
            labelList.add(0, LABEL_UNLABELLED)
            labelList.add(0, LABEL_ALL)
            return labelList
        }

    val assignableLabels: List<Label> get() = dao.allLabelsSortedByName()

    fun changeBookmarkSortOrder() {
        bookmarkSortOrder = when (bookmarkSortOrder) {
            BookmarkSortOrder.BIBLE_ORDER -> BookmarkSortOrder.CREATED_AT
            BookmarkSortOrder.CREATED_AT -> BookmarkSortOrder.BIBLE_ORDER
        }
    }

    private var bookmarkSortOrder: BookmarkSortOrder
        get() {
            val bookmarkSortOrderStr = getSharedPreference(BOOKMARK_SORT_ORDER, BookmarkSortOrder.BIBLE_ORDER.toString())
            return try {
                BookmarkSortOrder.valueOf(bookmarkSortOrderStr!!)
            } catch (e: IllegalArgumentException) { BookmarkSortOrder.BIBLE_ORDER }
        }
        private set(bookmarkSortOrder) {
            saveSharedPreference(BOOKMARK_SORT_ORDER, bookmarkSortOrder.toString())
        }

    val bookmarkSortOrderDescription: String
        get() = if (BookmarkSortOrder.BIBLE_ORDER == bookmarkSortOrder) {
            getResourceString(R.string.sort_by_bible_book)
        } else {
            getResourceString(R.string.sort_by_date)
        }

    private val isCurrentDocumentBookmarkable: Boolean
        get() {
            val currentPageControl = activeWindowPageManagerProvider.activeWindowPageManager
            return currentPageControl.isBibleShown || currentPageControl.isCommentaryShown
        }

    internal fun showBookmarkLabelsActivity(currentActivity: Activity, bookmark: Bookmark) {
        val intent = Intent(currentActivity, BookmarkLabels::class.java)
        intent.putExtra(BOOKMARK_IDS_EXTRA, longArrayOf(bookmark.id))
        currentActivity.startActivity(intent)
    }

    private var _speakLabel: Label? = null
    val speakLabel: Label get() = _speakLabel?: dao.getOrCreateSpeakLabel().also { _speakLabel = it }

    fun reset() {
        _speakLabel = null
    }

    fun isSpeakBookmark(bookmark: Bookmark): Boolean = labelsForBookmark(bookmark).contains(speakLabel)
    fun speakBookmarkForVerse(verse: Verse) = dao.bookmarksForVerseStartWithLabel(verse, speakLabel).firstOrNull()

    fun bookmarksInBook(book: BibleBook): List<Bookmark> = dao.bookmarksInBook(book)
    fun bookmarksForVerseRange(verseRange: VerseRange): List<Bookmark> = dao.bookmarksForVerseRange(verseRange)

    companion object {
        const val BOOKMARK_IDS_EXTRA = "bookmarkIds"
        const val LABEL_NO_EXTRA = "labelNo"
        private const val BOOKMARK_SORT_ORDER = "BookmarkSortOrder"
        private const val TAG = "BookmarkControl"
    }

}
