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
package net.bible.sharedlogic.platform

import org.crosswire.jsword.book.Book

/**
 * Starts and cancels background search-index builds. Android runs them in a foreground service; other hosts supply
 * their own runner. Nothing behind this port may assume a foreground service.
 */
interface SearchIndexScheduler {
    /** Queues the index build of [book]; a book already queued or being built is ignored. */
    fun enqueue(book: Book)

    /** Drops a queued, not yet started build of [initials]. */
    fun dequeue(initials: String)
}
