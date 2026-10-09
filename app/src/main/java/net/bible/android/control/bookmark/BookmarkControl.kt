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
package net.bible.android.control.bookmark

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.net.Uri
import net.bible.sharedcore.log.Log
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import net.bible.android.database.LogEntry
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.android.common.resource.ResourceProvider
import net.bible.android.common.toV11n
import net.bible.android.control.page.DocumentCategory
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WorkspaceChanges
import net.bible.android.control.report.ErrorReportControl
import net.bible.android.database.IdType
import net.bible.android.database.LogEntryTypes
import net.bible.android.database.bookmarks.BookmarkEntities.BaseBookmarkToLabel
import net.bible.android.database.bookmarks.BookmarkEntities.BaseBookmarkWithNotes
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkToLabel
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkWithNotes
import net.bible.android.database.bookmarks.BookmarkEntities.EditAction
import net.bible.android.database.bookmarks.BookmarkEntities.GenericBookmarkToLabel
import net.bible.android.database.bookmarks.BookmarkEntities.GenericBookmarkWithNotes
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import net.bible.android.database.bookmarks.BookmarkEntities.StudyPadTextEntry
import net.bible.android.database.bookmarks.BookmarkEntities.StudyPadTextEntryText
import net.bible.android.database.bookmarks.BookmarkEntities.StudyPadTextEntryWithText
import net.bible.android.database.bookmarks.BookmarkSortOrder
import net.bible.android.database.bookmarks.BookmarkStyle
import net.bible.android.database.bookmarks.PARAGRAH_BREAK_LABEL_NAME
import net.bible.android.database.bookmarks.PARAGRAPH_BREAK_LABEL_ID
import net.bible.android.database.bookmarks.TextContentType
import net.bible.android.database.bookmarks.PlaybackSettings
import net.bible.android.database.bookmarks.SPEAK_LABEL_ID
import net.bible.android.database.bookmarks.SPEAK_LABEL_NAME
import net.bible.android.database.bookmarks.UNLABELED_LABEL_ID
import net.bible.android.database.bookmarks.UNLABELED_NAME
import net.bible.android.database.bookmarks.AI_LABEL_ID
import net.bible.android.database.bookmarks.AI_LABEL_NAME
import android.graphics.Color
import net.bible.android.misc.OsisFragment
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.Dialogs
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.OsisError
import net.bible.service.sword.SwordContentFacade
import net.bible.sharedcore.event.EventSource
import net.bible.sharedcore.event.Events
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.plainTextToHtml
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.NoSuchKeyException
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.koin.java.KoinJavaComponent
import java.lang.IllegalArgumentException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import net.bible.sharedcore.bookmark.BookmarkDisplayStyle
import net.bible.service.db.blockingDb

val LABEL_ALL_ID = IdType.empty()

/**
 * Side effects queued while a [BookmarkControl] bridge runs, flushed by the bridge in order once it
 * has returned. Carried in the coroutine context so the suspend twins need no extra parameter.
 */
private class PendingSideEffects : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<PendingSideEffects>
    val effects = mutableListOf<() -> Unit>()
}

