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

import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.service.common.CommonUtils
import kotlinx.serialization.Serializable

/**
 * The label-editor intent contract, lifted out of the about-to-be-deleted `LabelEditActivity`
 * (Batch Z-late phase 1) because `LabelEditComposeActivity`, `ManageLabelsComposeActivity` and
 * `LabelEditMapper` all exchange this payload. Object-wrapped and Json-qualified for the same two
 * reasons as [ManageLabelsContract].
 */
object LabelEditContract {
    @Serializable
    data class LabelData (
        val isAssigning: Boolean,
        var label: BookmarkEntities.Label,

        var isAutoAssign: Boolean,

        var isAutoAssignPrimary: Boolean,
        var isThisBookmarkSelected: Boolean,
        var isThisBookmarkPrimary: Boolean,
        var delete: Boolean = false,
        var deleteOrphanedBookmarks: Boolean = false,
        val suggestedName: String? = null,
        var workspaceOverride: WorkspaceEntities.WorkspaceLabelOverride? = null,
        var hasWorkspaceContext: Boolean = false,
    ) {
        fun toJSON(): String = CommonUtils.json.encodeToString(serializer(), this)

        companion object {
            fun fromJSON(str: String): LabelData = CommonUtils.json.decodeFromString(serializer(), str)
        }
    }
}
