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

import android.util.Log
import net.bible.android.control.page.DocumentCategory
import net.bible.android.database.IdType
import net.bible.service.common.AndBibleAddons
import net.bible.service.common.CommonUtils
import net.bible.service.common.ReloadAddonsEvent
import net.bible.service.db.DatabaseContainer
import net.bible.android.control.event.ABEventBus

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
    private const val TAG = "PromptRepository"

    private val dao get() = DatabaseContainer.instance.aiSettingsDb.agentPromptDao()
    private val categoryDao get() = DatabaseContainer.instance.aiSettingsDb.promptCategoryDao()
    private val overrideDao get() = DatabaseContainer.instance.aiSettingsDb.builtinPromptOverrideDao()

    /** Cached add-on prompts loaded from CSV files. Invalidated on ReloadAddonsEvent. */
    private var addonPromptsCache: List<AgentPrompt>? = null

    init {
        ABEventBus.register(this)
    }

    /** Called by EventBus when add-on modules are reloaded. */
    @Suppress("unused")
    fun onEvent(event: ReloadAddonsEvent) {
        addonPromptsCache = null
    }

    fun clearAddonCache() {
        addonPromptsCache = null
    }

    private fun loadAddonPrompts(): List<AgentPrompt> {
        addonPromptsCache?.let { return it }
        val prompts = mutableListOf<AgentPrompt>()
        for (pack in AndBibleAddons.providedPromptPacks) {
            try {
                val parsed = pack.file.inputStream().use { PromptCsvUtils.parsePromptsFromCsv(it) }
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
    private fun applyOverride(prompt: AgentPrompt): AgentPrompt {
        val override = overrideDao.getById(prompt.id) ?: return prompt
        return prompt.copy(configuredModelId = override.configuredModelId)
    }

    /**
     * Get a prompt by ID, checking built-in prompts first, then add-ons, then DB.
     * Built-in prompts are merged with any DB overrides.
     */
    fun promptById(id: IdType): AgentPrompt? {
        BuiltInPrompts.promptById(id)?.let { return applyOverride(it) }
        return loadAddonPrompts().find { it.id == id }
            ?: dao.promptById(id)
    }

    private fun loadBuiltInPrompts(): List<AgentPrompt> {
        val prompts = if (CommonUtils.isDebugMode) BuiltInPrompts.allBuiltInPrompts()
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
    fun allPrompts(): List<AgentPrompt> {
        val hidden = CommonUtils.aiSettings.hiddenBuiltInPrompts
        val visibleBuiltIn = loadBuiltInPrompts().filter { it.id !in hidden }
        return visibleBuiltIn + loadAddonPrompts() + dao.allPrompts()
    }

    /**
     * Returns all prompts including hidden ones, for use in settings UI.
     */
    fun allPromptsIncludingHidden(): List<AgentPrompt> =
        loadBuiltInPrompts() + loadAddonPrompts() + dao.allPrompts()

    /**
     * Returns prompts filtered by context and optionally by document category.
     *
     * When [documentCategory] is null (e.g. WORKSPACE_MENU, NOTE_EDITOR), no document type
     * filtering is applied. When provided, prompts with [AgentPrompt.bibleOnly] = true are
     * hidden unless the category is [DocumentCategory.BIBLE].
     */
    fun promptsForContext(context: PromptContext, documentCategory: DocumentCategory? = null): List<AgentPrompt> {
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
    fun copyPrompt(id: IdType): IdType? {
        val source = promptById(id) ?: return null
        val effectiveCategoryId = getCategoryForPrompt(source)?.id
        val newOrder = source.orderNumber + 1
        dao.shiftOrderNumbersAfter(source.orderNumber)
        val newPrompt = source.copy(
            id = IdType(),
            name = source.name + " (copy)",
            createdAt = System.currentTimeMillis(),
            orderNumber = newOrder,
            categoryId = effectiveCategoryId,
        ).also { it.sourceModule = null }
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
        dao.deleteAll()
    }

    /** Delete all user-created categories (built-in categories are in code and unaffected). */
    fun deleteAllUserCategories() {
        categoryDao.deleteAll()
    }

    /** Set the model override for a built-in prompt. */
    fun setBuiltinPromptModelOverride(promptId: IdType, modelId: IdType) {
        overrideDao.upsert(BuiltinPromptOverride(id = promptId, configuredModelId = modelId))
    }

    /** Hide a built-in prompt so it doesn't appear in any context. */
    fun setBuiltInPromptHidden(promptId: IdType, hidden: Boolean) {
        val current = CommonUtils.aiSettings.hiddenBuiltInPrompts
        CommonUtils.aiSettings.hiddenBuiltInPrompts = if (hidden) current + promptId else current - promptId
    }

    /** Check if a built-in prompt is hidden. */
    fun isBuiltInPromptHidden(promptId: IdType): Boolean =
        promptId in CommonUtils.aiSettings.hiddenBuiltInPrompts

    // --- Category operations ---

    /** All categories: built-in (from code) + user-created (from DB). */
    fun allCategories(): List<PromptCategory> =
        BuiltInPrompts.defaultCategories() + categoryDao.all()

    /** User-created categories only (from DB). */
    fun userCategories(): List<PromptCategory> = categoryDao.all()

    fun categoryById(id: IdType): PromptCategory? =
        BuiltInPrompts.defaultCategories().find { it.id == id } ?: categoryDao.getById(id)

    fun insertCategory(category: PromptCategory) = categoryDao.insert(category)

    fun updateCategory(category: PromptCategory) = categoryDao.update(category)

    /**
     * Delete a user category. If [deletePrompts] is true, deletes all prompts in the category.
     * Otherwise moves them to root (categoryId = null).
     */
    fun deleteCategory(categoryId: IdType, deletePrompts: Boolean) {
        if (deletePrompts) {
            dao.deleteByCategoryId(categoryId)
        } else {
            categoryDao.clearCategoryFromPrompts(categoryId)
        }
        categoryDao.delete(PromptCategory(id = categoryId))
    }

    /**
     * Returns the effective category for a prompt.
     * Built-in prompts use hardcoded defaults; user/addon prompts use the DB field.
     */
    fun getCategoryForPrompt(prompt: AgentPrompt): PromptCategory? {
        if (isBuiltIn(prompt.id)) {
            val catId = BuiltInPrompts.defaultCategoryForPrompt(prompt.id) ?: return null
            return BuiltInPrompts.defaultCategories().find { it.id == catId }
        }
        return prompt.categoryId?.let { categoryById(it) }
    }

    /** Whether a category is hidden from AI Actions dialogs (works for both built-in and user categories). */
    fun isCategoryHidden(category: PromptCategory): Boolean =
        category.hidden || category.id in CommonUtils.aiSettings.hiddenBuiltInCategories

    // --- Favorite operations ---

    /** Sentinel ID for the virtual "Favorites" category used in prompt lists. */
    val FAVORITES_CATEGORY_ID: IdType = IdType.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff")

    fun favoritePromptIds(): Set<IdType> = CommonUtils.aiSettings.favoritePrompts

    fun isFavorite(id: IdType): Boolean = id in CommonUtils.aiSettings.favoritePrompts

    fun toggleFavorite(id: IdType) {
        val current = CommonUtils.aiSettings.favoritePrompts
        CommonUtils.aiSettings.favoritePrompts = if (id in current) current - id else current + id
    }

    /**
     * Returns prompts grouped by category for the given context.
     * Key null = uncategorized prompts. Hidden categories are excluded.
     */
    fun promptsForContextGrouped(
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
