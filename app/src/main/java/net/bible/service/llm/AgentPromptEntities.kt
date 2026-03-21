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
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.serialization.Serializable
import net.bible.android.database.IdType
import net.bible.service.common.CommonUtils
import net.bible.service.llm.agent.PermissionMode

/** All agent tools. Enum names are converted to camelCase for ToolRegistry / LLM function calling. */
@Serializable
enum class AgentTool {
    // Read tools
    GET_VERSE_CONTENT,
    SEARCH_BIBLE,
    SEARCH_BY_STRONGS,
    GET_COMMENTARIES,
    GET_DICTIONARY_ENTRY,
    GET_BOOKMARKS_FOR_VERSE,
    GET_BOOKMARKS_WITH_LABEL,
    GET_ALL_LABELS,
    GET_STUDY_PAD_CONTENT,
    SEARCH_STUDY_PADS,
    GET_INSTALLED_DOCUMENTS,
    GET_MY_DOCUMENTS,
    GET_MY_DOCUMENT_PAGES,
    GET_WINDOWS,

    // Write tools
    CREATE_BOOKMARK,
    ADD_BOOKMARK_NOTE,
    UPDATE_BOOKMARK_NOTE,
    CREATE_LABEL,
    ADD_LABEL_TO_BOOKMARK,
    DELETE_BOOKMARK,
    DELETE_LABEL,
    REMOVE_LABEL_FROM_BOOKMARK,
    ADD_STUDY_PAD_ENTRY,
    CREATE_MY_DOCUMENT,
    ADD_MY_DOCUMENT_PAGE,
    EDIT_MY_DOCUMENT_PAGE,
    DELETE_MY_DOCUMENT_PAGE,
    CREATE_WINDOW,
    MANAGE_WINDOW,
    SET_WINDOW_DOCUMENT,
    SET_DOCUMENT_TITLE,
    FINISH_WITH_STUDY_PAD,
    FINISH_WITH_MY_DOCUMENT_PAGE,
    FINISH_WITHOUT_DOCUMENT;

    /** camelCase name used in LLM function calling (e.g. GET_VERSE_CONTENT -> "getVerseContent") */
    val camelCaseName: String by lazy {
        name.lowercase().replace(Regex("_([a-z])")) { m -> m.groupValues[1].uppercase() }
    }

    companion object {
        private val byToolName: Map<String, AgentTool> by lazy {
            entries.associateBy { it.camelCaseName }
        }

        fun fromToolName(name: String): AgentTool? = byToolName[name]
    }
}

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
    /** API wire format (only used for CUSTOM providerType) */
    val apiFormat: ApiFormat? = null,
    /** User's preferred model for this provider (null = provider's first model) */
    val defaultModel: String? = null,
    /** Whether this is the default provider for new prompts / global usage */
    @ColumnInfo(defaultValue = "0") val isDefault: Boolean = false,
    /** Display ordering */
    @ColumnInfo(defaultValue = "0") val orderNumber: Int = 0,
    /** Custom pricing: input cost per million tokens (0.0 = use built-in pricing) */
    @ColumnInfo(defaultValue = "0.0") val customInputPrice: Double = 0.0,
    /** Custom pricing: output cost per million tokens (0.0 = use built-in pricing) */
    @ColumnInfo(defaultValue = "0.0") val customOutputPrice: Double = 0.0,
) {
    fun resolveProvider(): LlmProvider = try {
        LlmProvider.valueOf(providerType)
    } catch (_: IllegalArgumentException) {
        LlmProvider.CUSTOM
    }

    /** Explicit for CUSTOM, from enum for known providers. */
    fun resolveEndpoint(): String {
        val provider = resolveProvider()
        return if (provider == LlmProvider.CUSTOM) endpoint ?: "" else provider.endpoint
    }

    /** Explicit for CUSTOM, from enum for known providers. */
    fun resolveApiFormat(): ApiFormat {
        val provider = resolveProvider()
        return if (provider == LlmProvider.CUSTOM) apiFormat ?: ApiFormat.OPENAI else provider.apiFormat
    }

    fun resolveAdapter(): LlmApiAdapter = when (resolveApiFormat()) {
        ApiFormat.OPENAI -> OpenAiApiAdapter()
        ApiFormat.ANTHROPIC -> AnthropicApiAdapter()
    }

    fun resolveModels(): List<String> = resolveProvider().models

    /** Explicit choice, or first from provider's list. */
    fun resolveDefaultModel(): String =
        defaultModel?.takeIf { it.isNotBlank() } ?: resolveModels().firstOrNull() ?: ""
}

private val prefs get() = CommonUtils.realSharedPreferences

