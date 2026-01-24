/*
 * Copyright (c) 2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.link.LinkControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.IdType
import net.bible.android.view.activity.page.Selection
import net.bible.service.llm.AgentPrompt
import net.bible.service.llm.tools.ToolResult
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.mydocument.MyDocumentBookManager
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import java.util.concurrent.CopyOnWriteArrayList

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
object AgentSessionManager {
    /** Active sessions, keyed by workspace ID */
    private val activeSessions = mutableMapOf<IdType, AgentSession>()

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
     * 2. Creates/starts an AgentSession
     * 3. Executes the prompt via AgentExecutor
     * 4. Saves the response to AI Documents
     * 5. Opens the saved page in a linked window
     *
     * @param prompt The AgentPrompt to execute
     * @param selection The user's selection (verses, text, etc.)
     * @param windowControl WindowControl for accessing current window state
     * @param linkControl LinkControl for opening the result
     */
    suspend fun executePrompt(
        prompt: AgentPrompt,
        selection: Selection,
        windowControl: WindowControl,
        linkControl: LinkControl
    ) {
        val workspaceId = windowControl.windowRepository.id

        // Build AgentContext
        val context = buildAgentContext(prompt, selection, windowControl)

        // Start session
        val session = getOrCreateSession(workspaceId)
        session.start(context)

        // Execute via AgentExecutor
        val executor = AgentExecutor()
        executor.execute(prompt.id, context).collect { event ->
            handleAgentEvent(event, session, prompt, context, linkControl)
        }
    }

    private suspend fun buildAgentContext(
        prompt: AgentPrompt,
        selection: Selection,
        windowControl: WindowControl
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

            // Get selected text
            selectedText = if (book != null && pageKey != null) {
                SwordContentFacade.getTextWithinOrdinalsAsString(book, pageKey, ordinalRange).joinToString(" ")
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
            highlightedText = highlightedText
        )
    }

    private suspend fun handleAgentEvent(
        event: AgentEvent,
        session: AgentSession,
        prompt: AgentPrompt,
        context: AgentContext,
        linkControl: LinkControl
    ) {
        when (event) {
            is AgentEvent.Started -> {
                session.addLogEntry(AgentLogEntry.info("Executing: ${prompt.name}"))
            }
            is AgentEvent.Iteration -> {
                session.addLogEntry(AgentLogEntry.info("Iteration ${event.number}"))
            }
            is AgentEvent.ToolCalling -> {
                session.addLogEntry(
                    AgentLogEntry.action("Tool: ${event.toolName}", details = event.arguments)
                )
            }
            is AgentEvent.ToolCompleted -> {
                val isSuccess = event.result is ToolResult.Success
                val status = if (isSuccess) EntryStatus.COMPLETED else EntryStatus.FAILED
                session.addLogEntry(
                    AgentLogEntry(
                        type = LogEntryType.ACTION,
                        message = "Tool ${event.toolName} ${if (isSuccess) "completed" else "failed"}",
                        details = event.result.toJson(),
                        status = status
                    )
                )
            }
            is AgentEvent.TextResponse -> {
                if (event.isFinal) {
                    session.addLogEntry(
                        AgentLogEntry.info("Response received", details = event.text.take(200))
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
                    sourceContext = context.verseRefString
                )

                session.addLogEntry(AgentLogEntry.info("Saved: $title"))

                // Open the page in linked window
                linkControl.openAIDocument(pageInfo.documentInitials, pageInfo.pageKey)

                session.stop("Completed")
            }
            is AgentEvent.Error -> {
                session.addLogEntry(AgentLogEntry.error(event.message, details = event.cause?.message))
                session.stop()
            }
            is AgentEvent.Cancelled -> {
                session.addLogEntry(AgentLogEntry.info("Cancelled"))
                session.stop()
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
            val title = match.groupValues[1].trim().take(80) // Limit title length
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

    private const val TAG = "AgentSessionManager"
}
