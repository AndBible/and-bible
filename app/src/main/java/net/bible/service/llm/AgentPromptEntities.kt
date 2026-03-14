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

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.serialization.Serializable
import net.bible.android.database.IdType
import net.bible.service.common.SecureStorage
import net.bible.service.llm.agent.PermissionMode

/**
 * Enum of all agent tools, used for type-safe tool permission sets.
 *
 * The @SerialName values match the camelCase tool names used in ToolRegistry
 * and LLM function calling, ensuring backwards-compatible JSON serialization.
 */
@Serializable
enum class AgentTool {
    // Read tools
    GET_VERSE_CONTENT,
    SEARCH_BIBLE,
    GET_COMMENTARIES,
    GET_DICTIONARY_ENTRY,
    GET_BOOKMARKS_FOR_VERSE,
    GET_BOOKMARKS_WITH_LABEL,
    GET_ALL_LABELS,
    GET_STUDY_PAD_CONTENT,
    SEARCH_STUDY_PADS,
    GET_INSTALLED_DOCUMENTS,

    // Write tools
    CREATE_BOOKMARK,
    ADD_BOOKMARK_NOTE,
    UPDATE_BOOKMARK_NOTE,
    CREATE_LABEL,
    ADD_LABEL_TO_BOOKMARK,
    ADD_STUDY_PAD_ENTRY,
    SET_DOCUMENT_TITLE,
    FINISH_WITH_STUDY_PAD,
    FINISH_WITHOUT_DOCUMENT;

    companion object {
        private val byToolName: Map<String, AgentTool> by lazy {
            entries.associateBy {
                it.name.lowercase().replace(Regex("_([a-z])")) { m -> m.groupValues[1].uppercase() }
            }
        }

        /** Look up an AgentTool by its camelCase tool name (as used in [Tool.name]). */
        fun fromToolName(name: String): AgentTool? = byToolName[name]
    }
}

/**
 * Context where a prompt can be shown/used.
 */
@Serializable
enum class PromptContext {
    VERSE_SELECTION,       // Verse selection (One Tap Actions)
    TEXT_SELECTION,        // Free text selection
    WINDOW_MENU,           // Window Button popup menu
    WORKSPACE_MENU,        // Toolbar 3-dot menu
    NOTE_EDITOR,           // Bookmark note editing
}

/**
 * Persistent configuration for an LLM provider.
 *
 * Each row represents one configured provider (e.g. Gemini, OpenAI, a custom endpoint).
 * Multiple CUSTOM entries are allowed. API keys are stored in SharedPreferences
 * keyed by `"llm_api_key_${id}"` to keep them out of database backups.
 */
@Entity(
    indices = [
        Index("orderNumber"),
    ]
)
data class LlmProviderConfig(
    @PrimaryKey val id: IdType = IdType(),
    /** LlmProvider enum name (GEMINI, OPENAI, CUSTOM, …) */
    val providerType: String,
    /** User-visible name (auto-set for known providers, user-chosen for CUSTOM) */
    val displayName: String,
    /** Custom endpoint URL (only used for CUSTOM providerType) */
    val endpoint: String? = null,
    /** "OPENAI" or "ANTHROPIC" (only used for CUSTOM providerType) */
    val apiFormat: String? = null,
    /** User's preferred model for this provider (null = provider's first model) */
    val defaultModel: String? = null,
    /** Whether this is the default provider for new prompts / global usage */
    @ColumnInfo(defaultValue = "0") val isDefault: Boolean = false,
    /** Display ordering */
    @ColumnInfo(defaultValue = "0") val orderNumber: Int = 0,
) {
    /** Resolve the LlmProvider enum for this config. */
    fun resolveProvider(): LlmProvider = try {
        LlmProvider.valueOf(providerType)
    } catch (_: IllegalArgumentException) {
        LlmProvider.CUSTOM
    }

    /** Effective endpoint: explicit for CUSTOM, from enum for known providers. */
    fun resolveEndpoint(): String {
        val provider = resolveProvider()
        return if (provider == LlmProvider.CUSTOM) endpoint ?: "" else provider.endpoint
    }

    /** Effective API format: explicit for CUSTOM, from enum for known providers. */
    fun resolveApiFormat(): ApiFormat {
        val provider = resolveProvider()
        return if (provider == LlmProvider.CUSTOM) {
            try { ApiFormat.valueOf(apiFormat ?: ApiFormat.OPENAI.name) } catch (_: IllegalArgumentException) { ApiFormat.OPENAI }
        } else {
            provider.apiFormat
        }
    }

    /** Get the LlmApiAdapter for this provider config. */
    fun resolveAdapter(): LlmApiAdapter = when (resolveApiFormat()) {
        ApiFormat.OPENAI -> OpenAiApiAdapter()
        ApiFormat.ANTHROPIC -> AnthropicApiAdapter()
    }

    /** Available models for this provider config. */
    fun resolveModels(): List<String> = resolveProvider().models

    /** Effective default model: explicit choice, or first from provider's list. */
    fun resolveDefaultModel(): String =
        defaultModel?.takeIf { it.isNotBlank() } ?: resolveModels().firstOrNull() ?: ""
}

