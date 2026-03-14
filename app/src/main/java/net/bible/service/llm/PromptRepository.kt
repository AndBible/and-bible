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

package net.bible.service.llm

import net.bible.android.database.IdType
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer

/**
 * Unified facade for accessing both built-in and user-created prompts.
 *
 * Built-in prompts live in code (BuiltInPrompts) and are read-only.
 * User prompts live in the database and are fully editable.
 *
 * This singleton provides a single access point for all prompt operations,
 * replacing direct DAO usage throughout the codebase.
 */
object PromptRepository {

    private val dao get() = DatabaseContainer.instance.aiSettingsDb.agentPromptDao()

    /**
     * Get a prompt by ID, checking built-in prompts first, then DB.
     */
    fun promptById(id: IdType): AgentPrompt? {
        return BuiltInPrompts.promptById(id) ?: dao.promptById(id)
    }

    /**
     * Returns all prompts: built-in first (filtered by debug mode), then user prompts from DB.
     *
     * Test prompts (debugOnly) are excluded when not in debug mode.
     */
    fun allPrompts(): List<AgentPrompt> {
        val builtIn = if (CommonUtils.isDebugMode) {
            BuiltInPrompts.allBuiltInPrompts()
        } else {
            BuiltInPrompts.productionPrompts()
        }
        val userPrompts = dao.allPrompts()
        return builtIn + userPrompts
    }

    /**
     * Returns prompts filtered by context, respecting debug mode for test prompts.
     */
    fun promptsForContext(context: PromptContext): List<AgentPrompt> {
        return allPrompts().filter { context in it.showIn }
    }

    /**
     * Check if a prompt ID belongs to a built-in prompt.
     */
    fun isBuiltIn(id: IdType): Boolean = BuiltInPrompts.isBuiltIn(id)

    /**
     * Copy a prompt (built-in or user) into the DB as a new user prompt with a new ID.
     * Returns the new prompt's ID, or null if the source prompt was not found.
     */
    fun copyPrompt(id: IdType): IdType? {
        val source = promptById(id) ?: return null
        val newPrompt = source.copy(
            id = IdType(),
            name = source.name + " (copy)",
            createdAt = System.currentTimeMillis(),
        )
        dao.insert(newPrompt)
        return newPrompt.id
    }

    /**
     * Insert a new user prompt into the database.
     */
    fun insertPrompt(prompt: AgentPrompt) {
        require(!BuiltInPrompts.isBuiltIn(prompt.id)) { "Cannot insert a prompt with a built-in ID" }
        dao.insert(prompt)
    }

    /**
     * Update an existing user prompt in the database.
     */
    fun updatePrompt(prompt: AgentPrompt) {
        require(!BuiltInPrompts.isBuiltIn(prompt.id)) { "Cannot update a built-in prompt" }
        dao.update(prompt)
    }

    /**
     * Delete a user prompt from the database.
     */
    fun deletePrompt(prompt: AgentPrompt) {
        require(!BuiltInPrompts.isBuiltIn(prompt.id)) { "Cannot delete a built-in prompt" }
        dao.delete(prompt)
    }

    /**
     * Delete all user prompts from the database.
     * Built-in prompts are unaffected (they live in code).
     */
    fun deleteAllUserPrompts() {
        dao.allPrompts().forEach { dao.delete(it) }
    }
}
