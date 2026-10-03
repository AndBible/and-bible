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

package net.bible.sharedcore.ai

/**
 * Aggregate state of one category's read (or write) tools in
 * [net.bible.sharedui.ai.ToolPermissionList]'s category header.
 *
 * Batch 17f replaced the old three-value `CategoryToggleState` (ALL_ON / MIXED / ALL_OFF) with the
 * actual shared [ToolPermission], because the header now renders the SAME control its rows do and
 * therefore has to distinguish states the old enum collapsed: a write category that is uniformly
 * ASK and one that is uniformly ALLOW were both "ALL_ON".
 */
sealed interface CategoryPermissionState {
    /** Every tool of this kind in the category carries [permission]. */
    data class Uniform(val permission: ToolPermission) : CategoryPermissionState

    /** The tools disagree; the header's control shows its dedicated "mixed" affordance. */
    data object Mixed : CategoryPermissionState
}

/**
 * @return `null` when [tools] is empty — the category has no tools of this read/write kind, so the
 *   caller hides that control entirely (mirrors classic `ToolPermissionListBuilder`'s `View.GONE`).
 */
fun categoryPermissionState(
    tools: List<ToolVd>,
    permissionFor: (toolId: String) -> ToolPermission,
): CategoryPermissionState? {
    if (tools.isEmpty()) return null
    val distinct = tools.mapTo(mutableSetOf()) { permissionFor(it.id) }
    return distinct.singleOrNull()?.let { CategoryPermissionState.Uniform(it) } ?: CategoryPermissionState.Mixed
}