/** Extension to get the API key from SecureStorage (encrypted). */
fun LlmProviderConfig.getApiKey(): String =
    SecureStorage.getString("llm_api_key_${id}", "") ?: ""

/** Extension to set the API key in SecureStorage (encrypted). */
fun LlmProviderConfig.setApiKey(key: String) =
    SecureStorage.setString("llm_api_key_${id}", key)

/** Extension to remove the API key from SecureStorage. */
fun LlmProviderConfig.removeApiKey() =
    SecureStorage.remove("llm_api_key_${id}")

@Dao
interface LlmProviderConfigDao {
    @Query("SELECT * FROM LlmProviderConfig ORDER BY orderNumber")
    fun all(): List<LlmProviderConfig>

    @Query("SELECT * FROM LlmProviderConfig WHERE isDefault = 1 LIMIT 1")
    fun getDefault(): LlmProviderConfig?

    @Query("SELECT * FROM LlmProviderConfig WHERE id = :id")
    fun getById(id: IdType): LlmProviderConfig?

    @Insert
    fun insert(config: LlmProviderConfig)

    @Update
    fun update(config: LlmProviderConfig)

    @Delete
    fun delete(config: LlmProviderConfig)

    @Query("UPDATE LlmProviderConfig SET isDefault = 0")
    fun clearDefault()

    @Query("SELECT COUNT(*) FROM LlmProviderConfig")
    fun getCount(): Int

    @Query("DELETE FROM LlmProviderConfig")
    fun deleteAll()
}

/**
 * User-defined or default prompt for LLM operations.
 *
 * Prompts can be used in different contexts (showIn) and are displayed
 * in the appropriate menus/dialogs based on their configuration.
 */
@Entity(
    foreignKeys = [ForeignKey(
        entity = LlmProviderConfig::class,
        parentColumns = ["id"],
        childColumns = ["providerConfigId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [
        Index("orderNumber"),
        Index("createdAt"),
        Index("providerConfigId"),
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
    /**
     * Per-prompt permission mode override.
     * null = use global default from settings
     * Explicit value = override for this prompt
     */
    @ColumnInfo(defaultValue = "NULL") var permissionMode: PermissionMode? = null,
    /** Per-prompt tool permission overrides. null = no override (use global defaults). */
    @ColumnInfo(defaultValue = "NULL") var allowedTools: Set<AgentTool>? = null,
    @ColumnInfo(defaultValue = "NULL") var deniedTools: Set<AgentTool>? = null,
    /** Per-prompt model override. null = use global default from settings. */
    @ColumnInfo(defaultValue = "NULL") var modelOverride: String? = null,
    /** FK → LlmProviderConfig. null = use default provider. ON DELETE SET_NULL. */
    @ColumnInfo(defaultValue = "NULL") var providerConfigId: IdType? = null,
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
