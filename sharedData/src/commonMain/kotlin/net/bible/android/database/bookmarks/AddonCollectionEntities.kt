/*
 * Copyright (c) 2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.database.bookmarks

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import net.bible.android.database.IdType

/**
 * Add-on bookmark collections (spec 2026-11-20-addon-bookmark-collections-design): an AND_BIBLE add-on module
 * may provide read-only labels, which are imported into `BookmarkDatabase` as a cache of the module.
 *
 * None of these tables is a synced table (`SyncableDatabaseDefinition.BOOKMARKS.tables`): they are per-device
 * state. Every device builds its collections from its own installed modules.
 */
class AddonCollectionEntities {
    /** One imported collection module: what was imported last, so the reconciler can tell new/changed/unchanged. */
    @Entity
    data class AddonCollectionModule(
        @PrimaryKey val moduleInitials: String,
        val version: String,
        /** SHA-256 (hex) of the collection database file, to catch a re-published file under an unchanged version. */
        val contentHash: String,
        val importedAt: Long,
    )

    /**
     * Ownership of one imported row: [tableName] is a synced bookmark table and ([id1], [id2]) its sync key
     * (`SyncableDatabaseDefinition.Table.idField1`/`idField2`; [id2] null for single-key tables). A row listed here
     * belongs to module [moduleInitials]: it is read-only, never logged for sync and deleted with the module.
     */
    @Entity(
        indices = [
            Index(value = ["tableName", "id1", "id2"], unique = true),
            Index("moduleInitials"),
        ],
    )
    data class AddonCollectionRow(
        val moduleInitials: String,
        val tableName: String,
        val id1: IdType,
        val id2: IdType?,
        @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    )

    /** Per-device, user-chosen state of an add-on label that would otherwise live on the (read-only) label row. */
    @Entity
    data class AddonLabelLocalState(
        @PrimaryKey val labelId: IdType,
        val favourite: Boolean = false,
    )

    /**
     * Per-device StudyPad expand/collapse state of an add-on label link (`BibleBookmarkToLabel` or
     * `GenericBookmarkToLabel` row owned by a module). Overrides the link row's own `expandContent`.
     * (The plan's `AddonLabelLocalState.expandedBookmarkIds` JSON list, normalised.)
     */
    @Entity(primaryKeys = ["bookmarkId", "labelId"])
    data class AddonLinkLocalState(
        val bookmarkId: IdType,
        val labelId: IdType,
        val expandContent: Boolean,
    )
}
