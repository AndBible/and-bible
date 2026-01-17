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

import net.bible.android.control.event.ABEventBus
import net.bible.android.database.IdType
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
}
