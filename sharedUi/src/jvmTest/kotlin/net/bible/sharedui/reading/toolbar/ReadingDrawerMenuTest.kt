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

import net.bible.sharedui.strings.keyed.KeyedReadingMenuStrings
import net.bible.sharedui.strings.keyed.StringTable
import net.bible.sharedui.strings.lookup
import net.bible.sharedui.strings.lookupArray
import net.bible.sharedui.strings.lookupPlural
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The drawer's rows, their dispatch and the pinned list of Android rows it leaves out. */
class ReadingDrawerMenuTest {
    private class FakeHost : ReadingDrawerHost {
        val calls = mutableListOf<String>()
        var searchOk = true
        var speakOk = true
        override fun openChooseDocument() { calls += "chooser" }
        override fun openSearch() { calls += "search" }
        override fun showSpeakTransport() { calls += "speak" }
        override fun showHistorySheet() { calls += "history" }
        override fun openDownloads() { calls += "downloads" }
        override fun openMyDocuments() { calls += "myDocuments" }
        override fun openBookmarks() { calls += "bookmarks" }
        override fun openReadingProgress() { calls += "progress" }
        override fun openReadingPlan() { calls += "plan" }
        override fun navigate(route: String) { calls += "navigate:$route" }
        override fun openUrl(url: String) { calls += "url:$url" }
        override fun searchable() = searchOk
        override fun speakable() = speakOk
        override fun versionName() = "9.9"
    }

    private val host = FakeHost()
    private val table = StringTable.of("en", { lookup("en", it) }, { n, c -> lookupPlural("en", n, c) }, { lookupArray("en", it) })
    private val menu = ReadingDrawerMenu(host) { KeyedReadingMenuStrings(table) }

    @Test fun rowsAreInAndroidsOrderAndGroupedUnderAndroidsHeadings() {
        val state = menu.build()
        assertEquals(ReadingDrawerMenu.ROW_IDS, state.groups.flatMap { g -> g.items.map { it.id } })
        assertEquals(listOf(null, "Administration", "Information"), state.groups.map { it.title })
        assertEquals("Version 9.9", state.versionText)
        assertEquals("Bible Study (AndBible)", state.appName)
    }

    @Test fun searchAndSpeakRowsAreDisabledWhenThePageCannotDoThem() {
        host.searchOk = false; host.speakOk = false
        val items = menu.build().groups.flatMap { it.items }.associateBy { it.id }
        assertFalse(items.getValue("searchButton").enabled)
        assertFalse(items.getValue("speakButton").enabled)
        assertTrue(items.getValue("historyButton").enabled)
        menu.handle("searchButton"); menu.handle("speakButton")
        assertTrue(host.calls.isEmpty(), "a disabled row does nothing even if a stale click arrives")
    }

    @Test fun everyRowReachesItsDestination() {
        ReadingDrawerMenu.ROW_IDS.forEach(menu::handle)
        assertEquals(
            listOf(
                "chooser", "search", "speak", "bookmarks", "myDocuments", "plan", "progress", "history", "downloads",
                "navigate:ai/prompts", "navigate:settings/app",
                "url:${ReadingDrawerMenu.NEED_HELP_LINK}", "url:${ReadingDrawerMenu.CONTRIBUTE_LINK}",
            ),
            host.calls,
        )
        host.calls.clear()
        menu.handle("noSuchRow")
        assertTrue(host.calls.isEmpty())
    }

    /** Shown plus omitted rows are exactly Android's drawer table, so a row cannot disappear without being named. */
    @Test fun shownAndOmittedRowsTogetherAreAndroidsDrawer() {
        val source = File("../app/src/main/java/net/bible/android/view/activity/page/DrawerMenuStateBuilder.kt").readText()
        val androidIds = Regex("StaticEntry\\(R\\.id\\.(\\w+), \"(\\w+)\"").findAll(source).map { it.groupValues[2] }.toList()
        assertTrue(androidIds.size >= 20, "found the Android table: $androidIds")
        assertEquals(androidIds.sorted(), (ReadingDrawerMenu.ROW_IDS + ReadingDrawerMenu.OMITTED_ROWS.keys).sorted())
    }
}
