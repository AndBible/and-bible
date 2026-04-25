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

import net.bible.android.database.IdType

enum class LogEntryType {
    INFO,
    ACTION,
    PERMISSION_REQUEST,
    ERROR,
    LLM_COMMENT
}

enum class EntryStatus {
    PENDING,
    APPROVED,
    DENIED,
    COMPLETED,
    FAILED
}

/** Displayed in the Agent Log Bottom Sheet during execution. */
data class AgentLogEntry(
    val id: IdType = IdType(),
    val timestamp: Long = System.currentTimeMillis(),
    val type: LogEntryType,
    @Volatile var message: String,
    @Volatile var details: String? = null,
    @Volatile var status: EntryStatus = EntryStatus.PENDING,
    val relatedPermission: AgentPermission? = null,
    @Volatile var costInfo: String? = null,
    @Volatile var isTotalCost: Boolean = false,
    val showRawLogLink: Boolean = false,
    /** Correlation key for tool calls; pairs ToolCalling and ToolCompleted events into one entry. */
    val toolCallId: String? = null
) {
    companion object {
        fun info(message: String, details: String? = null, showRawLogLink: Boolean = false) = AgentLogEntry(
            type = LogEntryType.INFO,
            message = message,
            details = details,
            status = EntryStatus.COMPLETED,
            showRawLogLink = showRawLogLink
        )

        fun action(message: String, details: String? = null, toolCallId: String? = null) = AgentLogEntry(
            type = LogEntryType.ACTION,
            message = message,
            details = details,
            status = EntryStatus.PENDING,
            toolCallId = toolCallId
        )

        fun permissionRequest(message: String, permission: AgentPermission) = AgentLogEntry(
            type = LogEntryType.PERMISSION_REQUEST,
            message = message,
            status = EntryStatus.PENDING,
            relatedPermission = permission
        )

        fun error(message: String, details: String? = null, showRawLogLink: Boolean = false) = AgentLogEntry(
            type = LogEntryType.ERROR,
            message = message,
            details = details,
            status = EntryStatus.FAILED,
            showRawLogLink = showRawLogLink
        )

        fun comment(message: String) = AgentLogEntry(
            type = LogEntryType.LLM_COMMENT,
            message = message,
            status = EntryStatus.COMPLETED
        )
    }
}
