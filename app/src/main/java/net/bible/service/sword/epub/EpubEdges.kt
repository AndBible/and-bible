/*
 * Copyright (c) 2022-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.sword.epub

import kotlinx.coroutines.sync.Mutex
import net.bible.android.database.EpubDao
import net.bible.android.database.EpubFragment
import net.bible.android.database.EpubHtmlToFrag
import net.bible.android.database.StyleSheet
import net.bible.service.db.blockingAtEdge

// Android blocking edges for the JSword synchronous Backend/driver contract. They stay in :app because
// blockingAtEdge does; the domain files call these functions. Making them suspend is the edge-reduction backlog.

internal fun epubFragmentBlocking(dao: EpubDao, id: Long): EpubFragment? = blockingAtEdge { dao.getFragment(id) } // L1-edge: JSword synchronous Backend contract

internal fun epubFragmentsBlocking(dao: EpubDao): List<EpubFragment> = blockingAtEdge { dao.fragments() } // L1-edge: JSword synchronous Backend contract

internal fun epubLockOwnerBlocking(owner: Mutex) {
    blockingAtEdge { owner.lock() } // L1-edge: JSword driver delete is synchronous
}

internal fun epubClearBlocking(writeDao: EpubDao) {
    blockingAtEdge { writeDao.clear() } // L1-edge: JSword BookType.getBackend (first open optimizes)
}

internal fun epubInsertFragmentsBlocking(writeDao: EpubDao, fragments: List<EpubFragment>, originalId: String, styleSheets: Array<StyleSheet>) {
    blockingAtEdge { // L1-edge: JSword BookType.getBackend (first open optimizes)
        val ids = writeDao.insert(*fragments.toTypedArray())
        for((id, frag) in ids.zip(fragments)) {
            frag.id = id
        }
        writeDao.insert(EpubHtmlToFrag(originalId, fragments[0].id))
        writeDao.insert(*styleSheets)
    }
}

internal fun epubInsertHtmlToFragsBlocking(writeDao: EpubDao, epubHtmlToFrags: List<EpubHtmlToFrag>) {
    blockingAtEdge { writeDao.insert(*epubHtmlToFrags.toTypedArray()) } // L1-edge: JSword BookType.getBackend (first open optimizes)
}
