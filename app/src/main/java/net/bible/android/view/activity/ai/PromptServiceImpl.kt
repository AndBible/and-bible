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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.onMain
import net.bible.android.database.IdType
import net.bible.android.view.activity.page.AppSettingsUpdated
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.AgentPrompt
import net.bible.service.llm.AgentTool
import net.bible.service.llm.BuiltInPrompts
import net.bible.service.llm.BuiltinPromptOverride
import net.bible.service.llm.LlmCostTracker
import net.bible.service.llm.LlmProvider
import net.bible.service.llm.ModelPricing
import net.bible.service.llm.PromptCategory
import net.bible.service.llm.PromptContext
import net.bible.service.llm.PromptRepository
import net.bible.service.llm.agent.PermissionMode
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolRegistry
import net.bible.sharedcore.ai.PromptCategoryVd
import net.bible.sharedcore.ai.PromptEditData
import net.bible.sharedcore.ai.PromptGroupVd
import net.bible.sharedcore.ai.PromptService
import net.bible.sharedcore.ai.PromptVd
import net.bible.sharedcore.ai.ToolCategoryVd
import net.bible.sharedcore.ai.ToolPermission
import net.bible.sharedcore.ai.ToolVd
import net.bible.sharedcore.settings.SettingsItem

/**
 * Android impl of [PromptService] backing [net.bible.sharedcore.ai.AiPromptsController] (the prompt
 * manager) and [net.bible.sharedcore.ai.PromptEditController] (the PromptEdit screen). Wraps the
 * classic prompt domain, mirroring `AiSettingsActivity`/`PromptEditActivity` exactly:
 *
 * - [PromptRepository] for all prompt/category CRUD, favorites, built-in hide state, copy and
 *   reorder (orderNumber swap), plus [BuiltinPromptOverride] upsert for the built-in model override.
 * - [ToolRegistry.getConfigurableToolsByCategory] for the Permissions-tab tool list and
 *   [globalToolPermission] resolution against [CommonUtils.aiSettings] `permanentlyAllowed/DeniedTools`.
 * - [CommonUtils.settings] `llmConfigured` for the not-configured CTA gate and
 *   [CommonUtils.aiSettings] for `defaultModelId` / hidden state.
 *
 * [groups] is published in the **classic `loadPrompts` order** — a virtual Favorites group first
 * (only when non-empty), then the uncategorized bucket, then all categories in `orderNumber` order
 * (built-in + user, empty user categories included). [showHidden] gates hidden built-in prompts AND
 * hidden categories in/out of that list (the screen renders the ones that are present with a
 * "(hidden)" suffix so they can be individually restored).
 *
 * Mutations rebuild [groups] locally via [refresh] (mirroring classic's per-action `loadPrompts()`;
 * classic does NOT broadcast for these), while the ABEventBus bridge re-emits when an external
 * change broadcasts [AppSettingsUpdated] (e.g. the PromptEdit host saving a prompt, or the host's
 * `onResume` refresh). Registered as a Koin single (lives for the process). Room here uses
 * `allowMainThreadQueries`, so the non-suspend reads/mutations run synchronously on the caller
 * thread (as elsewhere in the AI settings layer).
 */
class PromptServiceImpl : PromptService {
    private val settings get() = CommonUtils.aiSettings
    private val overrideDao get() = DatabaseContainer.instance.aiSettingsDb.builtinPromptOverrideDao()
    private val modelDao get() = DatabaseContainer.instance.aiSettingsDb.llmConfiguredModelDao()
    private val providerDao get() = DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao()

    private val _configured = MutableStateFlow(CommonUtils.settings.llmConfigured)
    override val configured: StateFlow<Boolean> = _configured.asStateFlow()

    private val _showHidden = MutableStateFlow(false)
    override val showHidden: StateFlow<Boolean> = _showHidden.asStateFlow()

    private val _groups = MutableStateFlow(buildGroups())
    override val groups: StateFlow<List<PromptGroupVd>> = _groups.asStateFlow()

    init {
        ABEventBus.register(this) {
            onMain<AppSettingsUpdated> { refresh() }
        }
    }

    // --- groups (classic AiSettingsActivity.loadPrompts, Favorites → uncategorized → categories) --

