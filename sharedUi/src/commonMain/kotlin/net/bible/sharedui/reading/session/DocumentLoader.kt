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


@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.reading.session

import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.serializer
import net.bible.android.common.toV11n
import net.bible.android.control.PassageChangeMediator
import net.bible.android.control.page.BibleDocument
import net.bible.android.control.page.ClientBookmarkLabel
import net.bible.android.control.page.Document
import net.bible.android.control.page.DocumentCategory
import net.bible.android.control.page.ErrorDocument
import net.bible.android.control.page.ErrorSeverity
import net.bible.android.control.page.MyNotesDocument
import net.bible.android.control.page.OrdinalRange
import net.bible.android.control.page.OsisDocument
import net.bible.android.control.page.StudyPadDocument
import net.bible.android.control.page.contentJson
import net.bible.android.control.versification.toVerseRange
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.database.json
import net.bible.android.misc.wrapString
import net.bible.android.view.activity.page.FavouriteLabelIdsCache
import net.bible.sharedcore.bibleview.BibleViewEvents
import net.bible.sharedcore.bibleview.JsCall
import net.bible.sharedcore.bibleview.JsExpr
import net.bible.sharedcore.bibleview.SessionLock
import net.bible.sharedcore.bibleview.SetupContent
import net.bible.sharedcore.log.Log
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.platform.CoreStrings
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.KeyUtil
import org.crosswire.jsword.passage.RangedPassage
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.koin.mp.KoinPlatform

/** The largest serialised document JS is sent; a bigger one is replaced by an "page too large" error document. */
@kotlin.native.HiddenFromObjC
const val MAX_DOC_STR_LENGTH = 4000000

/**
 * What [DocumentLoader] needs from the platform view. Everything WebView-specific (zoom, scroll
 * offsets, the Android settings behind [configInputs], the "is this still the window's view" check)
 * stays behind this seam.
 */
@kotlin.native.HiddenFromObjC
interface DocumentLoaderPlatform {
    /** Zero until the view has been laid out and updated once. */
    val lastUpdated: Long
    val topOffset: Float
    val bottomOffset: Float

    /** Whether the active-window indicator should be drawn (a setting plus more than one visible window). */
    val hasActiveIndicator: Boolean

    /** The `set_config` inputs; [favouriteLabelIds] come from the loader's cache. */
    fun configInputs(favouriteLabelIds: List<IdType>): ConfigInputs

    /** False once the window has been given another view (the load result then belongs to nobody). */
    fun isCurrentView(): Boolean

    fun enableZoomForMap(isMap: Boolean)
}

/**
 * Document loading, chapter paging and config of one reading window, moved out of Android's
 * `BibleView`. It implements [SessionPaging], so bridge calls reach it through `session.paging`.
 *
 * A load that has been superseded by a newer one (or by a window/page change) never applies its JS:
 * [loadDocument] takes a generation under [documentLoadingLock] and re-checks it, on the main
 * thread, before it publishes anything.
 */
