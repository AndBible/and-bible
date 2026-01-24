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

package net.bible.service.llm

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.serialization.Serializable
import net.bible.android.database.IdType

/**
 * Context where a prompt can be shown/used.
 */
@Serializable
enum class PromptContext {
    TEXT_DISPLAY_SETTINGS, // LLM Mode - online processing for document display
    VERSE_SELECTION,       // Verse selection (One Tap Actions)
    TEXT_SELECTION,        // Free text selection
    WINDOW_MENU,           // Window Button popup menu
    WORKSPACE_MENU,        // Toolbar 3-dot menu
    NOTE_EDITOR,           // Bookmark note editing
}

/**
 * User-defined or default prompt for LLM operations.
 *
 * Prompts can be used in different contexts (showIn) and are displayed
 * in the appropriate menus/dialogs based on their configuration.
 */
@Entity(
    indices = [
        Index("orderNumber"),
        Index("createdAt"),
    ]
)
@Serializable
data class AgentPrompt(
    @PrimaryKey var id: IdType = IdType(),
    var name: String = "",
    @ColumnInfo(defaultValue = "NULL") var description: String? = null,
    var promptTemplate: String = "",
    var showIn: Set<PromptContext> = emptySet(),
    @ColumnInfo(defaultValue = "0") var orderNumber: Int = 0,
    @ColumnInfo(defaultValue = "0") var createdAt: Long = System.currentTimeMillis(),
    /**
     * Controls cache key matching strictness:
     * - true (default): Cache key = promptId + full context hash (Bible version, selected text, etc.)
     * - false: Cache key = promptId + KJVA ordinals only (verse range)
     *
     * Examples:
     * - "Translate to Finnish" → true (translation depends on source text)
     * - "Word study" → false (focuses on original languages, translation doesn't matter)
     * - "Cross-references" → false (same across all versions)
     */
    @ColumnInfo(defaultValue = "1") var strictContextMatching: Boolean = true,
)

@Dao
interface AgentPromptDao {
    @Insert
    fun insert(entity: AgentPrompt)

    @Insert
    fun insertAll(prompts: List<AgentPrompt>)

    @Update
    fun update(entity: AgentPrompt)

    @Delete
    fun delete(entity: AgentPrompt)

    @Query("SELECT * FROM AgentPrompt WHERE id = :id")
    fun promptById(id: IdType): AgentPrompt?

    @Query("SELECT * FROM AgentPrompt ORDER BY orderNumber, createdAt DESC")
    fun allPrompts(): List<AgentPrompt>

    @Query("SELECT COUNT(*) FROM AgentPrompt")
    fun getCount(): Int

    @Query("DELETE FROM AgentPrompt WHERE id = :id")
    fun deleteById(id: IdType)
}
