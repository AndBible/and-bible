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
        var offered: Set<String> = ReadingDrawerMenu.EXTRA_ROWS
        override fun openStudyPads() { calls += "studyPads" }
        override fun extraRowOffered(id: String) = id in offered
        override fun runExtraRow(id: String) { calls += "extra:$id" }
    }

    private val host = FakeHost()
    private val table = StringTable.of("en", { lookup("en", it) }, { n, c -> lookupPlural("en", n, c) }, { lookupArray("en", it) })
    private val menu = ReadingDrawerMenu(host) { KeyedReadingMenuStrings(table) }

    /** Android's `DrawerMenuStateBuilder` table, written out: group heading, then the row ids and icon keys in order. */
    private val androidTable = listOf(
        null to listOf(
            "chooseDocumentButton" to "ic_library_books_white_24dp", "searchButton" to "ic_search_24dp",
            "speakButton" to "ic_baseline_headphones_24", "bookmarksButton" to "ic_baseline_bookmark_24",
            "studyPadsButton" to "ic_baseline_studypads_24", "myDocumentsButton" to "ic_baseline_description_24",
            "dailyReadingPlanButton" to "ic_reading_plan_24dp", "readingProgressButton" to "ic_bar_chart_24dp",
            "historyButton" to "ic_history_clock_24dp",
        ),
        "Administration" to listOf(
            "downloadButton" to "ic_file_download_24dp", "backupMainMenu" to "ic_settings_backup_restore_db_24dp",
            "googleDriveSync" to "ic_syncdb_24dp", "managePrompts" to "icon_robot", "settingsButton" to "ic_settings_white_24dp",
        ),
        "Information" to listOf(
            "helpButton" to "ic_help_white_24dp", "buyDevelopment" to "baseline_attach_money_24",
            "needHelp" to "ic_need_help_24dp", "howToContribute" to "ic_baseline_emoji_people_24",
            "appLicence" to "ic_baseline_copyright_24",
        ),
        "Contact" to listOf(
            "tellFriend" to "ic_baseline_people_24", "rateButton" to "ic_rate_review_white_24dp",
            "bugReport" to "ic_bug_report_white_24dp",
        ),
    )

    @Test fun rowsInAndroidOrder() {
        host.offered = ReadingDrawerMenu.EXTRA_ROWS
        val state = menu.build()
        assertEquals(
            androidTable,
            state.groups.map { g -> g.title to g.items.map { it.id to it.iconKey } },
        )
        assertEquals(ReadingDrawerMenu.ROW_IDS, state.groups.flatMap { g -> g.items.map { it.id } })
    }

    @Test fun extraRowsHiddenWithoutImplementation() {
        host.offered = emptySet()
        val state = menu.build()
        val ids = state.groups.flatMap { g -> g.items.map { it.id } }
        assertEquals(ReadingDrawerMenu.ROW_IDS - ReadingDrawerMenu.EXTRA_ROWS, ids)
        assertEquals(listOf(null, "Administration", "Information"), state.groups.map { it.title }, "the empty Contact group is dropped too")
        ReadingDrawerMenu.EXTRA_ROWS.forEach(menu::handle)
        assertTrue(host.calls.isEmpty(), "a stale click on a hidden extra row does nothing")
    }

    @Test fun onlyTheOfferedExtraRowsAppear() {
        host.offered = setOf("helpButton", "appLicence", "bugReport", "tellFriend") // what H2 offers
        val ids = menu.build().groups.flatMap { g -> g.items.map { it.id } }
        assertEquals(ReadingDrawerMenu.ROW_IDS - setOf("rateButton", "buyDevelopment"), ids)
    }

    @Test fun backupNavigatesToBackupRoute() {
        menu.handle("backupMainMenu")
        assertEquals(listOf("navigate:backup/restore"), host.calls)
    }

    @Test fun cloudSyncNavigatesToSyncSettings() {
        menu.handle("googleDriveSync")
        assertEquals(listOf("navigate:settings/sync"), host.calls)
    }

    @Test fun studyPadsAndExtraRowsReachTheHost() {
        menu.handle("studyPadsButton")
        ReadingDrawerMenu.EXTRA_ROWS.sorted().forEach(menu::handle)
        assertEquals(
            listOf("studyPads") + ReadingDrawerMenu.EXTRA_ROWS.sorted().map { "extra:$it" },
            host.calls,
        )
    }

    @Test fun rowsAreInAndroidsOrderAndGroupedUnderAndroidsHeadings() {
        val state = menu.build()
        assertEquals(listOf(null, "Administration", "Information", "Contact"), state.groups.map { it.title })
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
                "chooser", "search", "speak", "bookmarks", "studyPads", "myDocuments", "plan", "progress", "history",
                "downloads", "navigate:backup/restore", "navigate:settings/sync",
                "navigate:ai/prompts", "navigate:settings/app",
                "extra:helpButton", "extra:buyDevelopment",
                "url:${ReadingDrawerMenu.NEED_HELP_LINK}", "url:${ReadingDrawerMenu.CONTRIBUTE_LINK}",
                "extra:appLicence", "extra:tellFriend", "extra:rateButton", "extra:bugReport",
            ),
            host.calls,
        )
        host.calls.clear()
        menu.handle("noSuchRow")
        assertTrue(host.calls.isEmpty())
    }

    /** Shown plus omitted rows are exactly Android's drawer table, so a row cannot disappear without being named. */
    @Test fun omittedRowsPinMatchesAndroidTable() {
        val source = File("../app/src/main/java/net/bible/android/view/activity/page/DrawerMenuStateBuilder.kt").readText()
        val androidIds = Regex("StaticEntry\\(R\\.id\\.(\\w+), \"(\\w+)\"").findAll(source).map { it.groupValues[2] }.toList()
        assertTrue(androidIds.size >= 20, "found the Android table: $androidIds")
        assertEquals(androidIds.sorted(), (ReadingDrawerMenu.ROW_IDS + ReadingDrawerMenu.OMITTED_ROWS.keys).sorted())
    }
}
