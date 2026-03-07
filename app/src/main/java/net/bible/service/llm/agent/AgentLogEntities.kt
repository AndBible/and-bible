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

import net.bible.android.database.IdType

/**
 * Types of log entries in the agent execution log.
 */
enum class LogEntryType {
    /** Informational message (e.g., "Starting execution", "Iteration 1") */
    INFO,
    /** Tool action being performed or completed */
    ACTION,
    /** Permission request for write operation */
    PERMISSION_REQUEST,
    /** Error during execution */
    ERROR
}

/**
 * Status of a log entry, particularly for permission requests.
 */
enum class EntryStatus {
    /** Entry is pending (e.g., waiting for user approval) */
    PENDING,
    /** Permission was approved */
    APPROVED,
    /** Permission was denied */
    DENIED,
    /** Action completed successfully */
    COMPLETED,
    /** Action failed */
    FAILED
}

/**
 * A single entry in the agent execution log.
 *
 * Log entries are displayed in the Agent Log Bottom Sheet to show
 * real-time progress of agent execution to the user.
 *
 * @param id Unique identifier for this log entry
 * @param timestamp When this entry was created
 * @param type Type of log entry
 * @param message Main message to display
 * @param details Additional details (e.g., tool arguments, error stack trace)
 * @param status Current status of the entry
 * @param relatedPermission The permission being requested, if this is a permission request
 */
data class AgentLogEntry(
    val id: IdType = IdType(),
    val timestamp: Long = System.currentTimeMillis(),
    val type: LogEntryType,
    val message: String,
    val details: String? = null,
    @Volatile var status: EntryStatus = EntryStatus.PENDING,
    val relatedPermission: AgentPermission? = null,
    @Volatile var costInfo: String? = null,
    @Volatile var isTotalCost: Boolean = false
) {
    companion object {
        /**
         * Create an info log entry.
         */
        fun info(message: String, details: String? = null) = AgentLogEntry(
            type = LogEntryType.INFO,
            message = message,
            details = details,
            status = EntryStatus.COMPLETED
        )

        /**
         * Create an action log entry.
         */
        fun action(message: String, details: String? = null) = AgentLogEntry(
            type = LogEntryType.ACTION,
            message = message,
            details = details,
            status = EntryStatus.PENDING
        )

        /**
         * Create a permission request log entry.
         */
        fun permissionRequest(message: String, permission: AgentPermission) = AgentLogEntry(
            type = LogEntryType.PERMISSION_REQUEST,
            message = message,
            status = EntryStatus.PENDING,
            relatedPermission = permission
        )

        /**
         * Create an error log entry.
         */
        fun error(message: String, details: String? = null) = AgentLogEntry(
            type = LogEntryType.ERROR,
            message = message,
            details = details,
            status = EntryStatus.FAILED
        )
    }
}
