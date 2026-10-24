package net.bible.sharedcore.ai

import kotlinx.coroutines.flow.StateFlow

/**
 * Host seam for the prompt manager (`PromptManagerActivity`) and the PromptEdit screen.
 * Wraps `net.bible.service.llm.PromptRepository` (allPrompts/allPromptsIncludingHidden,
 * promptsForContextGrouped, favoritePromptIds/toggleFavorite, allCategories/insertCategory/
 * updateCategory/deleteCategory, isReadOnly/isBuiltIn, setBuiltInPromptHidden/isCategoryHidden,
 * copyPrompt, setBuiltinPromptModelOverride) plus `ToolRegistry` (getConfigurableToolsByCategory)
 * and `CommonUtils.aiSettings`/`CommonUtils.settings.llmConfigured` for reorder/model-choice state.
 */
interface PromptService {
    val ready: StateFlow<Boolean>
    val configured: StateFlow<Boolean>                 // CommonUtils.settings.llmConfigured
    val groups: StateFlow<List<PromptGroupVd>>         // grouped+ordered, respects show/hide-hidden
    val showHidden: StateFlow<Boolean>
    // Whether at least one hidden built-in prompt exists, independent of [showHidden] (which merely
    // filters [groups], so [groups] alone can't answer this once hidden prompts are already filtered
    // out). Mirrors classic `AiSettingsActivity.onPrepareOptionsMenu`'s
    // `CommonUtils.aiSettings.hiddenBuiltInPrompts.isNotEmpty()` gate for the "restore hidden prompts"
    // menu item — gates the Compose overflow's show/hide-hidden toggle the same way.
    val hasHiddenPrompts: StateFlow<Boolean>
    suspend fun setShowHidden(v: Boolean)
    suspend fun toggleFavorite(promptId: String)
    suspend fun setPromptHidden(promptId: String, hidden: Boolean)
    suspend fun setCategoryHidden(categoryId: String, hidden: Boolean)
    suspend fun deletePrompt(promptId: String)
    // deletePrompts=false: move the category's prompts to the uncategorized bucket (classic
    // "keep prompts"); deletePrompts=true: cascade-delete the prompts too (classic "and prompts").
    suspend fun deleteCategory(categoryId: String, deletePrompts: Boolean)
    suspend fun movePrompt(promptId: String, up: Boolean)
    // Moves a user (non-read-only) prompt to a different category; null = uncategorized bucket.
    // Mirrors classic AiSettingsActivity.showMoveToCategoryDialog (prompt.categoryId = target;
    // PromptRepository.updatePrompt(prompt)). No-op for a read-only (built-in/add-on) prompt.
    suspend fun movePromptToCategory(promptId: String, categoryId: String?)
    suspend fun moveCategory(categoryId: String, up: Boolean)
    suspend fun createCategory(name: String)
    suspend fun renameCategory(categoryId: String, name: String)
    suspend fun refresh()
    // PromptEdit support:
    suspend fun prompt(id: String): PromptEditData?            // full editable data
    fun newPromptData(template: String?, defaultContext: String?): PromptEditData
    suspend fun categories(): List<PromptCategoryVd>
    fun toolsByCategory(): List<Pair<ToolCategoryVd, List<ToolVd>>>
    fun globalToolPermission(toolId: String): ToolPermission   // for the PROMPT-mode "Default(…)" label
    suspend fun modelChoices(): List<net.bible.sharedcore.settings.SettingsItem.Choice>  // model override options
    suspend fun savePrompt(data: PromptEditData): String       // returns saved prompt id
    suspend fun deletePromptById(id: String)
    suspend fun copyPrompt(id: String): String?                // new copy id; null if the source prompt is gone
    // Built-in prompt "Save" path (model override only, no copy-to-customize): upserts a
    // BuiltinPromptOverride row for promptId; null modelId clears the override.
    suspend fun setBuiltinPromptModelOverride(promptId: String, modelId: String?)
}
