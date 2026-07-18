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
 * controller's initial state from a freshly-decoded [ManageLabels.ManageLabelsData]; [applyResult]
 * writes the controller's final result snapshot back in place (mode/isWindow untouched, same as
 * classic `saveAndExit`); [applyReset] sets the `reset` flag (classic `reset()`).
 */
object ManageLabelsMapper {

    /** [ManageLabels.Mode] -> the portable [ManageLabelsMode] the shared controller/screen use. */
    fun toMode(mode: ManageLabels.Mode): ManageLabelsMode = when (mode) {
        ManageLabels.Mode.STUDYPAD -> ManageLabelsMode.STUDYPAD
        ManageLabels.Mode.WORKSPACE -> ManageLabelsMode.WORKSPACE
        ManageLabels.Mode.ASSIGN -> ManageLabelsMode.ASSIGN
        ManageLabels.Mode.HIDELABELS -> ManageLabelsMode.HIDELABELS
    }

    fun seedSelected(data: ManageLabels.ManageLabelsData): Set<String> =
        data.selectedLabels.map { it.toString() }.toSet()

    fun seedAutoAssign(data: ManageLabels.ManageLabelsData): Set<String> =
        data.autoAssignLabels.map { it.toString() }.toSet()

    fun seedAutoAssignPrimary(data: ManageLabels.ManageLabelsData): String? =
        data.autoAssignPrimaryLabel?.toString()

    fun seedBookmarkPrimary(data: ManageLabels.ManageLabelsData): String? =
        data.bookmarkPrimaryLabel?.toString()

    /**
     * Writes the controller's final result snapshot back into [data] (mutating the mutable sets
     * in place, same as classic `saveAndExit`/`editLabel` — [ManageLabels.ManageLabelsData.mode]
     * and `isWindow` are untouched) and returns [data] for chaining. Callers pass the (possibly
     * new-label-id-remapped) `controller.resultXxx()` snapshots.
     */
    fun applyResult(
        data: ManageLabels.ManageLabelsData,
        selected: Set<String>,
        autoAssign: Set<String>,
        changed: Set<String>,
        deleted: Set<String>,
        deletedWithOrphaned: Set<String>,
        autoAssignPrimary: String?,
        bookmarkPrimary: String?,
    ): ManageLabels.ManageLabelsData {
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
    fun applyReset(data: ManageLabels.ManageLabelsData): ManageLabels.ManageLabelsData {
        data.reset = true
        return data
    }
}
