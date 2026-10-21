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

/** The overflow menu's and drawer's keyed strings: every key exists, resolves in the generated table, and KEYS is exact. */
class KeyedReadingMenuStringsTest {
    private val table = StringTable.of("en", { lookup("en", it) }, { n, c -> lookupPlural("en", n, c) }, { lookupArray("en", it) })

    @Test fun everyKeyIsAStringResourceOfTheApp() {
        val xml = File("../app/src/main/res/values/strings.xml").readText()
        assertEquals(emptyList(), KeyedReadingMenuStrings.KEYS.filter { !xml.contains("<string name=\"$it\"") })
    }

    @Test fun everyKeyResolvesInTheGeneratedEnglishTable() {
        assertEquals(emptyList(), KeyedReadingMenuStrings.KEYS.filter { table.text(it) == it })
    }

    @Test fun keysListsExactlyTheKeysTheMembersRead() {
        val read = mutableSetOf<String>()
        val recording = KeyedReadingMenuStrings(
            StringTable("en", { read += it; it }, { k, _ -> read += k; k }, { _, _ -> "" }, { emptyList() }),
        )
        recording.apply {
            toggleFullscreen; nightMode; switchToWorkspace; tiltToScroll; reversedSplitMode; windowPinning; allTextOptions
            chooseDocument; search; speak; bookmarks; studyPads; myDocuments; readingPlan; readingProgress; history
            administration; download; backupAndRestore; cloudSync; aiSettings; settings; information; helpAndTips
            buyDevelopment; questions; howToContribute; appLicence; contact; tellFriend; rateApplication; bugReport
            appNameMedium; versionText("1")
            llmActions // R3
        }
        assertEquals(read, KeyedReadingMenuStrings.KEYS.toSet())
        assertEquals(KeyedReadingMenuStrings.KEYS.size, read.size, "no duplicates in KEYS")
    }

    @Test fun versionTextFillsTheVersion() {
        assertEquals("Version 1.2", KeyedReadingMenuStrings(table).versionText("1.2"))
    }
}
