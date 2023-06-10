/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.android.control.bookmark

import android.util.Log
import net.bible.android.activity.R
import net.bible.android.common.resource.ResourceProvider
import net.bible.android.common.toV11n
import net.bible.android.control.ApplicationScope
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.page.DocumentCategory
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.IdType
import net.bible.android.database.LogEntryTypes
import net.bible.android.database.bookmarks.BookmarkEntities.BookmarkWithNotes
import net.bible.android.database.bookmarks.BookmarkEntities.BookmarkToLabel
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import net.bible.android.database.bookmarks.BookmarkEntities.StudyPadTextEntry
import net.bible.android.database.bookmarks.BookmarkEntities.StudyPadTextEntryText
import net.bible.android.database.bookmarks.BookmarkEntities.StudyPadTextEntryWithText
import net.bible.android.database.bookmarks.BookmarkSortOrder
import net.bible.android.database.bookmarks.BookmarkStyle
import net.bible.android.database.bookmarks.PlaybackSettings
import net.bible.android.database.bookmarks.SPEAK_LABEL_NAME
import net.bible.android.database.bookmarks.UNLABELED_NAME
import net.bible.android.misc.OsisFragment
import net.bible.service.db.BookmarksUpdatedViaSyncEvent
import net.bible.service.db.DatabaseContainer
import net.bible.service.sword.OsisError
import net.bible.service.sword.SwordContentFacade
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import java.lang.IndexOutOfBoundsException
import java.util.*
import javax.inject.Inject
import kotlin.collections.ArrayList
import kotlin.math.min

abstract class BookmarkEvent

class BookmarkAddedOrUpdatedEvent(val bookmark: BookmarkWithNotes): BookmarkEvent()
class BookmarkToLabelAddedOrUpdatedEvent(val bookmarkToLabel: BookmarkToLabel)
class BookmarksDeletedEvent(val bookmarkIds: List<IdType>): BookmarkEvent()
class LabelAddedOrUpdatedEvent(val label: Label): BookmarkEvent()
class BookmarkNoteModifiedEvent(val bookmarkId: IdType, val notes: String?, val lastUpdatedOn: Long): BookmarkEvent()

class StudyPadOrderEvent(
    val labelId: IdType,
    val newStudyPadTextEntry: StudyPadTextEntryWithText? = null,
    val bookmarkToLabelsOrderChanged: List<BookmarkToLabel>,
    val studyPadOrderChanged: List<StudyPadTextEntryWithText>
)

class StudyPadTextEntryDeleted(val studyPadTextEntryId: IdType)

val LABEL_ALL_ID = IdType.empty()

