package net.bible.sharedcore.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Staging brain for `AiDocumentFilterActivity` (per-document AI tool access filter).
 *
 * [DocumentFilterService.groups] is read once as the catalog (category + doc identity/name); the
 * excluded set is tracked locally as a working set (edited via [toggle]/[resetAll]), and [state]
 * recomputes each [AiDocVd.allowed] against that working set on every edit.
 *
 * Classic save-on-apply: nothing reaches [DocumentFilterService.setExcluded] until [save]. Unlike
 * [GlobalToolPermissionsController.resetAll] (backed by a dedicated persisted service action),
 * [DocumentFilterService] has no `resetAll` — so [resetAll] here is a purely local edit (clears
 * the working excluded set, i.e. "allow everything"), staged like any other [toggle] until [save].
 */
class AiDocumentFilterController(
    private val service: DocumentFilterService,
    private val scope: CoroutineScope,
) {
    private val groups: List<AiDocGroupVd> = service.groups()

    private fun seedExcluded(): Set<String> =
        groups.flatMap { it.docs }.filterNot { it.allowed }.mapTo(mutableSetOf()) { it.initials }

    private var initial: Set<String> = seedExcluded()

    private val _excluded = MutableStateFlow(initial)

    private val _state = MutableStateFlow(applyExcluded(initial))
    val state: StateFlow<List<AiDocGroupVd>> = _state.asStateFlow()

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    /** Flips one document's allowed/excluded state in the working set only. */
    fun toggle(initials: String) {
        val excluded = _excluded.value
        publish(if (initials in excluded) excluded - initials else excluded + initials)
    }

    /** Clears the working excluded set (all documents allowed), staged until [save]. */
    fun resetAll() = publish(emptySet())

    /** Persists the working excluded set (classic "Save" button), then re-baselines dirty tracking. */
    fun save() {
        service.setExcluded(_excluded.value)
        initial = _excluded.value
        _isDirty.value = false
    }

    private fun publish(next: Set<String>) {
        _excluded.value = next
        _state.value = applyExcluded(next)
        _isDirty.value = next != initial
    }

    private fun applyExcluded(excluded: Set<String>): List<AiDocGroupVd> =
        groups.map { group -> group.copy(docs = group.docs.map { it.copy(allowed = it.initials !in excluded) }) }
}
