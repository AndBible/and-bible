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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import net.bible.sharedcore.bibleview.NO_DOCUMENT_GENERATION
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
    private val chapterLoadJobs = mutableListOf<Job>()
    private var documentLoadGeneration = 0L

    /** Serialises the commentary / general book requests of one direction: each moves the cursor the next one reads. */
    private val beginningCursor = Mutex()
    private val endCursor = Mutex()

    /** The generation of the most recently admitted load (for tests). */
    val loadGeneration: Long get() = documentLoadGeneration

    /** The page's admission state (#3975); this loader drives its transitions, all on the main thread. */
    private val admission get() = session.admission

    init {
        session.admission.onLoadFailed = { target, failure -> showLoadError(target, failure) }
    }

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
        val source = DocumentSource.of(window)
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
                // A Bible page's verse in the MODULE's versification, like its content: the window's verse can be in
                // another one (a module switch keeps it), and the page's chapters, its jump target and its scroll
                // reports' ordinals are the module's (#3975).
                session.initialKey = (location.first as? Verse)?.takeIf { document is BibleDocument }
                    ?.toV11n((document as BibleDocument).swordBook.versification) ?: location.first
                initialAnchorOrdinal = location.second
                initialHtmlId = location.third
                session.contentVisible = false
                // Only the admitted document's chapter, not merged with the previous document's range: a stale
                // scroll report of the previous page must not match it (osTicket 3508). replaceDocument sets the same.
                val chapter = loadedChapter(session.initialKey as? Verse)
                session.minChapter = chapter
                session.maxChapter = chapter
                session.firstDocument = document
                // From now on only calls of the page that received this document act on the window (#3975); chapter
                // requests of the replaced page are answered null, the page is about to be cleared. A Bible / MyNotes
                // document holds the chapters of its range (an introduction is loaded with chapter 1).
                val contexts = when (document) {
                    is BibleDocument -> DocumentSource.chapterContexts(document.verseRange)
                    is MyNotesDocument -> DocumentSource.chapterContexts(document.verseRange)
                    else -> setOf(source.context)
                }
                admission.admit(generation, source, contexts)
                try {
                    if (docStr.length > MAX_DOC_STR_LENGTH) {
                        val errorDoc = ErrorDocument(strings().errorPageTooLarge, ErrorSeverity.NORMAL)
                        docStr = errorDoc.asJson
                        session.firstDocument = errorDoc
                    }
                    latestDocumentStr = docStr
                    needsDocument = true
                    platform.enableZoomForMap(session.pageControl.currentPageManager.isMapShown)
                    if (!session.jsChannel.loading) replaceDocument()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The document is admitted but the page never received it (the WebView threw while it was sent):
                    // the page still shows the previous document, whose calls are closed now. An error document with a
                    // retry replaces it, instead of a window whose page belongs to no admitted document.
                    Log.e(TAG, "Delivering admitted document $generation failed; showing an error document", e)
                    try {
                        showLoadError(source, e)
                    } catch (again: Exception) {
                        // The web view takes no call at all; the next load or page restart sends the error document.
                        Log.e(TAG, "Delivering the error document failed too", again)
                    }
                }
            }
        }
    }

    /**
     * Main thread: the request of [target] (another document) failed before its document reached the page. The page
     * still shows the previous document, which the window no longer holds; instead of leaving it closed for good, an
     * error document with a retry link (`ab-retry://`, [ReadingLinkHandler]) replaces it and is admitted for [target].
     */
    private fun showLoadError(target: DocumentSource, failure: Throwable) {
        if (session.isDisposed || !platform.isCurrentView() || session.windowOrNull !== target.window) {
            // This page no longer serves target's window (disposed, or its view was handed on): the window's own view
            // loads it; nothing of this page is admitted for it.
            Log.w(TAG, "No error document for $target: the page no longer serves its window (${failure.message})")
            return
        }
        val errorDoc = ErrorDocument(strings().errorOccurred, ErrorSeverity.ERROR, retry = true)
        val generation = documentLoadingLock.withLock {
            ++documentLoadGeneration
        }
        Log.w(TAG, "Showing an error document (generation $generation) after: ${failure.message}")
        session.contentVisible = false
        session.initialKey = null
        session.minChapter = -1
        session.maxChapter = -1
        session.firstDocument = errorDoc
        admission.admit(generation, target, isError = true)
        documentLoadingLock.withLock {
            latestDocumentStr = errorDoc.asJson
            needsDocument = true
        }
        if (!session.jsChannel.loading) replaceDocument()
    }

    /**
     * Forgets the window this loader served, when its live page is handed to another window (a workspace switch
     * reuses the outgoing web views, #3950): in-flight loads become stale, a page restart no longer resends the old
     * window's document, and the next document re-uploads the labels (their styles are per workspace). The page keeps
     * showing the old content until the new window's document replaces it.
     */
    fun forgetDocument() {
        documentLoadingLock.withLock {
            ++documentLoadGeneration
            latestDocumentStr = null
            needsDocument = false
            initialAnchorOrdinal = null
            initialHtmlId = null
        }
        admission.forget()
        labelsUploaded = false
        session.firstDocument = null
        session.initialKey = null
        // The old window's chapters; a report or chapter request of the old page must not match them (osTicket 3508).
        session.minChapter = -1
        session.maxChapter = -1
        cancelPendingChapterLoads()
    }

    /**
     * The window asked for another document or key (`Window.loadText`), which [loadDocument] admits later. Until then
     * the page still shows the outgoing document; see [DocumentAdmission.request]. Returns the token for
     * [documentRequestEnded].
     */
    fun documentRequested(): Long = admission.request()

    /** The request [request] ended; see [DocumentAdmission.requestEnded]. */
    fun documentRequestEnded(request: Long, unchanged: Boolean = false, failure: Throwable? = null) =
        admission.requestEnded(request, unchanged, failure)

    /**
     * Runs the page's chapter request [call] on the main thread if [generation] is the document the page holds and the
     * window still shows it; see [DocumentAdmission.runPaging]. [call] then reaches [requestMoreToBeginning] or
     * [requestMoreToEnd] in the same main-thread step.
     */
    override fun runForPageDocument(generation: Long?, call: () -> Unit, drop: () -> Unit) =
        admission.runPaging(generation, call, drop)

    /** The chapter range a document loaded at [verse] starts with; its book introduction (chapter 0) is chapter 1's. */
    private fun loadedChapter(verse: Verse?): Int = verse?.chapter?.let { maxOf(it, 1) } ?: -1

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
        var generation = NO_DOCUMENT_GENERATION
        val documentStr = documentLoadingLock.withLock {
            if (latestDocumentStr == null || !needsDocument) return@withLock null
            // The admitted one, even while a newer document is requested: this page gets this document.
            generation = admission.admittedGeneration
            needsDocument = false
            session.contentVisible = true
            session.minChapter = loadedChapter(verse)
            session.maxChapter = loadedChapter(verse)
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
            BibleViewEvents.clearDocument(generation) + configCommand(true) +
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
            // Chapter scroll-loads read the book too: wait for the startup unlock (suspends, never blocks Main).
            net.bible.service.startup.ReadingAppInit.awaitCipherUnlock()
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
        if (chapterLoadJobs.isNotEmpty()) {
            Log.i(TAG, "Cancelling ${chapterLoadJobs.size} pending chapter load(s)")
            chapterLoadJobs.forEach { it.cancel() }
            chapterLoadJobs.clear()
        }
        emit(BibleViewEvents.resetLoadingCount())
    }

    /**
     * Main thread (reached through [runForPageDocument], which admitted the call against the page's document in the
     * same step). A Bible chapter is reserved in the loaded range now and read off main from the module and book the
     * page's content came from; a commentary block or general book entry is computed and read under the direction's
     * cursor lock, see [requestMoreKey].
     */
    override fun requestMoreToBeginning(callId: Long) {
        Log.i(TAG, "requestMoreTextAtTop")
        if (isBible) {
            requestMoreChapter(callId, session.minChapter - 1)
        } else {
            requestMoreKey(callId, forward = false)
        }
    }

    /** Main thread; see [requestMoreToBeginning]. */
    override fun requestMoreToEnd(callId: Long) {
        Log.i(TAG, "requestMoreTextAtEnd")
        if (isBible) {
            requestMoreChapter(callId, session.maxChapter + 1)
        } else {
            requestMoreKey(callId, forward = true)
        }
    }

    private fun answerNull(callId: Long) {
        emit(BibleViewEvents.response(callId, null))
    }

    /** Tests: makes the read of a chapter for which this answers true fail. */
    var chapterReadFailureForTest: ((Int) -> Boolean)? = null

    /** Tests: makes the read of a commentary block / general book entry for which this answers true fail. */
    var entryReadFailureForTest: ((Key) -> Boolean)? = null

    private fun requestMoreChapter(callId: Long, newChap: Int) {
        val page = window.pageManager.currentBible
        val book = page.currentDocument as? org.crosswire.jsword.book.sword.SwordBook ?: return answerNull(callId)
        // The page's book, in the module's versification its chapters are numbered in.
        val bibleBook = (session.initialKey as? Verse)?.book ?: page.currentBibleVerse.getVerseSelected(book.versification).book
        val lastChap = book.versification.getLastChapter(bibleBook)
        if (newChap < 1 || newChap > lastChap) return answerNull(callId)
        val generation = admission.admittedGeneration
        val context = DocumentSource.chapterContext(bibleBook, newChap)
        addChapter(newChap)
        admission.addContext(generation, context)
        chapterLoadJobs += launchAnswer(callId) {
            try {
                check(chapterReadFailureForTest?.invoke(newChap) != true) { "chapter $newChap unreadable" }
                page.getDocumentForChapter(newChap, book, bibleBook).contentJson()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // The page will not hold the chapter: release its reservation, so a later request can read it again.
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) { releaseChapter(generation, newChap, context) }
                throw e
            }
        }
    }

    /** Main thread: the read of [chapter], reserved for the page of [generation], failed. */
    private fun releaseChapter(generation: Long, chapter: Int, context: String) {
        if (admission.admittedGeneration != generation) return
        when (chapter) {
            session.maxChapter -> session.maxChapter = chapter - 1
            session.minChapter -> session.minChapter = chapter + 1
        }
        admission.removeContext(generation, context)
    }

    /**
     * A commentary block or general book entry before or after the page's content. Requests of one direction are
     * serialised (each moves the cursor, [BibleWindowSession.firstKey] / [BibleWindowSession.lastKey], that the next
     * reads); the next key is computed off main from the module captured here, and the cursor moves on the main thread
     * only while the page's document is still admitted. A page replaced meanwhile gets null (its page is cleared); a
     * window that switched module without requesting yet makes the call wait again ([DocumentAdmission.runPaging]).
     */
    private fun requestMoreKey(callId: Long, forward: Boolean) {
        val generation = admission.admittedGeneration
        val isCommentaryPage = isCommentary
        val page: net.bible.android.control.page.CurrentPageBase =
            if (isCommentaryPage) window.pageManager.currentCommentary else window.pageManager.currentGeneralBook
        val generalPage = page as? net.bible.android.control.page.CurrentGeneralBookPage
        val book = page.currentDocument ?: return answerNull(callId)
        val mutex = if (forward) endCursor else beginningCursor
        chapterLoadJobs += session.scope.launch(Dispatchers.Main.immediate) {
            var previous: Key? = null
            val next: Key? = mutex.withLock {
                if (admission.admittedGeneration != generation) return@withLock null
                val cursor = (if (forward) session.lastKey else session.firstKey) ?: return@withLock null
                val next = try {
                    withContext(Dispatchers.IO) {
                        net.bible.service.startup.ReadingAppInit.awaitCipherUnlock()
                        if (isCommentaryPage) {
                            val commentary = page as net.bible.android.control.page.CurrentCommentaryPage
                            val verse = cursor as? Verse ?: return@withContext null
                            if (forward) commentary.nextBlockStart(verse, book) else commentary.prevBlockStart(verse, book)
                        } else {
                            generalPage!!.getKeyPlus(cursor, if (forward) 1 else -1, book).takeIf { it != cursor }
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Next key failed for call $callId", e)
                    null
                } ?: return@withLock null
                if (admission.admittedGeneration != generation) return@withLock null
                if (page.currentDocument != book) {
                    // The window switched module without a request yet: wait for its outcome.
                    admission.runPaging(generation, call = { requestMoreKey(callId, forward) }, drop = { answerNull(callId) })
                    return@launch
                }
                previous = cursor
                if (forward) session.lastKey = next else session.firstKey = next
                admission.addContext(generation, contextOfKey(next, isCommentaryPage))
                next
            }
            if (next == null) return@launch answerNull(callId)
            val json = try {
                withContext(Dispatchers.IO) {
                    check(entryReadFailureForTest?.invoke(next) != true) { "entry $next unreadable" }
                    page.getPageContent(next, book).contentJson()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chapter load failed for call $callId", e)
                // Main again: the page will not hold the entry. Release its context, and the cursor unless a later
                // request of this direction has moved on from it.
                if (admission.admittedGeneration == generation) {
                    if (forward && session.lastKey == next) session.lastKey = previous
                    if (!forward && session.firstKey == next) session.firstKey = previous
                    admission.removeContext(generation, contextOfKey(next, isCommentaryPage))
                }
                null
            }
            emit(BibleViewEvents.response(callId, json?.let { JsExpr(it) }))
        }
    }

    /** The admission context [key] adds ([DocumentSource]): a commentary block's Bible book, a general book's entry. */
    private fun contextOfKey(key: Key, commentary: Boolean): String? =
        if (commentary) (key as? Verse)?.book?.osis else key.getOsisRef()

    /**
     * The page reports it has (re)started: it holds no labels and no document. Usually this is the first page of the
     * session, but a page can also restart on the same session (iOS: WebKit killed the hidden app's WebContent process
     * and the page reloaded), so the labels and the latest document are always sent again; otherwise the restarted page
     * waits in its loading state for good.
     */
    override fun setClientReady() = session.jsChannel.runOnMain {
        // A (re)started page shows nothing until it reports the document it is sent below, and holds only that
        // document: what infinite scroll added to the old page is gone with it.
        admission.pageRestarted()
        // So are the commentary / general book cursors: the replayed document starts at its key again.
        session.initialKey = session.initialKey
        session.jsChannel.markClientReady()
        documentLoadingLock.withLock { if (latestDocumentStr != null) needsDocument = true }
        labelsUploaded = false
        replaceDocument()
        updateActive()
        // The window may stand where only the old page reached (a chapter or entry infinite scroll added): the replayed
        // document does not hold it, so the window would stay closed for that page. Load where the window is instead.
        if (!admission.showsWindow()) {
            Log.i(TAG, "The restarted page does not hold the window's position; reloading it")
            session.windowOrNull?.loadText()
        }
    }

    override fun parseRef(callId: Long, s: String) {
        val ref = wrapString(session.linkControl.resolveRef(s, (session.firstDocument as? BibleDocument)?.swordBook)?.getOsisRef())
        emit(BibleViewEvents.response(callId, JsExpr(ref)))
    }
}
