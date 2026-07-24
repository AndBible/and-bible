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

package net.bible.android.view.activity.page

import net.bible.android.activity.R
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.reading.DrawerGroupSpec
import net.bible.sharedcore.reading.DrawerItemSpec
import net.bible.sharedcore.reading.DrawerMenu
import net.bible.sharedcore.reading.DrawerMenuState

/**
 * Builds the Compose reading-view navigation drawer's item list and maps a clicked row back to its
 * `R.id.*` — the Compose-side counterpart of classic `MainBibleActivity`'s `NavigationView`
 * (`R.menu.main_bible_drawer_menu` + `navigation_drawer_with_footer.xml`), ported for Batch Z-early.
 *
 * Third instance of the pattern established by [OptionsMenuStateBuilder] and
 * [WindowPaneMenuStateBuilder]: a static table mirroring the menu XML in declaration order, guarded
 * by a drift test that inflates the real XML and compares. Titles are resolved here (host-side) into
 * `DrawerItem.label`, exactly as `OptionsMenuStateBuilder.build` does; `:sharedCore` never sees `R`.
 *
 * Dispatch is NOT here: a click goes through `MainBibleActivity` to
 * [MenuCommandHandler.handleMenuRequest] with [resIdFor]'s `R.id.*`, which is the very same handler
 * the classic `setNavigationItemSelectedListener` calls.
 */
object DrawerMenuStateBuilder {

    private data class StaticEntry(
        val resId: Int,
        val idName: String,
        val titleRes: Int,
        val iconName: String,
    )

    /** `null` title = the untitled top tier, mirroring the XML's bare `<item>`s. */
    private data class StaticGroup(val titleRes: Int?, val entries: List<StaticEntry>)

    private val groups: List<StaticGroup> = listOf(
        StaticGroup(null, listOf(
            StaticEntry(R.id.chooseDocumentButton, "chooseDocumentButton", R.string.chooce_document, "ic_library_books_white_24dp"),
            StaticEntry(R.id.searchButton, "searchButton", R.string.search, "ic_search_24dp"),
            StaticEntry(R.id.speakButton, "speakButton", R.string.speak, "ic_baseline_headphones_24"),
            StaticEntry(R.id.bookmarksButton, "bookmarksButton", R.string.bookmarks, "ic_baseline_bookmark_24"),
            StaticEntry(R.id.studyPadsButton, "studyPadsButton", R.string.studypads, "ic_baseline_studypads_24"),
            StaticEntry(R.id.myDocumentsButton, "myDocumentsButton", R.string.my_documents_title, "ic_baseline_description_24"),
            StaticEntry(R.id.dailyReadingPlanButton, "dailyReadingPlanButton", R.string.rdg_plan_title, "ic_reading_plan_24dp"),
            StaticEntry(R.id.readingProgressButton, "readingProgressButton", R.string.reading_progress_title, "ic_bar_chart_24dp"),
            StaticEntry(R.id.historyButton, "historyButton", R.string.history, "ic_history_clock_24dp"),
        )),
        StaticGroup(R.string.administration, listOf(
            StaticEntry(R.id.downloadButton, "downloadButton", R.string.download, "ic_file_download_24dp"),
            StaticEntry(R.id.backupMainMenu, "backupMainMenu", R.string.backup_and_restore, "ic_settings_backup_restore_db_24dp"),
            StaticEntry(R.id.googleDriveSync, "googleDriveSync", R.string.cloud_sync_title, "ic_syncdb_24dp"),
            StaticEntry(R.id.managePrompts, "managePrompts", R.string.ai_settings, "icon_robot"),
            StaticEntry(R.id.settingsButton, "settingsButton", R.string.settings, "ic_settings_white_24dp"),
        )),
        StaticGroup(R.string.information, listOf(
            StaticEntry(R.id.helpButton, "helpButton", R.string.help_and_tips, "ic_help_white_24dp"),
            StaticEntry(R.id.buyDevelopment, "buyDevelopment", R.string.buy_development, "baseline_attach_money_24"),
            StaticEntry(R.id.needHelp, "needHelp", R.string.questions_title, "ic_need_help_24dp"),
            StaticEntry(R.id.howToContribute, "howToContribute", R.string.how_to_contribute, "ic_baseline_emoji_people_24"),
            StaticEntry(R.id.appLicence, "appLicence", R.string.app_licence_title, "ic_baseline_copyright_24"),
        )),
        StaticGroup(R.string.contact, listOf(
            StaticEntry(R.id.tellFriend, "tellFriend", R.string.tell_friend_title, "ic_baseline_people_24"),
            StaticEntry(R.id.rateButton, "rateButton", R.string.rate_application, "ic_rate_review_white_24dp"),
            StaticEntry(R.id.bugReport, "bugReport", R.string.send_bug_report_title, "ic_bug_report_white_24dp"),
        )),
    )

