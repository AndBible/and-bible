package net.bible.sharedcore.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Staging brain for the prompt manager screen (`PromptManagerActivity`), listing prompts grouped
 * by category (+ a virtual Favorites group) with per-row actions (favorite/hide/delete/reorder)
 * and per-category actions (hide/delete/reorder/rename), plus creating a new category. Backed
 * directly by [PromptService]'s `StateFlow`s (no local mirror needed, mirroring
 * `AiProvidersController`'s pattern) since the service already publishes exactly the shape the
 * screen needs. `onOpenPrompt`/`onNewPrompt`/`onOpenConnectionSettings` are host-nav lambdas (the
 * host maps them to the PromptEdit screen and the AI connection settings screen respectively),
 * kept out of [PromptService] the same way [AiConnectionSettingsController] keeps navigation as a
 * constructor-supplied callback rather than a service concern.
 */
class AiPromptsController(
    private val service: PromptService,
    private val scope: CoroutineScope,
    private val onOpenPrompt: (String) -> Unit,
    private val onNewPrompt: () -> Unit,
    private val onOpenConnectionSettings: () -> Unit,
) {
    val configured: StateFlow<Boolean> = service.configured
    val groups: StateFlow<List<PromptGroupVd>> = service.groups
    val showHidden: StateFlow<Boolean> = service.showHidden
    val hasHiddenPrompts: StateFlow<Boolean> = service.hasHiddenPrompts

    fun onSetShowHidden(v: Boolean) = service.setShowHidden(v)

    fun onToggleFavorite(promptId: String) = service.toggleFavorite(promptId)
    fun onSetPromptHidden(promptId: String, hidden: Boolean) = service.setPromptHidden(promptId, hidden)
    fun onSetCategoryHidden(categoryId: String, hidden: Boolean) = service.setCategoryHidden(categoryId, hidden)
    fun onDeletePrompt(promptId: String) = service.deletePrompt(promptId)
    fun onDeleteCategory(categoryId: String, deletePrompts: Boolean) = service.deleteCategory(categoryId, deletePrompts)
    fun onMovePrompt(promptId: String, up: Boolean) = service.movePrompt(promptId, up)
    fun onMoveCategory(categoryId: String, up: Boolean) = service.moveCategory(categoryId, up)
    fun onCreateCategory(name: String) = service.createCategory(name)
    fun onRenameCategory(categoryId: String, name: String) = service.renameCategory(categoryId, name)
    fun onCopyPrompt(promptId: String) = service.copyPrompt(promptId)
    fun onMovePromptToCategory(promptId: String, categoryId: String?) = service.movePromptToCategory(promptId, categoryId)

    // Category list for the "Move to category…" picker (a plain snapshot, not a StateFlow — mirrors
    // how PromptEditComposeActivity reads [PromptService.categories] via `remember { }`).
    fun categories(): List<PromptCategoryVd> = service.categories()

    fun onOpenPrompt(id: String) = onOpenPrompt.invoke(id)
    fun onNewPrompt() = onNewPrompt.invoke()
    fun onOpenConnectionSettings() = onOpenConnectionSettings.invoke()
}
