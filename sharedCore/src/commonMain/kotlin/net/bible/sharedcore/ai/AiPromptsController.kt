package net.bible.sharedcore.ai

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The two CSV-import destinations classic's `import_prompts_csv` chooser offered (platform-dialog
 *  removal Task 14, replacing an `AlertDialog#setItems` pick-list of the same two options). */
enum class ImportMode { EDITABLE, ADDON }

/** Which modal the prompt manager screen is currently showing (screen-local, driven by the
 *  controller). [ChooseImportMode] answers [AiPromptsController.chooseImportMode]'s suspend call;
 *  [ImportErrors] is a host-formatted error summary for the same import (Task 14). */
sealed interface AiPromptsDialog {
    data object None : AiPromptsDialog
    data object ChooseImportMode : AiPromptsDialog
    data class ImportErrors(val text: String) : AiPromptsDialog
}

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

    private val _dialog = MutableStateFlow<AiPromptsDialog>(AiPromptsDialog.None)
    val dialog: StateFlow<AiPromptsDialog> = _dialog.asStateFlow()

    /** The one in-flight [chooseImportMode] call, if any -- completed by [confirmImportMode]
     *  (a mode) or [dismissImportModeChoice]/a host-requested sheet close (`null`, cancel). */
    private var importModeDeferred: CompletableDeferred<ImportMode?>? = null

    /**
     * Classic's `importPrompts`' `.setItems(editable, add-on)` chooser (Task 14), now a suspend call
     * over the screen's own [AiPromptsDialog.ChooseImportMode] sheet state: the host's SAF import
     * flow awaits this exactly where it used to await the platform dialog's
     * `suspendCancellableCoroutine`. Returns `null` on cancel (dismiss, or an app-wide sheet request
     * pre-empting this one -- see [dismissImportModeChoice]).
     */
    suspend fun chooseImportMode(): ImportMode? {
        val deferred = CompletableDeferred<ImportMode?>()
        importModeDeferred = deferred
        _dialog.value = AiPromptsDialog.ChooseImportMode
        return deferred.await()
    }

    /** [AbChoiceSheet][net.bible.sharedui.components.AbChoiceSheet]'s `onSelect`. A no-op unless the
     *  choice sheet is actually showing -- guards a stray second answer the same way every other
     *  confirm/choose function in this batch does (Task 13 fix round 1). */
    fun confirmImportMode(mode: ImportMode) {
        if (_dialog.value != AiPromptsDialog.ChooseImportMode) return
        _dialog.value = AiPromptsDialog.None
        importModeDeferred?.complete(mode)
        importModeDeferred = null
    }

    /** The sheet's own dismiss (Cancel/scrim/back), or an app-wide sheet request pre-empting it
     *  (plan correction 11: the host calls this from `AppDialogOverlay`'s `onSheetOpening`). Same
     *  no-op guard as [confirmImportMode]. */
    fun dismissImportModeChoice() {
        if (_dialog.value != AiPromptsDialog.ChooseImportMode) return
        _dialog.value = AiPromptsDialog.None
        importModeDeferred?.complete(null)
        importModeDeferred = null
    }

    /** Classic's post-import error summary (`.setMessage(message)` in an `AlertDialog`), now an
     *  [AiPromptsDialog.ImportErrors] state -- the host still formats [text] (it needs the import
     *  result's counts/messages, `:app`-side `PromptCsvUtils` data). */
    fun showImportErrors(text: String) { _dialog.value = AiPromptsDialog.ImportErrors(text) }

    fun dismissDialog() { _dialog.value = AiPromptsDialog.None }

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
