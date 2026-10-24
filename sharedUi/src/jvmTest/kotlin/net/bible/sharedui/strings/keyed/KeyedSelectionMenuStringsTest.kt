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
package net.bible.sharedui.strings.keyed

import net.bible.sharedui.strings.lookup
import net.bible.sharedui.strings.lookupArray
import net.bible.sharedui.strings.lookupPlural
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** The selection menu's keyed strings: every key exists, resolves in the generated table, and KEYS is exact. */
class KeyedSelectionMenuStringsTest {
    private val table = StringTable.of("en", { lookup("en", it) }, { n, c -> lookupPlural("en", n, c) }, { lookupArray("en", it) })

    @Test fun everyKeyIsAStringResourceOfTheApp() {
        val xml = File("../app/src/main/res/values/strings.xml").readText()
        assertEquals(emptyList(), KeyedSelectionMenuStrings.KEYS.filter { !xml.contains("<string name=\"$it\"") })
    }

    @Test fun everyKeyResolvesInTheGeneratedEnglishTable() {
        assertEquals(emptyList(), KeyedSelectionMenuStrings.KEYS.filter { table.text(it) == it })
    }

    @Test fun keysListsExactlyTheKeysTheMembersRead() {
        val read = mutableSetOf<String>()
        val recording = KeyedSelectionMenuStrings(
            StringTable("en", { read += it; it }, { k, _ -> read += k; k }, { _, _ -> "" }, { emptyList() }),
        )
        recording.apply {
            addBookmark; addBookmarkSelection; addBookmarkWholeVerse; addParagraphBreak; compare; memorize; shareVerses
            search; webSearch; lookupDictionary; copy; llmAction; goToRef("x"); searchLabel("x")
        }
        assertEquals(read, KeyedSelectionMenuStrings.KEYS.toSet())
        assertEquals(KeyedSelectionMenuStrings.KEYS.size, read.size, "no duplicates in KEYS")
    }

    @Test fun searchLabelQuotesAShortSelectionOnly() {
        val s = KeyedSelectionMenuStrings(table)
        assertEquals("Find \"grace\"", s.searchLabel("grace"))
        assertEquals("Find", s.searchLabel("a long selection of text"))
        assertEquals("Open John 3", s.goToRef("John 3"))
    }
}
