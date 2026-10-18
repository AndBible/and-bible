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

package net.bible.android.platform

import kotlinx.coroutines.sync.Mutex
import net.bible.android.database.EpubDao
import net.bible.android.database.EpubFragment
import net.bible.android.database.EpubHtmlToFrag
import net.bible.android.database.StyleSheet
import net.bible.service.db.blockingAtEdge
import net.bible.sharedlogic.platform.EpubStoreEdge

/**
 * Android [EpubStoreEdge] for the JSword synchronous Backend/driver contract. It stays in :app because
 * blockingAtEdge does; making the members suspend is the edge-reduction backlog.
 */
object AndroidEpubStoreEdge : EpubStoreEdge {
    override fun fragment(dao: EpubDao, id: Long): EpubFragment? = blockingAtEdge { dao.getFragment(id) } // L1-edge: JSword synchronous Backend contract

    override fun fragments(dao: EpubDao): List<EpubFragment> = blockingAtEdge { dao.fragments() } // L1-edge: JSword synchronous Backend contract

    override fun lockOwner(owner: Mutex) {
        blockingAtEdge { owner.lock() } // L1-edge: JSword driver delete is synchronous
    }

    override fun clear(writeDao: EpubDao) {
        blockingAtEdge { writeDao.clear() } // L1-edge: JSword BookType.getBackend (first open optimizes)
    }

    override fun insertFragments(writeDao: EpubDao, fragments: List<EpubFragment>, originalId: String, styleSheets: Array<StyleSheet>) {
        blockingAtEdge { // L1-edge: JSword BookType.getBackend (first open optimizes)
            val ids = writeDao.insert(*fragments.toTypedArray())
            for((id, frag) in ids.zip(fragments)) {
                frag.id = id
            }
            writeDao.insert(EpubHtmlToFrag(originalId, fragments[0].id))
            writeDao.insert(*styleSheets)
        }
    }

    override fun insertHtmlToFrags(writeDao: EpubDao, epubHtmlToFrags: List<EpubHtmlToFrag>) {
        blockingAtEdge { writeDao.insert(*epubHtmlToFrags.toTypedArray()) } // L1-edge: JSword BookType.getBackend (first open optimizes)
    }
}