@kotlin.native.HiddenFromObjC
class DocumentLoader(
    private val session: BibleWindowSession,
    private val platform: DocumentLoaderPlatform,
    private val strings: () -> CoreStrings = { KoinPlatform.getKoin().get() },
) : SessionPaging {
    private val documentLoadingLock = SessionLock()
    private val requestMoreLock = SessionLock()
    private val chapterLoadJobs = mutableListOf<Job>()
    private var documentLoadGeneration = 0L

    /** The generation of the most recently admitted load (for tests). */
    val loadGeneration: Long get() = documentLoadGeneration

    var latestDocumentStr: String? = null
        private set
    private var needsDocument: Boolean = false
    private var labelsUploaded = false
    var initialAnchorOrdinal: OrdinalRange? = null
        private set
    var initialHtmlId: String? = null
        private set

    var bookmarkLabels: List<BookmarkEntities.Label> = emptyList()
        private set

    /** Workspace label style overrides; refreshed on every load and when a label changes. */
    var labelOverrides: Map<IdType, WorkspaceEntities.WorkspaceLabelOverride> = emptyMap()

    /** Favourite label ids for the `set_config` command (synchronous): read in [loadDocument], kept
     *  current synchronously from the bookmark subscription; see [FavouriteLabelIdsCache] for the threading. */
    val favouriteLabels = FavouriteLabelIdsCache()

    private val window get() = session.window
    private val TAG get() = "DocumentLoader[${session.windowOrNull?.displayId}]"

    private val isBible get() = session.firstDocument is BibleDocument
    private val isCommentary get() = (session.firstDocument as? OsisDocument)?.book?.bookCategory == BookCategory.COMMENTARY
    private val isActive get() = session.windowControl.activeWindow.id == window.id

    private fun emit(call: JsCall) = session.emit(call)

    suspend fun refreshFavouriteLabels() {
        favouriteLabels.refresh { session.bookmarkControl.favouriteLabels().map { it.id } }
    }

    suspend fun loadDocument(
        document: Document,
        updateLocation: Boolean,
        key: Key?,
        anchorOrdinal: OrdinalRange?,
        htmlId: String?,
        windowContentGeneration: Long?,
    ) {
        val generation = documentLoadingLock.withLock {
            // A stale Window request must not invalidate an already admitted newer load.
            if (windowContentGeneration != null && window.contentLoadGeneration != windowContentGeneration) return@withLock null
            ++documentLoadGeneration
        } ?: return
        val currentPage = window.pageManager.currentPage
        val sourceDocument = currentPage.currentDocument
        val workspaceId = session.windowControl.windowRepository.id
        val location = withContext(Dispatchers.Main) {
            var loadKey = key
            var loadAnchor = anchorOrdinal
            var loadHtmlId = htmlId
            if (platform.lastUpdated == 0L || updateLocation) {
                if (currentPage.documentCategory in listOf(DocumentCategory.BIBLE, DocumentCategory.MYNOTE)) {
                    loadKey = KeyUtil.getVerse(window.pageManager.currentBibleVerse.verse)
                } else {
                    loadAnchor = currentPage.anchorOrdinal
                    loadHtmlId = currentPage.htmlId
                }
            }
            Triple(loadKey, loadAnchor, loadHtmlId)
        }
        val bookmarkControl = session.bookmarkControl
        bookmarkControl.labelUnlabelled()
        bookmarkControl.speakLabel()
        bookmarkControl.paragraphBreakLabel()
        val overrides = DatabaseContainer.instance.workspaceDb.workspaceDao().labelOverrides(workspaceId).associateBy { it.labelId }
        val labels = bookmarkControl.assignableLabels().map { it.withStyleOverrides(overrides[it.id]) }
        var docStr = withContext(Dispatchers.IO) { document.contentJson() }
        // This independent cache replays concurrent bookmark changes; populate before first config.
        refreshFavouriteLabels()
        withContext(Dispatchers.Main) {
            documentLoadingLock.withLock {
                if (generation != documentLoadGeneration ||
                    (windowContentGeneration != null && window.contentLoadGeneration != windowContentGeneration) ||
                    !platform.isCurrentView() ||
                    window.pageManager.currentPage !== currentPage || currentPage.currentDocument != sourceDocument ||
                    session.windowControl.windowRepository.id != workspaceId) return@withLock
                labelOverrides = overrides
                bookmarkLabels = labels
                session.initialKey = location.first
                initialAnchorOrdinal = location.second
                initialHtmlId = location.third
                session.contentVisible = false
                (session.initialKey as? Verse)?.chapter?.let { addChapter(it) }
                session.firstDocument = document
                if (docStr.length > MAX_DOC_STR_LENGTH) {
                    val errorDoc = ErrorDocument(strings().errorPageTooLarge, ErrorSeverity.NORMAL)
                    docStr = errorDoc.asJson
                    session.firstDocument = errorDoc
                }
                latestDocumentStr = docStr
                needsDocument = true
                platform.enableZoomForMap(session.pageControl.currentPageManager.isMapShown)
                if (!session.jsChannel.loading) replaceDocument()
            }
        }
    }

    private fun addChapter(chapter: Int) {
        when {
            chapter < session.minChapter -> session.minChapter = chapter
            chapter > session.maxChapter -> session.maxChapter = chapter
            else -> Log.e(TAG, "Chapter already included")
        }
    }

    /** `set_config` for the window's current state; synchronous so it keeps its place in the JS task queue
     *  relative to what is emitted right after it (an `initial` config must never land after a later one). */
    fun updateConfig(initial: Boolean = false) {
        emit(configCommand(initial))
    }

    private fun configCommand(initial: Boolean) =
        ConfigCommand.build(initial, platform.configInputs(favouriteLabels.ids))

    fun updateActive() =
        emit(BibleViewEvents.setActive(isActive, platform.hasActiveIndicator))

    private fun replaceDocument() {
        Log.i(TAG, "replaceDocument")
        cancelPendingChapterLoads()

        val verse = if (isBible || session.firstDocument is MyNotesDocument) session.initialKey as? Verse else null
        val documentStr = documentLoadingLock.withLock {
            if (latestDocumentStr == null || !needsDocument) return@withLock null
            needsDocument = false
            session.contentVisible = true
            requestMoreLock.withLock {
                session.minChapter = verse?.chapter ?: -1
                session.maxChapter = verse?.chapter ?: -1
            }
            latestDocumentStr
        } ?: return

        if (!labelsUploaded) {
            val bookmarkLabels = json.encodeToString(serializer(), bookmarkLabels.map { ClientBookmarkLabel(it) })
            emit(BibleViewEvents.updateLabels(JsExpr(bookmarkLabels)))
            labelsUploaded = true
        }

        val doc = session.firstDocument
        val jumpToId =
            if (doc is StudyPadDocument && doc.entryId != null)
                "o-${abs(doc.entryId.hashCode())}"
            else initialHtmlId

        emit(
            BibleViewEvents.clearDocument() + configCommand(true) +
                BibleViewEvents.addDocuments(JsExpr("$documentStr")) +
                BibleViewEvents.setupContent(SetupContent(
                    jumpToOrdinal = verse?.ordinal,
                    jumpToAnchor = initialAnchorOrdinal?.start,
                    jumpToId = jumpToId,
                    topOffset = platform.topOffset,
                    bottomOffset = platform.bottomOffset,
                    ordinalStart = initialAnchorOrdinal?.start,
                    ordinalEnd = initialAnchorOrdinal?.end,
                    highlight = initialAnchorOrdinal?.end != null,
                    bookInitials = window.pageManager.currentPage.currentDocument?.initials,
                    osisRef = window.pageManager.currentPage.key?.getOsisRef(),
                )) + BibleViewEvents.setTitle("BibleView-${window.displayId}")
        )
    }

    fun scrollOrJumpToVerse(key: Key, forceNow: Boolean) {
        Log.i(TAG, "Scroll or jump to:$key")
        var toVerse: Verse
        var endVerse: Verse? = null
        when (key) {
            is VerseRange -> {
                toVerse = key.start
                endVerse = key.end
            }
            is Verse -> toVerse = key
            is RangedPassage -> {
                val range = key.toVerseRange
                toVerse = range.start
                endVerse = range.end
            }
            else -> throw RuntimeException("illegal type")
        }
        val v = session.initialKey as? Verse
        if (session.firstDocument is MyNotesDocument) {
            toVerse = toVerse.toV11n(KJVA)
            endVerse = endVerse?.toV11n(KJVA)
        } else if (v != null) {
            toVerse = toVerse.toV11n(v.versification)
            endVerse = endVerse?.toV11n(v.versification)
        }
        val jumpToId = "o-${toVerse.ordinal}"
        val now = !session.contentVisible || forceNow
        val highlight = !session.contentVisible || endVerse != null
        emit(BibleViewEvents.scrollToVerse(jumpToId, now, highlight, toVerse.ordinal, endVerse?.ordinal))
        if (isActive) {
            PassageChangeMediator.onCurrentVerseChanged(window)
        }
    }

    fun scrollOrJumpToOrdinal(ordinal: OrdinalRange?, htmlId: String?, bookInitials: String?, osisRef: String?, forceNow: Boolean) {
        Log.i(TAG, "Scroll or jump to ordinal:$ordinal")

        // Return early if both ordinal and htmlId are null - nothing to scroll to
        if (ordinal == null && htmlId == null) {
            Log.w(TAG, "scrollOrJumpToOrdinal called with both ordinal and htmlId null - skipping")
            return
        }

        val now = !session.contentVisible || forceNow

        val highlight = !session.contentVisible || ordinal?.end != null
        val jumpToId = if (ordinal != null) "o-${ordinal.start}" else htmlId!!

        emit(BibleViewEvents.scrollToVerseIn(jumpToId, now, highlight, bookInitials, osisRef, ordinal?.start, ordinal?.end))
        if (isActive) {
            PassageChangeMediator.onCurrentVerseChanged(window)
        }
    }

    fun highlightOrdinalRange(bookInitials: String, osisRef: String, range: IntRange) {
        emit(BibleViewEvents.scrollToVerseIn(null, false, true, bookInitials, osisRef, range.first, range.last))
    }

    fun highlightBibleOrdinalRange(range: IntRange) {
        emit(BibleViewEvents.scrollToVerse(null, false, true, range.first, range.last))
    }

    /**
     * Loads on IO and answers the JS promise [callId] with the JSON [load] returns. A failing load logs
     * and answers null so the JS promise never hangs (cancellation is rethrown; the caller's cancel path
     * resets the loading count). Public only so tests can drive a failing load.
     */
    fun launchAnswer(callId: Long, load: suspend () -> String): Job = session.scope.launch(Dispatchers.IO) {
        val json = try {
            load()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Chapter load failed for call $callId", e)
            null
        }
        emit(BibleViewEvents.response(callId, json?.let { JsExpr(it) }))
    }

    /** Cancel all in-flight chapter loads (stale after document replacement / navigation). */
    private fun cancelPendingChapterLoads() {
        requestMoreLock.withLock {
            if (chapterLoadJobs.isNotEmpty()) {
                Log.i(TAG, "Cancelling ${chapterLoadJobs.size} pending chapter load(s)")
                chapterLoadJobs.forEach { it.cancel() }
                chapterLoadJobs.clear()
            }
        }
        emit(BibleViewEvents.resetLoadingCount())
    }

    override fun requestMoreToBeginning(callId: Long) = requestMoreLock.withLock {
        Log.i(TAG, "requestMoreTextAtTop")
        if (isBible) {
            val newChap = session.minChapter - 1
            if (newChap < 1) {
                emit(BibleViewEvents.response(callId, null))
                return@withLock
            }
            addChapter(newChap)

            val currentPage = window.pageManager.currentBible
            chapterLoadJobs += launchAnswer(callId) {
                val doc = currentPage.getDocumentForChapter(newChap)
                doc.contentJson()
            }
        } else if (isCommentary) {
            val currentPage = window.pageManager.currentCommentary
            val first = session.firstKey as? Verse ?: run {
                emit(BibleViewEvents.response(callId, null))
                return@withLock
            }
            val prevStart = currentPage.prevBlockStart(first) ?: run {
                emit(BibleViewEvents.response(callId, null))
                return@withLock
            }
            session.firstKey = prevStart
            chapterLoadJobs += launchAnswer(callId) {
                val doc = currentPage.getPageContent(prevStart)
                doc.contentJson()
            }
        } else {
            val currentPage = window.pageManager.currentGeneralBook
            val firstKey = session.firstKey
            firstKey ?: run {
                emit(BibleViewEvents.response(callId, null))
                return@withLock
            }
            val prevKey = currentPage.getKeyPlus(firstKey, -1)
            if (prevKey == firstKey) {
                emit(BibleViewEvents.response(callId, null))
                return@withLock
            }

            session.firstKey = prevKey

            chapterLoadJobs += launchAnswer(callId) {
                val doc = currentPage.getPageContent(prevKey)
                doc.contentJson()
            }
        }
    }

    override fun requestMoreToEnd(callId: Long) = requestMoreLock.withLock {
        Log.i(TAG, "requestMoreTextAtEnd")
        if (isBible) {
            val currentPage = window.pageManager.currentBible
            val newChap = session.maxChapter + 1
            val verse = currentPage.currentBibleVerse.verse
            val lastChap = verse.versification.getLastChapter(verse.book)
            if (newChap > lastChap) {
                emit(BibleViewEvents.response(callId, null))
                return@withLock
            }
            addChapter(newChap)

            chapterLoadJobs += launchAnswer(callId) {
                val doc = currentPage.getDocumentForChapter(newChap)
                doc.contentJson()
            }
        } else if (isCommentary) {
            val currentPage = window.pageManager.currentCommentary
            val last = session.lastKey as? Verse ?: run {
                emit(BibleViewEvents.response(callId, null))
                return@withLock
            }
            val nextStart = currentPage.nextBlockStart(last) ?: run {
                emit(BibleViewEvents.response(callId, null))
                return@withLock
            }
            session.lastKey = nextStart
            chapterLoadJobs += launchAnswer(callId) {
                val doc = currentPage.getPageContent(nextStart)
                doc.contentJson()
            }
        } else {
            val currentPage = window.pageManager.currentGeneralBook
            val lastKey = session.lastKey
            lastKey ?: run {
                emit(BibleViewEvents.response(callId, null))
                return@withLock
            }
            val nextKey = currentPage.getKeyPlus(lastKey, 1)
            if (nextKey == lastKey) {
                emit(BibleViewEvents.response(callId, null))
                return@withLock
            }
            session.lastKey = nextKey
            chapterLoadJobs += launchAnswer(callId) {
                val doc = currentPage.getPageContent(nextKey)
                doc.contentJson()
            }
        }
    }

    override fun setClientReady() = session.jsChannel.runOnMain {
        session.jsChannel.markClientReady()
        replaceDocument()
        updateActive()
    }

    override fun parseRef(callId: Long, s: String) {
        val ref = wrapString(session.linkControl.resolveRef(s, (session.firstDocument as? BibleDocument)?.swordBook)?.getOsisRef())
        emit(BibleViewEvents.response(callId, JsExpr(ref)))
    }
}
