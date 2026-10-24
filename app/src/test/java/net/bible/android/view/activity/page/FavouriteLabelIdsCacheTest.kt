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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import net.bible.android.control.bookmark.BookmarkChange
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import org.junit.Assert.assertEquals
import org.junit.Test

/** BibleView's favourite-label cache: no update is lost to a concurrent one or to a refresh in flight. */
class FavouriteLabelIdsCacheTest {
    private fun fav(id: IdType) = BookmarkChange.LabelUpserted(Label(id = id, favourite = true))

    @Test fun `a change that lands while a refresh reads is kept, not overwritten by the older read`() = runBlocking {
        val cache = FavouriteLabelIdsCache()
        val old = IdType()
        val added = IdType()
        val readStarted = CompletableDeferred<Unit>()
        val readResult = CompletableDeferred<List<IdType>>()

        val refresh = launch(Dispatchers.Default) {
            cache.refresh { readStarted.complete(Unit); readResult.await() }
        }
        withTimeout(10_000) { readStarted.await() }
        cache.apply(fav(added)) // emitted after its DB write, while the refresh's read is in flight
        readResult.complete(listOf(old)) // the read did not see the write yet
        withTimeout(10_000) { refresh.join() }

        assertEquals(listOf(old, added), cache.ids)
    }

    @Test fun `a change before or after a refresh is not replayed onto a later refresh`() = runBlocking {
        val cache = FavouriteLabelIdsCache()
        val gone = IdType()
        cache.apply(fav(gone))
        cache.refresh { emptyList() } // the database says it is no longer a favourite
        assertEquals(emptyList<IdType>(), cache.ids)
    }

    @Test fun `concurrent changes from many threads are all kept`() = runBlocking {
        val cache = FavouriteLabelIdsCache()
        val perThread = 500
        val ids = (0 until 8).map { List(perThread) { IdType() } }
        ids.map { mine ->
            async(Dispatchers.Default) { for (id in mine) { cache.apply(fav(id)); yield() } }
        }.awaitAll()
        assertEquals(ids.flatten().toSet(), cache.ids.toSet())
        assertEquals(8 * perThread, cache.ids.size)
    }
}
