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

package net.bible.service.llm.agent

import android.util.Log
import kotlinx.serialization.json.Json.Default.decodeFromString
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.common.toV11n
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.link.LinkControl
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowLayout.WindowState
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.view.activity.page.Selection
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.AgentPrompt
import net.bible.service.llm.BuiltInPrompts
import net.bible.service.llm.LlmCostTracker
import net.bible.service.llm.LlmPricing
import net.bible.service.llm.LlmRawLogRecord
import net.bible.service.llm.LlmUsage
import net.bible.service.llm.PromptRepository
import net.bible.service.llm.tools.ToolRegistry
import org.json.JSONObject
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.formatJsonForLog
import net.bible.service.llm.tools.stripMarkdownFromTitle
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.mydocument.MyDocumentBookManager
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.RangedPassage
import org.crosswire.jsword.passage.VerseRange
import net.bible.android.control.versification.toVerseRange
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import android.widget.Toast
import net.bible.android.database.mydocument.AiCachedPageWithContent
import net.bible.android.view.activity.base.CurrentActivityHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

open class AgentSessionManagerBase {
    @Inject lateinit var windowControl: WindowControl
    @Inject lateinit var linkControl: LinkControl
}

class AgentLogUpdatedEvent(
    val workspaceId: IdType,
    val entry: AgentLogEntry
)

/** Terminal outcome of an agent session, carried on [AgentSessionStatusChangedEvent]. */
enum class AgentStopReason { COMPLETED, ERROR, CANCELLED }

/**
 * Whether the agent log panel should auto-hide for a terminal session state.
 * Pure function — unit tested in AgentLogAutoHideTest. Hides for any non-error
 * terminal reason when the setting is enabled; keeps the panel visible on error
 * (so the user can read it) and ignores the start event (reason == null).
 */
fun shouldAutoHideAgentLog(settingEnabled: Boolean, reason: AgentStopReason?): Boolean =
    settingEnabled && reason != null && reason != AgentStopReason.ERROR

class AgentSessionStatusChangedEvent(
    val workspaceId: IdType,
    val isRunning: Boolean,
    /** Terminal outcome when [isRunning] is false; null on the start event. */
    val stopReason: AgentStopReason? = null
)

/** Posted when the agent is waiting for user to return to grant permission. */
class AgentPermissionWaitingEvent(
    val workspaceId: IdType,
    val waiting: Boolean,
    val toolName: String? = null
)

/** Result to open when user returns to the app after background completion. */
sealed class PendingAgentResult {
    data class OpenDocument(val documentInitials: String, val pageKey: String, val targetWindowId: IdType?) : PendingAgentResult()
    data class OpenStudyPad(val labelId: IdType, val scrollToEntryId: IdType?) : PendingAgentResult()
}

/** One active session per workspace, maintaining log entries and execution state. */
class AgentSession(val workspaceId: IdType) {
    private val _logEntries = CopyOnWriteArrayList<AgentLogEntry>()
    val logEntries: List<AgentLogEntry> get() = _logEntries

    /** Raw LLM conversation log for debug inspection. */
    var rawLlmLog: RawLlmLog? = null
        private set

    @Volatile
    var isRunning: Boolean = false
        private set

    /** Cumulative session cost in USD, updated on each API call. */
    @Volatile
    var sessionCostUsd: Double = 0.0
        private set

    var context: AgentContext? = null
        private set

    var job: Job? = null

    /** Result to open when user returns to the app after background completion. */
    @Volatile
    var pendingResult: PendingAgentResult? = null

    fun start(context: AgentContext) {
        this.context = context
        this.isRunning = true
        this.sessionCostUsd = 0.0
        _logEntries.clear()
        rawLlmLog = RawLlmLog()
        addLogEntry(AgentLogEntry.info("Agent started"))
        ABEventBus.post(AgentSessionStatusChangedEvent(workspaceId, true))
    }

    fun stop(message: String? = null, reason: AgentStopReason = AgentStopReason.CANCELLED) {
        if (message != null) {
            val hasRawLog = rawLlmLog?.isEmpty() == false
            addLogEntry(AgentLogEntry.info(message, showRawLogLink = hasRawLog))
        }
        this.isRunning = false
        this.job?.cancel()
        this.job = null
        ABEventBus.post(AgentSessionStatusChangedEvent(workspaceId, false, reason))
    }

    fun addLogEntry(entry: AgentLogEntry) {
        _logEntries.add(entry)
        ABEventBus.post(AgentLogUpdatedEvent(workspaceId, entry))
    }

