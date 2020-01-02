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
import net.bible.android.control.speak.PlaybackSettings
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.bookmark.BookmarkLabels
import net.bible.service.common.CommonUtils.getResourceColor
import net.bible.service.common.CommonUtils.getResourceString
import net.bible.service.common.CommonUtils.getSharedPreference
import net.bible.service.common.CommonUtils.limitTextLength
import net.bible.service.common.CommonUtils.saveSharedPreference
import net.bible.service.db.bookmark.BookmarkDBAdapter
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

	fun updateBookmarkSettings(settings: PlaybackSettings) {
        if (activeWindowPageManagerProvider.activeWindowPageManager.currentPage.bookCategory == BookCategory.BIBLE) {
            updateBookmarkSettings(activeWindowPageManagerProvider.activeWindowPageManager.currentBible.singleKey, settings)
        }
    }

    private fun updateBookmarkSettings(v: Verse, settings: PlaybackSettings) {
        var v = v
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

    fun addBookmarkForVerseRange(verseRange: VerseRange): Boolean {
        var bOk = false
        if (isCurrentDocumentBookmarkable) {
            var bookmarkDto = getBookmarkByKey(verseRange)
            val currentActivity = CurrentActivityHolder.getInstance().currentActivity
            val currentView = currentActivity.findViewById<View>(R.id.coordinatorLayout)
            var success = false
            var message: Int? = null
            if (bookmarkDto == null) { // prepare new bookmark and add to db
                bookmarkDto = BookmarkDto()
                bookmarkDto.verseRange = verseRange
                bookmarkDto = addOrUpdateBookmark(bookmarkDto, true)
                success = bookmarkDto != null
                message = R.string.bookmark_added
            } else {
                bookmarkDto = refreshBookmarkDate(bookmarkDto)
                success = bookmarkDto != null
                message = R.string.bookmark_date_updated
            }
            val affectedBookmark = bookmarkDto
            if (success) { // success
                val actionTextColor = getResourceColor(R.color.snackbar_action_text)
                Snackbar.make(currentView, message, Snackbar.LENGTH_LONG)
                    .setActionTextColor(actionTextColor)
                    .setAction(R.string.assign_labels) { showBookmarkLabelsActivity(currentActivity, affectedBookmark) }.show()
                bOk = true
            } else {
                Dialogs.getInstance().showErrorMsg(R.string.error_occurred)
            }
        }
        ABEventBus.getDefault().post(SynchronizeWindowsEvent())
        return bOk
    }

    fun deleteBookmarkForVerseRange(verseRange: VerseRange): Boolean {
        val bOk = false
        if (isCurrentDocumentBookmarkable) {
            val bookmarkDto = getBookmarkByKey(verseRange)
            val currentActivity = CurrentActivityHolder.getInstance().currentActivity
            val currentView = currentActivity.findViewById<View>(android.R.id.content)
            if (bookmarkDto != null) {
                if (deleteBookmark(bookmarkDto, true)) {
                    Snackbar.make(currentView, R.string.bookmark_deleted, Snackbar.LENGTH_SHORT).show()
                } else {
                    Dialogs.getInstance().showErrorMsg(R.string.error_occurred)
                }
            }
        }
        ABEventBus.getDefault().post(SynchronizeWindowsEvent())
        return bOk
    }

    // Label related methods
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
    // pure bookmark methods
    /** get all bookmarks  */
    val allBookmarks: List<BookmarkDto>
        get() {
            val db = BookmarkDBAdapter()
            return try {
                getSortedBookmarks(db.allBookmarks)
            } finally {
                emptyList<BookmarkDto>()
            }
        }

    /** create a new bookmark  */
    fun addOrUpdateBookmark(bookmark: BookmarkDto, doNotSync: Boolean=false): BookmarkDto {
        val db = BookmarkDBAdapter()
        val newBookmark = try {
            db.insertOrUpdateBookmark(bookmark)
        } finally {}
        if(!doNotSync) {
            ABEventBus.getDefault().post(SynchronizeWindowsEvent())
        }
        return newBookmark
    }

    /** update bookmark date  */
	private fun refreshBookmarkDate(bookmark: BookmarkDto?): BookmarkDto? {
        val db = BookmarkDBAdapter()
        var updatedBookmark: BookmarkDto? = null
        updatedBookmark = try {
            db.updateBookmarkDate(bookmark!!)
        } finally {}
        return updatedBookmark
    }

    /** get all bookmarks  */
    fun getBookmarksById(ids: LongArray): List<BookmarkDto> {
        val bookmarks: MutableList<BookmarkDto> = ArrayList()
        val db = BookmarkDBAdapter()
        try {
            for (id in ids) {
                val bookmark = db.getBookmarkDto(id)
                if (bookmark != null) {
                    bookmarks.add(bookmark)
                }
            }
        } finally {}
        return bookmarks
    }

    fun isBookmarkForKey(key: Key?): Boolean {
        return key != null && getBookmarkByKey(key) != null
    }

    /** get bookmark with the same start verse as this key if it exists or return null  */
    fun getBookmarkByKey(key: Key): BookmarkDto? {
        return getBookmarkByOsisRef(key.osisRef)
    }

    /** get bookmark with the same start verse as this key if it exists or return null  */
    fun getBookmarkByOsisRef(osisRef: String?): BookmarkDto? {
        val db = BookmarkDBAdapter()
        var bookmark: BookmarkDto? = null
        bookmark = try {
            db.getBookmarkByStartKey(osisRef!!)
        } finally {}
        return bookmark
    }

    /** delete this bookmark (and any links to labels)  */
    fun deleteBookmark(bookmark: BookmarkDto?, doNotSync: Boolean = false): Boolean {
        var bOk = false
        if (bookmark?.id != null) {
            val db = BookmarkDBAdapter()
            bOk = try {
                db.removeBookmark(bookmark)
            } finally { }
        }
        if(!doNotSync) {
            ABEventBus.getDefault().post(SynchronizeWindowsEvent())
        }
        return bOk
    }

    /** get bookmarks with the given label  */
    fun getBookmarksWithLabel(label: LabelDto?): List<BookmarkDto> {
        val db = BookmarkDBAdapter()
		var bookmarkList: List<BookmarkDto>
		try {
            bookmarkList = when {
				LABEL_ALL == label -> db.allBookmarks
				LABEL_UNLABELLED == label -> db.unlabelledBookmarks
				else -> db.getBookmarksWithLabel(label!!)
			}
            bookmarkList = getSortedBookmarks(bookmarkList)
        } finally {}
        return bookmarkList
    }

    /** get bookmarks associated labels  */
    fun getBookmarkLabels(bookmark: BookmarkDto?): List<LabelDto> {
        if (bookmark == null) {
            return ArrayList()
        }
        val labels: List<LabelDto>
        val db = BookmarkDBAdapter()
        labels = try {
            db.getBookmarkLabels(bookmark)
        } finally {}
        return labels
    }

    /** label the bookmark with these and only these labels  */
    @JvmOverloads
    fun setBookmarkLabels(bookmark: BookmarkDto?, labels_: List<LabelDto>, doNotSync: Boolean = false) { // never save LABEL_ALL
		val labels = labels_.toMutableList()
        labels.remove(LABEL_ALL)
        labels.remove(LABEL_UNLABELLED)
        val db = BookmarkDBAdapter()
        try {
            val prevLabels = db.getBookmarkLabels(bookmark!!)
            //find those which have been deleted and remove them
            val deleted: MutableSet<LabelDto> = HashSet(prevLabels)
            deleted.removeAll(labels)
            for (label in deleted) {
                db.removeBookmarkLabelJoin(bookmark, label)
            }
            //find those which are new and persist them
            val added: MutableSet<LabelDto> = HashSet(labels)
            added.removeAll(prevLabels)
            for (label in added) {
                db.insertBookmarkLabelJoin(bookmark, label)
            }
        } finally {}
        if(!doNotSync) {
            ABEventBus.getDefault().post(SynchronizeWindowsEvent())
        }
    }

    fun saveOrUpdateLabel(label: LabelDto): LabelDto {
        val db = BookmarkDBAdapter()
        return try {
            if (label.id == null) {
                db.insertLabel(label)
            } else {
                db.updateLabel(label)
            }
        } finally {}
    }

    /** delete this bookmark (and any links to labels)  */
    fun deleteLabel(label: LabelDto?): Boolean {
        var bOk = false
        if (label?.id != null && LABEL_ALL != label && LABEL_UNLABELLED != label) {
            val db = BookmarkDBAdapter()
            bOk = try {
                db.removeLabel(label)
            } finally {}
        }
        return bOk
    }

    // add special label that is automatically associated with all-bookmarks
    val allLabels: List<LabelDto>
        get() {
            val labelList = assignableLabels
            // add special label that is automatically associated with all-bookmarks
            labelList.add(0, LABEL_UNLABELLED)
            labelList.add(0, LABEL_ALL)
            return labelList
        }

    val assignableLabels: MutableList<LabelDto>
        get() {
            val db = BookmarkDBAdapter()
            val labelList: MutableList<LabelDto> = ArrayList()
            try {
                labelList.addAll(db.allLabels)
            } finally {}
			labelList.sort()
            return labelList
        }

    fun changeBookmarkSortOrder() {
        bookmarkSortOrder = if (bookmarkSortOrder == BookmarkSortOrder.BIBLE_BOOK) {
            BookmarkSortOrder.DATE_CREATED
        } else {
            BookmarkSortOrder.BIBLE_BOOK
        }
    }

    private var bookmarkSortOrder: BookmarkSortOrder
        private get() {
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
            Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e)
        }
        return bookmarkList
    }

    private val isCurrentDocumentBookmarkable: Boolean
        private get() {
            val currentPageControl = activeWindowPageManagerProvider.activeWindowPageManager
            return currentPageControl.isBibleShown || currentPageControl.isCommentaryShown
        }

    private fun showBookmarkLabelsActivity(currentActivity: Activity, bookmarkDto: BookmarkDto?) { // Show label view for new bookmark
        val intent = Intent(currentActivity, BookmarkLabels::class.java)
        intent.putExtra(BOOKMARK_IDS_EXTRA, longArrayOf(bookmarkDto!!.id!!))
        currentActivity.startActivity(intent)
    }

    val orCreateSpeakLabel: LabelDto
        get() {
            val db = BookmarkDBAdapter()
            val label: LabelDto
            label = try {
                db.orCreateSpeakLabel
            } finally {}
            return label
        }

    fun isSpeakBookmark(bookmark: BookmarkDto?): Boolean {
        return getBookmarkLabels(bookmark).contains(orCreateSpeakLabel)
    }

    companion object {
        const val BOOKMARK_IDS_EXTRA = "bookmarkIds"
        const val LABEL_NO_EXTRA = "labelNo"
        private const val BOOKMARK_SORT_ORDER = "BookmarkSortOrder"
        private const val TAG = "BookmarkControl"
    }

}
