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
 * Aggregate state of a category's bulk read/write toggle in
 * [net.bible.sharedui.ai.ToolPermissionList]'s `CategoryHeader` (E2/F35/F37). Mirrors classic
 * `ToolPermissionListBuilder.updateToggleState` in spirit, but distinguishes [MIXED] from [ALL_OFF]
 * (classic's plain `CheckBox` collapses both cases to "unchecked" since `isAllEnabled` is the only
 * thing it reads) so the Compose control can render a genuine three-state affordance
 * (`TriStateCheckbox`) instead of losing the "some but not all" case.
 */
enum class CategoryToggleState { ALL_ON, MIXED, ALL_OFF }

/** Permissions that count as "off" -- mirrors classic `isRowDisabled` (`checkedRadioButtonId ==
 *  R.id.radioDeny`), which is the trailing/rightmost radio option in every row layout regardless of
 *  GLOBAL vs PROMPT mode or read vs write tool kind. */
private val OFF_PERMISSIONS: Set<ToolPermission> = setOf(ToolPermission.DENY, ToolPermission.DISABLED)

/**
 * Computes the bulk-toggle state for one category's read (or write) tools, mirroring classic
 * `isAllEnabled`/`isAllDisabled` but adding the [CategoryToggleState.MIXED] case.
 *
 * @return `null` when [tools] is empty -- the category has no tools of this read/write kind, so the
 *   caller should hide the bulk control entirely (mirrors classic's `View.GONE` visibility rule for
 *   an empty `readTools`/`writeTools` list).
 */
fun categoryToggleState(tools: List<ToolVd>, permissionFor: (toolId: String) -> ToolPermission): CategoryToggleState? {
    if (tools.isEmpty()) return null
    val on = tools.map { permissionFor(it.id) !in OFF_PERMISSIONS }
    return when {
        on.all { it } -> CategoryToggleState.ALL_ON
        on.none { it } -> CategoryToggleState.ALL_OFF
        else -> CategoryToggleState.MIXED
    }
}
