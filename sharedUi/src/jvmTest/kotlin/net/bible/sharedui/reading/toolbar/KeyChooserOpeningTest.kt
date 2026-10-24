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
package net.bible.sharedui.reading.toolbar

import kotlinx.coroutines.runBlocking
import net.bible.sharedcore.reading.KeyChooserKind
import net.bible.sharedcore.reading.KeyChooserPage
import org.crosswire.jsword.passage.DefaultLeafKeyList
import org.crosswire.jsword.passage.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [resolveKeyChooserOpening] is Android's `composeStartKeyChooser` decision: a quick sheet where `KeyChooserRoute` says so. */
class KeyChooserOpeningTest {
    private val someKeys: List<Key> = listOf(DefaultLeafKeyList("a"), DefaultLeafKeyList("b"))

    private fun open(
        page: KeyChooserPage?,
        keys: List<Key> = someKeys,
        stillCurrent: () -> Boolean = { true },
        resolved: MutableList<KeyChooserKind> = mutableListOf(),
    ) = runBlocking { resolveKeyChooserOpening(page, { kind -> resolved += kind; keys }, stillCurrent) }

    @Test
    fun aBiblePageOpensTheGridQuickSheetWithoutResolvingKeys() {
        val resolved = mutableListOf<KeyChooserKind>()
        val opening = open(KeyChooserPage.BIBLE, keys = emptyList(), resolved = resolved)
        assertEquals(KeyChooserOpening.Sheet(KeyChooserKind.Grid, emptyList()), opening)
        // The grid has no key list: an empty list must NOT send it to the full screen.
        assertTrue(resolved.single() == KeyChooserKind.Grid)
    }

    @Test
    fun commentaryAndMyNoteShareTheGridSheet() {
        for (page in listOf(KeyChooserPage.COMMENTARY, KeyChooserPage.MY_NOTE)) {
            assertEquals(KeyChooserKind.Grid, (open(page) as KeyChooserOpening.Sheet).kind)
        }
    }

    @Test
    fun aMapPageOpensTheMapSheetWithTheResolvedKeys() {
        assertEquals(KeyChooserOpening.Sheet(KeyChooserKind.Map, someKeys), open(KeyChooserPage.MAP))
    }

    @Test
    fun aGeneralBookWithNoKeysFallsBackToTheFullScreen() {
        assertEquals(KeyChooserOpening.FullScreen, open(KeyChooserPage.GENERAL_BOOK, keys = emptyList()))
    }

    @Test
    fun pagesWithoutASheetGoFullScreenAndResolveNothing() {
        val resolved = mutableListOf<KeyChooserKind>()
        for (page in listOf(
            KeyChooserPage.DICTIONARY, KeyChooserPage.GENERAL_BOOK_MY_DOCUMENT,
            KeyChooserPage.GENERAL_BOOK_STUDY_PAD, KeyChooserPage.GENERAL_BOOK_MULTI_DOCUMENT, null,
        )) {
            assertEquals(KeyChooserOpening.FullScreen, open(page, resolved = resolved), "page $page")
        }
        assertEquals(emptyList(), resolved)
    }

    @Test
    fun aPageThatChangedWhileTheKeysWereResolvedOpensNothing() {
        assertEquals(null, open(KeyChooserPage.MAP, stillCurrent = { false }))
    }
}