    fun updateEntryStatus(entryId: IdType, newStatus: EntryStatus) {
        val entry = _logEntries.find { it.id == entryId }
        if (entry != null) {
            entry.status = newStatus
            ABEventBus.post(AgentLogUpdatedEvent(workspaceId, entry))
        }
    }

    /**
     * Updates an existing ACTION entry identified by [toolCallId]. Used to collapse
     * a ToolCalling+ToolCompleted pair into a single log row whose icon transitions
     * from hourglass to tick (or error). Returns true if the entry was found and
     * updated; false if no matching entry exists (caller can fall back to addLogEntry).
     */
    fun updateActionEntry(toolCallId: String, message: String, details: String?, status: EntryStatus): Boolean {
        val entry = _logEntries.find { it.toolCallId == toolCallId } ?: return false
        entry.message = message
        entry.details = details
        entry.status = status
        ABEventBus.post(AgentLogUpdatedEvent(workspaceId, entry))
        return true
    }

    fun addCost(cost: Double) {
        sessionCostUsd += cost
    }

    fun setLastEntryCost(costInfo: String, isTotalCost: Boolean = false) {
        val entry = _logEntries.lastOrNull() ?: return
        entry.costInfo = costInfo
        entry.isTotalCost = isTotalCost
        ABEventBus.post(AgentLogUpdatedEvent(workspaceId, entry))
    }

    fun clearLog() {
        _logEntries.clear()
        rawLlmLog = null
    }
}

/** One session per workspace, lazily created. */
object AgentSessionManager : AgentSessionManagerBase() {
    private val activeSessions = ConcurrentHashMap<IdType, AgentSession>()
    private var initialized = false

    @Synchronized
    private fun ensureInitialized() {
        if (!initialized) {
            CommonUtils.buildActivityComponent().inject(this)
            cleanupOldRawLogs()
            initialized = true
        }
    }

    @Synchronized
    fun getOrCreateSession(workspaceId: IdType): AgentSession {
        return activeSessions.getOrPut(workspaceId) { AgentSession(workspaceId) }
    }

    @Synchronized
    fun getSession(workspaceId: IdType): AgentSession? {
        return activeSessions[workspaceId]
    }

    fun isRunning(workspaceId: IdType): Boolean {
        return activeSessions[workspaceId]?.isRunning == true
    }

    fun addLogEntry(workspaceId: IdType, entry: AgentLogEntry) {
        getOrCreateSession(workspaceId).addLogEntry(entry)
    }

    fun stopAgent(workspaceId: IdType) {
        val session = activeSessions[workspaceId] ?: return
        if (session.isRunning) {
            session.stop("Cancelled by user")
        }
    }

    @Synchronized
    fun clearSession(workspaceId: IdType) {
        activeSessions[workspaceId]?.let { session ->
            if (session.isRunning) {
                session.stop("Session cleared")
            }
            session.clearLog()
        }
        activeSessions.remove(workspaceId)
    }

    fun getLogEntries(workspaceId: IdType): List<AgentLogEntry> {
        return activeSessions[workspaceId]?.logEntries ?: emptyList()
    }

