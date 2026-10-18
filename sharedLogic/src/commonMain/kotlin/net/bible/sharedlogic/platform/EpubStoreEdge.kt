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

package net.bible.sharedlogic.platform

import kotlinx.coroutines.sync.Mutex
import net.bible.android.database.EpubDao
import net.bible.android.database.EpubFragment
import net.bible.android.database.EpubHtmlToFrag
import net.bible.android.database.StyleSheet

/**
 * Blocking reads and writes of the EPUB fragment store for the JSword synchronous Backend/driver contract.
 *
 * Every member is synchronous because the JSword `Backend`/driver contract is synchronous. The Android
 * implementation blocks through `blockingAtEdge`; iOS supplies its own in I2. Making these `suspend` is the
 * edge-reduction backlog. Call sites resolve the port at the call, never held by a constructor or property initializer.
 */
interface EpubStoreEdge {
    /** Reads one fragment by id. */
    fun fragment(dao: EpubDao, id: Long): EpubFragment?

    /** Reads every fragment. */
    fun fragments(dao: EpubDao): List<EpubFragment>

    /** Blocks until the module owner lock is held. */
    fun lockOwner(owner: Mutex)

    /** Clears stale rows before a (re)optimization. */
    fun clear(writeDao: EpubDao)

    /** Inserts one original file's fragments, the first-fragment mapping and its style sheets. */
    fun insertFragments(writeDao: EpubDao, fragments: List<EpubFragment>, originalId: String, styleSheets: Array<StyleSheet>)

    /** Inserts the html-id to fragment mappings. */
    fun insertHtmlToFrags(writeDao: EpubDao, epubHtmlToFrags: List<EpubHtmlToFrag>)
}
