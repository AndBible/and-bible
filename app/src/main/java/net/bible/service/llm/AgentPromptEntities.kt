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

import net.bible.android.activity.R
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
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

/** UI-only grouping for tool permission screens. Does not affect database storage or agent logic. */
enum class ToolCategory(val displayNameResId: Int) {
    BIBLE_SEARCH(R.string.tool_category_bible_search),
    BOOKMARKS(R.string.tool_category_bookmarks),
    LABELS(R.string.tool_category_labels),
    STUDY_PADS(R.string.tool_category_study_pads),
    MY_DOCUMENTS(R.string.tool_category_my_documents),
    GENERAL_BOOKS(R.string.tool_category_general_books),
    WINDOWS(R.string.tool_category_windows),
}

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
    GET_GENBOOK_KEYS,
    GET_GENBOOK_CONTENT,
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
    UPDATE_STUDYPAD_TEXT_ENTRY,
    CREATE_STUDY_PAD,
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
    /** Display ordering */
    @ColumnInfo(defaultValue = "0") val orderNumber: Int = 0,
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
        ApiFormat.OPENAI -> {
            val provider = resolveProvider()
            val cacheControl = provider != LlmProvider.CUSTOM && provider.supportsCacheControl
            OpenAiApiAdapter(supportsCacheControl = cacheControl)
        }
        ApiFormat.ANTHROPIC -> AnthropicApiAdapter()
    }

    /** All available models for this provider (enum + dynamic). Used for "add model" picker. */
    fun resolveAvailableModels(): List<String> {
        val provider = resolveProvider()
        val dynamic = DynamicModelService.getCachedModels(provider.name)
        return dynamic?.map { it.id } ?: provider.models
    }
}

/**
 * A pre-configured LLM model linked to a provider.
 *
 * Users select from these pre-configured models when customizing prompts.
 * The global default model is stored in [GlobalAiSettings.defaultModelId].
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
        Index(value = ["providerConfigId", "modelId"], unique = true),
    ]
)
data class LlmConfiguredModel(
    @PrimaryKey val id: IdType = IdType(),
    val providerConfigId: IdType,
    /** Model identifier as sent to the API (e.g. "gemini-2.5-flash", "anthropic/claude-sonnet-4") */
    val modelId: String,
    @ColumnInfo(defaultValue = "0") val orderNumber: Int = 0,
    @ColumnInfo(defaultValue = "0.0") val inputPricePerMillion: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val outputPricePerMillion: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val cacheCreationPricePerMillion: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val cacheReadPricePerMillion: Double = 0.0,
) {
    /** Display name for UI. Currently just modelId; override later if custom names are needed. */
    val displayName: String get() = modelId

    companion object {
        /** Create a configured model with pricing auto-filled from known sources. */
        fun create(
            providerConfigId: IdType,
            modelId: String,
            orderNumber: Int = 0,
        ): LlmConfiguredModel {
            val pricing = LlmProvider.findPricing(modelId)
                ?: DynamicModelService.getPricingForModel(modelId)
            return LlmConfiguredModel(
                providerConfigId = providerConfigId,
                modelId = modelId,
                orderNumber = orderNumber,
                inputPricePerMillion = pricing?.inputPerMillion ?: 0.0,
                outputPricePerMillion = pricing?.outputPerMillion ?: 0.0,
                cacheCreationPricePerMillion = pricing?.cacheCreationPerMillion ?: 0.0,
                cacheReadPricePerMillion = pricing?.cacheReadPerMillion ?: 0.0,
            )
        }
    }
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

    @Query("SELECT * FROM LlmProviderConfig WHERE id = :id")
    fun getById(id: IdType): LlmProviderConfig?

    @Insert
    fun insert(config: LlmProviderConfig)

    @Update
    fun update(config: LlmProviderConfig)

    @Delete
    fun delete(config: LlmProviderConfig)

    @Query("SELECT COUNT(*) FROM LlmProviderConfig")
    fun getCount(): Int

    @Query("DELETE FROM LlmProviderConfig")
    fun deleteAll()
}

@Dao
interface LlmConfiguredModelDao {
    @Query("SELECT * FROM LlmConfiguredModel WHERE providerConfigId = :providerConfigId ORDER BY orderNumber")
    fun getByProvider(providerConfigId: IdType): List<LlmConfiguredModel>

