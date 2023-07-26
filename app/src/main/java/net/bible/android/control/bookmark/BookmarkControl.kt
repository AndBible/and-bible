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
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.BookmarkEntities.GenericBookmarkWithNotes
import net.bible.android.database.bookmarks.BookmarkEntities.GenericBookmarkToLabel
import net.bible.android.database.bookmarks.BookmarkEntities.BaseBookmarkWithNotes
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkWithNotes
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkToLabel
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
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import java.lang.IndexOutOfBoundsException
import java.util.*
import javax.inject.Inject
import kotlin.collections.ArrayList
import kotlin.math.min

abstract class BookmarkEvent

class BookmarkAddedOrUpdatedEvent(val bookmark: BaseBookmarkWithNotes): BookmarkEvent()
class BookmarkToLabelAddedOrUpdatedEvent(val bookmarkToLabel: BookmarkEntities.BaseBookmarkToLabel)
class BookmarksDeletedEvent(val bookmarkIds: List<IdType>): BookmarkEvent()
class LabelAddedOrUpdatedEvent(val label: Label): BookmarkEvent()
class BookmarkNoteModifiedEvent(val bookmarkId: IdType, val notes: String?, val lastUpdatedOn: Long): BookmarkEvent()

class StudyPadOrderEvent(
    val labelId: IdType,
    val newStudyPadTextEntry: StudyPadTextEntryWithText? = null,
    val bookmarkToLabelsOrderChanged: List<BibleBookmarkToLabel>,
    val genericBookmarkToLabelsOrderChanged: List<GenericBookmarkToLabel>,
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
            addOrUpdateBibleBookmark(bookmark)
            Log.i("SpeakBookmark", "Updated bookmark settings " + bookmark + settings.speed)
        }
    }

    val allBookmarks: List<BibleBookmarkWithNotes> get() = dao.allBookmarks()

    fun allBookmarksWithNotes(orderBy: BookmarkSortOrder): List<BibleBookmarkWithNotes> = dao.allBookmarksWithNotes(orderBy)

    fun addOrUpdateBibleBookmark(bookmark: BibleBookmarkWithNotes, labels: Set<IdType>?=null, updateNotes: Boolean = false): BibleBookmarkWithNotes =
        addOrUpdateBookmark(bookmark, labels, updateNotes) as BibleBookmarkWithNotes

    fun addOrUpdateGenericBookmark(bookmark: GenericBookmarkWithNotes, labels: Set<IdType>?=null, updateNotes: Boolean = false): GenericBookmarkWithNotes =
        addOrUpdateBookmark(bookmark, labels, updateNotes) as GenericBookmarkWithNotes

    fun addOrUpdateBookmark(bookmark: BaseBookmarkWithNotes, labels: Set<IdType>?=null, updateNotes: Boolean = false): BaseBookmarkWithNotes {
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
                    dao.deleteBookmarkNotes(bookmark)
                }
            }
        }

        val labelIdsInDb = labels?.mapNotNull {dao.labelById(it)?.id }

        if(labelIdsInDb != null) {
            val existingLabels = dao.labelsForBookmark(bookmark).map { it.id }.toSet()
            val toBeDeleted = existingLabels.filterNot { labelIdsInDb.contains(it) }
            val toBeAdded = labelIdsInDb.filterNot { existingLabels.contains(it) }

            dao.deleteLabelsFromBookmark(bookmark, toBeDeleted.map {it})

            when(bookmark) {
                is BibleBookmarkWithNotes -> {
                    val addBookmarkToLabels = toBeAdded.filter { !it.isEmpty }.map {
                        BibleBookmarkToLabel(bookmark.id, it, orderNumber = dao.countStudyPadEntities(it))
                    }
                    dao.insertBookmarkToLabels(addBookmarkToLabels)
                }
                is GenericBookmarkWithNotes -> {
                    val addBookmarkToLabels = toBeAdded.filter { !it.isEmpty }.map {
                        GenericBookmarkToLabel(bookmark.id, it, orderNumber = dao.countStudyPadEntities(it))
                    }
                    dao.insertGenericBookmarkToLabels(addBookmarkToLabels)
                }
            }
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
    fun toggleBookmarkLabel(bookmark: BaseBookmarkWithNotes, labelId: String) {
        val labels = labelsForBookmark(bookmark).toMutableList()
        val foundLabel = labels.find { it.id == IdType(labelId) }
        if(foundLabel != null) {
            labels.remove(foundLabel)
        } else {
            labels.add(labelById(IdType(labelId))!!)
        }
        setLabelsForBookmark(bookmark, labels)
    }

    fun bookmarksByIds(ids: List<IdType>): List<BibleBookmarkWithNotes> = dao.bookmarksByIds(ids)

    fun bookmarkById(id: IdType): BibleBookmarkWithNotes? = dao.bookmarkById(id)

    fun genericBookmarkById(id: IdType): GenericBookmarkWithNotes? = dao.genericBookmarkById(id)

    fun hasBookmarksForVerse(verse: Verse): Boolean = dao.hasBookmarksForVerse(verse)

    fun firstBookmarkStartingAtVerse(key: Verse): BibleBookmarkWithNotes? = dao.bookmarksStartingAtVerse(key).firstOrNull()

    fun deleteBookmark(bookmark: BaseBookmarkWithNotes) {
        dao.delete(bookmark)
        sanitizeStudyPadOrder(bookmark)
        ABEventBus.post(BookmarksDeletedEvent(listOf(bookmark.id)))
    }

    private fun deleteBookmarks(bookmarks: List<BaseBookmarkWithNotes>) {
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

    fun deleteGenericBookmarksById(bookmarkIds: List<IdType>) = deleteBookmarks(dao.genericBookmarksByIds(bookmarkIds))

    fun getBookmarksWithLabel(label: Label, orderBy: BookmarkSortOrder = BookmarkSortOrder.BIBLE_ORDER, addData: Boolean = false): List<BibleBookmarkWithNotes> {
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

    fun getGenericBookmarksWithLabel(label: Label, addData: Boolean = false): List<GenericBookmarkWithNotes> {
        val bookmarks = when {
            labelAll == label -> dao.allGenericBookmarks()
            labelUnlabelled == label -> dao.unlabelledGenericBookmarks()
            else -> dao.genericBookmarksWithLabel(label)
        }
        if(addData) for (it in bookmarks) {
            addText(it)
            addLabels(it)
        }
        return bookmarks
    }

    fun bookmarksByLabelId(labelId: IdType) = dao.bookmarksWithLabel(labelId, BookmarkSortOrder.ORDER_NUMBER)

    fun labelsForBookmark(bookmark: BaseBookmarkWithNotes): List<Label> = dao.labelsForBookmark(bookmark)

    fun setLabelsForBookmark(bookmark: BaseBookmarkWithNotes, labels: List<Label>) =
        addOrUpdateBookmark(bookmark, labels.map { it.id }.toSet())

    fun insertOrUpdateLabel(label: Label): Label {
        label.name = label.name.trim()
        if(label.id.isEmpty) throw RuntimeException("Illegal empty label.id")
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

    fun isSpeakBookmark(bookmark: BaseBookmarkWithNotes): Boolean = labelsForBookmark(bookmark).contains(speakLabel)
    fun speakBookmarkForVerse(verse: Verse) = dao.bookmarksForVerseStartWithLabel(verse, speakLabel).firstOrNull()

    fun changeLabelsForBookmark(bookmark: BaseBookmarkWithNotes, labelIds: List<IdType>) {
        dao.clearLabels(bookmark)
        when(bookmark) {
            is BibleBookmarkWithNotes -> dao.insertBookmarkToLabels(labelIds.map { BibleBookmarkToLabel(bookmark.id, it)})
            is GenericBookmarkWithNotes -> dao.insertGenericBookmarkToLabels(labelIds.map { GenericBookmarkToLabel(bookmark.id, it)})
        }
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
    fun saveGenericBookmarkNote(bookmarkId: IdType, note: String?) {
        if(note == null) {
            dao.deleteGenericBookmarkNotes(bookmarkId)
        } else {
            dao.saveGenericBookmarkNote(bookmarkId, note)
        }
        val bookmark = dao.genericBookmarkById(bookmarkId)!!
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

        val genericBookmarksDeletes = e.updated.filter { it.type == LogEntryTypes.DELETE && it.tableName == "GenericBookmark" }.map { it.entityId1 }
        ABEventBus.post(BookmarksDeletedEvent(genericBookmarksDeletes))

        val genericBookmarkUpserts = e.updated.filter {
            (it.type == LogEntryTypes.UPSERT && it.tableName == "GenericBookmark") || it.tableName == "GenericBookmarkNotes"
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
            ABEventBus.post(StudyPadOrderEvent(withText.labelId, withText, emptyList(), emptyList(), emptyList()))
        }

        val studyPadTextEntryUpserts = e.updated.filter {
            it.type == LogEntryTypes.UPSERT && it.tableName == "StudyPadTextEntry"
        }.map { it.entityId1 }

        val labelIds = mutableSetOf<IdType>()

        for(studyPadTextEntryId in studyPadTextEntryUpserts) {
            val withText = dao.studyPadTextEntryById(studyPadTextEntryId) ?: continue
            ABEventBus.post(StudyPadOrderEvent(withText.labelId, withText, emptyList(), emptyList(), emptyList()))
            labelIds.add(withText.labelId)
        }

        val bookmarkToLabelUpserts = e.updated.filter {
            it.type == LogEntryTypes.UPSERT && it.tableName == "BookmarkToLabel"
        }.map { Pair(it.entityId1, it.entityId2) }

        for(ids in bookmarkToLabelUpserts) {
            labelIds.add(ids.second)
            bookmarkUpserts.add(ids.first)
        }

        val genericBookmarkToLabelUpserts = e.updated.filter {
            it.type == LogEntryTypes.UPSERT && it.tableName == "GenericBookmarkToLabel"
        }.map { Pair(it.entityId1, it.entityId2) }

        for(ids in genericBookmarkToLabelUpserts) {
            labelIds.add(ids.second)
            genericBookmarkUpserts.add(ids.first)
        }

        for(labelId in labelIds) {
            sanitizeStudyPadOrder(labelId, true)
        }

        for(b in dao.bookmarksByIds(bookmarkUpserts.toList())) {
            addLabels(b)
            addText(b)
            ABEventBus.post(BookmarkAddedOrUpdatedEvent(b))
        }
        for(b in dao.genericBookmarksByIds(genericBookmarkUpserts.toList())) {
            addLabels(b)
            addText(b)
            ABEventBus.post(BookmarkAddedOrUpdatedEvent(b))
        }
    }

    fun deleteLabels(toList: List<IdType>) {
        dao.deleteLabelsByIds(toList)
    }

    fun bookmarksInBook(book: BibleBook): List<BibleBookmarkWithNotes> = dao.bookmarksInBook(book)
    fun bookmarksForVerseRange(verseRange: VerseRange, withLabels: Boolean = false, withText: Boolean = true): List<BibleBookmarkWithNotes> {
        val bookmarks = dao.bookmarksForVerseRange(verseRange)
        if(withLabels) for (b in bookmarks) {
            addLabels(b)
        }
        if(withText) for (b in bookmarks) {
            addText(b)
        }
        return bookmarks
    }
    fun genericBookmarksFor(document: Book, key: Key, withLabels: Boolean = false, withText: Boolean = true): List<GenericBookmarkWithNotes> {
        if (document.bookCategory == BookCategory.BIBLE) return emptyList()
        val bookmarks = dao.genericBookmarksFor(document, key)
        if(withLabels) for (b in bookmarks) {
            addLabels(b)
        }
        if(withText) for (b in bookmarks) {
            addText(b)
        }
        return bookmarks
    }

    private fun addLabels(b: BaseBookmarkWithNotes) {
        val bookmarkToLabels = dao.getBookmarkToLabelsForBookmark(b)
        b.setBaseBookmarkToLabels(bookmarkToLabels)
        b.labelIds = bookmarkToLabels.map { it.labelId }
    }

    internal fun addText(b: BaseBookmarkWithNotes) = when(b) {
        is BibleBookmarkWithNotes -> addText(b)
        is GenericBookmarkWithNotes -> addText(b)
        else -> throw RuntimeException("Illegal type")
    }
    private fun addText(b: GenericBookmarkWithNotes) {
        val osis = SwordContentFacade.readOsisFragment(b.book, b.bookKey)
        b.text = SwordContentFacade.getTextWithinOrdinals(osis, b.ordinalStart, b.ordinalEnd, b.startOffset, b.endOffset)
    }
    private fun addText(b: BibleBookmarkWithNotes) {
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
        ABEventBus.post(StudyPadOrderEvent(entry.labelId, withText, emptyList(), emptyList(), emptyList()))
    }

    fun updateBookmarkToLabel(bookmarkToLabel: BookmarkEntities.BaseBookmarkToLabel) {
        dao.update(bookmarkToLabel)
        ABEventBus.post(BookmarkToLabelAddedOrUpdatedEvent(bookmarkToLabel))
    }

    fun updateBookmarkTimestamp(bookmarkId: IdType) {
        dao.updateBookmarkDate(dao.bookmarkById(bookmarkId)!!.id)
    }

    fun updateGenericBookmarkTimestamp(bookmarkId: IdType) {
        dao.updateGenericBookmarkDate(dao.genericBookmarkById(bookmarkId)!!.id)
    }

    fun getBookmarkToLabel(bookmarkId: IdType, labelId: IdType): BibleBookmarkToLabel? = dao.getBookmarkToLabel(bookmarkId, labelId)

    fun getGenericBookmarkToLabel(bookmarkId: IdType, labelId: IdType): GenericBookmarkToLabel? = dao.getGenericBookmarkToLabel(bookmarkId, labelId)

    fun getBookmarkToLabel(bookmark: BaseBookmarkWithNotes, labelId: IdType): BookmarkEntities.BaseBookmarkToLabel? = dao.getBookmarkToLabel(bookmark, labelId)

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
        val genericBookmarkToLabels = dao.getGenericBookmarkToLabelsForLabel(labelId)
        val studyPadTextEntries = dao.studyPadTextEntriesByLabelId(labelId)
        val all = ArrayList<Any>()
        all.addAll(studyPadTextEntries)
        all.addAll(bookmarkToLabels)
        all.addAll(genericBookmarkToLabels)
        all.sortBy {
            when (it) {
                is BookmarkEntities.BaseBookmarkToLabel -> it.orderNumber
                is StudyPadTextEntryWithText -> it.orderNumber
                else -> 0
            }
        }
        val changedBookmarkToLabels = mutableListOf<BibleBookmarkToLabel>()
        val changedGenericBookmarkToLabels = mutableListOf<GenericBookmarkToLabel>()
        val changedJournalTextEntries = mutableListOf<StudyPadTextEntryWithText>()

        for ((count, it) in all.withIndex()) {
            when (it) {
                is BibleBookmarkToLabel -> {
                    if(it.orderNumber != count) {
                        it.orderNumber = count
                        changedBookmarkToLabels.add(it)
                    }
                }
                is GenericBookmarkToLabel -> {
                    if(it.orderNumber != count) {
                        it.orderNumber = count
                        changedGenericBookmarkToLabels.add(it)
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
                    if(updateAllInUi) genericBookmarkToLabels else changedGenericBookmarkToLabels,
                    if(updateAllInUi) studyPadTextEntries else changedJournalTextEntries
                )
            )
    }

    private fun sanitizeStudyPadOrder(bookmark: BaseBookmarkWithNotes) {
        for (it in labelsForBookmark(bookmark)) {
            sanitizeStudyPadOrder(it.id)
        }
    }

    fun createStudyPadEntry(labelId: IdType, entryOrderNumber: Int) {
        val entry = StudyPadTextEntryWithText(labelId = labelId, orderNumber = entryOrderNumber + 1)
        val bookmarkToLabels = dao.getBookmarkToLabelsForLabel(labelId).filter { it.orderNumber > entryOrderNumber }.onEach {it.orderNumber++}
        val genericBookmarkToLabels = dao.getGenericBookmarkToLabelsForLabel(labelId).filter { it.orderNumber > entryOrderNumber }.onEach {it.orderNumber++}
        val studyPadTextEntries = dao.studyPadTextEntriesByLabelId(labelId).filter { it.orderNumber > entryOrderNumber }.onEach { it.orderNumber++ }

        dao.updateBookmarkToLabels(bookmarkToLabels)
        dao.updateGenericBookmarkToLabels(genericBookmarkToLabels)
        updateJournalTextEntries(studyPadTextEntries)
        dao.insert(entry.studyPadTextEntryEntity)
        dao.insert(entry.studyPadTextEntryTextEntity)

        ABEventBus.post(StudyPadOrderEvent(labelId, entry, bookmarkToLabels, genericBookmarkToLabels, studyPadTextEntries))
    }

    fun removeBookmarkLabel(bookmarkId: IdType, labelId: IdType) {
        val bookmark = dao.bookmarkById(bookmarkId)!!
        val labels = labelsForBookmark(bookmark).filter { it.id != labelId }
        setLabelsForBookmark(bookmark, labels)
    }

    fun removeGenericBookmarkLabel(bookmarkId: IdType, labelId: IdType) {
        val bookmark = dao.genericBookmarkById(bookmarkId)!!
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

    fun updateOrderNumbers(
        labelId: IdType,
        bookmarksToLabels: List<BibleBookmarkToLabel>,
        genericBookmarksToLabels: List<GenericBookmarkToLabel>,
        studyPadTextEntries: List<StudyPadTextEntryWithText>
    ) {
        dao.updateStudyPadTextEntries(studyPadTextEntries.map { it.studyPadTextEntryEntity })
        dao.updateBookmarkToLabels(bookmarksToLabels)
        dao.updateGenericBookmarkToLabels(genericBookmarksToLabels)
        ABEventBus.post(StudyPadOrderEvent(labelId, null, bookmarksToLabels, genericBookmarksToLabels, studyPadTextEntries))
    }

    fun setAsPrimaryLabel(bookmarkId: IdType, labelId: IdType) {
        val bookmark = dao.bookmarkById(bookmarkId)?: return
        bookmark.primaryLabelId = labelId
        addOrUpdateBookmark(bookmark)
    }

    fun setAsPrimaryLabelGeneric(bookmarkId: IdType, labelId: IdType) {
        val bookmark = dao.genericBookmarkById(bookmarkId)?: return
        bookmark.primaryLabelId = labelId
        addOrUpdateBookmark(bookmark)
    }

    fun updateStudyPadTextEntryText(id: IdType, text: String) {
        val textEntry = StudyPadTextEntryText(id, text)
        dao.update(textEntry)
        val withText = dao.studyPadTextEntryById(id)!!
        ABEventBus.post(StudyPadOrderEvent(withText.labelId, withText, emptyList(), emptyList(), emptyList()))
    }

    companion object {
        const val LABEL_NO_EXTRA = "labelNo"
        private const val TAG = "BookmarkControl"
    }

}