    private fun buildGroups(): List<PromptGroupVd> {
        if (!CommonUtils.settings.llmConfigured) return emptyList()
        val showHidden = _showHidden.value
        val hiddenPromptIds = settings.hiddenBuiltInPrompts
        val favIds = PromptRepository.favoritePromptIds()

        val allPrompts = PromptRepository.allPromptsIncludingHidden()
        val visiblePrompts = if (showHidden) allPrompts
            else allPrompts.filter { !isPromptHidden(it, hiddenPromptIds) }

        fun toVd(p: AgentPrompt) = PromptVd(
            id = p.id.toString(),
            name = p.name,
            description = p.description ?: "",
            categoryId = PromptRepository.getCategoryForPrompt(p)?.id?.toString(),
            isBuiltIn = PromptRepository.isBuiltIn(p.id),
            isReadOnly = PromptRepository.isReadOnly(p.id),
            isFavorite = p.id in favIds,
            isHidden = isPromptHidden(p, hiddenPromptIds),
        )

        val grouped = visiblePrompts.groupBy { PromptRepository.getCategoryForPrompt(it) }
        val result = mutableListOf<PromptGroupVd>()

        // Virtual "Favorites" group first (only when non-empty)
        val favoritePrompts = visiblePrompts.filter { it.id in favIds }
        if (favoritePrompts.isNotEmpty()) {
            result.add(PromptGroupVd(category = null, isFavorites = true, prompts = favoritePrompts.map(::toVd)))
        }
        // Uncategorized bucket
        grouped[null]?.let { result.add(PromptGroupVd(category = null, isFavorites = false, prompts = it.map(::toVd))) }
        // Then all categories in orderNumber order (built-in + user, empty user categories included)
        for (cat in PromptRepository.allCategories().sortedBy { it.orderNumber }) {
            val hiddenCat = PromptRepository.isCategoryHidden(cat)
            if (hiddenCat && !showHidden) continue
            val vd = toCategoryVd(cat, hiddenCat)
            val catPrompts = grouped[cat]
            if (catPrompts != null) {
                result.add(PromptGroupVd(category = vd, isFavorites = false, prompts = catPrompts.map(::toVd)))
            } else if (!BuiltInPrompts.isBuiltInCategory(cat.id)) {
                // Show empty user categories so the user can manage them (classic parity).
                result.add(PromptGroupVd(category = vd, isFavorites = false, prompts = emptyList()))
            }
        }
        return result
    }

    /** A built-in prompt hidden via `hiddenBuiltInPrompts`; user/add-on prompts are never hidden this way. */
    private fun isPromptHidden(p: AgentPrompt, hiddenPromptIds: Set<IdType>): Boolean =
        PromptRepository.isBuiltIn(p.id) && p.id in hiddenPromptIds

    private fun toCategoryVd(cat: PromptCategory, hidden: Boolean = PromptRepository.isCategoryHidden(cat)) =
        PromptCategoryVd(
            id = cat.id.toString(),
            name = cat.name,
            isBuiltIn = BuiltInPrompts.isBuiltInCategory(cat.id),
            isHidden = hidden,
        )

    // --- manager mutations (each mirrors classic's action + loadPrompts() reload) -----------------

    override fun setShowHidden(v: Boolean) {
        _showHidden.value = v
        refresh()
    }

    override fun toggleFavorite(promptId: String) {
        PromptRepository.toggleFavorite(IdType(promptId))
        refresh()
    }

    override fun setPromptHidden(promptId: String, hidden: Boolean) {
        PromptRepository.setBuiltInPromptHidden(IdType(promptId), hidden)
        refresh()
    }

    override fun setCategoryHidden(categoryId: String, hidden: Boolean) {
        val id = IdType(categoryId)
        val cat = PromptRepository.categoryById(id) ?: return
        if (BuiltInPrompts.isBuiltInCategory(id)) {
            val current = settings.hiddenBuiltInCategories
            settings.hiddenBuiltInCategories = if (hidden) current + id else current - id
        } else {
            PromptRepository.updateCategory(cat.copy(hidden = hidden))
        }
        refresh()
    }

