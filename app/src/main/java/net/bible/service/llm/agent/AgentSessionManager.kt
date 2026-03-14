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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Base class for AgentSessionManager with injected dependencies.
 */
open class AgentSessionManagerBase {
    @Inject lateinit var windowControl: WindowControl
    @Inject lateinit var linkControl: LinkControl
}

/**
 * Event posted when the agent log is updated.
 *
 * @param workspaceId ID of the workspace where the log was updated
 * @param entry The log entry that was added or updated
 */
class AgentLogUpdatedEvent(
    val workspaceId: IdType,
    val entry: AgentLogEntry
)

/**
 * Event posted when an agent session's status changes.
 *
 * @param workspaceId ID of the workspace
 * @param isRunning Whether the agent is currently running
 */
class AgentSessionStatusChangedEvent(
    val workspaceId: IdType,
    val isRunning: Boolean
)

/**
 * Represents an active agent session for a workspace.
 *
 * Each workspace can have one active agent session at a time.
 * The session maintains the log entries and execution state.
 *
 * @param workspaceId ID of the workspace this session belongs to
 */
class AgentSession(val workspaceId: IdType) {
    /** Log entries for this session, thread-safe for concurrent access */
    private val _logEntries = CopyOnWriteArrayList<AgentLogEntry>()

    /** Read-only view of log entries */
    val logEntries: List<AgentLogEntry> get() = _logEntries.toList()

    /** Whether an agent is currently executing */
    @Volatile
    var isRunning: Boolean = false
        private set

    /** Current context of the running agent */
    var context: AgentContext? = null
        private set

    /** Coroutine Job for the running agent, used for cancellation */
    var job: Job? = null

    /**
     * Start the agent session with the given context.
     */
    fun start(context: AgentContext) {
        this.context = context
        this.isRunning = true
        _logEntries.clear()
        addLogEntry(AgentLogEntry.info("Agent started"))
        ABEventBus.post(AgentSessionStatusChangedEvent(workspaceId, true))
    }

    /**
     * Stop the agent session.
     */
    fun stop(message: String? = null) {
        if (message != null) {
            addLogEntry(AgentLogEntry.info(message))
        }
        this.isRunning = false
        this.job?.cancel()
        this.job = null
        ABEventBus.post(AgentSessionStatusChangedEvent(workspaceId, false))
    }

    /**
     * Add a log entry to this session.
     */
    fun addLogEntry(entry: AgentLogEntry) {
        _logEntries.add(entry)
        ABEventBus.post(AgentLogUpdatedEvent(workspaceId, entry))
    }

    /**
     * Update the status of an existing log entry.
     */
    fun updateEntryStatus(entryId: IdType, newStatus: EntryStatus) {
        val entry = _logEntries.find { it.id == entryId }
        if (entry != null) {
            entry.status = newStatus
            ABEventBus.post(AgentLogUpdatedEvent(workspaceId, entry))
        }
    }

    /**
     * Set cost info on the most recent log entry and notify UI.
     */
    fun setLastEntryCost(costInfo: String, isTotalCost: Boolean = false) {
        val entry = _logEntries.lastOrNull() ?: return
        entry.costInfo = costInfo
        entry.isTotalCost = isTotalCost
        ABEventBus.post(AgentLogUpdatedEvent(workspaceId, entry))
    }

    /**
     * Clear all log entries.
     */
    fun clearLog() {
        _logEntries.clear()
    }
}

/**
 * Singleton manager for agent sessions.
 *
 * Maintains one agent session per workspace. Sessions are created lazily
 * when first accessed and persist for the lifetime of the workspace.
 * Log entries are workspace-specific, meaning different workspaces have
 * independent agent logs.
 */
object AgentSessionManager : AgentSessionManagerBase() {
    /** Active sessions, keyed by workspace ID */
    private val activeSessions = ConcurrentHashMap<IdType, AgentSession>()

    private var initialized = false

    /**
     * Ensure dependencies are injected. Called lazily before first use.
     */
    @Synchronized
    private fun ensureInitialized() {
        if (!initialized) {
            CommonUtils.buildActivityComponent().inject(this)
            initialized = true
        }
    }

    /**
     * Get or create an agent session for the given workspace.
     *
     * @param workspaceId ID of the workspace
     * @return The session for this workspace
     */
    @Synchronized
    fun getOrCreateSession(workspaceId: IdType): AgentSession {
        return activeSessions.getOrPut(workspaceId) { AgentSession(workspaceId) }
    }

