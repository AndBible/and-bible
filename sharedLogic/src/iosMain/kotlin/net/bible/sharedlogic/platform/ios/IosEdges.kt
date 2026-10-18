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
package net.bible.sharedlogic.platform.ios

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import net.bible.android.database.EpubDao
import net.bible.android.database.EpubFragment
import net.bible.android.database.EpubHtmlToFrag
import net.bible.android.database.IdType
import net.bible.android.database.StyleSheet
import net.bible.android.database.mydocument.MyDocumentDao
import net.bible.android.database.mydocument.MyDocumentPage
import net.bible.android.database.mydocument.MyDocumentPageWithContent
import net.bible.sharedlogic.platform.EpubStoreEdge
import net.bible.sharedlogic.platform.FirstLoadEdge
import net.bible.sharedlogic.platform.MyDocumentStoreEdge

/**
 * The Native twin of `blockingAtEdge`. Unlike Android there is no transaction guard: `isInDbTransactionOnThread()`
 * is constant false on iOS (`DbTransactionMarker.ios.kt`), so the Android check would be dead code. A bridge called
 * from inside a Room transaction would therefore deadlock instead of failing fast; callers must not do that.
 */
private fun <T> blockingEdge(block: suspend () -> T): T = runBlocking { block() }

/** iOS [FirstLoadEdge]: same contract as `AndroidFirstLoadEdge`, bridged with `runBlocking`. */
object IosFirstLoadEdge : FirstLoadEdge {
    override fun <T> awaitFirstLoad(body: suspend () -> T): T =
        blockingEdge { body() } // L1-edge: activeWindow read or host bootstrap before the first load finished
}

/** iOS [EpubStoreEdge]: same contract as `AndroidEpubStoreEdge`, bridged with `runBlocking`. */
object IosEpubStoreEdge : EpubStoreEdge {
    override fun fragment(dao: EpubDao, id: Long): EpubFragment? = blockingEdge { dao.getFragment(id) } // L1-edge: JSword synchronous Backend contract

    override fun fragments(dao: EpubDao): List<EpubFragment> = blockingEdge { dao.fragments() } // L1-edge: JSword synchronous Backend contract

    override fun lockOwner(owner: Mutex) {
        blockingEdge { owner.lock() } // L1-edge: JSword driver delete is synchronous
    }

    override fun clear(writeDao: EpubDao) {
        blockingEdge { writeDao.clear() } // L1-edge: JSword BookType.getBackend (first open optimizes)
    }

    override fun insertFragments(writeDao: EpubDao, fragments: List<EpubFragment>, originalId: String, styleSheets: Array<StyleSheet>) {
        blockingEdge { // L1-edge: JSword BookType.getBackend (first open optimizes)
            val ids = writeDao.insert(*fragments.toTypedArray())
            for ((id, frag) in ids.zip(fragments)) {
                frag.id = id
            }
            writeDao.insert(EpubHtmlToFrag(originalId, fragments[0].id))
            writeDao.insert(*styleSheets)
        }
    }

    override fun insertHtmlToFrags(writeDao: EpubDao, epubHtmlToFrags: List<EpubHtmlToFrag>) {
        blockingEdge { writeDao.insert(*epubHtmlToFrags.toTypedArray()) } // L1-edge: JSword BookType.getBackend (first open optimizes)
    }
}

/** iOS [MyDocumentStoreEdge]: same contract as `AndroidMyDocumentStoreEdge`, bridged with `runBlocking`. */
object IosMyDocumentStoreEdge : MyDocumentStoreEdge {
    override fun pages(dao: MyDocumentDao, documentId: IdType): List<MyDocumentPage> = blockingEdge { dao.pagesForDocument(documentId) } // L1-edge: JSword synchronous Backend contract

    override fun pageWithContent(dao: MyDocumentDao, documentId: IdType, pageKey: String): MyDocumentPageWithContent? = blockingEdge { dao.pageByKeyWithContent(documentId, pageKey) } // L1-edge: JSword synchronous Backend contract
}
