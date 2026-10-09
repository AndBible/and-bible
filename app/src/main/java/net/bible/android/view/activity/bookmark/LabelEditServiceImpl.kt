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

import net.bible.android.control.bookmark.BookmarkControl
import net.bible.service.db.blockingDb
import net.bible.android.database.IdType
import net.bible.sharedcore.bookmark.LabelEditService

/** Android-side impl of the [LabelEditService] seam, backed by [BookmarkControl]. */
class LabelEditServiceImpl(private val bookmarkControl: BookmarkControl) : LabelEditService {
    override fun orphanedBookmarkCount(labelId: String): Int =
        if (labelId.isEmpty()) 0
        else blockingDb { bookmarkControl.findOrphanedBookmarks(listOf(IdType(labelId))) }.size // L1-pending(bookmark)
}
