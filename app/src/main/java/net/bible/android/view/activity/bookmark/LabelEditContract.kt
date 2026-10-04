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

// Left top-level (not nested in `LabelEditContract`) unlike `LabelData` above: `customIconMap` is
// neither a generic name at risk of colliding in this package nor a serialized payload at risk of
// the `json`-shadowing trap `LabelEditContract`'s KDoc describes, so nesting it would only force
// every call site to spell `LabelEditContract.customIconMap` for no corresponding safety gain.
// Moved verbatim from the about-to-be-deleted `LabelEditActivity` (Batch Z-late phase 1); it is
// read by `LabelEditComposeActivity`, `ManageLabelsComposeActivity` and the classic
// `ManageLabelItemAdapter`.
// Reordered customIconMap with logical categories
val customIconMap = mapOf(
    // Religious / Spiritual
    "book" to R.drawable.icon_book,
    "book-bible" to R.drawable.icon_book_bible,
    "cross" to R.drawable.icon_cross,
    "church" to R.drawable.icon_church,
    "star-of-david" to R.drawable.icon_star_of_david,
    "person-praying" to R.drawable.icon_person_praying,

    // Informational / Symbolic
    "info" to R.drawable.icon_circle_info,
    "question" to R.drawable.icon_circle_question,
    "exclamation" to R.drawable.icon_circle_exclamation,
    "lightbulb" to R.drawable.icon_lightbulb,
    "bell" to R.drawable.icon_bell,
    "flag" to R.drawable.icon_flag,
    "star" to R.drawable.icon_star,
    "tag" to R.drawable.icon_tag,

    // Communication / Social
    "envelope" to R.drawable.icon_envelope,
    "comment" to R.drawable.icon_comment,
    "share-nodes" to R.drawable.icon_share_nodes,
    "link" to R.drawable.icon_link,
    "handshake" to R.drawable.icon_handshake,

    // Time & Location
    "clock" to R.drawable.icon_clock,
    "map-marker" to R.drawable.icon_location_dot,
    "globe" to R.drawable.icon_globe,
    "landmark" to R.drawable.icon_landmark,
    "calendar" to R.drawable.icon_calendar,

    // People & Media / Miscellaneous
    "user" to R.drawable.icon_user,
    "music" to R.drawable.icon_music,
    "microphone" to R.drawable.icon_microphone,
    "key" to R.drawable.icon_key,
    "crown" to R.drawable.icon_crown,
    "heart" to R.drawable.icon_heart,
    "heart-crack" to R.drawable.icon_heart_crack,
    "robot" to R.drawable.icon_robot
)
