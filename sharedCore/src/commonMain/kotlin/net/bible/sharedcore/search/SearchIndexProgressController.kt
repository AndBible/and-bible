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
package net.bible.sharedcore.search

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Plain view-data for one indexing job row (percent 0..100; indeterminate when 0). */
data class ProgressJob(val id: String, val label: String, val percent: Int, val indeterminate: Boolean)

enum class SearchIndexError { FAILED }

/**
 * Framework-free progress-state holder for the search-index screen. The host bridges JSword's
 * JobManager/WorkListener to [setJobs]; the completion/navigation logic stays in the host (JSword +
 * Intent). [onHide] is the "Continue in background" action.
 */
class SearchIndexProgressController(
    private val onHide: () -> Unit,
) {
    private val _jobs = MutableStateFlow<List<ProgressJob>>(emptyList())
    val jobs: StateFlow<List<ProgressJob>> = _jobs.asStateFlow()

    private val _noTasks = MutableStateFlow(false)
    val noTasks: StateFlow<Boolean> = _noTasks.asStateFlow()

    private val _error = MutableStateFlow<SearchIndexError?>(null)
    val error: StateFlow<SearchIndexError?> = _error.asStateFlow()

    fun setJobs(jobs: List<ProgressJob>) {
        _jobs.value = jobs
        // A job hides the no-tasks line immediately, but its absence does NOT reveal the line here:
        // classic parity delays revealing "no tasks running" until [revealNoTasksIfIdle] fires (~4s).
        if (jobs.isNotEmpty()) _noTasks.value = false
    }

    /** Reveal the "no tasks running" line only if still idle (called after the ~4s classic delay). */
    fun revealNoTasksIfIdle() { _noTasks.value = _jobs.value.isEmpty() }

    fun showError() { _error.value = SearchIndexError.FAILED }
    fun dismissError() { _error.value = null }
    fun hide() { onHide() }
}
