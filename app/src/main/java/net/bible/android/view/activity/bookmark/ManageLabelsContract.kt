/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

import net.bible.android.activity.R
import net.bible.android.control.page.window.WorkspaceChanges
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities
import net.bible.service.common.CommonUtils
import kotlinx.serialization.Serializable
import android.util.Log

/**
 * The `ManageLabels` intent contract, lifted out of the activity itself because the classic
 * `ManageLabels` screen is being deleted (Batch Z-late phase 1) while four Compose screens and the
 * reading view still exchange this payload.
 *
 * Wrapped in an object rather than declared at top level so that `Mode` — a name far too generic
 * for a shared package — stays qualified at every call site, exactly as it was when nested in the
 * activity.
 *
 * The `Json` instance is FULLY QUALIFIED on purpose. `ManageLabels.kt` both imported
 * `CommonUtils.json` and declared a same-package `val json` with different settings; the import won,
 * but relying on that resolution rule after a file move is how a payload quietly starts rejecting
 * unknown properties. Same reasoning, and the same shape, as `RepositoryData` in
 * `download/CustomRepositoryEditor.kt`.
 */
object ManageLabelsContract {
    enum class Mode {STUDYPAD, WORKSPACE, ASSIGN, HIDELABELS}

    @Serializable
    data class ManageLabelsData(
        val mode: Mode,
        val selectedLabels: MutableSet<IdType> = mutableSetOf(),
        val autoAssignLabels: MutableSet<IdType> = mutableSetOf(),
        val deletedLabels: MutableSet<IdType> = mutableSetOf(),
        val deletedLabelsWithOrphanedBookmarks: MutableSet<IdType> = mutableSetOf(),
        val changedLabels: MutableSet<IdType> = mutableSetOf(),

        var autoAssignPrimaryLabel: IdType? = null,
        var bookmarkPrimaryLabel: IdType? = null,

        val isWindow: Boolean = false,

        var reset: Boolean = false,
    ) {
        val showUnassigned: Boolean get() = setOf(Mode.HIDELABELS, Mode.WORKSPACE).contains(mode)
        val showCheckboxes: Boolean get() = setOf(Mode.HIDELABELS, Mode.ASSIGN).contains(mode)
        val hasResetButton: Boolean get() = setOf(Mode.WORKSPACE, Mode.HIDELABELS).contains(mode)
        val hasReOrderButton: Boolean get() = setOf(Mode.HIDELABELS, Mode.ASSIGN, Mode.WORKSPACE).contains(mode)
        val workspaceEdits: Boolean get() = setOf(Mode.WORKSPACE, Mode.ASSIGN).contains(mode)
        val primaryShown: Boolean get() = setOf(Mode.WORKSPACE, Mode.ASSIGN).contains(mode)
        val showActiveCategory: Boolean get() = setOf(Mode.WORKSPACE, Mode.ASSIGN, Mode.HIDELABELS).contains(mode)
        val hideCategories: Boolean get() = setOf(Mode.STUDYPAD).contains(mode)

        val contextSelectedItems: MutableSet<IdType> get() =
            when (mode) {
                Mode.WORKSPACE -> autoAssignLabels
                else -> selectedLabels
            }

        var contextPrimaryLabel: IdType? get() =
            when (mode) {
                Mode.WORKSPACE -> autoAssignPrimaryLabel
                Mode.ASSIGN -> bookmarkPrimaryLabel
                else -> null
            }
            set(value) =
                when (mode) {
                    Mode.WORKSPACE -> autoAssignPrimaryLabel = value
                    Mode.ASSIGN -> bookmarkPrimaryLabel = value
                    else -> {}
                }

        val titleId: Int get() {
            return when(mode) {
                Mode.ASSIGN -> R.string.assign_labels
                Mode.STUDYPAD -> R.string.studypads
                Mode.WORKSPACE -> R.string.labels
                Mode.HIDELABELS -> R.string.bookmark_settings_hide_labels_title
            }
        }

        fun toJSON(): String = CommonUtils.json.encodeToString(serializer(), this)
        fun applyFrom(workspaceSettings: WorkspaceEntities.WorkspaceSettings?): ManageLabelsData {
            workspaceSettings?: return this
            autoAssignLabels.addAll(workspaceSettings.autoAssignLabels)
            autoAssignPrimaryLabel = workspaceSettings.autoAssignPrimaryLabel
            return this
        }

        companion object {
            fun fromJSON(str: String): ManageLabelsData = CommonUtils.json.decodeFromString(serializer(), str)
        }
    }
}

fun WorkspaceEntities.WorkspaceSettings.updateFrom(resultData: ManageLabelsContract.ManageLabelsData) {
    Log.i("ManageLabels", "WorkspaceEntities.updateRecentLabels")
    autoAssignLabels = resultData.autoAssignLabels
    autoAssignPrimaryLabel = resultData.autoAssignPrimaryLabel
    WorkspaceChanges.notifySettingsEdited()
}