open class BookmarkControl constructor(
    val windowControl: WindowControl,
    resourceProvider: ResourceProvider,
) {
    private val _changes = EventSource<BookmarkChange>()
    /** Every bookmark/label/StudyPad change, in emission order, on the emitter's thread. */
    val changes: Events<BookmarkChange> get() = _changes

    init {
        DatabaseContainer.bookmarksSynced.subscribe { updated -> blocking { onBookmarksSyncedSus(updated) } }
    }

    /**
     * Turns the rows a sync applied into [BookmarkChange]s. Runs as one bridged unit (see [blocking]);
     * the events are posted after the DAO reads are done.
     */
    private suspend fun onBookmarksSyncedSus(updated: List<LogEntry>) {
        val labelUpserts = updated.filter { it.type == LogEntryTypes.UPSERT && it.tableName == "Label" }.map { it.entityId1 }
        val labels = dao.labelsById(labelUpserts)
        for(l in labels) {
            emitChange(BookmarkChange.LabelUpserted(l))
        }

        val bookmarksDeletes = updated.filter { it.type == LogEntryTypes.DELETE && it.tableName == "BibleBookmark" }.map { it.entityId1 }
        emitChange(BookmarkChange.BookmarksDeleted(bookmarksDeletes))

        val bookmarkUpserts = updated.filter {
            (it.type == LogEntryTypes.UPSERT && it.tableName == "BibleBookmark") || it.tableName == "BibleBookmarkNotes"
        }.map { it.entityId1 }.toMutableSet()

        val genericBookmarksDeletes = updated.filter { it.type == LogEntryTypes.DELETE && it.tableName == "GenericBookmark" }.map { it.entityId1 }
        emitChange(BookmarkChange.BookmarksDeleted(genericBookmarksDeletes))

        val genericBookmarkUpserts = updated.filter {
            (it.type == LogEntryTypes.UPSERT && it.tableName == "GenericBookmark") || it.tableName == "GenericBookmarkNotes"
        }.map { it.entityId1 }.toMutableSet()

        val studyPadTextEntryDeletes = updated.filter {
            (it.type == LogEntryTypes.DELETE && it.tableName == "StudyPadTextEntry")
        }.map { it.entityId1 }

        for (studyPadTextEntryId in studyPadTextEntryDeletes) {
            emitChange(BookmarkChange.StudyPadTextEntryDeleted(studyPadTextEntryId))
        }

        val studyPadTextEntryTextUpserts = updated.filter {
            it.type == LogEntryTypes.UPSERT && it.tableName == "StudyPadTextEntryText"
        }.map { it.entityId1 }

        for(studyPadTextEntryId in studyPadTextEntryTextUpserts) {
            val withText = dao.studyPadTextEntryById(studyPadTextEntryId)!!
            emitChange(BookmarkChange.StudyPadOrder(withText.labelId, withText, emptyList(), emptyList(), emptyList()))
        }

        val studyPadTextEntryUpserts = updated.filter {
            it.type == LogEntryTypes.UPSERT && it.tableName == "StudyPadTextEntry"
        }.map { it.entityId1 }

        val labelIds = mutableSetOf<IdType>()

        for(studyPadTextEntryId in studyPadTextEntryUpserts) {
            val withText = dao.studyPadTextEntryById(studyPadTextEntryId) ?: continue
            emitChange(BookmarkChange.StudyPadOrder(withText.labelId, withText, emptyList(), emptyList(), emptyList()))
            labelIds.add(withText.labelId)
        }

        val bookmarkToLabelUpserts = updated.filter {
            it.type == LogEntryTypes.UPSERT && it.tableName == "BibleBookmarkToLabel"
        }.map { Pair(it.entityId1, it.entityId2) }

        for(ids in bookmarkToLabelUpserts) {
            labelIds.add(ids.second)
            bookmarkUpserts.add(ids.first)
        }

        val genericBookmarkToLabelUpserts = updated.filter {
            it.type == LogEntryTypes.UPSERT && it.tableName == "GenericBookmarkToLabel"
        }.map { Pair(it.entityId1, it.entityId2) }

        for(ids in genericBookmarkToLabelUpserts) {
            labelIds.add(ids.second)
            genericBookmarkUpserts.add(ids.first)
        }

        for(labelId in labelIds) {
            sanitizeStudyPadOrderSus(labelId, true)
        }

        for(b in dao.bibleBookmarksByIds(bookmarkUpserts.toList())) {
            addLabelsSus(b)
            addText(b)
            emitChange(BookmarkChange.BookmarksUpserted(listOf(b)))
        }
        for(b in dao.genericBookmarksByIds(genericBookmarkUpserts.toList())) {
            addLabelsSus(b)
            addText(b)
            emitChange(BookmarkChange.BookmarksUpserted(listOf(b)))
        }
    }

    val favouriteLabels: List<Label> get() = blockingDb { dao.favouriteLabels() }

    // Dummy labels for all / unlabelled
    private val labelAll = Label(LABEL_ALL_ID, resourceProvider.getString(R.string.all)?: "all", color = BookmarkStyle.GREEN_HIGHLIGHT.backgroundColor)

    private val bookmarkDb get() = DatabaseContainer.instance.bookmarkDb
    private val dao get() = bookmarkDb.bookmarkDao()

    /**
     * The public API of this class stays blocking (its callers are not coroutines yet); each public
     * function that touches the DAO is a thin [blocking] bridge around a private `...Sus` suspend
     * twin that calls the suspend DAO directly. Twins call each other, never the public blocking
     * functions, so no bridge is ever nested.
     *
     * Everything with an observable side effect beyond the database (a [BookmarkChange], a workspace
     * notification) goes through [emitChange]/[afterBridge] inside the twin. Those are queued and run
     * AFTER the bridge has returned, in their original order: a subscriber that itself bridges (for
     * example BibleView's `LabelUpserted` handler) must never run inside the runBlocking that carries
     * the DAO calls, nor inside a database transaction.
     */
    private fun <T> blocking(core: suspend () -> T): T {
        val pending = PendingSideEffects()
        try {
            return blockingDb { withContext(pending) { core() } }
        } finally {
            // Every queued effect runs, even when one throws or the core failed: an effect's failure is logged (it must
            // not mask the core's exception nor drop the effects after it); cancellation is the one thing not swallowed.
            pending.effects.forEach {
                try { it() } catch (e: CancellationException) { throw e } catch (e: Exception) { Log.e(TAG, "Side effect failed", e) }
            }
        }
    }

    /** Posts [change] now, or, inside [blocking], once the bridge has returned. */
    private suspend fun emitChange(change: BookmarkChange) = afterBridge { _changes.emit(change) }

    /** Runs [effect] now, or, inside [blocking], once the bridge has returned (in call order). */
    private suspend fun afterBridge(effect: () -> Unit) {
        val pending = coroutineContext[PendingSideEffects]
        if (pending != null) pending.effects.add(effect) else effect()
    }

	fun updateBookmarkPlaybackSettings(settings: PlaybackSettings) {
        val pageManager = windowControl.activeWindowPageManager
        if (pageManager.currentPage.documentCategory == DocumentCategory.BIBLE) {
            updateBookmarkPlaybackSettings(pageManager.currentBible.singleKey, settings)
        }
    }

    private fun updateBookmarkPlaybackSettings(v: Verse, settings: PlaybackSettings) = blocking {
        val verse = if (v.verse == 0) Verse(v.versification, v.book, v.chapter, 1) else v

        val bookmark = dao.bookmarksForVerseStartWithLabel(verse, speakLabelSus()).firstOrNull()
        if (bookmark?.playbackSettings != null) {
            bookmark.playbackSettings = settings
            addOrUpdateBookmarkSus(bookmark)
            Log.i("SpeakBookmark", "Updated bookmark settings " + bookmark + settings.speed)
        }
    }

    val allBibleBookmarks: List<BibleBookmarkWithNotes> get() = blockingDb { dao.allBookmarks() }

    fun addOrUpdateBibleBookmark(bookmark: BibleBookmarkWithNotes, labels: Set<IdType>?=null, updateNotes: Boolean = false): BibleBookmarkWithNotes =
        addOrUpdateBookmark(bookmark, labels, updateNotes) as BibleBookmarkWithNotes

    fun addOrUpdateGenericBookmark(bookmark: GenericBookmarkWithNotes, labels: Set<IdType>?=null, updateNotes: Boolean = false): GenericBookmarkWithNotes =
        addOrUpdateBookmark(bookmark, labels, updateNotes) as GenericBookmarkWithNotes

    fun addOrUpdateBookmark(bookmark: BaseBookmarkWithNotes, labels: Set<IdType>?=null, updateNotes: Boolean = false): BaseBookmarkWithNotes =
        blocking { addOrUpdateBookmarkSus(bookmark, labels, updateNotes) }

    private suspend fun addOrUpdateBookmarkSus(bookmark: BaseBookmarkWithNotes, labels: Set<IdType>?=null, updateNotes: Boolean = false): BaseBookmarkWithNotes {
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
                    dao.upsert(notes)
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
            val workspaceSettings = windowControl.windowRepository?.workspaceSettings // for tests "?."
            when(bookmark) {
                is BibleBookmarkWithNotes -> {
                    val addBookmarkToLabels = toBeAdded.filter { !it.isEmpty }.map { labelId ->
                        val cursor = workspaceSettings?.studyPadCursors[labelId]
                        val maxOrder = dao.countStudyPadEntities(labelId)
                        val orderNumber = cursor?.coerceAtMost(maxOrder) ?: maxOrder
                        if (cursor != null) {
                            incrementOrderNumbersFromSus(labelId, orderNumber)
                            workspaceSettings.studyPadCursors[labelId] = orderNumber + 1
                        }
                        BibleBookmarkToLabel(bookmark.id, labelId, orderNumber = orderNumber)
                    }
                    dao.insertBookmarkToLabels(addBookmarkToLabels)
                }
                is GenericBookmarkWithNotes -> {
                    val addBookmarkToLabels = toBeAdded.filter { !it.isEmpty }.map { labelId ->
                        val cursor = workspaceSettings?.studyPadCursors[labelId]
                        val maxOrder = dao.countStudyPadEntities(labelId)
                        val orderNumber = cursor?.coerceAtMost(maxOrder) ?: maxOrder
                        if (cursor != null) {
                            incrementOrderNumbersFromSus(labelId, orderNumber)
                            workspaceSettings.studyPadCursors[labelId] = orderNumber + 1
                        }
                        GenericBookmarkToLabel(bookmark.id, labelId, orderNumber = orderNumber)
                    }
                    dao.insertGenericBookmarkToLabels(addBookmarkToLabels)
                }
            }

            if (toBeAdded.any { workspaceSettings?.studyPadCursors?.containsKey(it) == true}) {
                afterBridge { WorkspaceChanges.notifySettingsEdited() }
            }

            if(labelIdsInDb.find { it == bookmark.primaryLabelId } == null) {
                bookmark.primaryLabelId = labelIdsInDb.firstOrNull()
                dao.update(bookmark.bookmarkEntity)
            }
            afterBridge { windowControl.windowRepository?.updateRecentLabels(toBeAdded.union(toBeDeleted).toList()) } // for tests "?."
        }

        addText(bookmark)
        addLabelsSus(bookmark)
        emitChange(BookmarkChange.BookmarksUpserted(listOf(bookmark)))
        return bookmark
    }
    
    fun updateBookmarkEditAction(bookmarkId: IdType, editAction: EditAction) = blocking {
        val bookmark = dao.bibleBookmarkById(bookmarkId) ?: dao.genericBookmarkById(bookmarkId) ?: return@blocking
        bookmark.editAction = editAction
        addOrUpdateBookmarkSus(bookmark)
        Unit
    }

    fun toggleBookmarkLabel(bookmark: BaseBookmarkWithNotes, labelId: String) = blocking {
        val labels = dao.labelsForBookmark(bookmark).toMutableList()
        val foundLabel = labels.find { it.id == IdType(labelId) }
        if(foundLabel != null) {
            labels.remove(foundLabel)
        } else {
            labels.add(dao.labelById(IdType(labelId))!!)
        }
        addOrUpdateBookmarkSus(bookmark, labels.map { it.id }.toSet())
        Unit
    }

    fun bibleBookmarksByIds(ids: List<IdType>): List<BibleBookmarkWithNotes> = blockingDb { dao.bibleBookmarksByIds(ids) }

    fun bibleBookmarkById(id: IdType): BibleBookmarkWithNotes? = blockingDb { dao.bibleBookmarkById(id) }

    fun genericBookmarkById(id: IdType): GenericBookmarkWithNotes? = blockingDb { dao.genericBookmarkById(id) }

    fun hasBookmarksForVerse(verse: Verse): Boolean = blockingDb { dao.hasBookmarksForVerse(verse) }

    fun bibleBookmarkStartingAtVerse(key: Verse): List<BibleBookmarkWithNotes> = blockingDb { dao.bookmarksStartingAtVerse(key) }

    fun deleteBookmark(bookmark: BaseBookmarkWithNotes) = blocking {
        dao.delete(bookmark)
        sanitizeStudyPadOrderSus(bookmark)
        emitChange(BookmarkChange.BookmarksDeleted(listOf(bookmark.id)))
    }

    private suspend fun deleteBookmarksSus(bookmarks: List<BaseBookmarkWithNotes>) {
        val labels = mutableSetOf<IdType>()
        for(b in bookmarks) {
            labels.addAll(dao.labelsForBookmark(b).map { it.id })
        }
        dao.deleteBookmarks(bookmarks)
        for (l in labels) {
            sanitizeStudyPadOrderSus(l)
        }
        emitChange(BookmarkChange.BookmarksDeleted(bookmarks.map { it.id }))
    }

    fun deleteBibleBookmarksById(bookmarkIds: List<IdType>) = blocking { deleteBookmarksSus(dao.bibleBookmarksByIds(bookmarkIds)) }

    fun deleteGenericBookmarksById(bookmarkIds: List<IdType>) = blocking { deleteBookmarksSus(dao.genericBookmarksByIds(bookmarkIds)) }

    fun getBibleBookmarksWithLabel(label: Label, orderBy: BookmarkSortOrder = BookmarkSortOrder.BIBLE_ORDER, addData: Boolean = false, search:String? = null): List<BibleBookmarkWithNotes> = blocking {
        val bookmarks = when {
            labelAll == label ->
                if (search == null) dao.allBookmarks(orderBy)
                else dao.searchAllBookmarks(orderBy, search)
            labelUnlabelledSus() == label ->
                if (search == null) dao.unlabelledBookmarks(orderBy)
                else dao.searchUnlabelledBookmarks(orderBy, search)
            else ->
                if (search == null) dao.bookmarksWithLabel(label, orderBy)
                else dao.searchBookmarksWithLabel(label, orderBy, search)
        }
        if(addData) for (it in bookmarks) {
            addText(it)
            addLabelsSus(it)
        }
        bookmarks
    }

    fun getGenericBookmarksWithLabel(label: Label, addData: Boolean = false, search:String? = null): List<GenericBookmarkWithNotes> = blocking {
        val bookmarks = when {
            labelAll == label ->
                if (search == null) dao.allGenericBookmarks()
                else dao.searchAllGenericBookmarks(search)
            labelUnlabelledSus() == label ->
                if (search == null) dao.unlabelledGenericBookmarks()
                else dao.searchUnlabelledGenericBookmarks(search)
            else ->
                if (search == null) dao.genericBookmarksWithLabel(label)
                else dao.searchGenericBookmarksWithLabel(label, search)
        }
        if(addData) for (it in bookmarks) {
            addText(it)
            addLabelsSus(it)
        }
        bookmarks
    }

    /**
     * Search for study pads that contain the given search text in their text entries or bookmark notes.
     * Returns a list of StudyPadSearchResult objects, each containing the matching label and list of matches.
     */
    fun searchStudyPadsByContent(searchText: String): List<StudyPadSearchResult> = blockingDb {
        val searchPattern = "%$searchText%"
        val results = mutableMapOf<IdType, MutableList<ContentMatch>>()

        // Search in study pad text entries
        val textEntries = dao.searchStudyPadTextEntriesByContent(searchPattern)
        for (entry in textEntries) {
            val matches = results.getOrPut(entry.labelId) { mutableListOf() }
            val snippet = generateTextSnippet(entry.text, searchText)
            matches.add(ContentMatch(
                entryId = entry.id,
                entryType = EntryType.TEXT_ENTRY,
                textSnippet = snippet.text,
                matchStart = snippet.matchStart,
                matchEnd = snippet.matchEnd
            ))
        }

        // Search in Bible bookmark notes
        val bibleBookmarks = dao.searchBibleBookmarkNotesByContent(searchPattern)
        for (bookmark in bibleBookmarks) {
            val labels = dao.labelsForBookmark(bookmark)
            for (label in labels) {
                val matches = results.getOrPut(label.id) { mutableListOf() }
                val snippet = generateTextSnippet(bookmark.notes ?: "", searchText)
                matches.add(ContentMatch(
                    entryId = bookmark.id,
                    entryType = EntryType.BOOKMARK_NOTE,
                    textSnippet = snippet.text,
                    matchStart = snippet.matchStart,
                    matchEnd = snippet.matchEnd
                ))
            }
        }

        // Search in generic bookmark notes
        val genericBookmarks = dao.searchGenericBookmarkNotesByContent(searchPattern)
        for (bookmark in genericBookmarks) {
            val labels = dao.labelsForBookmark(bookmark)
            for (label in labels) {
                val matches = results.getOrPut(label.id) { mutableListOf() }
                val snippet = generateTextSnippet(bookmark.notes ?: "", searchText)
                matches.add(ContentMatch(
                    entryId = bookmark.id,
                    entryType = EntryType.BOOKMARK_NOTE,
                    textSnippet = snippet.text,
                    matchStart = snippet.matchStart,
                    matchEnd = snippet.matchEnd
                ))
            }
        }

        // Create StudyPadSearchResult objects
        val searchResults = mutableListOf<StudyPadSearchResult>()
        for ((labelId, matches) in results) {
            val label = dao.labelById(labelId) ?: continue
            if (label.isSpecialLabel) continue // Skip special labels

            searchResults.add(StudyPadSearchResult(
                label = label,
                matchCount = matches.size,
                matches = matches
            ))
        }

        // Sort by match count (descending), then by label name (ascending)
        searchResults.sortedWith(
            compareByDescending<StudyPadSearchResult> { it.matchCount }
                .thenBy { it.label.name.lowercase() }
        )
    }

    internal fun generateTextSnippet(fullText: String, searchText: String, contextChars: Int = 50): StudyPadSearchResultTextSnippet {
        val searchLower = searchText.lowercase()
        val fullTextLower = fullText.lowercase()
        val matchIndex = fullTextLower.indexOf(searchLower)

        if (matchIndex == -1) {
            // No match found (shouldn't happen), return beginning of text
            val snippet = fullText.take(contextChars * 2)
            return StudyPadSearchResultTextSnippet(snippet, 0, 0)
        }

        // Calculate snippet start and end positions
        val snippetStart = maxOf(0, matchIndex - contextChars)
        val snippetEnd = minOf(fullText.length, matchIndex + searchText.length + contextChars)

        // Extract snippet
        var snippet = fullText.substring(snippetStart, snippetEnd)

        // Add ellipsis if needed
        val prefix = if (snippetStart > 0) "..." else ""
        val suffix = if (snippetEnd < fullText.length) "..." else ""

        // Calculate match position in snippet
        val matchStartInSnippet = prefix.length + (matchIndex - snippetStart)
        val matchEndInSnippet = matchStartInSnippet + searchText.length

        snippet = prefix + snippet + suffix

        return StudyPadSearchResultTextSnippet(snippet, matchStartInSnippet, matchEndInSnippet)
    }

    fun labelsForBookmark(bookmark: BaseBookmarkWithNotes): List<Label> = blockingDb { dao.labelsForBookmark(bookmark) }

    fun setLabelsForBookmark(bookmark: BaseBookmarkWithNotes, labels: List<Label>) =
        addOrUpdateBookmark(bookmark, labels.map { it.id }.toSet())

    fun insertOrUpdateLabel(label: Label): Label = blocking {
        label.name = label.name.trim()
        if(label.id.isEmpty) throw RuntimeException("Illegal empty label.id")
        if(label.new) {
            dao.insert(label)
            label.new = false
        } else {
            dao.update(label)
        }
        emitChange(BookmarkChange.LabelUpserted(label))
        label
    }

    fun deleteLabel(label: Label) = blockingDb { dao.delete(label) }

    // add special label that is automatically associated with all-bookmarks
    val allLabels: List<Label>
        get() = blocking {
            val labelList = dao.allLabelsSortedByName().toMutableList()
            labelList.sortBy { it.name.lowercase(Locale.getDefault()) }
            // add special label that is automatically associated with all-bookmarks
            labelList.add(0, labelUnlabelledSus())
            labelList.add(0, labelAll)
            labelList
        }

    val assignableLabels: List<Label> get() = blockingDb { dao.allLabelsSortedByName() }

    /**
     * Backs [speakLabel], [labelUnlabelled], [paragraphBreakLabel] and [aiLabel]. Takes
     * [SPECIAL_LABEL_LOCK], so **never call those getters inside a database transaction**: another
     * coroutine may hold the lock while waiting for that transaction's write lock.
     */
    private suspend fun getOrCreateSpecialLabelSus(
        canonicalId: IdType,
        create: () -> Label
    ): Label {
        // The check and the insert must be one step: every caller (BibleView.loadDocument runs for
        // several windows at once, on Dispatchers.IO) would otherwise both see "no label" on a fresh
        // database and the loser's insert dies with UNIQUE constraint failed: Label.id. The lock is
        // process-wide (not per instance) because the database is. It is a Mutex, not `synchronized`:
        // the DAO calls inside suspend, and a monitor must not be held across a suspension point.
        // The event is posted outside it.
        var created: Label? = null
        val label = SPECIAL_LABEL_LOCK.withLock {
            dao.labelById(canonicalId) ?: create().also {
                dao.insert(it)
                created = it
            }
        }
        created?.let {
            emitChange(BookmarkChange.LabelUpserted(it))
        }
        return label
    }

    private suspend fun speakLabelSus(): Label = getOrCreateSpecialLabelSus(SPEAK_LABEL_ID) {
        Label(id = SPEAK_LABEL_ID, name = SPEAK_LABEL_NAME, color = BookmarkStyle.SPEAK.backgroundColor)
    }

    private suspend fun labelUnlabelledSus(): Label = getOrCreateSpecialLabelSus(UNLABELED_LABEL_ID) {
        Label(id = UNLABELED_LABEL_ID, name = UNLABELED_NAME, color = BookmarkStyle.BLUE_HIGHLIGHT.backgroundColor)
    }

    private suspend fun paragraphBreakLabelSus(): Label = getOrCreateSpecialLabelSus(PARAGRAPH_BREAK_LABEL_ID) {
        Label(id = PARAGRAPH_BREAK_LABEL_ID, name = PARAGRAH_BREAK_LABEL_NAME, displayStyle = BookmarkDisplayStyle.HIDDEN, displayStyleWholeVerse = null)
    }

    private suspend fun aiLabelSus(): Label = getOrCreateSpecialLabelSus(AI_LABEL_ID) {
        Label(
            id = AI_LABEL_ID,
            name = AI_LABEL_NAME,
            color = Color.argb(255, 100, 100, 255),
            displayStyle = BookmarkDisplayStyle.MARKER,
            displayStyleWholeVerse = null,
            customIcon = "robot"
        )
    }

    val speakLabel: Label get() = blocking { speakLabelSus() }

    val labelUnlabelled: Label get() = blocking { labelUnlabelledSus() }

    val paragraphBreakLabel: Label get() = blocking { paragraphBreakLabelSus() }

    val aiLabel: Label get() = blocking { aiLabelSus() }

    fun reset() {}

    fun isSpeakBookmark(bookmark: BaseBookmarkWithNotes): Boolean = blocking { dao.labelsForBookmark(bookmark).contains(speakLabelSus()) }
    fun speakBookmarkForVerse(verse: Verse) = blocking { dao.bookmarksForVerseStartWithLabel(verse, speakLabelSus()).firstOrNull() }
    fun speakBookmarkForKey(key: BookAndKey): GenericBookmarkWithNotes? = blocking {
        dao.bookmarksForKeyStartWithLabel(key.document!!.initials, key.key.osisRef, key.ordinal!!.start, speakLabelSus().id).firstOrNull()
    }
    fun changeLabelsForBookmark(bookmark: BaseBookmarkWithNotes, labelIds: List<IdType>) = blockingDb { changeLabelsForBookmarkSus(bookmark, labelIds) }

    private suspend fun changeLabelsForBookmarkSus(bookmark: BaseBookmarkWithNotes, labelIds: List<IdType>) {
        dao.clearLabels(bookmark)
        when(bookmark) {
            is BibleBookmarkWithNotes -> dao.insertBookmarkToLabels(labelIds.map { BibleBookmarkToLabel(bookmark.id, it)})
            is GenericBookmarkWithNotes -> dao.insertGenericBookmarkToLabels(labelIds.map { GenericBookmarkToLabel(bookmark.id, it)})
        }
    }

    /**
     * Sets [labelIds] on every bookmark in [bookmarks] and announces the batch ONCE.
     *
     * F54 fix round 2: [changeLabelsForBookmark] is a bare DAO write that never touches the bookmark
     * object, and the bookmarks list hands over objects loaded with `addData = false`
     * (`labelIds == null`). Each one is refreshed in place first, or BibleView's
     * `ClientBibleBookmark.asJson` throws on `labelIds!!` and the WebView never updates.
     * [changeLabelsForBookmark] itself still announces nothing: its other caller is the CSV bulk import.
     */
    fun changeLabelsForBookmarks(bookmarks: List<BaseBookmarkWithNotes>, labelIds: List<IdType>) = blocking {
        for (bookmark in bookmarks) {
            changeLabelsForBookmarkSus(bookmark, labelIds)
            refreshTextAndLabelsSus(bookmark)
        }
        emitChange(BookmarkChange.BookmarksUpserted(bookmarks))
    }

    /** Announces [label] again after something outside this class changed how it renders (a workspace override). */
    fun notifyLabelChanged(label: Label) {
        _changes.emit(BookmarkChange.LabelUpserted(label))
    }

    /**
     * F54: [changeLabelsForBookmark] is a bare DAO write and never touches the in-memory
     * [BaseBookmarkWithNotes] it was given -- `labelIds`/`bookmarkToLabels`/`text` stay whatever they
     * were when the object was loaded (often unset, `addData = false`). A caller that posts
     * [BookmarkChange.BookmarksUpserted] with that same object afterwards (so an open reading view's
     * `ClientBibleBookmark`/`ClientGenericBookmark` serialisation can pick up the new labels) must
     * refresh it first. Mirrors the `addText`/`addLabels` pair [addOrUpdateBookmark] runs on its own
     * bookmark right before its own emission.
     */
    fun refreshTextAndLabels(bookmark: BaseBookmarkWithNotes) = blockingDb { refreshTextAndLabelsSus(bookmark) }

    private suspend fun refreshTextAndLabelsSus(bookmark: BaseBookmarkWithNotes) {
        addText(bookmark)
        addLabelsSus(bookmark)
    }

    fun saveBibleBookmarkNote(bookmarkId: IdType, note: String?) = blocking {
        if(note == null) {
            dao.deleteBookmarkNotes(bookmarkId)
        } else {
            val existingContentType = dao.bibleBookmarkById(bookmarkId)?.notesContentType?.name
            val contentType = existingContentType ?: CommonUtils.settings.notesContentType
            dao.saveBookmarkNote(bookmarkId, note, contentType)
        }
        val bookmark = dao.bibleBookmarkById(bookmarkId)!!
        addLabelsSus(bookmark)
        addText(bookmark)
        emitChange(BookmarkChange.NoteModified(bookmark.id, bookmark.notes, bookmark.lastUpdatedOn.time))
    }
    fun saveGenericBookmarkNote(bookmarkId: IdType, note: String?) = blocking {
        if(note == null) {
            dao.deleteGenericBookmarkNotes(bookmarkId)
        } else {
            val existingContentType = dao.genericBookmarkById(bookmarkId)?.notesContentType?.name
            val contentType = existingContentType ?: CommonUtils.settings.notesContentType
            dao.saveGenericBookmarkNote(bookmarkId, note, contentType)
        }
        val bookmark = dao.genericBookmarkById(bookmarkId)!!
        addLabelsSus(bookmark)
        addText(bookmark)
        emitChange(BookmarkChange.NoteModified(bookmark.id, bookmark.notes, bookmark.lastUpdatedOn.time))
    }

    /**
     * Find bookmarks that would become orphaned (have no labels) when the specified labels are deleted
     */
    fun findOrphanedBookmarks(labelIdsToDelete: List<IdType>): List<BaseBookmarkWithNotes> = blockingDb { findOrphanedBookmarksSus(labelIdsToDelete) }

    private suspend fun findOrphanedBookmarksSus(labelIdsToDelete: List<IdType>): List<BaseBookmarkWithNotes> {
        val bookmarksToDelete = mutableListOf<BaseBookmarkWithNotes>()
        
        for (labelId in labelIdsToDelete) {
            val bibleBookmarks = dao.bookmarksWithLabel(labelId)
            for (bookmark in bibleBookmarks) {
                val allLabels = dao.labelsForBookmark(bookmark.id).map { it.id }
                if (allLabels.all { it in labelIdsToDelete }) {
                    bookmarksToDelete.add(bookmark)
                }
            }
            
            val genericBookmarks = dao.genericBookmarksWithLabel(labelId)
            for (bookmark in genericBookmarks) {
                val allLabels = dao.labelsForGenericBookmark(bookmark.id).map { it.id }
                if (allLabels.all { it in labelIdsToDelete }) {
                    bookmarksToDelete.add(bookmark)
                }
            }
        }
        
        return bookmarksToDelete.distinct()
    }

    fun deleteLabels(labelIdList: List<IdType>, deleteOrphanedBookmarks: Boolean = false) = blocking {
        if (deleteOrphanedBookmarks) {
            val bookmarksToDelete = findOrphanedBookmarksSus(labelIdList)
            if (bookmarksToDelete.isNotEmpty()) {
                deleteBookmarksSus(bookmarksToDelete)
            }
        }
        var bookmarks: List<BaseBookmarkWithNotes> =
            dao.bibleBookmarksWithPrimaryLabel(labelIdList) +
            dao.genericBookmarksWithPrimaryLabel(labelIdList)

        dao.deleteLabelsByIds(labelIdList)
        val workspaceDao = DatabaseContainer.instance.workspaceDb.workspaceDao()
        for (labelId in labelIdList) {
            workspaceDao.deleteOverridesByLabelId(labelId)
        }
        bookmarks =
            dao.bibleBookmarksByIds(bookmarks.map { it.id }) +
            dao.genericBookmarksByIds(bookmarks.map { it.id })
        for (b in bookmarks) {
            addText(b)
            addLabelsSus(b)
        }
        emitChange(BookmarkChange.BookmarksUpserted(bookmarks))
        emitChange(BookmarkChange.LabelsDeleted(labelIdList))
    }

    fun bookmarksForVerseRange(verseRange: VerseRange, withLabels: Boolean = false, withText: Boolean = true): List<BibleBookmarkWithNotes> = blockingDb {
        val bookmarks = dao.bookmarksForVerseRange(verseRange)
        if(withLabels) for (b in bookmarks) {
            addLabelsSus(b)
        }
        if(withText) for (b in bookmarks) {
            addText(b)
        }
        bookmarks
    }
    fun genericBookmarksFor(document: Book, key: Key, withLabels: Boolean = false, withText: Boolean = true): List<GenericBookmarkWithNotes> {
        if (document.bookCategory == BookCategory.BIBLE) return emptyList()
        return blockingDb {
            val bookmarks = dao.genericBookmarksFor(document, key)
            if(withLabels) for (b in bookmarks) {
                addLabelsSus(b)
            }
            if(withText) for (b in bookmarks) {
                addText(b)
            }
            bookmarks
        }
    }

    private suspend fun addLabelsSus(b: BaseBookmarkWithNotes) {
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
        val book = b.book?: return
        try {
            val key = b.bookKey ?: book.getKey(b.key)
            val isWholePage = b.ordinalStart == null || b.ordinalEnd == null

            if (isWholePage) {
                // Whole-page bookmark: get full OSIS fragment for rendering
                b.osisFragment = OsisFragment(SwordContentFacade.readOsisFragment(book, key), key, book)
                // For oneliner preview, get text from beginning of page
                val ordinalRange = SwordContentFacade.ordinalRangeFor(book, key)
                val allTexts = SwordContentFacade.getTextWithinOrdinalsAsString(book, key, ordinalRange)
                // Use first text element as preview
                b.text = allTexts.firstOrNull()?.take(200)?.trim() ?: ""
                b.fullText = b.text
                b.startText = ""
                b.endText = ""
            } else {
                // Regular bookmark with ordinal range
                val verseTexts = SwordContentFacade.getTextWithinOrdinalsAsString(book, key, b.ordinalStart!!..b.ordinalEnd!!)
                addText(b, verseTexts, b.wholeVerse)
            }
        } catch (e: OsisError) {
            b.text = e.stringMsg
            return
        } catch (e: NoSuchKeyException) {
            b.text = e.message
            return
        }
    }

    private fun addText(b: BaseBookmarkWithNotes, texts: List<String>, wholeVerse: Boolean = false) {
        val result = computeBookmarkTexts(texts, b.startOffset, b.endOffset, wholeVerse) ?: run {
            b.startText = ""
            b.endText = ""
            b.text = application.getString(R.string.error_occurred)
            b.fullText = b.text
            return
        }
        b.startText = result.startText
        b.text = result.text
        b.endText = result.endText
        b.fullText = result.fullText
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
        addText(b, verseTexts, wholeVerse)
    }

    fun labelById(id: IdType): Label? = blockingDb { dao.labelById(id) }

    fun getStudyPadTextEntriesForLabel(label: Label): List<StudyPadTextEntryWithText> = blockingDb {
        dao.studyPadTextEntriesByLabelId(label.id)
    }

    fun updateStudyPadTextEntry(entry: StudyPadTextEntry) = blocking {
        dao.update(entry)
        val withText = dao.studyPadTextEntryById(entry.id)
        emitChange(BookmarkChange.StudyPadOrder(entry.labelId, withText, emptyList(), emptyList(), emptyList()))
    }

    /** Inserts a new bookmark-to-label link and announces it after the write succeeds. */
    fun insertBookmarkToLabel(bookmarkToLabel: BaseBookmarkToLabel) = blocking {
        when (bookmarkToLabel) {
            is BibleBookmarkToLabel -> dao.insert(bookmarkToLabel)
            is GenericBookmarkToLabel -> dao.insertGenericBookmarkToLabels(listOf(bookmarkToLabel))
            else -> throw RuntimeException("Illegal type")
        }
        emitChange(BookmarkChange.BookmarkToLabelUpserted(bookmarkToLabel))
    }

    fun updateBookmarkToLabel(bookmarkToLabel: BaseBookmarkToLabel) = blocking {
        dao.update(bookmarkToLabel)
        emitChange(BookmarkChange.BookmarkToLabelUpserted(bookmarkToLabel))
    }

    fun updateBibleBookmarkTimestamp(bookmarkId: IdType) = blockingDb {
        dao.updateBibleBookmarkDate(dao.bibleBookmarkById(bookmarkId)!!.id)
    }

    fun updateGenericBookmarkTimestamp(bookmarkId: IdType) = blockingDb {
        dao.updateGenericBookmarkDate(dao.genericBookmarkById(bookmarkId)!!.id)
    }

    fun getBibleBookmarkToLabel(bookmarkId: IdType, labelId: IdType): BibleBookmarkToLabel? = blockingDb { dao.getBibleBookmarkToLabel(bookmarkId, labelId) }

    fun getGenericBookmarkToLabel(bookmarkId: IdType, labelId: IdType): GenericBookmarkToLabel? = blockingDb { dao.getGenericBookmarkToLabel(bookmarkId, labelId) }

    fun getBookmarkToLabel(bookmark: BaseBookmarkWithNotes, labelId: IdType): BaseBookmarkToLabel? = blockingDb { dao.getBookmarkToLabel(bookmark, labelId) }

    fun getStudyPadById(journalTextEntryId: IdType): StudyPadTextEntryWithText? = blockingDb { dao.studyPadTextEntryById(journalTextEntryId) }

    private suspend fun updateStudyPadTextEntriesSus(studyPadTextEntries: List<StudyPadTextEntryWithText>) = dao.updateStudyPadTextEntries(studyPadTextEntries.map { it.studyPadTextEntryEntity })
    fun deleteStudyPadTextEntry(textEntryId: IdType) = blocking {
        val entry = dao.studyPadTextEntryById(textEntryId)!!
        dao.delete(entry.studyPadTextEntryEntity)
        emitChange(BookmarkChange.StudyPadTextEntryDeleted(textEntryId))
        sanitizeStudyPadOrderSus(entry.labelId)
    }

    private suspend fun sanitizeStudyPadOrderSus(labelId: IdType, updateAllInUi: Boolean = false) {
        val bookmarkToLabels = dao.getBookmarkToLabelsForLabel(labelId)
        val genericBookmarkToLabels = dao.getGenericBookmarkToLabelsForLabel(labelId)
        val studyPadTextEntries = dao.studyPadTextEntriesByLabelId(labelId)
        val all = ArrayList<Any>()
        all.addAll(studyPadTextEntries)
        all.addAll(bookmarkToLabels)
        all.addAll(genericBookmarkToLabels)
        all.sortBy {
            when (it) {
                is BaseBookmarkToLabel -> it.orderNumber
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
        dao.updateBibleBookmarkToLabels(changedBookmarkToLabels)
        dao.updateGenericBookmarkToLabels(changedGenericBookmarkToLabels)
        dao.updateStudyPadTextEntries(changedJournalTextEntries.map { it.studyPadTextEntryEntity })
        if(updateAllInUi || changedBookmarkToLabels.size > 0 || changedGenericBookmarkToLabels.size > 0 || changedJournalTextEntries.size > 0) {
            emitChange(BookmarkChange.StudyPadOrder(
                    labelId,
                    null,
                    if(updateAllInUi) bookmarkToLabels else changedBookmarkToLabels,
                    if(updateAllInUi) genericBookmarkToLabels else changedGenericBookmarkToLabels,
                    if(updateAllInUi) studyPadTextEntries else changedJournalTextEntries
                ))
        }
    }

    private suspend fun sanitizeStudyPadOrderSus(bookmark: BaseBookmarkWithNotes) {
        for (it in dao.labelsForBookmark(bookmark)) {
            sanitizeStudyPadOrderSus(it.id)
        }
    }

    private suspend fun incrementOrderNumbersFromSus(labelId: IdType, fromOrder: Int, newStudyPadTextEntry: StudyPadTextEntryWithText? = null) {
        val bookmarkToLabels = dao.getBookmarkToLabelsForLabel(labelId).filter { it.orderNumber >= fromOrder }.onEach { it.orderNumber++ }
        val genericBookmarkToLabels = dao.getGenericBookmarkToLabelsForLabel(labelId).filter { it.orderNumber >= fromOrder }.onEach { it.orderNumber++ }
        val studyPadTextEntries = dao.studyPadTextEntriesByLabelId(labelId)
            .filter { it.orderNumber >= fromOrder && it.id != newStudyPadTextEntry?.id }
            .onEach { it.orderNumber++ }

        dao.updateBibleBookmarkToLabels(bookmarkToLabels)
        dao.updateGenericBookmarkToLabels(genericBookmarkToLabels)
        updateStudyPadTextEntriesSus(studyPadTextEntries)

        if (newStudyPadTextEntry != null || bookmarkToLabels.isNotEmpty() || genericBookmarkToLabels.isNotEmpty() || studyPadTextEntries.isNotEmpty()) {
            emitChange(BookmarkChange.StudyPadOrder(
                labelId = labelId,
                newStudyPadTextEntry = newStudyPadTextEntry,
                bookmarkToLabelsOrderChanged = bookmarkToLabels,
                genericBookmarkToLabelsOrderChanged = genericBookmarkToLabels,
                studyPadOrderChanged = studyPadTextEntries
            ))
        }
    }

    private suspend fun updateStudyPadCursorIfNeeded(labelId: IdType, orderNumber: Int) {
        val workspaceSettings = windowControl.windowRepository?.workspaceSettings ?: return
        val cursor = workspaceSettings.studyPadCursors[labelId] ?: return
        if (cursor >= orderNumber) {
            workspaceSettings.studyPadCursors[labelId] = cursor + 1
            afterBridge { WorkspaceChanges.notifySettingsEdited() }
        }
    }

    fun createStudyPadEntry(labelId: IdType, entryOrderNumber: Int) = blocking {
        val entry = StudyPadTextEntryWithText(labelId = labelId, orderNumber = entryOrderNumber + 1, contentType = TextContentType.valueOf(CommonUtils.settings.notesContentType))

        dao.insert(entry.studyPadTextEntryEntity)
        dao.insert(entry.studyPadTextEntryTextEntity)

        incrementOrderNumbersFromSus(labelId, entryOrderNumber + 1, newStudyPadTextEntry = entry)
        updateStudyPadCursorIfNeeded(labelId, entryOrderNumber + 1)
    }

    fun createStudyPadEntryWithText(
        labelId: IdType,
        orderNumber: Int? = null,
        text: String,
        contentType: TextContentType? = null,
        sourcePromptId: IdType? = null,
        indentLevel: Int = 0
    ): StudyPadTextEntryWithText = blocking {
        val actualOrderNumber = orderNumber ?: dao.countStudyPadEntities(labelId)

        val entry = StudyPadTextEntryWithText(
            labelId = labelId,
            orderNumber = actualOrderNumber,
            indentLevel = indentLevel,
            text = text,
            contentType = contentType,
            sourcePromptId = sourcePromptId
        )

        dao.insert(entry.studyPadTextEntryEntity)
        dao.insert(entry.studyPadTextEntryTextEntity)

        incrementOrderNumbersFromSus(labelId, actualOrderNumber, newStudyPadTextEntry = entry)
        updateStudyPadCursorIfNeeded(labelId, actualOrderNumber)

        entry
    }

    fun removeBibleBookmarkLabel(bookmarkId: IdType, labelId: IdType) = blocking {
        val bookmark = dao.bibleBookmarkById(bookmarkId)!!
        val labels = dao.labelsForBookmark(bookmark).filter { it.id != labelId }
        addOrUpdateBookmarkSus(bookmark, labels.map { it.id }.toSet())
        Unit
    }

    fun removeGenericBookmarkLabel(bookmarkId: IdType, labelId: IdType) = blocking {
        val bookmark = dao.genericBookmarkById(bookmarkId)!!
        val labels = dao.labelsForBookmark(bookmark).filter { it.id != labelId }
        addOrUpdateBookmarkSus(bookmark, labels.map { it.id }.toSet())
        Unit
    }

    fun getNextLabel(label: Label): Label {
        val allLabels = blockingDb { dao.allLabelsSortedByName() }.filter { !it.isSpecialLabel }
        val thisIndex = allLabels.indexOf(label)
        return try {allLabels[thisIndex+1]} catch (e: IndexOutOfBoundsException) {allLabels[0]}
    }

    fun getPrevLabel(label: Label): Label {
        val allLabels = blockingDb { dao.allLabelsSortedByName() }.filter { !it.isSpecialLabel }
        val thisIndex = allLabels.indexOf(label)
        return try {allLabels[thisIndex-1]} catch (e: IndexOutOfBoundsException) {allLabels[allLabels.size - 1]}
    }

    fun updateOrderNumbers(
        labelId: IdType,
        bookmarksToLabels: List<BibleBookmarkToLabel>,
        genericBookmarksToLabels: List<GenericBookmarkToLabel>,
        studyPadTextEntries: List<StudyPadTextEntryWithText>
    ) = blocking {
        dao.updateStudyPadTextEntries(studyPadTextEntries.map { it.studyPadTextEntryEntity })
        dao.updateBibleBookmarkToLabels(bookmarksToLabels)
        dao.updateGenericBookmarkToLabels(genericBookmarksToLabels)
        emitChange(BookmarkChange.StudyPadOrder(labelId, null, bookmarksToLabels, genericBookmarksToLabels, studyPadTextEntries))
    }

    fun setAsPrimaryLabelForBible(bookmarkId: IdType, labelId: IdType) = blocking {
        val bookmark = dao.bibleBookmarkById(bookmarkId)?: return@blocking
        bookmark.primaryLabelId = labelId
        addOrUpdateBookmarkSus(bookmark)
        Unit
    }

    fun setAsPrimaryLabelForGeneric(bookmarkId: IdType, labelId: IdType) = blocking {
        val bookmark = dao.genericBookmarkById(bookmarkId)?: return@blocking
        bookmark.primaryLabelId = labelId
        addOrUpdateBookmarkSus(bookmark)
        Unit
    }

    fun updateStudyPadTextEntryText(id: IdType, text: String) = blocking {
        val textEntry = StudyPadTextEntryText(id, text)
        dao.update(textEntry)
        val withText = dao.studyPadTextEntryById(id)!!
        emitChange(BookmarkChange.StudyPadOrder(withText.labelId, withText, emptyList(), emptyList(), emptyList()))
    }

    suspend fun exportBookmarksToCSV(context: ActivityBase, exportBookmarks: List<BibleBookmarkWithNotes>) = context.run {
        try {
            if (exportBookmarks.isEmpty()) {
                Toast.makeText(context, getString(R.string.no_bookmarks_to_export), Toast.LENGTH_SHORT)
                    .show()
                return
            }

            // Show column selection dialog
            val selectedColumns = showColumnSelectionDialog(context)
            if (selectedColumns.isEmpty()) return // User cancelled or selected no columns

            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/csv"
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
                putExtra(Intent.EXTRA_TITLE, "bible_bookmarks_$timestamp.csv")
            }

            val result = awaitIntent(intent)
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { exportToUri(context, it, exportBookmarks, selectedColumns) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting CSV export", e)
            ErrorReportControl.showErrorDialog(
                context,
                getString(R.string.csv_export_failed, e.message),
                exception = e
            )
        }
    }

    suspend fun importBookmarksFromCSV(context: ActivityBase) = context.run {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/plain", "text/comma-separated-values"))
            }

            val result = awaitIntent(intent)
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { importFromUri(context, it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting CSV import", e)
            ErrorReportControl.showErrorDialog(
                context,
                getString(R.string.csv_import_failed, e.message),
                exception = e
            )
        }
    }


    private suspend fun showColumnSelectionDialog(context: ActivityBase): List<String> {
        val columns = BookmarkCsvUtils.availableColumns
        
        // Load previously unchecked columns from settings
        val uncheckedColumns = CommonUtils.settings.getStringSet("csv_export_unchecked_columns", emptySet())
        
        // Pre-select columns (all columns except those that were previously unchecked)
        val selectedColumns = Dialogs.multiselect(
            context,
            context.getString(R.string.csv_column_selection_title),
            columns,
            itemToString = { column -> column.displayName },
            preSelected = { column -> !uncheckedColumns.contains(column.key) }
        )
        
        // Save the inverse selection (unchecked items) to settings
        val selectedKeys = selectedColumns.map { it.key }.toSet()
        val newUncheckedColumns = columns.map { it.key }.filter { !selectedKeys.contains(it) }.toSet()
        CommonUtils.settings.setStringSet("csv_export_unchecked_columns", newUncheckedColumns)
        
        return selectedColumns.map { it.key }
    }

    private suspend fun exportToUri(context: Context, uri: Uri, bookmarks: List<BibleBookmarkWithNotes>, selectedColumns: List<String>) = context.run {
        withContext(Dispatchers.IO) {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                BookmarkCsvUtils.exportBookmarksToCsv(outputStream, bookmarks, this@BookmarkControl, selectedColumns)
            } ?: throw IllegalArgumentException("Could not open output stream for URI: $uri")
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    getString(R.string.csv_export_success, bookmarks.size),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * `internal`, not `private`: exercised directly by `BookmarkControlImportFromUriDialogTest` (Task
     * 19 Step 5's error dialog) rather than through the full `importBookmarksFromCSV` SAF round trip.
     */
    internal suspend fun importFromUri(context: Context, uri: Uri) = context.run {
        withContext(Dispatchers.IO) {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, this@BookmarkControl)

                withContext(Dispatchers.Main) {
                    if (result.errors > 0) {
                        // Show detailed error dialog. I2 fix: AppDialogRequest.Message is always
                        // parsed as HTML (parseHtmlRuns) -- a plain "\n"-joined summary collapses
                        // onto one line, and any "<...>" an exception's own message happens to
                        // contain is silently dropped as an unknown tag. plainTextToHtml keeps both.
                        val plainMessage =
                            getString(R.string.csv_import_errors, result.created, result.updated, result.errors) +
                                "\n\n" + result.errorMessages.take(5).joinToString("\n") +
                                if (result.errorMessages.size > 5) "\n..." else ""

                        KoinJavaComponent.get<AppDialogController>(AppDialogController::class.java).post(
                            AppDialogRequest.Message(
                                title = getString(R.string.import_items, "CSV"),
                                message = plainTextToHtml(plainMessage),
                                confirmText = getString(R.string.okay),
                                cancellable = true,
                            ),
                        )
                    } else {
                        Toast.makeText(
                            context,
                            getString(R.string.csv_import_success, result.created, result.updated),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } ?: throw IllegalArgumentException("Could not open input stream for URI: $uri")
        }
    }

    companion object {
        const val LABEL_NO_EXTRA = "labelNo"
        private const val TAG = "BookmarkControl"
        private val SPECIAL_LABEL_LOCK = Mutex()
    }

}

/** Display texts computed for a bookmark: the unselected prefix, the selected text, the
 *  unselected suffix, and the combined full text. */
internal data class BookmarkTexts(
    val startText: String,
    val text: String,
    val endText: String,
    val fullText: String,
)

/**
 * Splits the given verse [texts] into the unselected prefix ([BookmarkTexts.startText]), the
 * selected text ([BookmarkTexts.text]) and the unselected suffix ([BookmarkTexts.endText]) using
 * the bookmark's character offsets.
 *
 * [startOffset]/[endOffset] originate from the WebView text selection (or imported/synced data) and
 * may be out of range relative to the current verse text — e.g. when the bookmark was created against
 * a different module revision, or from invalid stored data (a negative offset has been observed in
 * the wild, causing StringIndexOutOfBoundsException). The offsets are therefore clamped to valid
 * bounds so rendering never throws. For in-range offsets the result is identical to slicing directly.
 *
 * Returns `null` when there is no verse text to render (caller substitutes an error message).
 */
internal fun computeBookmarkTexts(
    texts: List<String>,
    startOffset: Int?,
    endOffset: Int?,
    wholeVerse: Boolean,
): BookmarkTexts? {
    val firstVerse = texts.firstOrNull() ?: return null
    val start0 = (if (wholeVerse) 0 else startOffset ?: 0).coerceIn(0, firstVerse.length)
    return if (texts.size == 1) {
        val end0 = (if (wholeVerse) firstVerse.length else endOffset ?: firstVerse.length)
            .coerceIn(start0, firstVerse.length)
        val startText = firstVerse.substring(0, start0)
        val text = firstVerse.substring(start0, end0).trim()
        val endText = firstVerse.substring(end0)
        BookmarkTexts(startText, text, endText, "$startText$text$endText".trim())
    } else {
        val startText = firstVerse.substring(0, start0)
        val startSelection = firstVerse.substring(start0)
        val lastVerse = texts.last()
        val end0 = (if (wholeVerse) lastVerse.length else endOffset ?: lastVerse.length)
            .coerceIn(0, lastVerse.length)
        val endSelection = lastVerse.substring(0, end0)
        val endText = lastVerse.substring(end0)
        val middleVerses = if (texts.size > 2) texts.slice(1 until texts.size - 1).joinToString(" ") else ""
        val text = "$startSelection$middleVerses$endSelection".trim()
        BookmarkTexts(startText, text, endText, "$startText$text$endText".trim())
    }
}
