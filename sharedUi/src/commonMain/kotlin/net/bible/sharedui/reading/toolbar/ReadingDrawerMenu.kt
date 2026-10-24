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

    // --- R3 ---

    /** Opens the StudyPad chooser (the label manager in STUDYPAD mode); its answer is applied to the workspace settings. */
    fun openStudyPads()

    /** Whether the platform implements the extra row [id] (help, licence, bug report, tell a friend, rate, buy); a row it does not is not shown. */
    fun extraRowOffered(id: String): Boolean

    /** Runs the extra row [id]; only called for an id [extraRowOffered] accepted. */
    fun runExtraRow(id: String)
}

/**
 * The reading view's navigation drawer on a platform without Android's `MenuCommandHandler`: the rows of Android's
 * `DrawerMenuStateBuilder` in Android's order and grouping, and their click handling. The [EXTRA_ROWS] are shown only
 * when the host offers them ([ReadingDrawerHost.extraRowOffered]); a row without an implementation is hidden, never
 * inert. Rows left out for good are listed in [OMITTED_ROWS].
 */
@kotlin.native.HiddenFromObjC
class ReadingDrawerMenu(private val host: ReadingDrawerHost, private val strings: () -> KeyedReadingMenuStrings) {

    fun build(): DrawerMenuState {
        val s = strings()
        fun item(id: String, label: String, icon: String, enabled: Boolean = true) =
            DrawerItemSpec(id = id, label = label, iconKey = icon, enabled = enabled, visible = true)
        fun extra(id: String, label: String, icon: String) =
            DrawerItemSpec(id = id, label = label, iconKey = icon, enabled = true, visible = host.extraRowOffered(id))
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
                        item(ID_STUDY_PADS, s.studyPads, "ic_baseline_studypads_24"),
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
                        item(ID_BACKUP, s.backupAndRestore, "ic_settings_backup_restore_db_24dp"),
                        item(ID_CLOUD_SYNC, s.cloudSync, "ic_syncdb_24dp"),
                        item(ID_AI_SETTINGS, s.aiSettings, "icon_robot"),
                        item(ID_SETTINGS, s.settings, "ic_settings_white_24dp"),
                    ),
                ),
                DrawerGroupSpec(
                    title = s.information,
                    items = listOf(
                        extra(ID_HELP, s.helpAndTips, "ic_help_white_24dp"),
                        extra(ID_BUY_DEVELOPMENT, s.buyDevelopment, "baseline_attach_money_24"),
                        item(ID_NEED_HELP, s.questions, "ic_need_help_24dp"),
                        item(ID_HOW_TO_CONTRIBUTE, s.howToContribute, "ic_baseline_emoji_people_24"),
                        extra(ID_LICENCE, s.appLicence, "ic_baseline_copyright_24"),
                    ),
                ),
                DrawerGroupSpec(
                    title = s.contact,
                    items = listOf(
                        extra(ID_TELL_FRIEND, s.tellFriend, "ic_baseline_people_24"),
                        extra(ID_RATE, s.rateApplication, "ic_rate_review_white_24dp"),
                        extra(ID_BUG_REPORT, s.bugReport, "ic_bug_report_white_24dp"),
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
            ID_STUDY_PADS -> host.openStudyPads()
            ID_MY_DOCUMENTS -> host.openMyDocuments()
            ID_READING_PLAN -> host.openReadingPlan()
            ID_READING_PROGRESS -> host.openReadingProgress()
            ID_HISTORY -> host.showHistorySheet()
            ID_DOWNLOAD -> host.openDownloads()
            ID_BACKUP -> host.navigate(NavRoutes.BACKUP)
            ID_CLOUD_SYNC -> host.navigate(NavRoutes.SYNC_SETTINGS)
            ID_AI_SETTINGS -> host.navigate(NavRoutes.AI_PROMPTS)
            ID_SETTINGS -> host.navigate(NavRoutes.SETTINGS)
            ID_NEED_HELP -> host.openUrl(NEED_HELP_LINK)
            ID_HOW_TO_CONTRIBUTE -> host.openUrl(CONTRIBUTE_LINK)
            in EXTRA_ROWS -> if (host.extraRowOffered(id)) host.runExtraRow(id)
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
        const val ID_STUDY_PADS = "studyPadsButton"
        const val ID_BACKUP = "backupMainMenu"
        const val ID_CLOUD_SYNC = "googleDriveSync"
        const val ID_HELP = "helpButton"
        const val ID_LICENCE = "appLicence"
        const val ID_BUG_REPORT = "bugReport"
        const val ID_TELL_FRIEND = "tellFriend"
        const val ID_RATE = "rateButton"
        const val ID_BUY_DEVELOPMENT = "buyDevelopment"
        const val ID_NEED_HELP = "needHelp"
        const val ID_HOW_TO_CONTRIBUTE = "howToContribute"

        const val NEED_HELP_LINK = "https://github.com/AndBible/and-bible/wiki/Support"
        const val CONTRIBUTE_LINK = "https://github.com/AndBible/and-bible/wiki/How-to-contribute"

        /** Rows whose destination the platform supplies through [ReadingDrawerHost.runExtraRow] (`IosReadingExtras`). */
        val EXTRA_ROWS: Set<String> = setOf(ID_HELP, ID_LICENCE, ID_BUG_REPORT, ID_TELL_FRIEND, ID_RATE, ID_BUY_DEVELOPMENT)

        /** The ids of every row [build] can emit, in Android's order (an extra row only when the host offers it). */
        val ROW_IDS = listOf(
            ID_CHOOSE_DOCUMENT, ID_SEARCH, ID_SPEAK, ID_BOOKMARKS, ID_STUDY_PADS, ID_MY_DOCUMENTS, ID_READING_PLAN,
            ID_READING_PROGRESS, ID_HISTORY,
            ID_DOWNLOAD, ID_BACKUP, ID_CLOUD_SYNC, ID_AI_SETTINGS, ID_SETTINGS,
            ID_HELP, ID_BUY_DEVELOPMENT, ID_NEED_HELP, ID_HOW_TO_CONTRIBUTE, ID_LICENCE,
            ID_TELL_FRIEND, ID_RATE, ID_BUG_REPORT,
        )

        /**
         * Android drawer rows that are never shown, each with why. Pinned against Android's table by the drawer test
         * so a row cannot be dropped silently. Empty: every row is shown or, for the [EXTRA_ROWS], hidden until the
         * platform offers it.
         */
        val OMITTED_ROWS: Map<String, String> = emptyMap()
    }
}
