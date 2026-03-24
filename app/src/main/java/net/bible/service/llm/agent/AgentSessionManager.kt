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
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.view.activity.page.Selection
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.AgentPrompt
import net.bible.service.llm.BuiltInPrompts
import net.bible.service.llm.LlmCostTracker
import net.bible.service.llm.LlmPricing
import net.bible.service.llm.LlmUsage
import net.bible.service.llm.PromptRepository
import net.bible.service.llm.tools.ToolRegistry
import org.json.JSONObject
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.formatJsonForLog
import net.bible.service.llm.tools.stripMarkdownFromTitle
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.mydocument.MyDocumentBookManager
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import android.widget.Toast
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

class AgentSessionStatusChangedEvent(
    val workspaceId: IdType,
    val isRunning: Boolean
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
    val logEntries: List<AgentLogEntry> get() = _logEntries.toList()

    /** Raw LLM conversation log for debug inspection. */
    var rawLlmLog: RawLlmLog? = null
        private set

    @Volatile
    var isRunning: Boolean = false
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
        _logEntries.clear()
        rawLlmLog = RawLlmLog()
        addLogEntry(AgentLogEntry.info("Agent started"))
        ABEventBus.post(AgentSessionStatusChangedEvent(workspaceId, true))
    }

    fun stop(message: String? = null) {
        if (message != null) {
            val hasRawLog = rawLlmLog?.isEmpty() == false
            addLogEntry(AgentLogEntry.info(message, showRawLogLink = hasRawLog))
        }
        this.isRunning = false
        this.job?.cancel()
        this.job = null
        ABEventBus.post(AgentSessionStatusChangedEvent(workspaceId, false))
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
        userSpecification: String? = null
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
            executor.execute(prompt, context, session.rawLlmLog).collect { event ->
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
    ): net.bible.android.database.mydocument.AiCachedPageWithContent? {
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

    private suspend fun buildAgentContext(
        prompt: AgentPrompt,
        selection: Selection,
        additionalInstructions: String? = null,
        previousResponse: String? = null,
        userSpecification: String? = null
    ): AgentContext {
        val book = selection.bookInitials?.let { Books.installed().getBook(it) }
        val currentPage = windowControl.activeWindowPageManager.currentPage
        val pageKey = currentPage.key

        // Check if this is "whole page" mode (ordinals are -1)
        val isWholePageMode = selection.startOrdinal < 0

        // For whole page mode, use osisRef to get the key; otherwise use ordinals
        val verseRange: VerseRange?
        val selectedText: String
        val osisContent: String?

        if (isWholePageMode && book is SwordBook && book.bookCategory == BookCategory.BIBLE) {
            // Whole page mode: use osisRef or pageKey
            val keyToUse = selection.osisRef?.let {
                try {
                    book.getKey(it)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not parse osisRef: $it", e)
                    null
                }
            } ?: pageKey

            verseRange = keyToUse as? VerseRange ?: (keyToUse as? Verse)?.let { VerseRange(it.versification, it, it) }

            // Get all text from the page
            selectedText = if (book != null && keyToUse != null) {
                try {
                    SwordContentFacade.getCanonicalText(book, keyToUse, false)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not get canonical text", e)
                    ""
                }
            } else ""

            // Get OSIS content for the whole page
            osisContent = if (keyToUse != null) {
                try {
                    val fragment = SwordContentFacade.readOsisFragment(book, keyToUse)
                    XMLOutputter(Format.getRawFormat()).outputString(fragment)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not get OSIS content", e)
                    null
                }
            } else null
        } else {
            // Selection mode: use ordinals
            val ordinalRange = selection.startOrdinal..selection.endOrdinal

            // Create VerseRange if Bible book
            verseRange = if (book is SwordBook && book.bookCategory == BookCategory.BIBLE) {
                try {
                    val v11n = book.versification
                    val startVerse = Verse(v11n, selection.startOrdinal)
                    val endVerse = Verse(v11n, selection.endOrdinal)
                    VerseRange(v11n, startVerse, endVerse)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not create VerseRange", e)
                    null
                }
            } else null

            // Get selected text using verseRange (not pageKey, which may be from a different document)
            selectedText = if (book != null && verseRange != null) {
                SwordContentFacade.getTextWithinOrdinalsAsString(book, verseRange, ordinalRange).joinToString(" ")
            } else ""

            // Get OSIS XML content for the selected verses
            osisContent = if (book != null) {
                try {
                    val keyForOsis = verseRange ?: pageKey
                    if (keyForOsis != null) {
                        val fragment = SwordContentFacade.readOsisFragment(book, keyForOsis)
                        XMLOutputter(Format.getRawFormat()).outputString(fragment)
                    } else null
                } catch (e: Exception) {
                    Log.w(TAG, "Could not get OSIS content", e)
                    null
                }
            } else null
        }

        // Get highlighted text (specific words selected by user) if available
        val highlightedText = selection.text.takeIf { it.isNotBlank() }

        val isBuiltIn = BuiltInPrompts.isBuiltIn(prompt.id)

        return AgentContext(
            promptId = prompt.id,
            workspaceId = windowControl.windowRepository.id,
            selectedVerseRange = verseRange,
            selectedContent = osisContent,
            activeDocumentInitials = selection.bookInitials,
            windowId = windowControl.activeWindow.id,
            selectedText = selectedText,
            highlightedText = highlightedText,
            selectionStartOffset = if (highlightedText != null) selection.startOffset else null,
            selectionEndOffset = if (highlightedText != null) selection.endOffset else null,
            promptPermissionMode = prompt.permissionMode,
            promptAvailableTools = prompt.allowedTools,
            // Built-in prompts: no permission auto-allow — rely on permissionMode instead
            promptAllowedTools = if (isBuiltIn) null else prompt.allowedTools,
            promptDeniedTools = prompt.deniedTools,
            noDocumentCreation = prompt.noDocumentCreation,
            previousResponse = previousResponse,
            additionalInstructions = additionalInstructions,
            userSpecification = userSpecification,
            noteEditorEntityType = selection.noteEditorEntityType,
            noteEditorEntityId = selection.noteEditorEntityId,
            noteEditorContent = selection.noteEditorContent,
            noteEditorContentType = selection.noteEditorContentType
        )
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
                    AgentLogEntry.action(app.getString(R.string.agent_log_tool, displayName), details = details)
                )
            }
            is AgentEvent.ToolCompleted -> {
                val tool = ToolRegistry.get(event.tool)
                val displayName = tool?.let { ToolRegistry.getDisplayName(it) } ?: event.tool.camelCaseName
                val isSuccess = event.result is ToolResult.Success
                val status = if (isSuccess) EntryStatus.COMPLETED else EntryStatus.FAILED
                val message = if (isSuccess) {
                    app.getString(R.string.agent_log_tool_completed, displayName)
                } else {
                    app.getString(R.string.agent_log_tool_failed, displayName)
                }
                val details = tool?.formatResultForLog(event.result) ?: formatJsonForLog(event.result.toJson())
                session.addLogEntry(
                    AgentLogEntry(
                        type = LogEntryType.ACTION,
                        message = message,
                        details = details,
                        status = status
                    )
                )

                // Track write tools usage
                if (isSuccess) {
                    if (tool?.requiresPermission == true) {
                        usedWriteToolsTracker.set(true)
                    }
                }
            }
            is AgentEvent.ApiCallCompleted -> {
                // Attach cost to the most recent log entry (typically the iteration entry)
                val cost = LlmPricing.estimateCost(event.usage, event.model)
                if (cost != null) {
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
                    // No document creation — just log the response
                    session.addLogEntry(AgentLogEntry.info(
                        app.getString(R.string.llm_no_document_creation_intercepted),
                        details = event.response.take(500)
                    ))
                    session.stop(app.getString(R.string.agent_log_completed))
                    attachTotalCost(session, event.usage, event.model)
                } else {
                    // Extract title from response (first markdown H1 heading)
                    val (title, content) = extractTitleFromResponse(event.response, prompt.name, context.verseRefString)

                    // Save to AI Documents
                    val pageInfo = MyDocumentBookManager.saveAIResponse(
                        response = content,
                        title = title,
                        sourcePromptId = context.promptId,
                        cacheableContext = cacheableContext,
                        usedWriteTools = usedWriteToolsTracker.get(),
                        sourceModelName = event.model.takeIf { it.isNotBlank() }
                    )

                    session.addLogEntry(AgentLogEntry.info(app.getString(R.string.agent_log_saved, title)))

                    // Open the page in target window or linked window
                    openMyDocumentResult(pageInfo.documentInitials, pageInfo.pageKey, targetWindowId, session)

                    session.stop(app.getString(R.string.agent_log_completed))
                    attachTotalCost(session, event.usage, event.model)
                }
            }
            is AgentEvent.CompletedWithDocument -> {
                // LLM explicitly provided title and content via setDocumentTitle tool
                val pageInfo = MyDocumentBookManager.saveAIResponse(
                    response = event.content,
                    title = event.title,
                    sourcePromptId = context.promptId,
                    cacheableContext = cacheableContext,
                    usedWriteTools = usedWriteToolsTracker.get(),
                    sourceModelName = event.model.takeIf { it.isNotBlank() }
                )

                session.addLogEntry(AgentLogEntry.info(app.getString(R.string.agent_log_saved, event.title)))

                // Open the page in target window or linked window
                openMyDocumentResult(pageInfo.documentInitials, pageInfo.pageKey, targetWindowId, session)

                session.stop(app.getString(R.string.agent_log_completed))
                attachTotalCost(session, event.usage, event.model)
            }
            is AgentEvent.CompletedWithoutDocument -> {
                // Task completed without creating a document (e.g., just created a bookmark)
                session.addLogEntry(AgentLogEntry.info(app.getString(R.string.agent_log_done, event.message)))
                session.stop(app.getString(R.string.agent_log_completed))
                attachTotalCost(session, event.usage, event.model)
            }
            is AgentEvent.CompletedWithStudyPad -> {
                session.addLogEntry(AgentLogEntry.info(app.getString(R.string.agent_log_done, event.message)))
                openStudyPadResult(event.labelId, event.scrollToEntryId, session)
                session.stop(app.getString(R.string.agent_log_completed))
                attachTotalCost(session, event.usage, event.model)
            }
            is AgentEvent.CompletedWithMyDocumentPage -> {
                session.addLogEntry(AgentLogEntry.info(app.getString(R.string.agent_log_done, event.message)))
                openMyDocumentResult(event.documentInitials, event.pageKey, targetWindowId, session)
                session.stop(app.getString(R.string.agent_log_completed))
                attachTotalCost(session, event.usage, event.model)
            }
            is AgentEvent.Error -> {
                val hasRawLog = session.rawLlmLog?.isEmpty() == false
                session.addLogEntry(AgentLogEntry.error(event.message, details = event.cause?.message, showRawLogLink = hasRawLog))
                session.stop()
            }
            is AgentEvent.Cancelled -> {
                val hasRawLog = session.rawLlmLog?.isEmpty() == false
                session.addLogEntry(AgentLogEntry.info(app.getString(R.string.agent_log_cancelled), showRawLogLink = hasRawLog))
                session.stop()
            }
        }
    }

    private fun attachTotalCost(session: AgentSession, usage: LlmUsage, model: String) {
        if (usage.totalTokens > 0) {
            val cost = LlmPricing.estimateCost(usage, model)
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
        keepPrevious: Boolean = false
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
            previousResponse = previousContent,
            skipCache = true
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
