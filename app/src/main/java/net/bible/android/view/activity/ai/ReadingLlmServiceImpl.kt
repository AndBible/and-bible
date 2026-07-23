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
package net.bible.android.view.activity.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.android.control.page.DocumentCategory
import net.bible.android.database.IdType
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.AgentPrompt
import net.bible.service.llm.LlmProvider
import net.bible.service.llm.PromptContext
import net.bible.service.llm.PromptRepository
import net.bible.service.sword.mydocument.MyDocumentBookManager
import net.bible.sharedcore.ai.reading.ReadingLlmService
import net.bible.sharedcore.ai.reading.ReadingModelVd
import net.bible.sharedcore.ai.reading.ReadingPromptGroupVd
import net.bible.sharedcore.ai.reading.ReadingPromptVd

/**
 * Android impl of [ReadingLlmService], wrapping the classic `LlmDialogHelper`/[PromptRepository]/
 * AI-DB DAO/[CommonUtils.aiSettings] logic for the reading-view AI dialogs (prompt selector,
 * ask-model-before-run, model selection, regenerate). Verbatim port — see `LlmDialogHelper.kt` for
 * the classic dialog-hosting code this replaces (the actual persistence/lookup logic is unchanged,
 * only the UI hosting differs). Registered as a Koin single; stateless (no StateFlow) since the
 * reading-view AI dialogs are one-shot (opened, answered, dismissed) rather than continuously
 * observed screens.
 */
class ReadingLlmServiceImpl : ReadingLlmService {

    override suspend fun promptGroupsFor(contextId: String, docCategoryId: String?): List<ReadingPromptGroupVd> =
        withContext(Dispatchers.IO) {
            val context = PromptContext.valueOf(contextId)
            val docCategory = docCategoryId?.let { DocumentCategory.valueOf(it) }
            val grouped = PromptRepository.promptsForContextGrouped(context, docCategory)
            val favIds = PromptRepository.favoritePromptIds()

            fun toVd(p: AgentPrompt) = ReadingPromptVd(
                id = p.id.toString(),
                name = p.name,
                description = p.description ?: "",
                isFavorite = p.id in favIds,
                specifyBeforeRun = p.specifyBeforeRun,
            )

            val groups = mutableListOf<ReadingPromptGroupVd>()

            // Virtual favourites group first (only when non-empty), always expanded.
            val favoritePrompts = grouped.values.flatten().filter { it.id in favIds }
            if (favoritePrompts.isNotEmpty()) {
                groups.add(
                    ReadingPromptGroupVd(
                        categoryName = application.getString(R.string.prompt_category_favorites),
                        categoryId = ReadingLlmService.FAVORITES_CATEGORY_ID,
                        isFavorites = true,
                        collapsed = false,
                        prompts = favoritePrompts.map(::toVd),
                    )
                )
            }

            // Uncategorized prompts.
            grouped[null]?.let { prompts ->
                groups.add(
                    ReadingPromptGroupVd(
                        categoryName = application.getString(R.string.prompt_category_uncategorized),
                        categoryId = null,
                        isFavorites = false,
                        collapsed = isCategoryCollapsed(contextId, null),
                        prompts = prompts.map(::toVd),
                    )
                )
            }

            // Then categorized, in the grouping's own (encounter) order.
            for ((category, prompts) in grouped) {
                if (category == null) continue
                val catId = category.id.toString()
                groups.add(
                    ReadingPromptGroupVd(
                        categoryName = category.name,
                        categoryId = catId,
                        isFavorites = false,
                        collapsed = isCategoryCollapsed(contextId, catId),
                        prompts = prompts.map(::toVd),
                    )
                )
            }

            // Classic early-return: nothing to show.
            if (groups.all { it.prompts.isEmpty() }) return@withContext emptyList()

            // If none of the groups ended up expanded, force the first one open (classic parity).
            if (groups.none { !it.collapsed }) {
                groups[0] = groups[0].copy(collapsed = false)
            }

            groups
        }

    override suspend fun toggleFavorite(promptId: String) = withContext(Dispatchers.IO) {
        PromptRepository.toggleFavorite(IdType(promptId))
    }

    override suspend fun configuredModels(): List<ReadingModelVd> = withContext(Dispatchers.IO) {
        val defaultModelId = CommonUtils.aiSettings.defaultModelId
        val modelDao = DatabaseContainer.instance.aiSettingsDb.llmConfiguredModelDao()
        val providerDao = DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao()

        val models = modelDao.all().sortedByDescending { it.id == defaultModelId }
        val providers = providerDao.all().associateBy { it.id }

        models.map { model ->
            ReadingModelVd(
                id = model.id.toString(),
                modelId = model.modelId,
                providerName = providers[model.providerConfigId]?.displayName ?: "?",
                isDefault = model.id == defaultModelId,
                supported = LlmProvider.isModelSupported(model.modelId),
            )
        }
    }

    override fun askModelBeforeRun(): Boolean = CommonUtils.aiSettings.askModelBeforeRun

    override suspend fun promptRequiresModelChoice(promptId: String): Boolean = withContext(Dispatchers.IO) {
        val prompt = PromptRepository.promptById(IdType(promptId))
        CommonUtils.aiSettings.askModelBeforeRun && prompt?.configuredModelId == null
    }

    override suspend fun setPromptModelDefault(promptId: String, modelId: String) = withContext(Dispatchers.IO) {
        val id = IdType(promptId)
        if (PromptRepository.isBuiltIn(id)) {
            PromptRepository.setBuiltinPromptModelOverride(id, IdType(modelId))
        } else {
            val prompt = PromptRepository.promptById(id) ?: return@withContext
            prompt.configuredModelId = IdType(modelId)
            DatabaseContainer.instance.aiSettingsDb.agentPromptDao().update(prompt)
        }
    }

    override suspend fun regenerateRequiresModelChoice(pageId: String): Boolean = withContext(Dispatchers.IO) {
        val page = MyDocumentBookManager.getAIDocumentPage(IdType(pageId))
        val prompt = page?.sourcePromptId?.let { PromptRepository.promptById(it) }
        CommonUtils.aiSettings.askModelBeforeRun && prompt?.configuredModelId == null
    }

    override fun isCategoryCollapsed(contextId: String, categoryId: String?): Boolean =
        CommonUtils.settings.getBoolean(collapsedPrefKey(contextId, categoryId), false)

    override fun setCategoryCollapsed(contextId: String, categoryId: String?, collapsed: Boolean) {
        CommonUtils.settings.setBoolean(collapsedPrefKey(contextId, categoryId), collapsed)
    }

    /** Verbatim classic key from `LlmDialogHelper.collapsedPrefKey`. */
    private fun collapsedPrefKey(contextId: String, categoryId: String?): String =
        "llm_cat_collapsed_${contextId}_${categoryId ?: "uncategorized"}"
}
