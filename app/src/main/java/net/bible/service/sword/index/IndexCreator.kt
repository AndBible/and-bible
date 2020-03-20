/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */
package net.bible.service.sword.index

import org.crosswire.jsword.book.Book
import org.crosswire.jsword.index.IndexManagerFactory

/** Optimise Lucene index creation
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class IndexCreator {
    /*
     * (non-Javadoc)
     *
     * @see
     * org.crosswire.jsword.index.search.AbstractIndex#generateSearchIndex(org
     * .crosswire.common.progress.Job)
     */
    fun scheduleIndexCreation(book: Book?) {
        val work = Thread {
            val indexManager = IndexManagerFactory.getIndexManager()
            indexManager.indexPolicy = AndroidIndexPolicy()
            indexManager.scheduleIndexCreation(book)
        }
        work.start()
    }
}