    private val entryByName: Map<String, StaticEntry> =
        groups.flatMap { it.entries }.associateBy { it.idName }

    /** Every entry's XML id name in declaration order — the drift test's expectation. */
    val entryIdNames: List<String> = groups.flatMap { it.entries }.map { it.idName }

    /**
     * Every entry's drawable name (the `iconKey` handed to `:sharedCore`), in declaration order.
     * Exposed for `ComposeReadingViewHostTest`, which asserts the host's explicit
     * `drawerIconResIds` table resolves every one of them (Batch Z-early A7 fix F).
     */
    val entryIconNames: List<String> = groups.flatMap { it.entries }.map { it.iconName }

    /**
     * Read-only per-submenu view of the static table: each group's heading `titleRes` (`null` for
     * the untitled top tier) paired with that group's entries as (idName, titleRes). Exposed only
     * for the XML drift test ([net.bible.android.view.activity.page.DrawerMenuStateBuilderTest]),
     * which needs both titles AND group boundaries preserved — a flattened id list (see
     * [entryIdNames]) can't catch a stale title or an item moved between submenus.
     */
    val groupsForDriftTest: List<Pair<Int?, List<Pair<String, Int>>>> =
        groups.map { g -> g.titleRes to g.entries.map { it.idName to it.titleRes } }

    /** The `R.id.*` a clicked [DrawerItem.id] maps to. Throws on an unknown id (stale click). */
    fun resIdFor(idName: String): Int =
        (entryByName[idName] ?: throw IllegalArgumentException("Unknown drawer item id: $idName")).resId

    /**
     * Mirrors the classic dynamic writes:
     * - `rateButton.isVisible = false` when [isRateVisible] is false,
     * - `googleDriveSync.isVisible = false` when [isCloudSyncAvailable] is false,
     * - `searchButton.isEnabled = showSearch`, `speakButton.isEnabled = showSpeak`.
     */
    fun build(
        showSearch: Boolean,
        showSpeak: Boolean,
        isCloudSyncAvailable: Boolean,
        isRateVisible: Boolean,
    ): DrawerMenuState = DrawerMenu.build(
        appName = application.getString(R.string.app_name_medium),
        versionText = application.getString(R.string.version_text, CommonUtils.applicationVersionName),
        groups = groups.map { group ->
            DrawerGroupSpec(
                title = group.titleRes?.let { application.getString(it) },
                items = group.entries.map { e ->
                    DrawerItemSpec(
                        id = e.idName,
                        label = application.getString(e.titleRes),
                        iconKey = e.iconName,
                        enabled = when (e.idName) {
                            "searchButton" -> showSearch
                            "speakButton" -> showSpeak
                            else -> true
                        },
                        visible = when (e.idName) {
                            "googleDriveSync" -> isCloudSyncAvailable
                            "rateButton" -> isRateVisible
                            else -> true
                        },
                    )
                },
            )
        },
    )
}
