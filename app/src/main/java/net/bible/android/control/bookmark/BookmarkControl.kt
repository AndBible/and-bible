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
import net.bible.android.control.event.passage.SynchronizeWindowsEvent
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.PlaybackSettings
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.bookmark.BookmarkLabels
import net.bible.service.common.CommonUtils.getResourceColor
import net.bible.service.common.CommonUtils.getResourceString
import net.bible.service.common.CommonUtils.getSharedPreference
import net.bible.service.common.CommonUtils.limitTextLength
import net.bible.service.common.CommonUtils.saveSharedPreference
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.bookmark.BookmarkDto
import net.bible.service.db.bookmark.LabelDto
import net.bible.service.sword.SwordContentFacade
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import java.util.*
import javax.inject.Inject

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
open class BookmarkControl @Inject constructor(
	private val swordContentFacade: SwordContentFacade,
	private val activeWindowPageManagerProvider: ActiveWindowPageManagerProvider, resourceProvider: ResourceProvider)
{
    private val LABEL_ALL = LabelDto(-999L, resourceProvider.getString(R.string.all), null)
	private val LABEL_UNLABELLED = LabelDto(-998L, resourceProvider.getString(R.string.label_unlabelled), null)
    val dao get() = DatabaseContainer.db.bookmarkDao()

	fun updateBookmarkSettings(settings: PlaybackSettings) {
        if (activeWindowPageManagerProvider.activeWindowPageManager.currentPage.bookCategory == BookCategory.BIBLE) {
            updateBookmarkSettings(activeWindowPageManagerProvider.activeWindowPageManager.currentBible.singleKey, settings)
        }
    }

    private fun updateBookmarkSettings(verse: Verse, settings: PlaybackSettings) {
        var v = verse
        if (v.verse == 0) {
            v = Verse(v.versification, v.book, v.chapter, 1)
        }
        val bookmarkDto = getBookmarkByKey(v)
        if (bookmarkDto?.playbackSettings != null) {
            bookmarkDto.playbackSettings = settings
            addOrUpdateBookmark(bookmarkDto)
            Log.d("SpeakBookmark", "Updated bookmark settings " + bookmarkDto + settings.speed)
        }
    }

    fun addBookmarkForVerseRange(verseRange: VerseRange) {
        if (isCurrentDocumentBookmarkable) {
            var bookmarkDto = getBookmarkByKey(verseRange)
            val currentActivity = CurrentActivityHolder.getInstance().currentActivity
            val currentView = currentActivity.findViewById<View>(R.id.coordinatorLayout)
            var message: Int? = null
            if (bookmarkDto == null) { // prepare new bookmark and add to db
                bookmarkDto = BookmarkDto()
                bookmarkDto.verseRange = verseRange
                bookmarkDto = addOrUpdateBookmark(bookmarkDto, true)
                message = R.string.bookmark_added
            } else {
                bookmarkDto = refreshBookmarkDate(bookmarkDto)
                message = R.string.bookmark_date_updated
            }
            val affectedBookmark = bookmarkDto
            val actionTextColor = getResourceColor(R.color.snackbar_action_text)
            Snackbar.make(currentView, message, Snackbar.LENGTH_LONG)
                .setActionTextColor(actionTextColor)
                .setAction(R.string.assign_labels) { showBookmarkLabelsActivity(currentActivity, affectedBookmark) }.show()
        }
        ABEventBus.getDefault().post(SynchronizeWindowsEvent())
    }

    fun deleteBookmarkForVerseRange(verseRange: VerseRange) {
        if (isCurrentDocumentBookmarkable) {
            val bookmarkDto = getBookmarkByKey(verseRange)
            val currentActivity = CurrentActivityHolder.getInstance().currentActivity
            val currentView = currentActivity.findViewById<View>(android.R.id.content)
            if (bookmarkDto != null) {
                deleteBookmark(bookmarkDto, true)
                Snackbar.make(currentView, R.string.bookmark_deleted, Snackbar.LENGTH_SHORT).show()
            }
        }
        ABEventBus.getDefault().post(SynchronizeWindowsEvent())
    }

    fun editBookmarkLabelsForVerseRange(verseRange: VerseRange) {
        if (isCurrentDocumentBookmarkable) {
            val bookmarkDto = getBookmarkByKey(verseRange)
            val currentActivity = CurrentActivityHolder.getInstance().currentActivity
            bookmarkDto?.let { showBookmarkLabelsActivity(currentActivity, it) }
        }
    }

    fun getBookmarkVerseKey(bookmark: BookmarkDto): String {
        var keyText = ""
        try {
            val versification = activeWindowPageManagerProvider.activeWindowPageManager.currentBible.versification
            keyText = bookmark.getVerseRange(versification).name
        } catch (e: Exception) {
            Log.e(TAG, "Error getting verse text", e)
        }
        return keyText
    }

    fun getBookmarkVerseText(bookmark: BookmarkDto): String? {
        var verseText: String? = ""
        try {
            val currentBible = activeWindowPageManagerProvider.activeWindowPageManager.currentBible
            val versification = currentBible.versification
            verseText = swordContentFacade.getPlainText(currentBible.currentDocument, bookmark.getVerseRange(versification))
            verseText = limitTextLength(verseText)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting verse text", e)
        }
        return verseText
    }

    val allBookmarks: List<BookmarkDto> get() = getSortedBookmarks(dao.allBookmarks().map { BookmarkDto(it) })

    /** create a new bookmark  */
    fun addOrUpdateBookmark(bookmark: BookmarkDto, doNotSync: Boolean=false): BookmarkDto {
        if(bookmark.id != null) {
            dao.update(bookmark.entity)
        } else {
            bookmark.id = dao.insert(bookmark.entity)
        }

        if(!doNotSync) {
            ABEventBus.getDefault().post(SynchronizeWindowsEvent())
        }
        return bookmark
    }

	private fun refreshBookmarkDate(bookmark: BookmarkDto) = BookmarkDto(dao.updateBookmarkDate(bookmark.entity))

    fun getBookmarksByIds(ids: LongArray): List<BookmarkDto> = dao.bookmarksByIds(ids).map { BookmarkDto(it) }

    fun isBookmarkForKey(key: Key?): Boolean = key != null && getBookmarkByKey(key) != null

    fun getBookmarkByKey(key: Key): BookmarkDto? = getBookmarkByOsisRef(key.osisRef)

    fun getBookmarkByOsisRef(osisRef: String?): BookmarkDto? {
        if(osisRef == null) return null
        val entity = dao.bookmarkByOsisRef(osisRef) ?: return null
        return BookmarkDto(entity)
    }

    fun deleteBookmark(bookmark: BookmarkDto, doNotSync: Boolean = false) {
        dao.delete(bookmark.entity)
        if(!doNotSync) {
            ABEventBus.getDefault().post(SynchronizeWindowsEvent())
        }
    }

    fun getBookmarksWithLabel(label: LabelDto): List<BookmarkDto> {
		val bookmarkList = when {
				LABEL_ALL == label -> dao.allBookmarks()
				LABEL_UNLABELLED == label -> dao.unlabelledBookmarks()
				else -> dao.bookmarksWithLabel(label.id!!)
			}.map { BookmarkDto(it) }

        return getSortedBookmarks(bookmarkList)
    }

    fun getBookmarkLabels(bookmark: BookmarkDto): List<LabelDto> {
        return dao.labelsForBookmark(bookmark.id!!).map { LabelDto(it) }
    }

    fun setBookmarkLabels(bookmark: BookmarkDto, labels: List<LabelDto>, doNotSync: Boolean = false) {
		val lbls = labels.toMutableList()
        lbls.remove(LABEL_ALL)
        lbls.remove(LABEL_UNLABELLED)

        val prevLabels = dao.labelsForBookmark(bookmark.id!!).map { LabelDto(it) }

        //find those which have been deleted and remove them
        val deleted = HashSet(prevLabels)
        deleted.removeAll(lbls)

        dao.delete(deleted.map { BookmarkEntities.BookmarkToLabel(bookmark.id!!, it.id!!) })

        //find those which are new and persist them
        val added = HashSet(lbls)
        added.removeAll(prevLabels)

        dao.insert(added.map { BookmarkEntities.BookmarkToLabel(bookmark.id!!, it.id!!) })

        if(!doNotSync) {
            ABEventBus.getDefault().post(SynchronizeWindowsEvent())
        }
    }

    fun insertOrUpdateLabel(label: LabelDto): LabelDto {
        if(label.id != null) {
            dao.update(label.entity)
        } else {
            label.id = dao.insert(label.entity)
        }
        return label
    }

    fun deleteLabel(label: LabelDto) = dao.delete(label.entity)


    // add special label that is automatically associated with all-bookmarks
    val allLabels: List<LabelDto>
        get() {
            val labelList = assignableLabels.toMutableList()
            // add special label that is automatically associated with all-bookmarks
            labelList.add(0, LABEL_UNLABELLED)
            labelList.add(0, LABEL_ALL)
            return labelList
        }

    val assignableLabels: List<LabelDto> get() = dao.allLabels().map { LabelDto(it) }.sorted()

    fun changeBookmarkSortOrder() {
        bookmarkSortOrder = if (bookmarkSortOrder == BookmarkSortOrder.BIBLE_BOOK) {
            BookmarkSortOrder.DATE_CREATED
        } else {
            BookmarkSortOrder.BIBLE_BOOK
        }
    }

    private var bookmarkSortOrder: BookmarkSortOrder
        get() {
            val bookmarkSortOrderStr = getSharedPreference(BOOKMARK_SORT_ORDER, BookmarkSortOrder.BIBLE_BOOK.toString())
            return BookmarkSortOrder.valueOf(bookmarkSortOrderStr!!)
        }
        private set(bookmarkSortOrder) {
            saveSharedPreference(BOOKMARK_SORT_ORDER, bookmarkSortOrder.toString())
        }

    val bookmarkSortOrderDescription: String
        get() = if (BookmarkSortOrder.BIBLE_BOOK == bookmarkSortOrder) {
            getResourceString(R.string.sort_by_bible_book)
        } else {
            getResourceString(R.string.sort_by_date)
        }

    private fun getSortedBookmarks(bookmarkList: List<BookmarkDto>): List<BookmarkDto> {
        val comparator: Comparator<BookmarkDto> = when (bookmarkSortOrder) {
            BookmarkSortOrder.DATE_CREATED -> BookmarkCreationDateComparator()
            BookmarkSortOrder.BIBLE_BOOK -> BookmarkDtoBibleOrderComparator(bookmarkList)
        }
        // the new Java 7 sort is stricter and occasionally generates errors, so prevent total crash on listing bookmarks
        try {
            Collections.sort(bookmarkList, comparator)
        } catch (e: Exception) {
            Dialogs.instance.showErrorMsg(R.string.error_occurred, e)
        }
        return bookmarkList
    }

    private val isCurrentDocumentBookmarkable: Boolean
        get() {
            val currentPageControl = activeWindowPageManagerProvider.activeWindowPageManager
            return currentPageControl.isBibleShown || currentPageControl.isCommentaryShown
        }

    private fun showBookmarkLabelsActivity(currentActivity: Activity, bookmarkDto: BookmarkDto?) { // Show label view for new bookmark
        val intent = Intent(currentActivity, BookmarkLabels::class.java)
        intent.putExtra(BOOKMARK_IDS_EXTRA, longArrayOf(bookmarkDto!!.id!!))
        currentActivity.startActivity(intent)
    }

    val speakLabel: LabelDto get() = LabelDto(dao.getOrCreateSpeakLabel())

    fun isSpeakBookmark(bookmark: BookmarkDto): Boolean {
        return getBookmarkLabels(bookmark).contains(speakLabel)
    }

    companion object {
        const val BOOKMARK_IDS_EXTRA = "bookmarkIds"
        const val LABEL_NO_EXTRA = "labelNo"
        private const val BOOKMARK_SORT_ORDER = "BookmarkSortOrder"
        private const val TAG = "BookmarkControl"
    }

}
