/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.sharedui.reading.session

import kotlin.concurrent.Volatile
import kotlinx.coroutines.launch
import kotlinx.serialization.serializer
import net.bible.android.common.toV11n
import net.bible.android.control.bookmark.BookmarkChange
import net.bible.android.control.page.BibleDocument
import net.bible.android.control.page.ClientAiDocMarker
import net.bible.android.control.page.ClientBibleBookmark
import net.bible.android.control.page.ClientBookmarkLabel
import net.bible.android.control.page.ClientGenericBookmark
import net.bible.android.control.page.MemorizeDocument
import net.bible.android.control.page.MyNotesDocument
import net.bible.android.control.page.StudyPadDocument
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowChange
import net.bible.android.control.page.window.WindowStateServiceImpl
import net.bible.android.control.progress.ProgressChange
import net.bible.android.control.progress.ProgressControl
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.database.json
import net.bible.sharedcore.bibleview.BibleViewEvents
import net.bible.sharedcore.bibleview.JsCall
import net.bible.sharedcore.bibleview.JsExpr
import net.bible.sharedcore.event.Events
import net.bible.sharedcore.event.Subscriptions
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.service.common.ReadingProgressSettings
import net.bible.service.db.DatabaseContainer
import net.bible.service.sword.SwordDocumentFacade
import net.bible.service.sword.mydocument.MyDocumentBookManager
import net.bible.service.sword.mydocument.MyDocumentChange
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.Versification
import org.koin.mp.KoinPlatform

/** What [SessionEventRelay] needs from the platform view for window changes (WebView, tilt scroll, offsets). */
interface SessionEventRelayPlatform {
    /** This window became (or stopped being) the active one: notifications and tilt scrolling follow it. */
    fun onActiveWindowChanged(isThisWindowActive: Boolean)

    fun updateOffsets(immediate: Boolean)

    /** A window layout configuration change: the next load-time check must re-read the windows. */
    fun markWindowsCheckNeeded()

    fun updateConfig()
}

/**
 * Forwards the controller change streams that concern one reading window (bookmarks, windows,
 * read status, memorization, reading-progress settings, AI document pages) to JS. Moved out of
 * Android's `BibleView`; [start] and [stop] are called where `BibleView.listenEvents` flips.
 *
 * Handlers run on the emitter's thread like the subscribers they replace; bookmark changes are
 * handled in order on [BibleWindowSession.scope]. [stop] cancels the subscriptions only: bookmark-change
 * jobs already queued keep running until the session is disposed ([BibleWindowSession.dispose] cancels its scope).
 */