    override fun deletePrompt(promptId: String) {
        val id = IdType(promptId)
        if (PromptRepository.isReadOnly(id)) return
        val prompt = PromptRepository.promptById(id) ?: return
        PromptRepository.deletePrompt(prompt)
        refresh()
    }

    override fun deleteCategory(categoryId: String, deletePrompts: Boolean) {
        // deletePrompts=false → keep prompts (moved to the uncategorized bucket); =true → cascade.
        // PromptRepository.deleteCategory implements exactly this two-outcome split.
        PromptRepository.deleteCategory(IdType(categoryId), deletePrompts)
        refresh()
    }

    override fun movePrompt(promptId: String, up: Boolean) {
        val id = IdType(promptId)
        if (PromptRepository.isReadOnly(id)) return
        // Adjacency among the non-read-only siblings of the SAME (non-Favorites) group, in the
        // already-published display order (mirrors classic swapPromptOrder / index-in-siblings).
        val group = _groups.value.firstOrNull { g -> !g.isFavorites && g.prompts.any { it.id == promptId } } ?: return
        val siblings = group.prompts.filter { !it.isReadOnly }
        val idx = siblings.indexOfFirst { it.id == promptId }
        val neighborIdx = if (up) idx - 1 else idx + 1
        if (idx < 0 || neighborIdx !in siblings.indices) return
        val a = PromptRepository.promptById(id) ?: return
        val b = PromptRepository.promptById(IdType(siblings[neighborIdx].id)) ?: return
        PromptRepository.updatePrompt(a.copy(orderNumber = b.orderNumber))
        PromptRepository.updatePrompt(b.copy(orderNumber = a.orderNumber))
        refresh()
    }

    override fun moveCategory(categoryId: String, up: Boolean) {
        // Adjacency among all category groups in display order (classic categoryGroups); only a
        // non-built-in category is movable, but the swap partner may be built-in (a no-op DB update).
        val categories = _groups.value.mapNotNull { it.category }
        val idx = categories.indexOfFirst { it.id == categoryId }
        if (idx < 0 || categories[idx].isBuiltIn) return
        val neighborIdx = if (up) idx - 1 else idx + 1
        if (neighborIdx !in categories.indices) return
        val a = PromptRepository.categoryById(IdType(categoryId)) ?: return
        val b = PromptRepository.categoryById(IdType(categories[neighborIdx].id)) ?: return
        val tmp = a.orderNumber
        PromptRepository.updateCategory(a.copy(orderNumber = b.orderNumber))
        PromptRepository.updateCategory(b.copy(orderNumber = tmp))
        refresh()
    }

