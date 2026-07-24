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

package net.bible.sharedcore.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Controller for the custom-repositories list screen (classic `CustomRepositories` activity).
 * Loads the current list from [service] on construction (mirroring classic `onCreate ->
 * reloadData()`) and lets the host feed back an editor round-trip via [applyResult] -- insert
 * (id == 0), update (existing id), or delete, then a refresh. A rejected duplicate-name
 * upsert (classic: an `InstallManager` built-in-name clash or a `SQLiteConstraintException`,
 * both surfaced by the host as `service.upsert` returning `false`) fires [onDuplicate] instead
 * of silently applying, mirroring classic's `ToastEvent(duplicate_custom_repository)`.
 */
class CustomRepositoryController(
    private val service: CustomRepositoryService,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(CustomRepoListState())
    val state: StateFlow<CustomRepoListState> = _state.asStateFlow()

    var onDuplicate: (name: String) -> Unit = {}

    init { refresh() }

    fun refresh() {
        scope.launch {
            val rows = service.list().map { RepoRow(it.id, it.name, it.description) }
            _state.value = CustomRepoListState(rows)
        }
    }

    fun applyResult(result: RepositoryResult) {
        if (result.cancel) return
        val repo = result.repository ?: return
        scope.launch {
            if (result.delete) {
                if (repo.id != 0L) service.delete(repo)
            } else {
                val applied = service.upsert(repo)
                if (!applied) {
                    onDuplicate(repo.name)
                    return@launch
                }
            }
            refresh()
        }
    }
}