    @Query("SELECT * FROM LlmConfiguredModel WHERE id = :id")
    fun getById(id: IdType): LlmConfiguredModel?

    @Query("SELECT * FROM LlmConfiguredModel ORDER BY orderNumber")
    fun all(): List<LlmConfiguredModel>

    @Insert
    fun insert(model: LlmConfiguredModel)

    @Update
    fun update(model: LlmConfiguredModel)

    @Delete
    fun delete(model: LlmConfiguredModel)

    @Query("DELETE FROM LlmConfiguredModel WHERE providerConfigId = :providerConfigId")
    fun deleteByProvider(providerConfigId: IdType)
}

/** Category (folder) for grouping prompts in the UI. */
@Entity
data class PromptCategory(
    @PrimaryKey var id: IdType = IdType(),
    var name: String = "",
    @ColumnInfo(defaultValue = "0") var orderNumber: Int = 0,
    /** When true, this category and its prompts are hidden from AI Actions dialogs. */
    @ColumnInfo(defaultValue = "0") var hidden: Boolean = false,
)

@Dao
interface PromptCategoryDao {
    @Query("SELECT * FROM PromptCategory ORDER BY orderNumber, name")
    fun all(): List<PromptCategory>

    @Query("SELECT * FROM PromptCategory WHERE id = :id")
    fun getById(id: IdType): PromptCategory?

    @Insert
    fun insert(category: PromptCategory)

    @Update
    fun update(category: PromptCategory)

    @Delete
    fun delete(category: PromptCategory)

    @Query("UPDATE AgentPrompt SET categoryId = NULL WHERE categoryId = :categoryId")
    fun clearCategoryFromPrompts(categoryId: IdType)

    @Query("DELETE FROM PromptCategory")
    fun deleteAll()
}

