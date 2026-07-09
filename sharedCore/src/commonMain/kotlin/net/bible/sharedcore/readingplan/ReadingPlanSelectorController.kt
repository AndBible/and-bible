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
package net.bible.sharedcore.readingplan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Plain view-data for one reading-plan row. [planCode] is the opaque stable id. */
data class PlanEntry(val planCode: String, val name: String, val description: String)

/** Shared error marker for the reading-plan screens (host resolves the cause). */
enum class ReadingPlanError { FAILED }

/**
 * Framework-free controller for the reading-plan chooser. [loadPlans] supplies already-resolved
 * rows; [hasDuplicates] reports the user/internal plan-name clash; [onSelect]/[onReset] are host
 * seams (start the plan + finish / reset the plan). No Android/JSword types here.
 */
class ReadingPlanSelectorController(
    private val loadPlans: () -> List<PlanEntry>,
    private val hasDuplicates: () -> Boolean,
    private val onSelect: (String) -> Unit,
    private val onReset: (String) -> Unit,
) {
    private val _plans = MutableStateFlow<List<PlanEntry>>(emptyList())
    val plans: StateFlow<List<PlanEntry>> = _plans.asStateFlow()

    private val _duplicateWarning = MutableStateFlow(false)
    val duplicateWarning: StateFlow<Boolean> = _duplicateWarning.asStateFlow()

    private val _error = MutableStateFlow<ReadingPlanError?>(null)
    val error: StateFlow<ReadingPlanError?> = _error.asStateFlow()

    init { load() }

    fun load() {
        try {
            _plans.value = loadPlans()
            _duplicateWarning.value = hasDuplicates()
        } catch (e: Exception) {
            _error.value = ReadingPlanError.FAILED
        }
    }

    fun select(planCode: String) = onSelect(planCode)

    fun reset(planCode: String) {
        try {
            onReset(planCode)
            load()
        } catch (e: Exception) {
            _error.value = ReadingPlanError.FAILED
        }
    }

    fun dismissError() { _error.value = null }
    fun dismissDuplicateWarning() { _duplicateWarning.value = false }
}
