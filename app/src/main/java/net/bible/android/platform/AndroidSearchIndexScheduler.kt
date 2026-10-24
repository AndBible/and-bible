/*
 * Copyright (c) 2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.android.platform

import android.content.Context
import net.bible.service.sword.index.SearchIndexingService
import net.bible.sharedlogic.platform.SearchIndexScheduler
import org.crosswire.jsword.book.Book

/** Android [SearchIndexScheduler]: the queue and foreground service host of [SearchIndexingService]. */
class AndroidSearchIndexScheduler(private val context: Context) : SearchIndexScheduler {
    override fun enqueue(book: Book) = SearchIndexingService.enqueue(context, book)

    override fun dequeue(initials: String) = SearchIndexingService.dequeue(initials)
}
