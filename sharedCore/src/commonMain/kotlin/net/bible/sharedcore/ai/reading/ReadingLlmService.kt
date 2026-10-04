package net.bible.sharedcore.ai.reading

/** One selectable prompt row in the reading-view prompt selector. */
data class ReadingPromptVd(
    val id: String,
    val name: String,
    val description: String,
    val isFavorite: Boolean,
    val specifyBeforeRun: Boolean,
)

/**
 * One collapsible group of prompts in the reading-view prompt selector, in classic display order:
 * favourites (if any) first, then uncategorized, then each named category.
 */
data class ReadingPromptGroupVd(
    val categoryName: String,
    val categoryId: String?,     // null = uncategorized; special FAVORITES_CATEGORY_ID for favourites
    val isFavorites: Boolean,
    val collapsed: Boolean,
    val prompts: List<ReadingPromptVd>,
)

/** One configured LLM model row for the reading-view model-selection dialog. */
data class ReadingModelVd(
    val id: String,
    val modelId: String,
    val providerName: String,
    val isDefault: Boolean,
    val supported: Boolean,
)

/**
 * Reading-view seam onto the AI/LLM subsystem, wrapping `net.bible.service.llm.PromptRepository` /
 * `LlmModelService` / `GlobalAiSettings` / `AgentPrompt` for the reading-view AI dialogs
 * (prompt selector, ask-model-before-run, model selection, regenerate).
 */
interface ReadingLlmService {
    /** Grouped prompts for this context+documentCategory, in classic order: favourites (if any),
     *  uncategorized, then categorized; each group's `collapsed` seeded from persisted state
     *  (favourites always expanded). Empty list ⇒ no prompts to show. */
    suspend fun promptGroupsFor(contextId: String, docCategoryId: String?): List<ReadingPromptGroupVd>

    /** Mirrors `PromptRepository.toggleFavorite` / `GlobalAiSettings.favoritePrompts`. */
    suspend fun toggleFavorite(promptId: String)

    /** Mirrors `LlmModelService`'s configured-model list, for the model-selection dialog. */
    suspend fun configuredModels(): List<ReadingModelVd>

    /** Mirrors `GlobalAiSettings.askModelBeforeRun`. */
    fun askModelBeforeRun(): Boolean

    /** True when the ask-model dialog is due for this prompt: askModelBeforeRun && the prompt has no model override. */
    suspend fun promptRequiresModelChoice(promptId: String): Boolean

    /** Persist the chosen model as this prompt's default (built-in vs user handled in the impl). */
    suspend fun setPromptModelDefault(promptId: String, modelId: String)

    /** True when regenerate should show the model picker: askModelBeforeRun && the page's source prompt has no override. */
    suspend fun regenerateRequiresModelChoice(pageId: String): Boolean

    /** Mirrors the per-context/category collapsed-group persisted state. */
    fun isCategoryCollapsed(contextId: String, categoryId: String?): Boolean

    /** Mirrors the per-context/category collapsed-group persisted state. */
    fun setCategoryCollapsed(contextId: String, categoryId: String?, collapsed: Boolean)

    companion object { const val FAVORITES_CATEGORY_ID = "__favorites__" }
}
