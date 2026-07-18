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
    val configured: StateFlow<Boolean>                 // CommonUtils.settings.llmConfigured
    val groups: StateFlow<List<PromptGroupVd>>         // grouped+ordered, respects show/hide-hidden
    val showHidden: StateFlow<Boolean>
    fun setShowHidden(v: Boolean)
    fun toggleFavorite(promptId: String)
    fun setPromptHidden(promptId: String, hidden: Boolean)
    fun setCategoryHidden(categoryId: String, hidden: Boolean)
    fun deletePrompt(promptId: String)
    // deletePrompts=false: move the category's prompts to the uncategorized bucket (classic
    // "keep prompts"); deletePrompts=true: cascade-delete the prompts too (classic "and prompts").
    fun deleteCategory(categoryId: String, deletePrompts: Boolean)
    fun movePrompt(promptId: String, up: Boolean)
    fun moveCategory(categoryId: String, up: Boolean)
    fun createCategory(name: String)
    fun renameCategory(categoryId: String, name: String)
    fun refresh()
    // PromptEdit support:
    fun prompt(id: String): PromptEditData?            // full editable data
    fun newPromptData(template: String?, defaultContext: String?): PromptEditData
    fun categories(): List<PromptCategoryVd>
    fun toolsByCategory(): List<Pair<ToolCategoryVd, List<ToolVd>>>
    fun globalToolPermission(toolId: String): ToolPermission   // for the PROMPT-mode "Default(…)" label
    fun modelChoices(): List<net.bible.sharedcore.settings.SettingsItem.Choice>  // model override options
    fun savePrompt(data: PromptEditData): String       // returns saved prompt id
    fun deletePromptById(id: String)
    fun copyPrompt(id: String): String                 // returns new copy id
    // Built-in prompt "Save" path (model override only, no copy-to-customize): upserts a
    // BuiltinPromptOverride row for promptId; null modelId clears the override.
    fun setBuiltinPromptModelOverride(promptId: String, modelId: String?)
}