    /**
     * Main entry point: builds context, checks cache, executes via AgentExecutor,
     * saves response to AI Documents, and opens result in a window.
     */
    suspend fun executePrompt(
        prompt: AgentPrompt,
        selection: Selection,
        targetWindowId: IdType? = null,
        additionalInstructions: String? = null,
        previousResponse: String? = null,
        skipCache: Boolean = false,
        userSpecification: String? = null,
        modelOverrideId: IdType? = null
    ) {
        ensureInitialized()
        val workspaceId = windowControl.windowRepository.id

        // Build AgentContext and CacheableContext
        val context = buildAgentContext(prompt, selection,
            additionalInstructions = additionalInstructions,
            previousResponse = previousResponse,
            userSpecification = userSpecification
        )
        val cacheableContext = CacheableContext.fromAgentContext(context)

        // Check cache (skipped during regeneration)
        if (!skipCache) {
            val cached = findCachedPage(prompt, cacheableContext)
            if (cached != null) {
                Log.i(TAG, "Cache hit for prompt ${prompt.id}: opening ${cached.pageKey}")
                // Open cached document directly
                openMyDocumentResult(MyDocumentBookManager.AI_DOCUMENTS_INITIALS, cached.pageKey, targetWindowId)
                return
            }
        }

        // Start session (prevent concurrent runs)
        val session = getOrCreateSession(workspaceId)
        if (session.isRunning) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    BibleApplication.application,
                    R.string.agent_already_running,
                    Toast.LENGTH_SHORT
                ).show()
            }
            return
        }
        session.start(context)

        // Track write tools usage
        val usedWriteToolsTracker = AtomicBoolean(false)

        // Execute via AgentExecutor
        val effectiveMaxIterations = prompt.maxIterations ?: CommonUtils.aiSettings.maxIterations
        val executor = AgentExecutor(maxIterations = effectiveMaxIterations)
        try {
            executor.execute(prompt, context, session.rawLlmLog, modelOverrideId = modelOverrideId).collect { event ->
                handleAgentEvent(event, session, prompt, context, cacheableContext, usedWriteToolsTracker, targetWindowId)
            }
        } catch (e: CancellationException) {
            // Flow collection may terminate before AgentEvent.Cancelled is collected.
            // Ensure the session is properly stopped so the UI reflects cancellation.
            if (session.isRunning) {
                val app = BibleApplication.application
                session.addLogEntry(AgentLogEntry.info(app.getString(R.string.agent_log_cancelled)))
                session.stop()
            }
            throw e
        }
    }

    /**
     * Find a cached page for the given prompt and context.
     *
     * Uses strict or loose matching based on prompt's strictContextMatching setting:
     * - strict (true): Matches full context hash (Bible version, selected text, etc.)
     * - loose (false): Matches only KJVA verse ordinals (cross-version)
     */
    private fun findCachedPage(
        prompt: AgentPrompt,
        cacheableContext: CacheableContext
    ): AiCachedPageWithContent? {
        val dao = DatabaseContainer.instance.myDocumentDb.myDocumentDao()

        return if (prompt.strictContextMatching) {
            // Strict: match full context
            val contextHash = cacheableContext.computeHash()
            dao.findCachedPageByContextHash(prompt.id, contextHash)
        } else {
            // Loose: match only verse ordinals
            val start = cacheableContext.kjvOrdinalStart
            val end = cacheableContext.kjvOrdinalEnd
            if (start != null && end != null) {
                dao.findCachedPageByVerseRange(prompt.id, start, end)
            } else null
        }
    }

    /** Intermediate result from content extraction (whole-page or selection mode). */
    private data class SelectionContent(
        val verseRange: VerseRange?,
        val selectedText: String,
        val osisContent: String?
    )

    /**
     * Coerce a JSword [Key] into a [VerseRange] when possible.
     *
     * `Book.getKey(osisRef)` on a Bible returns a [RangedPassage] for chapter/range
     * references — it is neither a [VerseRange] nor a [Verse], so a direct cast
     * fails and AI doc markers end up stored without KJVA ordinals (which makes
     * them invisible in BibleView).
     */
    internal fun keyToVerseRange(key: Key?): VerseRange? = when (key) {
        null -> null
        is VerseRange -> key
        is Verse -> VerseRange(key.versification, key, key)
        is RangedPassage -> if (!key.isEmpty) key.toVerseRange else null
        else -> null
    }

    /** Extract content for whole-page mode (ordinals are -1, Bible document). */
    private fun extractWholePageContent(book: SwordBook, selection: Selection, pageKey: Key?): SelectionContent {
        val keyToUse = selection.osisRef?.let {
            try { book.getKey(it) } catch (e: Exception) {
                Log.w(TAG, "Could not parse osisRef: $it", e)
                null
            }
        } ?: pageKey

        val verseRange = keyToVerseRange(keyToUse)

        val selectedText = if (keyToUse != null) {
            try { SwordContentFacade.getCanonicalText(book, keyToUse, false) }
            catch (e: Exception) { Log.w(TAG, "Could not get canonical text", e); "" }
        } else ""

        val osisContent = if (keyToUse != null) {
            try {
                val fragment = SwordContentFacade.readOsisFragment(book, keyToUse)
                XMLOutputter(Format.getRawFormat()).outputString(fragment)
            } catch (e: Exception) { Log.w(TAG, "Could not get OSIS content", e); null }
        } else null

        return SelectionContent(verseRange, selectedText, osisContent)
    }

    /** Extract content for ordinal-based selection mode. */
    private fun extractSelectionContent(book: Book?, selection: Selection, pageKey: Key?): SelectionContent {
        val ordinalRange = selection.startOrdinal..selection.endOrdinal

        val verseRange = if (book is SwordBook && book.bookCategory == BookCategory.BIBLE) {
            try {
                val v11n = book.versification
                VerseRange(v11n, Verse(v11n, selection.startOrdinal), Verse(v11n, selection.endOrdinal))
            } catch (e: Exception) { Log.w(TAG, "Could not create VerseRange", e); null }
        } else null

        val selectedText = if (book != null && verseRange != null) {
            SwordContentFacade.getTextWithinOrdinalsAsString(book, verseRange, ordinalRange).joinToString(" ")
        } else ""

        val osisContent = if (book != null) {
            try {
                val keyForOsis = verseRange ?: pageKey
                if (keyForOsis != null) {
                    val fragment = SwordContentFacade.readOsisFragment(book, keyForOsis)
                    XMLOutputter(Format.getRawFormat()).outputString(fragment)
                } else null
            } catch (e: Exception) { Log.w(TAG, "Could not get OSIS content", e); null }
        } else null

        return SelectionContent(verseRange, selectedText, osisContent)
    }

    private suspend fun buildAgentContext(
        prompt: AgentPrompt,
        selection: Selection,
        additionalInstructions: String? = null,
        previousResponse: String? = null,
        userSpecification: String? = null
    ): AgentContext {
        val isBuiltIn = BuiltInPrompts.isBuiltIn(prompt.id)

        // Workspace-level prompt: no specific verse/document selected
        if (selection.bookInitials == null && selection.startOrdinal < 0
            && selection.noteEditorEntityType == null) {
            val workspaceSummary = withContext(Dispatchers.Main.immediate) {
                buildWorkspaceWindowsSummary()
            }
            return AgentContext(
                promptId = prompt.id,
                workspaceId = windowControl.windowRepository.id,
                windowId = windowControl.activeWindow.id,
                promptPermissionMode = prompt.permissionMode,
                promptAvailableTools = prompt.allowedTools,
                promptAllowedTools = if (isBuiltIn) null else prompt.allowedTools,
                promptDeniedTools = prompt.deniedTools,
                noDocumentCreation = prompt.noDocumentCreation,
                previousResponse = previousResponse,
                additionalInstructions = additionalInstructions,
                userSpecification = userSpecification,
                workspaceWindowsSummary = workspaceSummary,
            )
        }

        val book = selection.bookInitials?.let { Books.installed().getBook(it) }
        val pageKey = windowControl.activeWindowPageManager.currentPage.key
        val isWholePageMode = selection.startOrdinal < 0

        val content = if (isWholePageMode && book is SwordBook && book.bookCategory == BookCategory.BIBLE) {
            extractWholePageContent(book, selection, pageKey)
        } else {
            extractSelectionContent(book, selection, pageKey)
        }

        val highlightedText = selection.text.takeIf { it.isNotBlank() }

        return AgentContext(
            promptId = prompt.id,
            workspaceId = windowControl.windowRepository.id,
            selectedVerseRange = content.verseRange,
            selectedContent = content.osisContent,
            activeDocumentInitials = selection.bookInitials,
            windowId = windowControl.activeWindow.id,
            selectedText = content.selectedText,
            highlightedText = highlightedText,
            selectionStartOffset = if (highlightedText != null) selection.startOffset else null,
            selectionEndOffset = if (highlightedText != null) selection.endOffset else null,
            promptPermissionMode = prompt.permissionMode,
            promptAvailableTools = prompt.allowedTools,
            promptAllowedTools = if (isBuiltIn) null else prompt.allowedTools,
            promptDeniedTools = prompt.deniedTools,
            noDocumentCreation = prompt.noDocumentCreation,
            previousResponse = previousResponse,
            additionalInstructions = additionalInstructions,
            userSpecification = userSpecification,
            noteEditorEntityType = selection.noteEditorEntityType,
            noteEditorEntityId = selection.noteEditorEntityId,
            noteEditorContent = selection.noteEditorContent,
            noteEditorContentType = selection.noteEditorContentType,
            selectionStartOrdinal = if (book is SwordBook && book.bookCategory != BookCategory.BIBLE)
                selection.startOrdinal.takeIf { it >= 0 } else null,
            selectionEndOrdinal = if (book is SwordBook && book.bookCategory != BookCategory.BIBLE)
                selection.endOrdinal.takeIf { it >= 0 } else null,
            sourceBookKey = selection.osisRef ?: pageKey?.osisRef,
        )
    }

    /** Build a text summary of all workspace windows for workspace-level prompt context. Must be called on Main thread. */
    private fun buildWorkspaceWindowsSummary(): String {
        val windowRepository = windowControl.windowRepository
        val activeWindowId = windowRepository.activeWindow.id
        val windows = windowRepository.windowList
            .filter { it.windowState != WindowState.CLOSED }

        val visible = windows.filter { it.windowState == WindowState.VISIBLE }
        val minimised = windows.filter { it.windowState == WindowState.MINIMISED }

        return buildString {
            append("Workspace: ${windowRepository.name}\n")
            append("Windows: ${windows.size} total (${visible.size} visible, ${minimised.size} minimised)\n\n")

            fun appendWindow(w: Window) {
                val page = w.pageManager.currentPage
                val doc = page.currentDocument
                val key = page.key
                append("- ${doc?.initials ?: "unknown"} (${doc?.name ?: "unknown"})")
                if (key != null) append(" at ${key.name}")
                if (w.id == activeWindowId) append(" [ACTIVE]")
                append("\n")
            }

            if (visible.isNotEmpty()) {
                append("Visible windows:\n")
                visible.forEach { appendWindow(it) }
            }
            if (minimised.isNotEmpty()) {
                append("\nMinimised windows:\n")
                minimised.forEach { appendWindow(it) }
            }
        }
    }

    private suspend fun handleAgentEvent(
        event: AgentEvent,
        session: AgentSession,
        prompt: AgentPrompt,
        context: AgentContext,
        cacheableContext: CacheableContext,
        usedWriteToolsTracker: AtomicBoolean,
        targetWindowId: IdType? = null
    ) {
        val app = BibleApplication.application
        when (event) {
            is AgentEvent.Started -> {
                session.addLogEntry(AgentLogEntry.info(
                    app.getString(R.string.agent_log_executing, prompt.name),
                    details = event.model
                ))
            }
            is AgentEvent.Iteration -> {
                session.addLogEntry(AgentLogEntry.info(app.getString(R.string.agent_log_iteration, event.number)))
            }
            is AgentEvent.ToolCalling -> {
                val tool = ToolRegistry.get(event.tool)
                val displayName = tool?.let { ToolRegistry.getDisplayName(it) } ?: event.tool.camelCaseName
                val details = if (tool != null) {
                    val args = try { JSONObject(event.arguments) } catch (_: Exception) { null }
                    args?.let { tool.formatArgsForLog(it) } ?: formatJsonForLog(event.arguments)
                } else {
                    event.arguments
                }
                session.addLogEntry(
                    AgentLogEntry.action(
                        app.getString(R.string.agent_log_tool, displayName),
                        details = details,
                        toolCallId = event.toolCallId
                    )
                )
            }
            is AgentEvent.ToolCompleted -> {
                val tool = ToolRegistry.get(event.tool)
                val displayName = tool?.let { ToolRegistry.getDisplayName(it) } ?: event.tool.camelCaseName
                val isSuccess = event.result is ToolResult.Success
                val status = if (isSuccess) EntryStatus.COMPLETED else EntryStatus.FAILED
                // On success, keep the original "Tool: X" message (per #3773 — only the icon needs to
                // change). On failure, switch to the failure message so the row is unambiguous.
                val message = if (isSuccess) {
                    app.getString(R.string.agent_log_tool, displayName)
                } else {
                    app.getString(R.string.agent_log_tool_failed, displayName)
                }
                val details = tool?.formatResultForLog(event.result) ?: formatJsonForLog(event.result.toJson())
                val updated = session.updateActionEntry(event.toolCallId, message, details, status)
                if (!updated) {
                    // Defensive fallback: if no ToolCalling entry exists for this toolCallId
                    // (shouldn't happen in normal flow), append a new entry as before.
                    session.addLogEntry(
                        AgentLogEntry(
                            type = LogEntryType.ACTION,
                            message = message,
                            details = details,
                            status = status
                        )
                    )
                }

                // Track write tools usage
                if (isSuccess) {
                    if (tool?.requiresPermission == true) {
                        usedWriteToolsTracker.set(true)
                    }
                }
            }
            is AgentEvent.ApiCallCompleted -> {
                // Attach cost to the most recent log entry (typically the iteration entry)
                val cost = LlmPricing.estimateCost(event.usage, event.model, event.configuredModelId)
                if (cost != null) {
                    session.addCost(cost)
                    session.setLastEntryCost(LlmCostTracker.formatCost(cost))
                }
            }
            is AgentEvent.TextResponse -> {
                if (event.isFinal) {
                    session.addLogEntry(
                        AgentLogEntry.info(app.getString(R.string.agent_log_response_received), details = event.text.take(200))
                    )
                } else {
                    val text = event.text.trim()
                    if (text.isNotBlank()) {
                        session.addLogEntry(AgentLogEntry.comment(text.take(300)))
                    }
                }
            }
            is AgentEvent.Completed -> {
                if (context.noDocumentCreation) {
                    session.addLogEntry(AgentLogEntry.info(
                        app.getString(R.string.llm_no_document_creation_intercepted),
                        details = event.response.take(500)
                    ))
                } else {
                    val (title, content) = extractTitleFromResponse(event.response, prompt.name, context.verseRefString)
                    val pageInfo = saveAndLogDocument(title, content, context, cacheableContext, usedWriteToolsTracker, event.model, session)
                    openMyDocumentResult(pageInfo.documentInitials, pageInfo.pageKey, targetWindowId, session)
                }
                completeSession(session, event, prompt)
            }
            is AgentEvent.CompletedWithDocument -> {
                val pageInfo = saveAndLogDocument(event.title, event.content, context, cacheableContext, usedWriteToolsTracker, event.model, session)
                openMyDocumentResult(pageInfo.documentInitials, pageInfo.pageKey, targetWindowId, session)
                completeSession(session, event, prompt)
            }
            is AgentEvent.CompletedWithoutDocument -> {
                session.addLogEntry(AgentLogEntry.info(app.getString(R.string.agent_log_done, event.message)))
                completeSession(session, event, prompt)
            }
            is AgentEvent.CompletedWithStudyPad -> {
                session.addLogEntry(AgentLogEntry.info(app.getString(R.string.agent_log_done, event.message)))
                openStudyPadResult(event.labelId, event.scrollToEntryId, session)
                completeSession(session, event, prompt)
            }
            is AgentEvent.CompletedWithMyDocumentPage -> {
                session.addLogEntry(AgentLogEntry.info(app.getString(R.string.agent_log_done, event.message)))
                openMyDocumentResult(event.documentInitials, event.pageKey, targetWindowId, session)
                completeSession(session, event, prompt)
            }
            is AgentEvent.Error -> {
                val hasRawLog = session.rawLlmLog?.isEmpty() == false
                session.addLogEntry(AgentLogEntry.error(event.message, details = event.cause?.message, showRawLogLink = hasRawLog))
                persistRawLogFromIterations(session, prompt)
                session.stop(reason = AgentStopReason.ERROR)
            }
            is AgentEvent.Cancelled -> {
                val hasRawLog = session.rawLlmLog?.isEmpty() == false
                session.addLogEntry(AgentLogEntry.info(app.getString(R.string.agent_log_cancelled), showRawLogLink = hasRawLog))
                persistRawLogFromIterations(session, prompt)
                session.stop()
            }
        }
    }

    /** Stops the session, attaches total cost, and persists raw log. Used by all completion event handlers. */
    private fun completeSession(session: AgentSession, event: CompletionEvent, prompt: AgentPrompt) {
        val app = BibleApplication.application
        session.stop(app.getString(R.string.agent_log_completed), AgentStopReason.COMPLETED)
        attachTotalCost(session, event.usage, event.model, event.configuredModelId)
        persistRawLog(
            session, prompt,
            model = event.model,
            providerType = resolveProviderType(event.configuredModelId),
            configuredModelId = event.configuredModelId,
            usage = event.usage,
            wasError = false
        )
    }

    private fun resolveProviderType(configuredModelId: IdType?): String {
        if (configuredModelId == null) return ""
        val db = DatabaseContainer.instance.aiSettingsDb
        val model = db.llmConfiguredModelDao().getById(configuredModelId) ?: return ""
        val provider = db.llmProviderConfigDao().getById(model.providerConfigId) ?: return ""
        return provider.providerType
    }

    private fun persistRawLog(
        session: AgentSession,
        prompt: AgentPrompt,
        model: String,
        providerType: String,
        configuredModelId: IdType?,
        usage: LlmUsage,
        wasError: Boolean
    ) {
        val rawLog = session.rawLlmLog ?: return
        if (rawLog.isEmpty()) return
        try {
            val formattedText = rawLog.format()
            val gzipped = RawLlmLog.gzipCompress(formattedText)
            val cost = LlmPricing.estimateCost(usage, model, configuredModelId) ?: 0.0

            val record = LlmRawLogRecord(
                promptId = prompt.id,
                promptName = prompt.name,
                promptDescription = prompt.description,
                configuredModelId = configuredModelId,
                modelName = model,
                providerType = providerType,
                totalInputTokens = usage.inputTokens,
                totalOutputTokens = usage.outputTokens,
                estimatedCostUsd = cost,
                logData = gzipped,
                iterationCount = rawLog.usageByIteration.size,
                wasError = wasError,
            )
            DatabaseContainer.instance.aiSettingsDb.llmRawLogRecordDao().insert(record)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist raw log", e)
        }
    }

    private fun persistRawLogFromIterations(session: AgentSession, prompt: AgentPrompt) {
        val rawLog = session.rawLlmLog ?: return
        if (rawLog.isEmpty()) return
        val lastIteration = rawLog.usageByIteration.values.lastOrNull()
        val totalUsage = rawLog.usageByIteration.values.fold(LlmUsage()) { acc, d -> acc + d.usage }
        persistRawLog(
            session, prompt,
            model = lastIteration?.model ?: "",
            providerType = resolveProviderType(lastIteration?.configuredModelId),
            configuredModelId = lastIteration?.configuredModelId,
            usage = totalUsage,
            wasError = true
        )
    }

    private fun cleanupOldRawLogs() {
        try {
            val retentionDays = CommonUtils.aiSettings.rawLogRetentionDays ?: return
            val cutoff = System.currentTimeMillis() - retentionDays.toLong() * 24 * 60 * 60 * 1000
            DatabaseContainer.instance.aiSettingsDb.llmRawLogRecordDao().deleteOlderThan(cutoff)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cleanup old raw logs", e)
        }
    }

    /** Saves an AI response document and logs it. Shared by Completed and CompletedWithDocument. */
    private fun saveAndLogDocument(
        title: String,
        content: String,
        context: AgentContext,
        cacheableContext: CacheableContext,
        usedWriteToolsTracker: AtomicBoolean,
        model: String,
        session: AgentSession
    ): MyDocumentBookManager.SavedPageInfo {
        val pageInfo = MyDocumentBookManager.saveAIResponse(
            response = content,
            title = title,
            sourcePromptId = context.promptId,
            cacheableContext = cacheableContext,
            usedWriteTools = usedWriteToolsTracker.get(),
            sourceModelName = model.takeIf { it.isNotBlank() }
        )
        session.addLogEntry(AgentLogEntry.info(
            BibleApplication.application.getString(R.string.agent_log_saved, title)
        ))
        return pageInfo
    }

    private fun attachTotalCost(session: AgentSession, usage: LlmUsage, model: String, configuredModelId: IdType? = null) {
        if (usage.totalTokens > 0) {
            val cost = LlmPricing.estimateCost(usage, model, configuredModelId)
            if (cost != null) {
                val app = BibleApplication.application
                session.setLastEntryCost(app.getString(R.string.llm_cost_total, LlmCostTracker.formatCost(cost)), isTotalCost = true)
            }
        }
    }

    /** Extracts markdown H1 heading as title, falls back to prompt name + verse ref. */
    private fun extractTitleFromResponse(
        response: String,
        fallbackPromptName: String,
        fallbackContext: String?
    ): Pair<String, String> {
        // Look for markdown H1 at the start: # Title
        val h1Regex = Regex("^\\s*#\\s+(.+?)\\s*(?:\\n|$)", RegexOption.MULTILINE)
        val match = h1Regex.find(response)

        return if (match != null) {
            val title = stripMarkdownFromTitle(match.groupValues[1].trim()).take(80)
            // Keep the content as-is (including the heading for display)
            Pair(title, response)
        } else {
            // Fallback: use prompt name + context
            val fallbackTitle = buildString {
                append(fallbackPromptName)
                fallbackContext?.let { append(": $it") }
            }.take(80)
            Pair(fallbackTitle, response)
        }
    }

    private suspend fun openMyDocumentResult(documentInitials: String, pageKey: String, targetWindowId: IdType?, session: AgentSession? = null) {
        if (CurrentActivityHolder.currentActivity == null) {
            // App is backgrounded — defer opening until user returns
            session?.pendingResult = PendingAgentResult.OpenDocument(documentInitials, pageKey, targetWindowId)
            return
        }
        if (targetWindowId != null) {
            val window = windowControl.windowRepository.getWindow(targetWindowId)
            if (window != null) {
                val book = Books.installed().getBook(documentInitials)
                val key = try { book?.getKey(pageKey) } catch (e: Exception) { null }
                if (book != null && key != null) {
                    withContext(Dispatchers.Main) {
                        window.pageManager.setCurrentDocumentAndKey(book, key)
                    }
                    return
                }
            }
        }
        withContext(Dispatchers.Main) {
            linkControl.openAIDocument(documentInitials, pageKey)
        }
    }

    private suspend fun openStudyPadResult(labelId: IdType, scrollToEntryId: IdType?, session: AgentSession? = null) {
        if (CurrentActivityHolder.currentActivity == null) {
            session?.pendingResult = PendingAgentResult.OpenStudyPad(labelId, scrollToEntryId)
            return
        }
        withContext(Dispatchers.Main) {
            linkControl.openStudyPad(labelId, scrollToEntryId)
        }
    }

    suspend fun regenerateAIDocument(
        pageId: IdType,
        targetWindowId: IdType? = null,
        additionalInstructions: String? = null,
        keepPrevious: Boolean = false,
        freshRun: Boolean = false,
        modelOverrideId: IdType? = null
    ): Boolean {
        ensureInitialized()
        val workspaceId = windowControl.windowRepository.id
        val session = activeSessions[workspaceId]
        if (session?.isRunning == true) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    BibleApplication.application,
                    R.string.agent_already_running,
                    Toast.LENGTH_SHORT
                ).show()
            }
            return false
        }
        val page = MyDocumentBookManager.getAIDocumentPage(pageId)
        if (page == null) {
            Log.w(TAG, "Cannot regenerate: page not found: $pageId")
            return false
        }

        val promptId = page.sourcePromptId
        if (promptId == null) {
            Log.w(TAG, "Cannot regenerate: no sourcePromptId")
            return false
        }

        // Get the prompt
        val prompt = PromptRepository.promptById(promptId)
        if (prompt == null) {
            Log.w(TAG, "Cannot regenerate: prompt not found: $promptId")
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    BibleApplication.application,
                    R.string.ai_regenerate_prompt_not_found,
                    Toast.LENGTH_LONG
                ).show()
            }
            return false
        }

        // Get cache entry for regeneration context
        val dao = DatabaseContainer.instance.myDocumentDb.myDocumentDao()
        val cacheEntry = dao.getCacheEntry(pageId)
        if (cacheEntry == null) {
            Log.w(TAG, "Cannot regenerate: no cache entry for page: $pageId")
            return false
        }

        // Read previous response before potentially deleting the page
        val previousContent = dao.getContent(pageId)

        val kjvOrdinalStart = cacheEntry.kjvOrdinalStart
        val kjvOrdinalEnd = cacheEntry.kjvOrdinalEnd

        // Parse stored context to get book initials
        val storedContext = cacheEntry.sourceContext?.let {
            try {
                decodeFromString<CacheableContext>(it)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse stored context", e)
                null
            }
        }

        val bookInitials = storedContext?.activeDocumentInitials
        val book = bookInitials?.let { Books.installed().getBook(it) } as? SwordBook

        // Create selection for regeneration
        val selection: Selection
        if (kjvOrdinalStart != null && kjvOrdinalEnd != null && book != null) {
            // Convert KJVA ordinals to target versification
            val targetVersification = book.versification

            val kjvaStart = Verse(KJVA, kjvOrdinalStart)
            val kjvaEnd = Verse(KJVA, kjvOrdinalEnd)

            val targetStart = kjvaStart.toV11n(targetVersification)
            val targetEnd = kjvaEnd.toV11n(targetVersification)

            selection = Selection(
                bookInitials = bookInitials,
                startOrdinal = targetStart.ordinal,
                startOffset = null,
                endOrdinal = targetEnd.ordinal,
                endOffset = null,
                bookmarks = emptyList(),
                notes = null,
                text = storedContext.selectedText ?: ""
            )
        } else {
            Log.w(TAG, "Cannot regenerate: missing ordinal data")
            return false
        }

        if (!keepPrevious) {
            MyDocumentBookManager.deleteAIDocumentPage(pageId)
        }
        executePrompt(
            prompt, selection,
            targetWindowId = targetWindowId,
            additionalInstructions = additionalInstructions,
            previousResponse = if (freshRun) null else previousContent,
            skipCache = true,
            modelOverrideId = modelOverrideId
        )
        return true
    }

    fun getCurrentSession(): AgentSession? {
        ensureInitialized()
        return try {
            val workspaceId = windowControl.windowRepository.id
            getOrCreateSession(workspaceId)
        } catch (e: Exception) {
            null  // No active workspace (e.g., background processing)
        }
    }

    private const val TAG = "AgentSessionManager"
}
