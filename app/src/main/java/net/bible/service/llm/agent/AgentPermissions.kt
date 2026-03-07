/*
 * Copyright (c) 2026 Tuomas Airaksinen and the AndBible contributors.
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

import kotlinx.serialization.Serializable

/**
 * Permission modes for agent write operations.
 * Controls when the user is asked for permission before write tools execute.
 */
@Serializable
enum class PermissionMode {
    /** Ask permission for every write operation (default) */
    ALWAYS_ASK,
    /** Ask once per agent run, then allow subsequent writes */
    ASK_ONCE_PER_RUN,
    /** Allow all write operations without asking */
    ALLOW_ALL,
    /** Deny all write operations without asking */
    DENY_ALL
}

/**
 * Represents a permission request from an agent tool.
 *
 * @param toolName Name of the tool requesting permission
 * @param description Human-readable description of what the tool wants to do
 * @param details Additional details about the operation (e.g., parameters)
 */
data class AgentPermission(
    val toolName: String,
    val description: String,
    val details: String? = null
)
