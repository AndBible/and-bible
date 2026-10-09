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

package net.bible.android.view.activity.page

import net.bible.android.control.bookmark.BookmarkChange
import net.bible.android.control.bookmark.favouriteIdsAfter
import net.bible.android.database.IdType

/**
 * [BibleView]'s favourite label ids, read synchronously by its `set_config` command. Kept current from
 * [BookmarkChange]s ([apply]) and re-read from the database ([refresh]); both may run on different
 * threads at once.
 *
 * - [apply] is atomic, so two concurrent changes never lose one another.
 * - A change that arrives while a [refresh] is reading is replayed onto the read result: the change is
 *   emitted after its own database write, so the read may or may not include it, and
 *   [favouriteIdsAfter] is idempotent. Without the replay, the older read would overwrite it.
 */
class FavouriteLabelIdsCache {
    private val lock = Any()
    @Volatile var ids: List<IdType> = emptyList()
        private set
    /** One log per refresh in flight; [apply] appends to each. Guarded by [lock]. */
    private val inFlight = mutableListOf<MutableList<BookmarkChange>>()

    fun apply(change: BookmarkChange) = synchronized(lock) {
        ids = favouriteIdsAfter(ids, change)
        inFlight.forEach { it.add(change) }
    }

    suspend fun refresh(read: suspend () -> List<IdType>) {
        val log = mutableListOf<BookmarkChange>()
        synchronized(lock) { inFlight.add(log) }
        try {
            val fresh = read()
            synchronized(lock) { ids = log.fold(fresh, ::favouriteIdsAfter) }
        } finally {
            synchronized(lock) { inFlight.removeAll { it === log } } // by identity: two empty logs are equal
        }
    }
}