@ApplicationScope
open class BookmarkControl @Inject constructor(
    val windowControl: WindowControl,
    resourceProvider: ResourceProvider,
) {
    init {
        ABEventBus.register(this)
    }
    // Dummy labels for all / unlabelled
    private val labelAll = Label(LABEL_ALL_ID, resourceProvider.getString(R.string.all)?: "all", color = BookmarkStyle.GREEN_HIGHLIGHT.backgroundColor)

    private val dao get() = DatabaseContainer.instance.bookmarkDb.bookmarkDao()

	fun updateBookmarkPlaybackSettings(settings: PlaybackSettings) {
        val pageManager = windowControl.activeWindowPageManager
        if (pageManager.currentPage.documentCategory == DocumentCategory.BIBLE) {
            updateBookmarkPlaybackSettings(pageManager.currentBible.singleKey, settings)
        }
    }

    private fun updateBookmarkPlaybackSettings(v: Verse, settings: PlaybackSettings) {
        val verse = if (v.verse == 0) Verse(v.versification, v.book, v.chapter, 1) else v

        val bookmark = dao.bookmarksForVerseStartWithLabel(verse, speakLabel).firstOrNull()
        if (bookmark?.playbackSettings != null) {
            bookmark.playbackSettings = settings
            addOrUpdateBookmark(bookmark)
            Log.i("SpeakBookmark", "Updated bookmark settings " + bookmark + settings.speed)
        }
    }

    val allBookmarks: List<BookmarkWithNotes> get() = dao.allBookmarks()

    fun allBookmarksWithNotes(orderBy: BookmarkSortOrder): List<BookmarkWithNotes> = dao.allBookmarksWithNotes(orderBy)

    fun addOrUpdateBookmark(bookmark: BookmarkWithNotes, labels: Set<IdType>?=null, updateNotes: Boolean = false): BookmarkWithNotes {
        val notes = bookmark.noteEntity
        if(bookmark.new) {
            dao.insert(bookmark.bookmarkEntity)
            if(notes != null) {
                dao.insert(notes)
            }
            bookmark.new = false
        } else {
            dao.update(bookmark.bookmarkEntity)
            if(updateNotes) {
                if (notes != null) {
                    dao.update(notes)
                } else {
                    dao.deleteBookmarkNotes(bookmark.id)
                }
            }
        }

        val labelIdsInDb = labels?.mapNotNull {dao.labelById(it)?.id }

        if(labelIdsInDb != null) {
            val existingLabels = dao.labelsForBookmark(bookmark.id).map { it.id }.toSet()
            val toBeDeleted = existingLabels.filterNot { labelIdsInDb.contains(it) }
            val toBeAdded = labelIdsInDb.filterNot { existingLabels.contains(it) }

            dao.deleteLabelsFromBookmark(bookmark.id, toBeDeleted.map {it})

            val addBookmarkToLabels = toBeAdded.filter {it.isNotEmpty() }.map { BookmarkToLabel(bookmark.id, it, orderNumber = dao.countStudyPadEntities(it)) }
            dao.insert(addBookmarkToLabels)
            if(labelIdsInDb.find { it == bookmark.primaryLabelId } == null) {
                bookmark.primaryLabelId = labelIdsInDb.firstOrNull()
                dao.update(bookmark.bookmarkEntity)
            }
            windowControl.windowRepository?.updateRecentLabels(toBeAdded.union(toBeDeleted).toList()) // for tests ?.
        }

        addText(bookmark)
        addLabels(bookmark)
        ABEventBus.post(
            BookmarkAddedOrUpdatedEvent(bookmark)
        )
        return bookmark
    }

    fun bookmarksByIds(ids: List<IdType>): List<BookmarkWithNotes> = dao.bookmarksByIds(ids)

    fun bookmarkById(id: IdType): BookmarkWithNotes? = dao.bookmarkById(id)

    fun hasBookmarksForVerse(verse: Verse): Boolean = dao.hasBookmarksForVerse(verse)

    fun firstBookmarkStartingAtVerse(key: Verse): BookmarkWithNotes? = dao.bookmarksStartingAtVerse(key).firstOrNull()

    fun deleteBookmark(bookmark: BookmarkWithNotes) {
        dao.delete(bookmark)
        sanitizeStudyPadOrder(bookmark)
        ABEventBus.post(BookmarksDeletedEvent(listOf(bookmark.id)))
    }

    fun deleteBookmarks(bookmarks: List<BookmarkWithNotes>) {
        val labels = mutableSetOf<IdType>()
        for(b in bookmarks) {
            labels.addAll(labelsForBookmark(b).map { it.id })
        }
        dao.deleteBookmarks(bookmarks)
        for (l in labels) {
            sanitizeStudyPadOrder(l)
        }
        ABEventBus.post(BookmarksDeletedEvent(bookmarks.map { it.id }))
    }

    fun deleteBookmarksById(bookmarkIds: List<IdType>) = deleteBookmarks(dao.bookmarksByIds(bookmarkIds))

    fun getBookmarksWithLabel(label: Label, orderBy: BookmarkSortOrder = BookmarkSortOrder.BIBLE_ORDER, addData: Boolean = false): List<BookmarkWithNotes> {
        val bookmarks = when {
            labelAll == label -> dao.allBookmarks(orderBy)
            labelUnlabelled == label -> dao.unlabelledBookmarks(orderBy)
            else -> dao.bookmarksWithLabel(label, orderBy)
        }
        if(addData) for (it in bookmarks) {
            addText(it)
            addLabels(it)
        }
        return bookmarks
    }

    fun bookmarksByLabelId(labelId: IdType) = dao.bookmarksWithLabel(labelId, BookmarkSortOrder.ORDER_NUMBER)

    fun labelsForBookmark(bookmark: BookmarkWithNotes): List<Label> {
        return dao.labelsForBookmark(bookmark.id)
    }

    fun setLabelsForBookmark(bookmark: BookmarkWithNotes, labels: List<Label>) =
        addOrUpdateBookmark(bookmark, labels.map { it.id }.toSet())

    fun insertOrUpdateLabel(label: Label): Label {
        label.name = label.name.trim()
        if(label.id.isEmpty()) throw RuntimeException("Illegal empty label.id")
        if(label.new) {
            dao.insert(label)
            label.new = false
        } else {
            dao.update(label)
        }
        ABEventBus.post(LabelAddedOrUpdatedEvent(label))
        return label
    }

    fun deleteLabel(label: Label) = dao.delete(label)

    // add special label that is automatically associated with all-bookmarks
    val allLabels: List<Label>
        get() {
            val labelList = assignableLabels.toMutableList()
            labelList.sortBy { it.name.lowercase(Locale.getDefault()) }
            // add special label that is automatically associated with all-bookmarks
            labelList.add(0, labelUnlabelled)
            labelList.add(0, labelAll)
            return labelList
        }

    val assignableLabels: List<Label> get() = dao.allLabelsSortedByName()

    val speakLabel: Label get() {
        return dao.speakLabelByName()
            ?: Label(name = SPEAK_LABEL_NAME, color = BookmarkStyle.SPEAK.backgroundColor).apply {
                dao.insert(this)
            }
    }

    val labelUnlabelled: Label get() {
        return dao.unlabeledLabelByName()
            ?: Label(name = UNLABELED_NAME, color = BookmarkStyle.BLUE_HIGHLIGHT.backgroundColor).apply {
                dao.insert(this)
            }
    }

    fun reset() {}

    fun isSpeakBookmark(bookmark: BookmarkWithNotes): Boolean = labelsForBookmark(bookmark).contains(speakLabel)
    fun speakBookmarkForVerse(verse: Verse) = dao.bookmarksForVerseStartWithLabel(verse, speakLabel).firstOrNull()

    fun changeLabelsForBookmark(bookmark: BookmarkWithNotes, labelIds: List<IdType>) {
        dao.clearLabels(bookmark)
        dao.insert(labelIds.map { BookmarkToLabel(bookmark.id, it)})
    }

    fun saveBookmarkNote(bookmarkId: IdType, note: String?) {
        if(note == null) {
            dao.deleteBookmarkNotes(bookmarkId)
        } else {
            dao.saveBookmarkNote(bookmarkId, note)
        }
        val bookmark = dao.bookmarkById(bookmarkId)!!
        addLabels(bookmark)
        addText(bookmark)
        ABEventBus.post(BookmarkNoteModifiedEvent(bookmark.id, bookmark.notes, bookmark.lastUpdatedOn.time))
    }

    fun onEvent(e: BookmarksUpdatedViaSyncEvent) {
        val labelUpserts = e.updated.filter { it.type == LogEntryTypes.UPSERT && it.tableName == "Label" }.map { it.entityId1 }
        val labels = dao.labelsById(labelUpserts)
        for(l in labels) {
            ABEventBus.post(LabelAddedOrUpdatedEvent(l))
        }

        val bookmarksDeletes = e.updated.filter { it.type == LogEntryTypes.DELETE && it.tableName == "Bookmark" }.map { it.entityId1 }
        ABEventBus.post(BookmarksDeletedEvent(bookmarksDeletes))

        val bookmarkUpserts = e.updated.filter {
            (it.type == LogEntryTypes.UPSERT && it.tableName == "Bookmark") || it.tableName == "BookmarkNotes"
        }.map { it.entityId1 }.toMutableSet()

        val studyPadTextEntryDeletes = e.updated.filter {
            (it.type == LogEntryTypes.DELETE && it.tableName == "StudyPadTextEntry")
        }.map { it.entityId1 }

        for (studyPadTextEntryId in studyPadTextEntryDeletes) {
            ABEventBus.post(StudyPadTextEntryDeleted(studyPadTextEntryId))
        }

        val studyPadTextEntryTextUpserts = e.updated.filter {
            it.type == LogEntryTypes.UPSERT && it.tableName == "StudyPadTextEntryText"
        }.map { it.entityId1 }

        for(studyPadTextEntryId in studyPadTextEntryTextUpserts) {
            val withText = dao.studyPadTextEntryById(studyPadTextEntryId)!!
            ABEventBus.post(StudyPadOrderEvent(withText.labelId, withText, emptyList(), emptyList()))
        }

        val studyPadTextEntryUpserts = e.updated.filter {
            it.type == LogEntryTypes.UPSERT && it.tableName == "StudyPadTextEntry"
        }.map { it.entityId1 }

        val labelIds = mutableSetOf<IdType>()

        for(studyPadTextEntryId in studyPadTextEntryUpserts) {
            val withText = dao.studyPadTextEntryById(studyPadTextEntryId) ?: continue
            ABEventBus.post(StudyPadOrderEvent(withText.labelId, withText, emptyList(), emptyList()))
            labelIds.add(withText.labelId)
        }

        val bookmarkToLabelUpserts = e.updated.filter {
            it.type == LogEntryTypes.UPSERT && it.tableName == "BookmarkToLabel"
        }.map { Pair(it.entityId1, it.entityId2) }

        for(ids in bookmarkToLabelUpserts) {
            labelIds.add(ids.second)
            bookmarkUpserts.add(ids.first)
        }

        for(labelId in labelIds) {
            sanitizeStudyPadOrder(labelId, true)
        }

        for(b in dao.bookmarksByIds(bookmarkUpserts.toList())) {
            addLabels(b)
            addText(b)
            ABEventBus.post(BookmarkAddedOrUpdatedEvent(b))
        }
    }

    fun deleteLabels(toList: List<IdType>) {
        dao.deleteLabelsByIds(toList)
    }

    fun bookmarksInBook(book: BibleBook): List<BookmarkWithNotes> = dao.bookmarksInBook(book)
    fun bookmarksForVerseRange(verseRange: VerseRange, withLabels: Boolean = false, withText: Boolean = true): List<BookmarkWithNotes> {
        val bookmarks = dao.bookmarksForVerseRange(verseRange)
        if(withLabels) for (b in bookmarks) {
            addLabels(b)
        }
        if(withText) for (b in bookmarks) {
            addText(b)
        }
        return bookmarks
    }

    private fun addLabels(b: BookmarkWithNotes) {
        val bookmarkToLabels = dao.getBookmarkToLabelsForBookmark(b.id)
        b.bookmarkToLabels = bookmarkToLabels
        b.labelIds = bookmarkToLabels.map { it.labelId }
    }

    internal fun addText(b: BookmarkWithNotes) {
        val book = b.book ?: windowControl.defaultBibleDoc(false) as SwordBook? ?: return // last ?: return is needed for tests
        b.osisFragment =
            try {
                OsisFragment(SwordContentFacade.readOsisFragment(book, b.verseRange.toV11n(book.versification)), b.verseRange, book)
            }
            catch (e: OsisError) {
                Log.e(TAG, "Error in getting content from $book for ${b.verseRange}")
                null
            }
        val verseTexts = b.verseRange.map {  SwordContentFacade.getCanonicalText(book, it, true) }
        val wholeVerse = b.wholeVerse || b.book == null
        val startOffset = if(wholeVerse) 0 else b.startOffset ?: 0
        var startVerse = verseTexts.first()
        var endOffset = if(wholeVerse) startVerse.length else b.endOffset ?: startVerse.length
        val start = startVerse.slice(0 until min(startOffset, startVerse.length))
        if(verseTexts.size == 1) {
            val end = startVerse.slice(endOffset until startVerse.length)
            b.text = startVerse.slice(startOffset until min(endOffset, startVerse.length)).trim()
            b.startText = start
            b.endText = end
            b.fullText = """$start${b.text}$end""".trim()
        } else if(verseTexts.size > 1) {
            startVerse = startVerse.slice(startOffset until startVerse.length)
            val lastVerse = verseTexts.last()
            endOffset = if(wholeVerse) lastVerse.length else b.endOffset ?: lastVerse.length
            val endVerse = lastVerse.slice(0 until min(lastVerse.length, endOffset))
            val end = lastVerse.slice(endOffset until lastVerse.length)
            val middleVerses = if(verseTexts.size > 2) {
                verseTexts.slice(1 until verseTexts.size-1).joinToString(" ")
            } else ""
            b.startText = start
            b.endText = end
            b.text = "$startVerse$middleVerses$endVerse".trim()
            b.fullText = """$start${b.text}$end""".trim()
        }
    }

    fun labelById(id: IdType): Label? = dao.labelById(id)

    fun getJournalTextEntriesForLabel(label: Label): List<StudyPadTextEntryWithText> {
        return dao.studyPadTextEntriesByLabelId(label.id)
    }

    fun updateStudyPadTextEntry(entry: StudyPadTextEntry) {
        dao.update(entry)
        val withText = dao.studyPadTextEntryById(entry.id)
        ABEventBus.post(StudyPadOrderEvent(entry.labelId, withText, emptyList(), emptyList()))
    }

    fun updateBookmarkToLabel(bookmarkToLabel: BookmarkToLabel) {
        dao.update(bookmarkToLabel)
        ABEventBus.post(BookmarkToLabelAddedOrUpdatedEvent(bookmarkToLabel))
    }

    fun updateBookmarkTimestamp(bookmarkId: IdType) {
        dao.updateBookmarkDate(dao.bookmarkById(bookmarkId)!!.id)
    }

    fun getBookmarkToLabel(bookmarkId: IdType, labelId: IdType): BookmarkToLabel? = dao.getBookmarkToLabel(bookmarkId, labelId)

    fun getStudyPadById(journalTextEntryId: IdType): StudyPadTextEntryWithText? = dao.studyPadTextEntryById(journalTextEntryId)

    private fun updateJournalTextEntries(studyPadTextEntries: List<StudyPadTextEntryWithText>) = dao.updateStudyPadTextEntries(studyPadTextEntries.map { it.studyPadTextEntryEntity })
    fun deleteStudyPadTextEntry(textEntryId: IdType) {
        val entry = dao.studyPadTextEntryById(textEntryId)!!
        dao.delete(entry.studyPadTextEntryEntity)
        ABEventBus.post(StudyPadTextEntryDeleted(textEntryId))
        sanitizeStudyPadOrder(entry.labelId)
    }

    private fun sanitizeStudyPadOrder(labelId: IdType, updateAllInUi: Boolean = false) {
        val bookmarkToLabels = dao.getBookmarkToLabelsForLabel(labelId)
        val studyPadTextEntries = dao.studyPadTextEntriesByLabelId(labelId)
        val all = ArrayList<Any>()
        all.addAll(studyPadTextEntries)
        all.addAll(bookmarkToLabels)
        all.sortBy {
            when (it) {
                is BookmarkToLabel -> it.orderNumber
                is StudyPadTextEntryWithText -> it.orderNumber
                else -> 0
            }
        }
        val changedBookmarkToLabels = mutableListOf<BookmarkToLabel>()
        val changedJournalTextEntries = mutableListOf<StudyPadTextEntryWithText>()

        for ((count, it) in all.withIndex()) {
            when (it) {
                is BookmarkToLabel -> {
                    if(it.orderNumber != count) {
                        it.orderNumber = count
                        changedBookmarkToLabels.add(it)
                    }
                }
                is StudyPadTextEntryWithText -> {
                    if(it.orderNumber != count) {
                        it.orderNumber = count
                        changedJournalTextEntries.add(it)
                    }
                }
            }
        }
        dao.updateBookmarkToLabels(changedBookmarkToLabels)
        dao.updateStudyPadTextEntries(changedJournalTextEntries.map { it.studyPadTextEntryEntity })
        if(updateAllInUi || changedBookmarkToLabels.size > 0 || changedJournalTextEntries.size > 0)
            ABEventBus.post(
                StudyPadOrderEvent(
                    labelId,
                    null,
                    if(updateAllInUi) bookmarkToLabels else changedBookmarkToLabels,
                    if(updateAllInUi) studyPadTextEntries else changedJournalTextEntries
                )
            )
    }

    private fun sanitizeStudyPadOrder(bookmark: BookmarkWithNotes) {
        for (it in labelsForBookmark(bookmark)) {
            sanitizeStudyPadOrder(it.id)
        }
    }

    fun createStudyPadEntry(labelId: IdType, entryOrderNumber: Int) {
        val entry = StudyPadTextEntryWithText(labelId = labelId, orderNumber = entryOrderNumber + 1)
        val bookmarkToLabels = dao.getBookmarkToLabelsForLabel(labelId).filter { it.orderNumber > entryOrderNumber }.onEach {it.orderNumber++}
        val studyPadTextEntries = dao.studyPadTextEntriesByLabelId(labelId).filter { it.orderNumber > entryOrderNumber }.onEach { it.orderNumber++ }

        dao.updateBookmarkToLabels(bookmarkToLabels)
        updateJournalTextEntries(studyPadTextEntries)
        dao.insert(entry.studyPadTextEntryEntity)
        dao.insert(entry.studyPadTextEntryTextEntity)

        ABEventBus.post(StudyPadOrderEvent(labelId, entry, bookmarkToLabels, studyPadTextEntries))
    }

    fun removeBookmarkLabel(bookmarkId: IdType, labelId: IdType) {
        val bookmark = dao.bookmarkById(bookmarkId)!!
        val labels = labelsForBookmark(bookmark).filter { it.id != labelId }
        setLabelsForBookmark(bookmark, labels)
    }

    fun getNextLabel(label: Label): Label {
        val allLabels = dao.allLabelsSortedByName().filter { !it.isSpecialLabel }
        val thisIndex = allLabels.indexOf(label)
        return try {allLabels[thisIndex+1]} catch (e: IndexOutOfBoundsException) {allLabels[0]}
    }

    fun getPrevLabel(label: Label): Label {
        val allLabels = dao.allLabelsSortedByName().filter { !it.isSpecialLabel }
        val thisIndex = allLabels.indexOf(label)
        return try {allLabels[thisIndex-1]} catch (e: IndexOutOfBoundsException) {allLabels[allLabels.size - 1]}
    }

    fun updateOrderNumbers(labelId: IdType, bookmarksToLabels: List<BookmarkToLabel>, studyPadTextEntries: List<StudyPadTextEntryWithText>) {
        dao.updateStudyPadTextEntries(studyPadTextEntries.map { it.studyPadTextEntryEntity })
        dao.updateBookmarkToLabels(bookmarksToLabels)
        ABEventBus.post(StudyPadOrderEvent(labelId, null, bookmarksToLabels, studyPadTextEntries))
    }

    fun setAsPrimaryLabel(bookmarkId: IdType, labelId: IdType) {
        val bookmark = dao.bookmarkById(bookmarkId)?: return
        bookmark.primaryLabelId = labelId
        addOrUpdateBookmark(bookmark)
    }

    fun updateStudyPadTextEntryText(id: IdType, text: String) {
        val textEntry = StudyPadTextEntryText(id, text)
        dao.update(textEntry)
        val withText = dao.studyPadTextEntryById(id)!!
        ABEventBus.post(StudyPadOrderEvent(withText.labelId, withText, emptyList(), emptyList()))
    }

    companion object {
        const val LABEL_NO_EXTRA = "labelNo"
        private const val TAG = "BookmarkControl"
    }

}
