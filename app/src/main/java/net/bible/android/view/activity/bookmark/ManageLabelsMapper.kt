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
package net.bible.android.view.activity.bookmark

import net.bible.android.database.IdType
import net.bible.sharedcore.bookmark.ManageLabelsMode

/**
 * Pure `ManageLabelsData` <-> shared-controller mapper. This is the one place Room-adjacent
 * `IdType` sets meet the portable `String`-keyed seeds/results consumed by the shared
 * `ManageLabelsController`. Mirrors classic `ManageLabels`' `data` field semantics verbatim:
 * [seedSelected]/[seedAutoAssign]/[seedAutoAssignPrimary]/[seedBookmarkPrimary] build the
 * controller's initial state from a freshly-decoded [ManageLabelsContract.ManageLabelsData]; [applyResult]
 * writes the controller's final result snapshot back in place (mode/isWindow untouched, same as
 * classic `saveAndExit`); [applyReset] sets the `reset` flag (classic `reset()`).
 */
object ManageLabelsMapper {

    /**
     * F97 (fix batch 3 §2.3.1): the HIDELABELS payload -- the hidden-label set and nothing else.
     * Deliberately NOT `applyFrom(workspaceSettings)`: that copied the ACTIVE workspace's
     * auto-assign list in, so a label editor opened from another workspace's hide list showed (and
     * the return path wrote back) the active workspace's state. Classic had the same leak.
     */
    fun hideLabelsData(selected: Collection<IdType>, isWindow: Boolean): ManageLabelsContract.ManageLabelsData =
        ManageLabelsContract.ManageLabelsData(
            mode = ManageLabelsContract.Mode.HIDELABELS,
            selectedLabels = selected.toMutableSet(),
            isWindow = isWindow,
        )

    /**
     * F97: whether the label editor reached from [mode] may show and write workspace state (the
     * "This workspace" section and its per-label override). Hiding labels is not the place to edit
     * another workspace's auto-assign, and the editor has no way to know which workspace a hide list
     * belongs to.
     */
    fun labelEditHasWorkspaceContext(mode: ManageLabelsContract.Mode): Boolean =
        mode != ManageLabelsContract.Mode.HIDELABELS

    /** [ManageLabelsContract.Mode] -> the portable [ManageLabelsMode] the shared controller/screen use. */
    fun toMode(mode: ManageLabelsContract.Mode): ManageLabelsMode = when (mode) {
        ManageLabelsContract.Mode.STUDYPAD -> ManageLabelsMode.STUDYPAD
        ManageLabelsContract.Mode.WORKSPACE -> ManageLabelsMode.WORKSPACE
        ManageLabelsContract.Mode.ASSIGN -> ManageLabelsMode.ASSIGN
        ManageLabelsContract.Mode.HIDELABELS -> ManageLabelsMode.HIDELABELS
    }

    fun seedSelected(data: ManageLabelsContract.ManageLabelsData): Set<String> =
        data.selectedLabels.map { it.toString() }.toSet()

    fun seedAutoAssign(data: ManageLabelsContract.ManageLabelsData): Set<String> =
        data.autoAssignLabels.map { it.toString() }.toSet()

    fun seedAutoAssignPrimary(data: ManageLabelsContract.ManageLabelsData): String? =
        data.autoAssignPrimaryLabel?.toString()

    fun seedBookmarkPrimary(data: ManageLabelsContract.ManageLabelsData): String? =
        data.bookmarkPrimaryLabel?.toString()

    /**
     * Writes the controller's final result snapshot back into [data] (mutating the mutable sets
     * in place, same as classic `saveAndExit`/`editLabel` — [ManageLabelsContract.ManageLabelsData.mode]
     * and `isWindow` are untouched) and returns [data] for chaining. Callers pass the (possibly
     * new-label-id-remapped) `controller.resultXxx()` snapshots.
     */
    fun applyResult(
        data: ManageLabelsContract.ManageLabelsData,
        selected: Set<String>,
        autoAssign: Set<String>,
        changed: Set<String>,
        deleted: Set<String>,
        deletedWithOrphaned: Set<String>,
        autoAssignPrimary: String?,
        bookmarkPrimary: String?,
    ): ManageLabelsContract.ManageLabelsData {
        fun replace(target: MutableSet<IdType>, source: Set<String>) {
            target.clear()
            target.addAll(source.map { IdType(it) })
        }
        replace(data.selectedLabels, selected)
        replace(data.autoAssignLabels, autoAssign)
        replace(data.changedLabels, changed)
        replace(data.deletedLabels, deleted)
        replace(data.deletedLabelsWithOrphanedBookmarks, deletedWithOrphaned)
        data.autoAssignPrimaryLabel = autoAssignPrimary?.let { IdType(it) }
        data.bookmarkPrimaryLabel = bookmarkPrimary?.let { IdType(it) }
        return data
    }

    /** Sets the reset flag (classic `reset()`: `data.reset = true`). */
    fun applyReset(data: ManageLabelsContract.ManageLabelsData): ManageLabelsContract.ManageLabelsData {
        data.reset = true
        return data
    }
}