    /**
     * Get the session for a workspace, if it exists.
     *
     * @param workspaceId ID of the workspace
     * @return The session, or null if none exists
     */
    @Synchronized
    fun getSession(workspaceId: IdType): AgentSession? {
        return activeSessions[workspaceId]
    }

    /**
     * Check if an agent is running in the given workspace.
     *
     * @param workspaceId ID of the workspace
     * @return True if an agent is running
     */
    fun isRunning(workspaceId: IdType): Boolean {
        return activeSessions[workspaceId]?.isRunning == true
    }

    /**
     * Add a log entry to the specified workspace's session.
     *
     * Creates the session if it doesn't exist.
     *
     * @param workspaceId ID of the workspace
     * @param entry The log entry to add
     */
    fun addLogEntry(workspaceId: IdType, entry: AgentLogEntry) {
        getOrCreateSession(workspaceId).addLogEntry(entry)
    }

    /**
     * Stop the running agent for a workspace.
     *
     * Cancels the coroutine job and stops the session.
     *
     * @param workspaceId ID of the workspace
     */
    fun stopAgent(workspaceId: IdType) {
        val session = activeSessions[workspaceId] ?: return
        if (session.isRunning) {
            session.stop("Cancelled by user")
        }
    }

    /**
     * Clear the session for a workspace.
     *
     * This stops any running agent and clears the log.
     *
     * @param workspaceId ID of the workspace
     */
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

    /**
     * Get log entries for a workspace.
     *
     * @param workspaceId ID of the workspace
     * @return List of log entries, or empty list if no session exists
     */
    fun getLogEntries(workspaceId: IdType): List<AgentLogEntry> {
        return activeSessions[workspaceId]?.logEntries ?: emptyList()
    }

