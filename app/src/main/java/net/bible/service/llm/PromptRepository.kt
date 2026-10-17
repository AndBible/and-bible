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

import net.bible.sharedcore.platform.AppEnvironment

import org.koin.core.component.KoinComponent
import net.bible.sharedcore.log.Log
import net.bible.android.control.page.DocumentCategory
import net.bible.android.database.IdType
import net.bible.service.db.DatabaseContainer
import net.bible.sharedlogic.platform.PromptPackSource
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.mp.KoinPlatform

/**
 * Unified facade for accessing both built-in and user-created prompts.
 *
 * Built-in prompts live in code (BuiltInPrompts) and are read-only.
 * User prompts live in the database and are fully editable.
 *
 * This singleton provides a single access point for all prompt operations,
 * replacing direct DAO usage throughout the codebase.
 */
object PromptRepository : KoinComponent {
    private val aiSettings: AiSettings get() = getKoin().get()
    private const val TAG = "PromptRepository"
    private val environment: AppEnvironment get() = getKoin().get()
    private val promptPacks: PromptPackSource by lazy { KoinPlatform.getKoin().get<PromptPackSource>() }

    private val dao get() = DatabaseContainer.instance.aiSettingsDb.agentPromptDao()
    private val categoryDao get() = DatabaseContainer.instance.aiSettingsDb.promptCategoryDao()
    private val overrideDao get() = DatabaseContainer.instance.aiSettingsDb.builtinPromptOverrideDao()

    /** Cached add-on prompts loaded from CSV files. Invalidated on [PromptPackSource.reloaded]. */
    private var addonPromptsCache: List<AgentPrompt>? = null

    /** Subscribed on first use, not in an initializer: [PromptPackSource] comes from Koin. */
    private val reloadSubscription by lazy { promptPacks.reloaded.subscribe { addonPromptsCache = null } }

    fun clearAddonCache() {
        addonPromptsCache = null
    }

    private fun loadAddonPrompts(): List<AgentPrompt> {
        addonPromptsCache?.let { return it }
        reloadSubscription
        val prompts = mutableListOf<AgentPrompt>()
        for (pack in promptPacks.packs) {
            try {
                val parsed = FileSystem.SYSTEM.source(pack.path.toPath()).use { PromptCsvUtils.parsePromptsFromCsv(it) }
                parsed.forEach { it.sourceModule = pack.moduleName }
                prompts.addAll(parsed)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading prompt pack from ${pack.moduleName}", e)
            }
        }
        addonPromptsCache = prompts
        return prompts
    }

    /** Check if a prompt ID belongs to an add-on module prompt. */
    fun isAddon(id: IdType): Boolean = loadAddonPrompts().any { it.id == id }

    /** Check if a prompt is read-only (built-in or add-on). */
    fun isReadOnly(id: IdType): Boolean = isBuiltIn(id) || isAddon(id)

    /** Apply DB overrides (e.g. model selection) to a built-in prompt. */
    private suspend fun applyOverride(prompt: AgentPrompt): AgentPrompt {
        val override = overrideDao.getById(prompt.id) ?: return prompt
        return prompt.copy(configuredModelId = override.configuredModelId)
    }

    /**
     * Get a prompt by ID, checking built-in prompts first, then add-ons, then DB.
     * Built-in prompts are merged with any DB overrides.
     */
    suspend fun promptById(id: IdType): AgentPrompt? {
        BuiltInPrompts.promptById(id)?.let { return applyOverride(it) }
        return loadAddonPrompts().find { it.id == id }
            ?: dao.promptById(id)
    }

    /** Apply built-in overrides in the caller's coroutine. */
    private suspend fun loadBuiltInPrompts(): List<AgentPrompt> {
        val prompts = if (environment.isDebugMode) BuiltInPrompts.allBuiltInPrompts()
            else BuiltInPrompts.productionPrompts()
        return prompts.map { applyOverride(it) }
    }

