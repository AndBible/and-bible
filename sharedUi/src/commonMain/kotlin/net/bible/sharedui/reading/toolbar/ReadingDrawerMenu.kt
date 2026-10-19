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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.reading.toolbar

import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.DrawerGroupSpec
import net.bible.sharedcore.reading.DrawerItemSpec
import net.bible.sharedcore.reading.DrawerMenu
import net.bible.sharedcore.reading.DrawerMenuState
import net.bible.sharedui.strings.keyed.KeyedReadingMenuStrings

/** What the drawer's rows do on the platform; the iOS implementation is `IosReadingToolbarHost`. */
@kotlin.native.HiddenFromObjC
interface ReadingDrawerHost {
    fun openChooseDocument()
    fun openSearch()

    /** Shows the Speak transport bar (idempotent: a menu row never hides it). */
    fun showSpeakTransport()
    fun showHistorySheet()
    fun openDownloads()

    /** Opens My Documents, recording the asking window so a document it opens lands there. */
    fun openMyDocuments()

    /** Opens the bookmark list; a picked bookmark opens in the asking window. */
    fun openBookmarks()

    /** Opens the reading progress screen; a chapter or memorize answer opens in the asking window. */
    fun openReadingProgress()

    /** Opens the daily reading plan (it opens passages itself; Android has no result collector for it either). */
    fun openReadingPlan()

    /** Opens a screen of the navigation graph. */
    fun navigate(route: String)
    fun openUrl(url: String)

    /** The active page can be searched (Android disables the row otherwise). */
    fun searchable(): Boolean
    fun speakable(): Boolean
    fun versionName(): String
}

/**
 * The reading view's navigation drawer on a platform without Android's `MenuCommandHandler`: the rows of Android's
 * `DrawerMenuStateBuilder` that have a working destination, in Android's order and grouping, and their click
 * handling. Rows left out, with what they wait for, are listed in [OMITTED_ROWS].
 */
@kotlin.native.HiddenFromObjC
class ReadingDrawerMenu(private val host: ReadingDrawerHost, private val strings: () -> KeyedReadingMenuStrings) {

    fun build(): DrawerMenuState {
        val s = strings()
        fun item(id: String, label: String, icon: String, enabled: Boolean = true) =
            DrawerItemSpec(id = id, label = label, iconKey = icon, enabled = enabled, visible = true)
        return DrawerMenu.build(
            appName = s.appNameMedium,
            versionText = s.versionText(host.versionName()),
            groups = listOf(
                DrawerGroupSpec(
                    title = null,
                    items = listOf(
                        item(ID_CHOOSE_DOCUMENT, s.chooseDocument, "ic_library_books_white_24dp"),
                        item(ID_SEARCH, s.search, "ic_search_24dp", host.searchable()),
                        item(ID_SPEAK, s.speak, "ic_baseline_headphones_24", host.speakable()),
                        item(ID_BOOKMARKS, s.bookmarks, "ic_baseline_bookmark_24"),
                        item(ID_MY_DOCUMENTS, s.myDocuments, "ic_baseline_description_24"),
                        item(ID_READING_PLAN, s.readingPlan, "ic_reading_plan_24dp"),
                        item(ID_READING_PROGRESS, s.readingProgress, "ic_bar_chart_24dp"),
                        item(ID_HISTORY, s.history, "ic_history_clock_24dp"),
                    ),
                ),
                DrawerGroupSpec(
                    title = s.administration,
                    items = listOf(
                        item(ID_DOWNLOAD, s.download, "ic_file_download_24dp"),
                        item(ID_AI_SETTINGS, s.aiSettings, "icon_robot"),
                        item(ID_SETTINGS, s.settings, "ic_settings_white_24dp"),
                    ),
                ),
                DrawerGroupSpec(
                    title = s.information,
                    items = listOf(
                        item(ID_NEED_HELP, s.questions, "ic_need_help_24dp"),
                        item(ID_HOW_TO_CONTRIBUTE, s.howToContribute, "ic_baseline_emoji_people_24"),
                    ),
                ),
            ),
        )
    }

