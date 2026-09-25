package net.bible.sharedcore.bookmark

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the host must do when the editor closes. */
sealed interface LabelEditResult {
    data class Save(val state: LabelEditState) : LabelEditResult
    data class Delete(val state: LabelEditState, val deleteOrphaned: Boolean) : LabelEditResult
    data object Cancel : LabelEditResult
}

/** Delete UX the host must show: a plain confirm, or the 3-way orphaned-bookmarks dialog. */
sealed interface DeletePrompt {
    data object Confirm : DeletePrompt
    data class Orphaned(val count: Int) : DeletePrompt
}

class LabelEditController(
    initial: LabelEditState,
    private val service: LabelEditService,
    @Suppress("unused") private val scope: CoroutineScope,
    private val onFinish: (LabelEditResult) -> Unit,
) {
    private val initialState = initial
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<LabelEditState> = _state.asStateFlow()

    private val _deletePrompt = MutableStateFlow<DeletePrompt?>(null)
    val deletePrompt: StateFlow<DeletePrompt?> = _deletePrompt.asStateFlow()

    /** Classic `requestUp`'s discard-changes question (Task 13), kept independent of [deletePrompt]:
     *  the two can never be showing at once (one is the up/back gesture, the other the Delete
     *  button), and unlike [deletePrompt] this one needs no Android string resource -- the message
     *  already has a `LocalStrings` entry, so the screen renders it directly. */
    private val _discardPrompt = MutableStateFlow(false)
    val discardPrompt: StateFlow<Boolean> = _discardPrompt.asStateFlow()

    fun setName(v: String) { if (_state.value.nameEditable) _state.value = _state.value.copy(name = v) }
    fun setColor(argb: Int) { _state.value = _state.value.copy(color = argb) }
    fun setCustomIcon(name: String?) { _state.value = _state.value.copy(customIcon = name) }

    fun setSelectionStyle(style: BookmarkDisplayStyle) { _state.value = _state.value.copy(selectionStyle = style) }
    fun setWholeVerseStyle(style: BookmarkDisplayStyle?) { _state.value = _state.value.copy(wholeVerseStyle = style) }
    fun toggleFavourite() { _state.value = _state.value.copy(favourite = !_state.value.favourite) }

    fun toggleThisBookmarkSelected() {
        val selected = !_state.value.thisBookmarkSelected
        _state.value = _state.value.copy(thisBookmarkSelected = selected, thisBookmarkPrimary = if (!selected) false else _state.value.thisBookmarkPrimary)
    }
    fun toggleThisBookmarkPrimary() = with(_state.value) { if (thisBookmarkPrimaryEnabled) _state.value = copy(thisBookmarkPrimary = !thisBookmarkPrimary) }
    fun toggleAutoAssign() {
        val on = !_state.value.autoAssign
        _state.value = _state.value.copy(autoAssign = on, autoAssignPrimary = if (!on) false else _state.value.autoAssignPrimary)
    }
    fun toggleAutoAssignPrimary() = with(_state.value) { if (autoAssignPrimaryEnabled) _state.value = copy(autoAssignPrimary = !autoAssignPrimary) }
    fun setOverrideMode(mode: OverrideMode) { _state.value = _state.value.copy(overrideMode = mode) }

    fun isDirty(): Boolean = _state.value != initialState

    fun save() = onFinish(LabelEditResult.Save(_state.value.normalizedForSave()))
    fun cancel() = onFinish(LabelEditResult.Cancel)

    /** Classic `requestUp` (`LabelEditComposeActivity.kt:256-266`): a dirty editor asks before
     *  throwing the edits away, a clean one just leaves. */
    fun requestUp() {
        if (isDirty()) _discardPrompt.value = true else cancel()
    }
    fun confirmDiscard() {
        _discardPrompt.value = false
        cancel()
    }
    fun dismissDiscardPrompt() { _discardPrompt.value = false }

    fun requestDelete() {
        val count = service.orphanedBookmarkCount(_state.value.labelId)
        _deletePrompt.value = if (count > 0) DeletePrompt.Orphaned(count) else DeletePrompt.Confirm
    }
    fun dismissDeletePrompt() { _deletePrompt.value = null }
    fun confirmDelete(deleteOrphaned: Boolean) {
        _deletePrompt.value = null
        onFinish(LabelEditResult.Delete(_state.value, deleteOrphaned))
    }
}