    /**
     * Returns all prompts: built-in first (filtered by debug mode and hidden state),
     * then add-on prompts, then user prompts from DB.
     *
     * Test prompts (debugOnly) are excluded when not in debug mode.
     * Hidden built-in prompts are excluded from all contexts.
     */
    suspend fun allPrompts(): List<AgentPrompt> {
        val hidden = aiSettings.hiddenBuiltInPrompts
        return run {
            val visibleBuiltIn = loadBuiltInPrompts().filter { it.id !in hidden }
            visibleBuiltIn + loadAddonPrompts() + dao.allPrompts()
        }
    }

    /**
     * Returns all prompts including hidden ones, for use in settings UI.
     */
    suspend fun allPromptsIncludingHidden(): List<AgentPrompt> =
        loadBuiltInPrompts() + loadAddonPrompts() + dao.allPrompts()

    /**
     * Returns prompts filtered by context and optionally by document category.
     *
     * When [documentCategory] is null (e.g. WORKSPACE_MENU, NOTE_EDITOR), no document type
     * filtering is applied. When provided, prompts with [AgentPrompt.bibleOnly] = true are
     * hidden unless the category is [DocumentCategory.BIBLE].
     */
    suspend fun promptsForContext(context: PromptContext, documentCategory: DocumentCategory? = null): List<AgentPrompt> {
        return allPrompts().filter { prompt ->
            context in prompt.showIn &&
            (documentCategory == null || !prompt.bibleOnly || documentCategory == DocumentCategory.BIBLE)
        }
    }

    /**
     * Check if a prompt ID belongs to a built-in prompt.
     */
    fun isBuiltIn(id: IdType): Boolean = BuiltInPrompts.isBuiltIn(id)

    /**
     * Copy a prompt (built-in, add-on, or user) into the DB as a new user prompt with a new ID.
     * Returns the new prompt's ID, or null if the source prompt was not found.
     */
    suspend fun copyPrompt(id: IdType): IdType? {
        val source = promptById(id) ?: return null
        val effectiveCategoryId = getCategoryForPrompt(source)?.id
        val newOrder = source.orderNumber + 1
        val newPrompt = source.copy(
            id = IdType(),
            name = source.name + " (copy)",
            createdAt = kotlin.time.Clock.System.now().toEpochMilliseconds(), // swap to net.bible.sharedlogic.util.currentTimeMillis() when G1 Part A lands
            orderNumber = newOrder,
            categoryId = effectiveCategoryId,
        ).also { it.sourceModule = null }
        run {
            dao.shiftOrderNumbersAfter(source.orderNumber)
            dao.insert(newPrompt)
        }
        return newPrompt.id
    }

    /**
     * Insert a new user prompt into the database.
     */
    suspend fun insertPrompt(prompt: AgentPrompt) {
        require(!BuiltInPrompts.isBuiltIn(prompt.id)) { "Cannot insert a prompt with a built-in ID" }
        dao.insert(prompt)
    }

    /**
     * Update an existing user prompt in the database.
     */
    suspend fun updatePrompt(prompt: AgentPrompt) {
        require(!BuiltInPrompts.isBuiltIn(prompt.id)) { "Cannot update a built-in prompt" }
        dao.update(prompt)
    }

    /**
     * Delete a user prompt from the database.
     */
    suspend fun deletePrompt(prompt: AgentPrompt) {
        require(!BuiltInPrompts.isBuiltIn(prompt.id)) { "Cannot delete a built-in prompt" }
        dao.delete(prompt)
    }

    /**
     * Delete all user prompts from the database.
     * Built-in prompts are unaffected (they live in code).
     */
    suspend fun deleteAllUserPrompts() {
        dao.deleteAll()
    }

    /** Delete all user-created categories (built-in categories are in code and unaffected). */
    suspend fun deleteAllUserCategories() {
        categoryDao.deleteAll()
    }

    /** Set the model override for a built-in prompt. */
    suspend fun setBuiltinPromptModelOverride(promptId: IdType, modelId: IdType) {
        overrideDao.upsert(BuiltinPromptOverride(id = promptId, configuredModelId = modelId))
    }