fun LlmProviderConfig.getApiKey(): String =
    prefs.getString("llm_api_key_${id}", "") ?: ""

fun LlmProviderConfig.setApiKey(key: String) =
    prefs.edit().putString("llm_api_key_${id}", key).apply()

fun LlmProviderConfig.removeApiKey() =
    prefs.edit().remove("llm_api_key_${id}").apply()

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

/** User-defined or default prompt for LLM operations. */
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
    /** Per-prompt permission mode override (null = use global default). */
    @ColumnInfo(defaultValue = "NULL") var permissionMode: PermissionMode? = null,
    /** Per-prompt tool permission overrides. null = no override (use global defaults). */
    @ColumnInfo(defaultValue = "NULL") var allowedTools: Set<AgentTool>? = null,
    @ColumnInfo(defaultValue = "NULL") var deniedTools: Set<AgentTool>? = null,
    /** Per-prompt model override. null = use global default from settings. */
    @ColumnInfo(defaultValue = "NULL") var modelOverride: String? = null,
    /** FK → LlmProviderConfig. null = use default provider. ON DELETE SET_NULL. */
    @ColumnInfo(defaultValue = "NULL") var providerConfigId: IdType? = null,
    /** When true, show an edit dialog for the prompt text before sending it to the LLM. */
    @ColumnInfo(defaultValue = "0") var editBeforeRun: Boolean = false,
    /** When true, the prompt does not create a document — results appear only in the agent log. */
    @ColumnInfo(defaultValue = "0") var noDocumentCreation: Boolean = false,
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

/**
 * Singleton entity for global AI settings that are synced across devices.
 * Uses a fixed ID so sync recognizes it as the same row on all devices.
 */
@Entity
data class GlobalAiSettings(
    @PrimaryKey val id: IdType = SINGLETON_ID,
    @ColumnInfo(defaultValue = "NULL") val agentPermissionMode: PermissionMode? = null,
    @ColumnInfo(defaultValue = "NULL") val permanentlyAllowedTools: Set<AgentTool>? = null,
    @ColumnInfo(defaultValue = "NULL") val permanentlyDeniedTools: Set<AgentTool>? = null,
    val aiExcludedDocuments: Set<String> = emptySet(),
    @ColumnInfo(defaultValue = "0") val commentaryMaxResponseTokens: Int = 0,
) {
    companion object {
        /** Distinct from GlobalTextDisplaySettings SINGLETON_ID (…0001) in WorkspaceDB. */
        val SINGLETON_ID = IdType.fromString("a1000000-0000-0000-0000-000000000001")
    }
}

@Dao
interface GlobalAiSettingsDao {
    @Query("SELECT * FROM GlobalAiSettings LIMIT 1")
    fun get(): GlobalAiSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun set(settings: GlobalAiSettings)
}

/**
 * Per-device cumulative LLM usage record. Each device writes only its own row
 * (keyed by providerConfigId + deviceId), and the UI sums across all devices for totals.
 * This avoids data loss with last-writer-wins sync for cumulative counters.
 *
 * Uses a standard IdType PK for sync compatibility (LogEntry expects IdType entity IDs).
 * The unique index on (providerConfigId, deviceId) ensures one row per device per provider.
 */
@Entity(
    foreignKeys = [ForeignKey(
        entity = LlmProviderConfig::class,
        parentColumns = ["id"],
        childColumns = ["providerConfigId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index("providerConfigId"),
        Index(value = ["providerConfigId", "deviceId"], unique = true),
    ]
)
data class LlmUsageRecord(
    @PrimaryKey val id: IdType = IdType(),
    val providerConfigId: IdType,
    val deviceId: String,
    @ColumnInfo(defaultValue = "0") val inputTokens: Long = 0,
    @ColumnInfo(defaultValue = "0") val outputTokens: Long = 0,
    @ColumnInfo(defaultValue = "0") val cacheCreationTokens: Long = 0,
    @ColumnInfo(defaultValue = "0") val cacheReadTokens: Long = 0,
    @ColumnInfo(defaultValue = "0.0") val estimatedCostUsd: Double = 0.0,
)

@Dao
interface LlmUsageRecordDao {
    @Query("SELECT * FROM LlmUsageRecord WHERE providerConfigId = :configId")
    fun getByConfig(configId: IdType): List<LlmUsageRecord>

    @Query("SELECT * FROM LlmUsageRecord WHERE providerConfigId = :configId AND deviceId = :deviceId")
    fun get(configId: IdType, deviceId: String): LlmUsageRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(record: LlmUsageRecord)

    @Query("DELETE FROM LlmUsageRecord WHERE providerConfigId = :configId")
    fun deleteByConfig(configId: IdType)
}