    /**
     * Execute an LLM prompt with the given selection.
     *
     * This is the main entry point for running LLM prompts. It:
     * 1. Builds the AgentContext from the selection
     * 2. Checks cache for existing result
     * 3. If cached, opens the cached document directly
     * 4. If not cached, executes the prompt via AgentExecutor
     * 5. Saves the response to AI Documents
     * 6. Opens the saved page in a linked window
     *
     * @param prompt The AgentPrompt to execute
     * @param selection The user's selection (verses, text, etc.)
     */
    suspend fun executePrompt(
        prompt: AgentPrompt,
        selection: Selection,
        targetWindowId: IdType? = null
    ) {
        ensureInitialized()
        val workspaceId = windowControl.windowRepository.id

        // Build AgentContext and CacheableContext
        val context = buildAgentContext(prompt, selection)
        val cacheableContext = CacheableContext.fromAgentContext(context)

        // Check cache
        val cached = findCachedPage(prompt, cacheableContext)
        if (cached != null) {
            Log.i(TAG, "Cache hit for prompt ${prompt.id}: opening ${cached.pageKey}")
            // Open cached document directly
            openAIDocumentResult(MyDocumentBookManager.AI_DOCUMENTS_INITIALS, cached.pageKey, targetWindowId)
            return
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
        val executor = AgentExecutor()
        try {
            executor.execute(prompt.id, context).collect { event ->
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
        selection: Selection
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

        return AgentContext(
            promptId = prompt.id,
            selectedVerseRange = verseRange,
            selectedContent = osisContent,
            activeDocumentInitials = selection.bookInitials,
            windowId = windowControl.activeWindow.id,
            selectedText = selectedText,
            highlightedText = highlightedText,
            promptPermissionMode = prompt.permissionMode,
            promptAllowedTools = prompt.allowedTools,
            promptDeniedTools = prompt.deniedTools
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
                session.addLogEntry(AgentLogEntry.info(app.getString(R.string.agent_log_executing, prompt.name)))
            }
            is AgentEvent.Iteration -> {
                session.addLogEntry(AgentLogEntry.info(app.getString(R.string.agent_log_iteration, event.number)))
            }
            is AgentEvent.ToolCalling -> {
                val tool = ToolRegistry.get(event.toolName)
                val displayName = tool?.let { ToolRegistry.getDisplayName(it) } ?: event.toolName
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
                val tool = ToolRegistry.get(event.toolName)
                val displayName = tool?.let { ToolRegistry.getDisplayName(it) } ?: event.toolName
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
                }
            }
            is AgentEvent.Completed -> {
                // Extract title from response (first markdown H1 heading)
                val (title, content) = extractTitleFromResponse(event.response, prompt.name, context.verseRefString)

                // Save to AI Documents
                val pageInfo = MyDocumentBookManager.saveAIResponse(
                    response = content,
                    title = title,
                    sourcePromptId = context.promptId,
                    cacheableContext = cacheableContext,
                    usedWriteTools = usedWriteToolsTracker.get()
                )

                session.addLogEntry(AgentLogEntry.info(app.getString(R.string.agent_log_saved, title)))

                // Open the page in target window or linked window
                openAIDocumentResult(pageInfo.documentInitials, pageInfo.pageKey, targetWindowId)

                session.stop(app.getString(R.string.agent_log_completed))
                attachTotalCost(session, event.usage, event.model)
            }
            is AgentEvent.CompletedWithDocument -> {
                // LLM explicitly provided title and content via setDocumentTitle tool
                val pageInfo = MyDocumentBookManager.saveAIResponse(
                    response = event.content,
                    title = event.title,
                    sourcePromptId = context.promptId,
                    cacheableContext = cacheableContext,
                    usedWriteTools = usedWriteToolsTracker.get()
                )

                session.addLogEntry(AgentLogEntry.info(app.getString(R.string.agent_log_saved, event.title)))

                // Open the page in target window or linked window
                openAIDocumentResult(pageInfo.documentInitials, pageInfo.pageKey, targetWindowId)

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
                linkControl.openStudyPad(event.labelId, event.scrollToEntryId)
                session.stop(app.getString(R.string.agent_log_completed))
                attachTotalCost(session, event.usage, event.model)
            }
            is AgentEvent.Error -> {
                session.addLogEntry(AgentLogEntry.error(event.message, details = event.cause?.message))
                session.stop()
            }
            is AgentEvent.Cancelled -> {
                session.addLogEntry(AgentLogEntry.info(app.getString(R.string.agent_log_cancelled)))
                session.stop()
            }
        }
    }

    /**
     * Attach session-total cost to the last log entry (the completion/stop entry).
     */
    private fun attachTotalCost(session: AgentSession, usage: LlmUsage, model: String) {
        if (usage.totalTokens > 0) {
            val cost = LlmPricing.estimateCost(usage, model)
            if (cost != null) {
                val app = BibleApplication.application
                session.setLastEntryCost(app.getString(R.string.llm_cost_total, LlmCostTracker.formatCost(cost)), isTotalCost = true)
            }
        }
    }

    /**
     * Extract title from response content.
     *
     * Looks for a markdown H1 heading (# Title) at the start of the response.
     * Returns the title and the remaining content (with or without the heading).
     *
     * @param response The full LLM response
     * @param fallbackPromptName Fallback prompt name if no title found
     * @param fallbackContext Fallback context (e.g., verse ref) if no title found
     * @return Pair of (title, content)
     */
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

    /**
     * Regenerate an AI document using stored context.
     *
     * @param pageId ID of the page to regenerate
     * @return true if regeneration was started, false if it failed
     */
    /**
     * Open an AI document result in the target window, or fall back to linkControl.
     */
    private suspend fun openAIDocumentResult(documentInitials: String, pageKey: String, targetWindowId: IdType?) {
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
        linkControl.openAIDocument(documentInitials, pageKey)
    }

    suspend fun regenerateAIDocument(pageId: IdType, targetWindowId: IdType? = null): Boolean {
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
            return false
        }

        // Get cache entry for regeneration context
        val dao = DatabaseContainer.instance.myDocumentDb.myDocumentDao()
        val cacheEntry = dao.getCacheEntry(pageId)
        if (cacheEntry == null) {
            Log.w(TAG, "Cannot regenerate: no cache entry for page: $pageId")
            return false
        }

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

        // Delete old page first to avoid cache hit, then execute the prompt
        MyDocumentBookManager.deleteAIDocumentPage(pageId)
        executePrompt(prompt, selection, targetWindowId = targetWindowId)
        return true
    }

    /**
     * Get the current workspace's agent session, if available.
     *
     * Used by LlmProcessingService.processWithTools to post log entries
     * without requiring a full agent execution context.
     *
     * @return The session for the active workspace, or null if unavailable
     */
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
