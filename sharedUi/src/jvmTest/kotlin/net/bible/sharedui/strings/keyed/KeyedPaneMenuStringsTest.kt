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

import net.bible.sharedui.strings.PaneMenuStrings
import net.bible.sharedui.strings.lookup
import net.bible.sharedui.strings.lookupArray
import net.bible.sharedui.strings.lookupPlural
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The pane menu's keyed strings: every key exists in Android's `strings.xml` and the templates fill as Android's do. */
class KeyedPaneMenuStringsTest {
    private val strings = KeyedPaneMenuStrings(
        StringTable.of("en", { lookup("en", it) }, { n, c -> lookupPlural("en", n, c) }, { lookupArray("en", it) }),
    )

    @Test fun everyKeyIsAStringResourceOfTheApp() {
        val xml = File("../app/src/main/res/values/strings.xml").readText()
        val missing = KeyedPaneMenuStrings.KEYS.filter { !xml.contains("<string name=\"$it\"") }
        assertEquals(emptyList(), missing, "keys without a strings.xml entry")
    }

    @Test fun everyKeyResolvesInTheGeneratedEnglishTable() {
        val table = StringTable.of("en", { lookup("en", it) }, { n, c -> lookupPlural("en", n, c) }, { lookupArray("en", it) })
        // An unknown key answers the key itself (the generated tables' behaviour), so a resolved key differs from it.
        val unresolved = KeyedPaneMenuStrings.KEYS.filter { table.text(it) == it }
        assertEquals(emptyList(), unresolved, "keys the generated tables do not know")
    }

    @Test fun plainRowsAreTheEnglishLabels() {
        assertEquals("Close", strings.close)
        assertTrue(strings.newWindow.isNotBlank())
        assertTrue(strings.allTextOptionsWindowMenuTitle.isNotBlank())
    }

    @Test fun templatesTakeTheirArgumentsInAndroidsOrder() {
        assertEquals("Export as HTML", strings.exportFileFormat("HTML"))
        assertEquals("Open Gen 1:1", strings.goToRef("Gen 1:1"))
        assertEquals("Position 2 (KJV:Gen 1:1)", strings.moveWindowToPosition(2, "KJV", "Gen 1:1"))
        assertEquals("Window 3 (KJV: Gen 1:1)", strings.copySettingsToWindow(3, "KJV", "Gen 1:1"))
        assertEquals("Group 4", strings.syncGroupN(4))
    }

    @Test fun keysListsExactlyTheKeysTheMembersRead() {
        val read = mutableSetOf<String>()
        val recording = KeyedPaneMenuStrings(
            StringTable("en", { read += it; it }, { k, _ -> read += k; k }, { _, _ -> "" }, { emptyList() }),
        )
        recording.apply {
            newWindow; windowMaximise; windowMinimise; changeToNormal; moveWindow; windowPinMode; windowSynchronise
            addWholePageBookmark; textOptionsWindowMenuTitle; allTextOptionsWindowMenuTitle; copySettings; copyReference
            close; disableSync; studypad; llmActions; copySettingsToWorkspace; copySettingsToGlobal
            referenceCopiedToClipboard
            exportFileFormat("x"); exportSomething("x"); exportBookmarksCsv("x"); goToRef("x")
            moveWindowToPosition(1, "a", "b"); syncGroupN(1); copySettingsToWindow(1, "a", "b")
        }
        assertEquals(read, KeyedPaneMenuStrings.KEYS.toSet(), "KEYS must equal the keys the members read")
        assertEquals(KeyedPaneMenuStrings.KEYS.size, read.size, "no duplicates in KEYS")
        // The member count is pinned too: a member added to PaneMenuStrings must be read above (and so listed).
        assertEquals(26, PaneMenuStrings::class.java.methods.size, "PaneMenuStrings members changed: update this test and KEYS")
    }
}