    /** Runs a clicked row; an unknown id (a stale click) does nothing. */
    fun handle(id: String) {
        when (id) {
            ID_CHOOSE_DOCUMENT -> host.openChooseDocument()
            ID_SEARCH -> if (host.searchable()) host.openSearch()
            ID_SPEAK -> if (host.speakable()) host.showSpeakTransport()
            ID_BOOKMARKS -> host.openBookmarks()
            ID_MY_DOCUMENTS -> host.openMyDocuments()
            ID_READING_PLAN -> host.openReadingPlan()
            ID_READING_PROGRESS -> host.openReadingProgress()
            ID_HISTORY -> host.showHistorySheet()
            ID_DOWNLOAD -> host.openDownloads()
            ID_AI_SETTINGS -> host.navigate(NavRoutes.AI_PROMPTS)
            ID_SETTINGS -> host.navigate(NavRoutes.SETTINGS)
            ID_NEED_HELP -> host.openUrl(NEED_HELP_LINK)
            ID_HOW_TO_CONTRIBUTE -> host.openUrl(CONTRIBUTE_LINK)
        }
    }

    companion object {
        const val ID_CHOOSE_DOCUMENT = "chooseDocumentButton"
        const val ID_SEARCH = "searchButton"
        const val ID_SPEAK = "speakButton"
        const val ID_BOOKMARKS = "bookmarksButton"
        const val ID_MY_DOCUMENTS = "myDocumentsButton"
        const val ID_READING_PLAN = "dailyReadingPlanButton"
        const val ID_READING_PROGRESS = "readingProgressButton"
        const val ID_HISTORY = "historyButton"
        const val ID_DOWNLOAD = "downloadButton"
        const val ID_AI_SETTINGS = "managePrompts"
        const val ID_SETTINGS = "settingsButton"
        const val ID_NEED_HELP = "needHelp"
        const val ID_HOW_TO_CONTRIBUTE = "howToContribute"

        const val NEED_HELP_LINK = "https://github.com/AndBible/and-bible/wiki/Support"
        const val CONTRIBUTE_LINK = "https://github.com/AndBible/and-bible/wiki/How-to-contribute"

        /** The ids of the rows in the order [build] emits them. */
        val ROW_IDS = listOf(
            ID_CHOOSE_DOCUMENT, ID_SEARCH, ID_SPEAK, ID_BOOKMARKS, ID_MY_DOCUMENTS, ID_READING_PLAN, ID_READING_PROGRESS, ID_HISTORY,
            ID_DOWNLOAD, ID_AI_SETTINGS, ID_SETTINGS, ID_NEED_HELP, ID_HOW_TO_CONTRIBUTE,
        )

        /**
         * Android drawer rows iOS does not show yet, each with what it waits for. Pinned against Android's table by
         * the drawer test so a row cannot be dropped silently.
         */
        val OMITTED_ROWS: Map<String, String> = mapOf(
            "studyPadsButton" to "the label manager's STUDYPAD mode with its result applied to the workspace settings (needs a navigator member that awaits the manager, like assignLabels)",
            "backupMainMenu" to "backup and restore popup (Stream F backup port)",
            "googleDriveSync" to "cloud sync is not offered on iOS (Android's isCloudSyncAvailable is false)",
            "helpButton" to "help and tips dialog (the help HTML is an Android raw resource)",
            "buyDevelopment" to "an external purchase link, left out of the App Store build",
            "appLicence" to "licence dialog (the licence text is an Android raw resource)",
            "tellFriend" to "share sheet message with the store link (no iOS store link yet)",
            "rateButton" to "App Store rating flow (Android's is hidden in several channels; iOS has none yet)",
            "bugReport" to "bug report flow (Android BugReport, no iOS counterpart)",
        )
    }
}