    /** Hide a built-in prompt so it doesn't appear in any context. */
    fun setBuiltInPromptHidden(promptId: IdType, hidden: Boolean) {
        val current = aiSettings.hiddenBuiltInPrompts
        aiSettings.hiddenBuiltInPrompts = if (hidden) current + promptId else current - promptId
    }

    /** Check if a built-in prompt is hidden. */
    fun isBuiltInPromptHidden(promptId: IdType): Boolean =
        promptId in aiSettings.hiddenBuiltInPrompts

    // --- Category operations ---

    /** All categories: built-in (from code) + user-created (from DB). */
    suspend fun allCategories(): List<PromptCategory> =
        BuiltInPrompts.defaultCategories() + userCategories()

    /** User-created categories only (from DB). */
    suspend fun userCategories(): List<PromptCategory> = categoryDao.all()

    suspend fun categoryById(id: IdType): PromptCategory? =
        BuiltInPrompts.defaultCategories().find { it.id == id } ?: categoryDao.getById(id)

    suspend fun insertCategory(category: PromptCategory) = categoryDao.insert(category)

    suspend fun updateCategory(category: PromptCategory) = categoryDao.update(category)

    /**
     * Delete a user category. If [deletePrompts] is true, deletes all prompts in the category.
     * Otherwise moves them to root (categoryId = null).
     */
    suspend fun deleteCategory(categoryId: IdType, deletePrompts: Boolean) {
        run {
            if (deletePrompts) {
                dao.deleteByCategoryId(categoryId)
            } else {
                categoryDao.clearCategoryFromPrompts(categoryId)
            }
            categoryDao.delete(PromptCategory(id = categoryId))
        }
    }

    /**
     * Returns the effective category for a prompt.
     * Built-in prompts use hardcoded defaults; user/addon prompts use the DB field.
     */
    suspend fun getCategoryForPrompt(prompt: AgentPrompt): PromptCategory? {
        if (isBuiltIn(prompt.id)) {
            val catId = BuiltInPrompts.defaultCategoryForPrompt(prompt.id) ?: return null
            return BuiltInPrompts.defaultCategories().find { it.id == catId }
        }
        return prompt.categoryId?.let { categoryById(it) }
    }

    /** Whether a category is hidden from AI Actions dialogs (works for both built-in and user categories). */
    fun isCategoryHidden(category: PromptCategory): Boolean =
        category.hidden || category.id in aiSettings.hiddenBuiltInCategories

    // --- Favorite operations ---

    /** Sentinel ID for the virtual "Favorites" category used in prompt lists. */
    val FAVORITES_CATEGORY_ID: IdType = IdType.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff")

    fun favoritePromptIds(): Set<IdType> = aiSettings.favoritePrompts

    fun isFavorite(id: IdType): Boolean = id in aiSettings.favoritePrompts

    fun toggleFavorite(id: IdType) {
        val current = aiSettings.favoritePrompts
        aiSettings.favoritePrompts = if (id in current) current - id else current + id
    }

    /**
     * Returns prompts grouped by category for the given context.
     * Key null = uncategorized prompts. Hidden categories are excluded.
     */
    suspend fun promptsForContextGrouped(
        context: PromptContext,
        documentCategory: DocumentCategory? = null
    ): Map<PromptCategory?, List<AgentPrompt>> {
        val prompts = promptsForContext(context, documentCategory)
        val categoryMap = allCategories().associateBy { it.id }
        fun resolveCategory(prompt: AgentPrompt): PromptCategory? {
            if (isBuiltIn(prompt.id)) {
                val catId = BuiltInPrompts.defaultCategoryForPrompt(prompt.id) ?: return null
                return categoryMap[catId]
            }
            return prompt.categoryId?.let { categoryMap[it] }
        }
        return prompts
            .map { it to resolveCategory(it) }
            .filter { (_, cat) -> cat == null || !isCategoryHidden(cat) }
            .groupBy({ it.second }, { it.first })
    }
}