class SessionEventRelay(
    private val session: BibleWindowSession,
    private val documentLoader: DocumentLoader,
    private val platform: SessionEventRelayPlatform,
    private val windowChanges: () -> Events<WindowChange> = {
        KoinPlatform.getKoin().get<WindowStateServiceImpl>().windowChanges
    },
    private val progressChanges: Events<ProgressChange> = ProgressControl.changes,
    private val readingProgressSettingsChanged: Events<Unit> = ReadingProgressSettings.changed,
    private val aiDocChanges: Events<MyDocumentChange> = MyDocumentBookManager.changes,
    private val bookmarkChangeEvents: () -> Events<BookmarkChange> = { session.bookmarkControl.changes },
) {
    private val subscriptions = Subscriptions()
    private val bookmarkChanges by lazy { OrderedLauncher(session.scope) }
    @Volatile private var started = false

    private val window: Window get() = session.window
    private val firstDocument get() = session.firstDocument
    private fun emit(call: JsCall) = session.emit(call)

    fun start() {
        if (started) return
        started = true
        subscriptions.add(windowChanges().subscribe { change ->
            when (change) {
                is WindowChange.ActiveWindowChanged -> onActiveWindowChanged(change.window)
                WindowChange.WindowsChanged -> onWindowsChanged()
                WindowChange.RestoreButtonsChanged -> platform.updateOffsets(false)
                WindowChange.LayoutConfigurationChanged -> platform.markWindowsCheckNeeded()
            }
        })
        subscriptions.add(progressChanges.subscribe { change ->
            when (change) {
                is ProgressChange.Memorization -> onMemorizationChanged(change)
                is ProgressChange.ChapterReadStatus -> onChapterReadStatusChanged(change)
                is ProgressChange.ActiveCycle -> onActiveCycleChanged()
            }
        })
        subscriptions.add(readingProgressSettingsChanged.subscribe { onReadingProgressSettingsChanged() })
        subscriptions.add(bookmarkChangeEvents().subscribe { change ->
            documentLoader.favouriteLabels.apply(change)
            bookmarkChanges.launch("bibleview-bookmarks-${window.id}") { onBookmarkChange(change) }
        })
        subscriptions.add(aiDocChanges.subscribe {
            if (it is MyDocumentChange.AiDocPages) onAiDocPagesChanged(it)
        })
    }

    fun stop() {
        if (!started) return
        started = false
        subscriptions.cancelAll()
    }

    private fun onActiveWindowChanged(activeWindow: Window) {
        platform.onActiveWindowChanged(window == activeWindow)
        documentLoader.updateActive()
    }

    private fun onWindowsChanged() {
        if (window.isVisible) {
            platform.updateOffsets(true)
            platform.updateConfig()
        }
    }

    private fun onAiDocPagesChanged(change: MyDocumentChange.AiDocPages) {
        // For Bible documents, convert ordinals to target versification.
        // For all other documents, pass markers as-is — Vue.js filters by sourceBookInitials/Key.
        val v11n = (firstDocument as? BibleDocument)?.swordBook?.versification

        if (change.markers.isNotEmpty()) {
            val markerStr = change.markers.map { ClientAiDocMarker(it, v11n).asJson }.joinToString(",", "[", "]")
            emit(BibleViewEvents.addOrUpdateAiDocMarkers(JsExpr(markerStr)))
        }
        if (change.deletedPageIds.isNotEmpty()) {
            emit(BibleViewEvents.deleteAiDocMarkers(change.deletedPageIds.map { it.toString() }))
        }
    }

    /** Emits bookmark changes to the reading view after any required suspend reads. */
    private suspend fun onBookmarkChange(change: BookmarkChange) {
        when (change) {
            is BookmarkChange.BookmarksUpserted -> {
                val document = firstDocument

                val clientBookmarks = change.bookmarks.map {
                    when (it) {
                        is BookmarkEntities.BibleBookmarkWithNotes ->
                            ClientBibleBookmark(
                                it,
                                when (document) {
                                    is BibleDocument -> document.swordBook.versification
                                    is MyNotesDocument -> KJVA
                                    else -> null
                                }
                            )

                        is BookmarkEntities.GenericBookmarkWithNotes -> ClientGenericBookmark(it)
                        else -> throw RuntimeException("Invalid type")
                    }
                }.map { it.asJson }

                val bookmarkStr = clientBookmarks.joinToString(",", "[", "]")
                emit(BibleViewEvents.addOrUpdateBookmarks(JsExpr(bookmarkStr)))
            }
            is BookmarkChange.NoteModified -> {
                emit(BibleViewEvents.bookmarkNoteModified(change.bookmarkId.toString(), change.lastUpdatedOn, change.notes))
            }
            is BookmarkChange.StudyPadOrder -> {
                val doc = firstDocument
                if(doc !is StudyPadDocument || doc.label.id != change.labelId) return
                val studyPadTextEntryJson = json.encodeToString(serializer(), change.newStudyPadTextEntry)
                val bookmarkToLabels = json.encodeToString(serializer(), change.bookmarkToLabelsOrderChanged)
                val genericBookmarkToLabels = json.encodeToString(serializer(), change.genericBookmarkToLabelsOrderChanged)
                val studyPadItems = json.encodeToString(serializer(), change.studyPadOrderChanged)
                emit(BibleViewEvents.addOrUpdateStudyPad(JsExpr(studyPadTextEntryJson), JsExpr(bookmarkToLabels), JsExpr(genericBookmarkToLabels), JsExpr(studyPadItems)))
            }
            is BookmarkChange.BookmarkToLabelUpserted -> {
                val doc = firstDocument
                if(doc !is StudyPadDocument || doc.label.id != change.bookmarkToLabel.labelId) return
                val bookmarkToLabelStr = when(change.bookmarkToLabel) {
                    is BookmarkEntities.BibleBookmarkToLabel ->json.encodeToString(serializer(), change.bookmarkToLabel)
                    is BookmarkEntities.GenericBookmarkToLabel -> json.encodeToString(serializer(), change.bookmarkToLabel)
                    else -> throw RuntimeException("Illegal type")
                }
                emit(BibleViewEvents.addOrUpdateBookmarkToLabel(JsExpr(bookmarkToLabelStr)))
            }
            is BookmarkChange.StudyPadTextEntryDeleted -> {
                if(firstDocument !is StudyPadDocument) return
                emit(BibleViewEvents.deleteStudyPadTextEntry(change.studyPadTextEntryId.toString()))
            }
            is BookmarkChange.LabelUpserted -> {
                val workspaceId = session.windowControl.windowRepository.id
                val dao = DatabaseContainer.instance.workspaceDb.workspaceDao()
                documentLoader.labelOverrides = dao.labelOverrides(workspaceId).associateBy { it.labelId }
                val overriddenLabel = change.label.withStyleOverrides(documentLoader.labelOverrides[change.label.id])
                val labelStr = json.encodeToString(serializer(), ClientBookmarkLabel(overriddenLabel))
                emit(BibleViewEvents.updateLabels(JsExpr("[$labelStr]")))
            }
            is BookmarkChange.BookmarksDeleted -> {
                val bookmarkIds = json.encodeToString(serializer(), change.bookmarkIds)
                emit(BibleViewEvents.deleteBookmarks(JsExpr(bookmarkIds)))
            }
            is BookmarkChange.LabelsDeleted -> {
                val labelIds = json.encodeToString(serializer(), change.labelIds)
                emit(BibleViewEvents.deleteLabels(JsExpr(labelIds)))
            }
        }
    }

    private fun onReadingProgressSettingsChanged() {
        val settingsJson = ReadingProgressSettings.getBundleAsJson()
        emit(BibleViewEvents.updateReadingProgressSettings(JsExpr(settingsJson)))
        platform.updateConfig()
    }

    private fun onMemorizationChanged(event: ProgressChange.Memorization) {
        val doc = firstDocument
        if (doc !is BibleDocument && doc !is MemorizeDocument) return

        // Convert KJV ordinals to document versification
        val v11n = when (doc) {
            is BibleDocument -> doc.swordBook.versification
            is MemorizeDocument -> doc.bookInitials?.let {
                (SwordDocumentFacade.getDocumentByInitials(it) as? SwordBook)?.versification
            }
            else -> null
        }
        val addedMemorized = convertOrdinals(v11n, event.addedMemorized)
        val removedMemorized = convertOrdinals(v11n, event.removedMemorized)
        val addedTargets = convertOrdinals(v11n, event.addedTargets)
        val removedTargets = convertOrdinals(v11n, event.removedTargets)
        emit(BibleViewEvents.updateMemorizationData(addedMemorized, removedMemorized, addedTargets, removedTargets))
    }

    private fun onChapterReadStatusChanged(event: ProgressChange.ChapterReadStatus) {
        val doc = firstDocument
        if (doc !is BibleDocument) return
        // Different windows may show the same chapter number across different books, so
        // filter by KJV book ordinal here — the Vue-side tracker only checks chapter number.
        val docKjvBookOrdinal = doc.verseRange.toV11n(KJVA).start.book.ordinal
        if (docKjvBookOrdinal != event.kjvBookOrdinal) return
        emit(BibleViewEvents.updateChapterReadStatus(event.chapter, event.count))
    }

    private fun onActiveCycleChanged() {
        val doc = firstDocument
        if (doc !is BibleDocument) return
        if (session.minChapter < 0 || session.maxChapter < 0) return
        val v11n = doc.swordBook.versification
        val book = doc.verseRange.start.book
        val chapters = session.minChapter..session.maxChapter
        session.scope.launch {
            for (chapter in chapters) {
                val count = ProgressControl.getChapterReadCount(v11n, book, chapter)
                emit(BibleViewEvents.updateChapterReadStatus(chapter, count))
            }
        }
    }

    private fun convertOrdinals(v11n: Versification?, kjvOrdinals: List<Int>): List<Int> {
        val converted = if (v11n != null) {
            kjvOrdinals.map { Verse(KJVA, it).toV11n(v11n).ordinal }
        } else {
            kjvOrdinals
        }
        return converted
    }
}