    override fun createCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        PromptRepository.insertCategory(PromptCategory(name = trimmed))
        refresh()
    }

    override fun renameCategory(categoryId: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val cat = PromptRepository.categoryById(IdType(categoryId)) ?: return
        PromptRepository.updateCategory(cat.copy(name = trimmed))
        refresh()
    }

    override fun refresh() {
        _configured.value = CommonUtils.settings.llmConfigured
        _groups.value = buildGroups()
    }

    // --- PromptEdit support -----------------------------------------------------------------------

    override fun prompt(id: String): PromptEditData? =
        PromptRepository.promptById(IdType(id))?.let(::toEditData)

    override fun newPromptData(template: String?, defaultContext: String?): PromptEditData {
        val contexts = defaultContext
            ?.let { runCatching { PromptContext.valueOf(it) }.getOrNull() }
            ?.let { setOf(it.name) } ?: emptySet()
        return PromptEditData(
            id = null,
            name = "",
            description = "",
            template = template ?: "",
            categoryId = null,
            contexts = contexts,
            isTextTransformation = false,
            permissionMode = null,
            allowedTools = emptySet(),
            deniedTools = emptySet(),
            modelOverrideId = null,
            maxIterations = null,
            strictContextMatching = true,
            specifyBeforeRun = false,
            noDocumentCreation = false,
            autoIncludeDocuments = false,
            autoIncludeCommentaries = false,
            isReadOnly = false,
            isBuiltIn = false,
            bibleOnly = false,
        )
    }

    private fun toEditData(p: AgentPrompt): PromptEditData = PromptEditData(
        id = p.id.toString(),
        name = p.name,
        description = p.description ?: "",
        template = p.promptTemplate,
        categoryId = PromptRepository.getCategoryForPrompt(p)?.id?.toString(),
        contexts = p.showIn.map { it.name }.toSet(),
        isTextTransformation = p.isTextTransformation,
        permissionMode = p.permissionMode?.name,
        allowedTools = p.allowedTools?.map { it.name }?.toSet() ?: emptySet(),
        deniedTools = p.deniedTools?.map { it.name }?.toSet() ?: emptySet(),
        modelOverrideId = p.configuredModelId?.toString(),
        maxIterations = p.maxIterations,
        strictContextMatching = p.strictContextMatching,
        specifyBeforeRun = p.specifyBeforeRun,
        noDocumentCreation = p.noDocumentCreation,
        autoIncludeDocuments = p.autoIncludeDocuments,
        autoIncludeCommentaries = p.autoIncludeCommentaries,
        isReadOnly = PromptRepository.isReadOnly(p.id),
        isBuiltIn = PromptRepository.isBuiltIn(p.id),
        bibleOnly = p.bibleOnly,
    )

    override fun categories(): List<PromptCategoryVd> =
        PromptRepository.allCategories().sortedBy { it.orderNumber }.map { toCategoryVd(it) }

    override fun toolsByCategory(): List<Pair<ToolCategoryVd, List<ToolVd>>> =
        ToolRegistry.getConfigurableToolsByCategory().map { (category, tools) ->
            ToolCategoryVd(id = category.name, displayName = ToolRegistry.getCategoryDisplayName(category)) to
                tools.map { toToolVd(it) }
        }

    private fun toToolVd(tool: Tool) = ToolVd(
        id = tool.agentTool.name,
        displayName = ToolRegistry.getDisplayName(tool),
        description = tool.description,
        requiresPermission = tool.requiresPermission,
        categoryId = tool.category.name,
    )

    /**
     * Resolves a tool's current GLOBAL default for the PROMPT-mode "Default (…)" label
     * ([net.bible.sharedui.ai.ToolPermissionList] reads the returned token's `.name`). Uses the SAME
     * resolution as [ToolPermissionServiceImpl.permissionFor] (the GLOBAL screen), so the label
     * agrees with what that screen would show:
     * - explicitly denied → [ToolPermission.DENY] (write) / [ToolPermission.DISABLED] (read);
     * - explicitly allowed → [ToolPermission.ALLOW] (write) / [ToolPermission.ENABLED] (read);
     * - otherwise the neutral default — a **write** tool is [ToolPermission.ASK] (so PromptEdit shows
     *   "Ask (default)", the truthful three-way write default, not a misleading "Default (allowed)"),
     *   a **read** tool is [ToolPermission.ENABLED].
     *
     * The shared label control maps an `ASK` token to the "Ask (default)" string
     * (`ToolPermissionList.defaultOptionLabel`), so no new resource strings are needed.
     */
    override fun globalToolPermission(toolId: String): ToolPermission {
        val agentTool = runCatching { AgentTool.valueOf(toolId) }.getOrNull()
        val isWrite = agentTool?.let { ToolRegistry.get(it)?.requiresPermission } ?: false
        val denied = agentTool != null && agentTool in settings.permanentlyDeniedTools
        val allowed = agentTool != null && agentTool in settings.permanentlyAllowedTools
        return when {
            denied -> if (isWrite) ToolPermission.DENY else ToolPermission.DISABLED
            allowed -> if (isWrite) ToolPermission.ALLOW else ToolPermission.ENABLED
            else -> if (isWrite) ToolPermission.ASK else ToolPermission.ENABLED
        }
    }

    /** Model-override options, mirroring classic `PromptEditActivity.setupModelPreference` verbatim. */
    override fun modelChoices(): List<SettingsItem.Choice> {
        val defaultModelId = settings.defaultModelId
        val models = modelDao.all().sortedByDescending { it.id == defaultModelId }
        val providerConfigs = providerDao.all().associateBy { it.id }

        val choices = mutableListOf<SettingsItem.Choice>()

        // First entry: "" = default/null.
        val defaultModel = defaultModelId?.let { modelDao.getById(it) }
        val defaultProvider = defaultModel?.let { providerConfigs[it.providerConfigId] }
        val defaultSuffix = if (defaultModel != null && defaultProvider != null) {
            " (${defaultModel.modelId} — ${defaultProvider.displayName})"
        } else ""
        choices.add(SettingsItem.Choice(value = "", label = application.getString(R.string.prompt_model_default) + defaultSuffix))

        for (model in models) {
            val provider = providerConfigs[model.providerConfigId]
            val providerName = provider?.displayName ?: "?"
            val pricing = ModelPricing(
                model.inputPricePerMillion, model.outputPricePerMillion,
                model.cacheCreationPricePerMillion, model.cacheReadPricePerMillion,
            )
            val priceStr = if (pricing.inputPerMillion > 0 || pricing.outputPerMillion > 0) {
                " (${LlmCostTracker.formatPriceCompact(pricing.inputPerMillion)}/${LlmCostTracker.formatPriceCompact(pricing.outputPerMillion)})"
            } else ""
            val prefix = if (LlmProvider.isModelSupported(model.modelId)) "✓ " else ""
            choices.add(SettingsItem.Choice(value = model.id.toString(), label = "$prefix${model.modelId} — $providerName$priceStr"))
        }
        return choices
    }

    override fun savePrompt(data: PromptEditData): String {
        val existing = data.id?.let { PromptRepository.promptById(IdType(it)) }
        val allowedSet = data.allowedTools.mapNotNull { toAgentTool(it) }.toSet()
        val deniedSet = data.deniedTools.mapNotNull { toAgentTool(it) }.toSet()
        // Classic parity: no override at all (null) unless the user set something on either side.
        val hasOverride = allowedSet.isNotEmpty() || deniedSet.isNotEmpty()

        val prompt = (existing ?: AgentPrompt()).copy(
            name = data.name.trim(),
            description = data.description.trim().ifBlank { null },
            promptTemplate = data.template,
            showIn = data.contexts.mapNotNull { runCatching { PromptContext.valueOf(it) }.getOrNull() }.toSet(),
            strictContextMatching = data.strictContextMatching,
            permissionMode = data.permissionMode?.let { runCatching { PermissionMode.valueOf(it) }.getOrNull() },
            allowedTools = if (hasOverride) allowedSet else null,
            deniedTools = if (hasOverride) deniedSet else null,
            configuredModelId = data.modelOverrideId?.let { IdType(it) },
            specifyBeforeRun = data.specifyBeforeRun,
            noDocumentCreation = data.noDocumentCreation,
            maxIterations = data.maxIterations,
            autoIncludeDocuments = data.autoIncludeDocuments,
            autoIncludeCommentaries = data.autoIncludeCommentaries,
            bibleOnly = data.bibleOnly,
            isTextTransformation = data.isTextTransformation,
            categoryId = data.categoryId?.let { IdType(it) },
        )
        if (existing == null) PromptRepository.insertPrompt(prompt) else PromptRepository.updatePrompt(prompt)
        refresh()
        return prompt.id.toString()
    }

    override fun deletePromptById(id: String) {
        val prompt = PromptRepository.promptById(IdType(id)) ?: return
        if (PromptRepository.isReadOnly(prompt.id)) return
        PromptRepository.deletePrompt(prompt)
        refresh()
    }

    override fun copyPrompt(id: String): String {
        val newId = PromptRepository.copyPrompt(IdType(id))
        refresh()
        return (newId ?: IdType(id)).toString()
    }

    override fun setBuiltinPromptModelOverride(promptId: String, modelId: String?) {
        // Mirrors classic PromptEditActivity.saveBuiltinOverride: upsert a BuiltinPromptOverride row
        // for the built-in prompt; a null configuredModelId row is equivalent to "use global default"
        // (PromptRepository.applyOverride copies the null through), i.e. the override is cleared.
        overrideDao.upsert(BuiltinPromptOverride(id = IdType(promptId), configuredModelId = modelId?.let { IdType(it) }))
        refresh()
    }

    private fun toAgentTool(name: String): AgentTool? = runCatching { AgentTool.valueOf(name) }.getOrNull()
}