/** User-defined or default prompt for LLM operations. */
@Entity(
    foreignKeys = [ForeignKey(
        entity = LlmConfiguredModel::class,
        parentColumns = ["id"],
        childColumns = ["configuredModelId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [
        Index("orderNumber"),
        Index("createdAt"),
        Index("configuredModelId"),
        Index("categoryId"),
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
    /** FK → LlmConfiguredModel. null = use global default model. ON DELETE SET_NULL. */
    @ColumnInfo(defaultValue = "NULL") var configuredModelId: IdType? = null,
    /** When true, show a text field for the user to specify the task before running the prompt. */
    @ColumnInfo(name = "editBeforeRun", defaultValue = "0") var specifyBeforeRun: Boolean = false,
    /** When true, the prompt does not create a document — results appear only in the agent log. */
    @ColumnInfo(defaultValue = "0") var noDocumentCreation: Boolean = false,
    /** Per-prompt max iterations override. null = use global default, 0 = unlimited. */
    @ColumnInfo(defaultValue = "NULL") var maxIterations: Int? = null,
    /** When true, auto-include getInstalledDocuments result in the user message. */
    @ColumnInfo(defaultValue = "0") var autoIncludeDocuments: Boolean = false,
    /** When true, auto-include getCommentaries result in the user message (requires verse context). */
    @ColumnInfo(defaultValue = "0") var autoIncludeCommentaries: Boolean = false,
    /** When true, this prompt only appears when the active window shows a Bible document. */
    @ColumnInfo(defaultValue = "0") var bibleOnly: Boolean = false,
    /** When true, uses a simplified system prompt for text transformation (translation, editing).
     *  Preserves formatting, uses no tools, and routes note editor results back to the note. */
    @ColumnInfo(defaultValue = "0") var isTextTransformation: Boolean = false,
    /** FK → PromptCategory. null = uncategorized (root level). No DB constraint — may reference addon categories. */
    @ColumnInfo(defaultValue = "NULL") var categoryId: IdType? = null,
) {
    /** Name of the add-on module this prompt comes from. Transient — not stored in DB. */
    @Ignore var sourceModule: String? = null
}

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

    @Query("SELECT * FROM AgentPrompt ORDER BY orderNumber")
    fun allPrompts(): List<AgentPrompt>

    @Query("SELECT COUNT(*) FROM AgentPrompt")
    fun getCount(): Int

    @Query("DELETE FROM AgentPrompt WHERE id = :id")
    fun deleteById(id: IdType)

    @Query("DELETE FROM AgentPrompt WHERE categoryId = :categoryId")
    fun deleteByCategoryId(categoryId: IdType)

    @Query("DELETE FROM AgentPrompt")
    fun deleteAll()

    @Query("UPDATE AgentPrompt SET orderNumber = orderNumber + 1 WHERE orderNumber > :afterOrder")
    fun shiftOrderNumbersAfter(afterOrder: Int)
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
    @ColumnInfo(defaultValue = "15000") val commentaryMaxResponseTokens: Int = 15000,
    val hiddenBuiltInPrompts: Set<IdType> = emptySet(),
    @ColumnInfo(defaultValue = "10") val maxIterations: Int = 10,
    val commentaryDeselected: Set<String> = emptySet(),
    /** Global default model. FK to LlmConfiguredModel (managed in code, not DB constraint). */
    @ColumnInfo(defaultValue = "NULL") val defaultModelId: IdType? = null,
    /** BCP 47 language tag for AI responses. null = use app language (Locale.getDefault()). */
    @ColumnInfo(defaultValue = "NULL") val aiLanguage: String? = null,
    /** When true, show a model selection dialog before executing any prompt (unless the prompt has an explicit model). */
    @ColumnInfo(defaultValue = "0") val askModelBeforeRun: Boolean = false,
    /** Whether the user has accepted the AI disclaimer (required before configuring any provider). */
    @ColumnInfo(defaultValue = "0") val aiDisclaimerAccepted: Boolean = false,
    val hiddenBuiltInCategories: Set<IdType> = emptySet(),
    /** Custom system prompt for agent mode. null = use built-in default. */
    @ColumnInfo(defaultValue = "NULL") val customAgentSystemPrompt: String? = null,
    /** Custom system prompt for text transformation mode. null = use built-in default. */
    @ColumnInfo(defaultValue = "NULL") val customTextTransformationSystemPrompt: String? = null,
    val favoritePrompts: Set<IdType> = emptySet(),
    /** Auto-delete raw logs older than this many days. null = no auto-delete. */
    @ColumnInfo(defaultValue = "30") val rawLogRetentionDays: Int? = 30,
    /** When true, auto-hide the agent log panel when a task finishes (unless it errored). */
    @ColumnInfo(defaultValue = "0") val autoHideAgentLogOnCompletion: Boolean = false,
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
 * (keyed by configuredModelId + deviceId), and the UI sums across all devices for totals.
 * This avoids data loss with last-writer-wins sync for cumulative counters.
 *
 * Uses a standard IdType PK for sync compatibility (LogEntry expects IdType entity IDs).
 * The unique index on (configuredModelId, deviceId) ensures one row per device per model.
 */
@Entity(
    indices = [
        Index("configuredModelId"),
        Index(value = ["configuredModelId", "deviceId"], unique = true),
    ]
)
data class LlmUsageRecord(
    @PrimaryKey val id: IdType = IdType(),
    val configuredModelId: IdType,
    val deviceId: String,
    @ColumnInfo(defaultValue = "0") val inputTokens: Long = 0,
    @ColumnInfo(defaultValue = "0") val outputTokens: Long = 0,
    @ColumnInfo(defaultValue = "0") val cacheCreationTokens: Long = 0,
    @ColumnInfo(defaultValue = "0") val cacheReadTokens: Long = 0,
    @ColumnInfo(defaultValue = "0.0") val estimatedCostUsd: Double = 0.0,
)

@Dao
interface LlmUsageRecordDao {
    @Query("SELECT * FROM LlmUsageRecord WHERE configuredModelId = :modelId")
    fun getByModel(modelId: IdType): List<LlmUsageRecord>

    @Query("SELECT * FROM LlmUsageRecord WHERE configuredModelId = :modelId AND deviceId = :deviceId")
    fun get(modelId: IdType, deviceId: String): LlmUsageRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(record: LlmUsageRecord)

    @Query("DELETE FROM LlmUsageRecord WHERE configuredModelId = :modelId")
    fun deleteByModel(modelId: IdType)

    @Query("SELECT * FROM LlmUsageRecord")
    fun all(): List<LlmUsageRecord>
}

/**
 * Persisted raw LLM conversation log for debugging.
 * Stored as a gzipped BLOB of the formatted log text.
 * NOT synced across devices — device-local debug data.
 */
@Entity(
    indices = [
        Index("timestamp"),
        Index("promptId"),
        Index("configuredModelId"),
    ]
)
data class LlmRawLogRecord(
    @PrimaryKey val id: IdType = IdType(),
    val promptId: IdType? = null,
    val promptName: String = "",
    val promptDescription: String? = null,
    val configuredModelId: IdType? = null,
    val modelName: String = "",
    /** LlmProvider enum name, denormalized so logs remain readable after provider deletion. */
    val providerType: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val totalInputTokens: Long = 0,
    val totalOutputTokens: Long = 0,
    @ColumnInfo(defaultValue = "0.0") val estimatedCostUsd: Double = 0.0,
    /** Gzipped formatted log text (from RawLlmLog.format()). */
    val logData: ByteArray,
    @ColumnInfo(defaultValue = "0") val iterationCount: Int = 0,
    /** Whether the session ended with an error or cancellation. */
    @ColumnInfo(defaultValue = "0") val wasError: Boolean = false,
)

/** Lightweight projection for list views — excludes the large logData BLOB. */
data class LlmRawLogSummary(
    val id: IdType,
    val promptId: IdType?,
    val promptName: String,
    val promptDescription: String?,
    val configuredModelId: IdType?,
    val modelName: String,
    val providerType: String,
    val timestamp: Long,
    val totalInputTokens: Long,
    val totalOutputTokens: Long,
    val estimatedCostUsd: Double,
    val iterationCount: Int,
    val wasError: Boolean,
)

@Dao
interface LlmRawLogRecordDao {
    @Query("SELECT id, promptId, promptName, promptDescription, configuredModelId, modelName, providerType, timestamp, totalInputTokens, totalOutputTokens, estimatedCostUsd, iterationCount, wasError FROM LlmRawLogRecord ORDER BY timestamp DESC")
    fun allSummaries(): List<LlmRawLogSummary>

    @Query("SELECT * FROM LlmRawLogRecord WHERE id = :id")
    fun getById(id: IdType): LlmRawLogRecord?

    @Insert
    fun insert(record: LlmRawLogRecord)

    @Query("DELETE FROM LlmRawLogRecord WHERE id IN (:ids)")
    fun deleteByIds(ids: List<IdType>)

    @Query("DELETE FROM LlmRawLogRecord WHERE timestamp < :beforeTimestamp")
    fun deleteOlderThan(beforeTimestamp: Long)

    @Query("SELECT COUNT(*) FROM LlmRawLogRecord")
    fun getCount(): Int

    @Query("DELETE FROM LlmRawLogRecord")
    fun deleteAll()
}

/**
 * Per-builtin-prompt overrides that persist user customizations (e.g. model selection)
 * without modifying the built-in prompt definitions in code.
 *
 * The [id] matches the built-in prompt's stable ID. Only fields that differ from the
 * code-defined defaults need to be set; null fields mean "use the built-in default".
 */
@Entity(
    foreignKeys = [ForeignKey(
        entity = LlmConfiguredModel::class,
        parentColumns = ["id"],
        childColumns = ["configuredModelId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("configuredModelId")]
)
@Serializable
data class BuiltinPromptOverride(
    /** Same ID as the built-in prompt this overrides. */
    @PrimaryKey val id: IdType,
    /** FK → LlmConfiguredModel. null = use global default model. */
    @ColumnInfo(defaultValue = "NULL") var configuredModelId: IdType? = null,
)

@Dao
interface BuiltinPromptOverrideDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: BuiltinPromptOverride)

    @Query("SELECT * FROM BuiltinPromptOverride WHERE id = :id")
    fun getById(id: IdType): BuiltinPromptOverride?

    @Query("SELECT * FROM BuiltinPromptOverride")
    fun all(): List<BuiltinPromptOverride>

    @Query("DELETE FROM BuiltinPromptOverride WHERE id = :id")
    fun deleteById(id: IdType)
}
