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

package net.bible.android.view.compose.golden

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import net.bible.android.TEST_SDK
import net.bible.sharedcore.reading.DrawerGroup
import net.bible.sharedcore.reading.DrawerItem
import net.bible.sharedcore.reading.DrawerMenuState
import net.bible.sharedui.reading.ReadingDrawerContent
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures [ReadingDrawerContent] directly rather than opening the real `ModalNavigationDrawer`:
 * force-opening a drawer/popup inside a Roborazzi capture hangs the test (the same rationale as
 * [WindowPaneMenuGoldenTest] / [ReadingOverflowMenuGoldenTest]).
 *
 * Icons resolve through [icon] to the same drawables production renders (the fixture carries
 * [DrawerMenuStateBuilder][net.bible.android.view.activity.page.DrawerMenuStateBuilder]'s
 * production `iconKey`s and looks them up by name), so the goldens below show the real 24dp row
 * glyphs and the 48dp header logo, not a layout with the icons stripped out.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ReadingDrawerGoldenTest {

    private fun item(id: String, label: String, iconKey: String, enabled: Boolean = true) =
        DrawerItem(id = id, label = label, iconKey = iconKey, enabled = enabled)

    /**
     * Resolves a drawer `iconKey` exactly as the host does (`ComposeReadingViewHost`'s
     * `drawerIconResIds` table), but by name, so this fixture needs no access to that private map.
     * Returning `null` for an unknown name would silently reproduce the icon-free goldens this
     * test used to record, so [everyFixtureIconKeyResolves] asserts none of them is unknown.
     */
    @Composable
    private fun icon(iconKey: String): Painter? {
        val context = LocalContext.current
        val resId = context.resources.getIdentifier(iconKey, "drawable", context.packageName)
        return if (resId == 0) null else painterResource(resId)
    }

    private fun state(
        searchEnabled: Boolean = true,
        speakEnabled: Boolean = true,
        includeSync: Boolean = true,
    ) = DrawerMenuState(
        appName = "AndBible",
        versionText = "Version 5.1.1110",
        groups = listOfNotNull(
            DrawerGroup(null, listOf(
                item("chooseDocumentButton", "Choose document", "ic_library_books_white_24dp"),
                item("searchButton", "Search", "ic_search_24dp", enabled = searchEnabled),
                item("speakButton", "Speak", "ic_baseline_headphones_24", enabled = speakEnabled),
                item("bookmarksButton", "Bookmarks", "ic_baseline_bookmark_24"),
                item("studyPadsButton", "StudyPads", "ic_baseline_studypads_24"),
                item("myDocumentsButton", "My documents", "ic_baseline_description_24"),
                item("dailyReadingPlanButton", "Reading plans", "ic_reading_plan_24dp"),
                item("readingProgressButton", "Reading progress", "ic_bar_chart_24dp"),
                item("historyButton", "History", "ic_history_clock_24dp"),
            )),
            DrawerGroup("Administration", listOfNotNull(
                item("downloadButton", "Download documents", "ic_file_download_24dp"),
                item("backupMainMenu", "Backup & Restore", "ic_settings_backup_restore_db_24dp"),
                if (includeSync) item("googleDriveSync", "Device synchronization", "ic_syncdb_24dp") else null,
                item("managePrompts", "AI Settings", "icon_robot"),
                item("settingsButton", "Application preferences", "ic_settings_white_24dp"),
            )),
            DrawerGroup("Information", listOf(
                item("helpButton", "Help & tips", "ic_help_white_24dp"),
                item("buyDevelopment", "Sponsor development", "baseline_attach_money_24"),
                item("needHelp", "Questions", "ic_need_help_24dp"),
                item("howToContribute", "How to contribute", "ic_baseline_emoji_people_24"),
                item("appLicence", "Licence", "ic_baseline_copyright_24"),
            )),
            DrawerGroup("Contact", listOf(
                item("tellFriend", "Tell a friend", "ic_baseline_people_24"),
                item("rateButton", "Rate application", "ic_rate_review_white_24dp"),
                item("bugReport", "Report a bug", "ic_bug_report_white_24dp"),
            )),
        ),
    )

    private fun content(s: DrawerMenuState): @Composable () -> Unit = {
        ReadingDrawerContent(state = s, icon = { key -> icon(key) }, onItemClick = {})
    }

    @Test
    fun everyFixtureIconKeyResolves() {
        val context = RuntimeEnvironment.getApplication()
        val keys = state().groups.flatMap { it.items }.map { it.iconKey } + "ic_logo"
        val unresolved = keys.filter {
            context.resources.getIdentifier(it, "drawable", context.packageName) == 0
        }
        assertThat("every fixture iconKey must name a real drawable", unresolved, equalTo(emptyList<String>()))
    }

    @Test fun items_matrix() =
        captureMatrix("ReadingDrawer", "items", heightDp = 1800, content = content(state()))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun items_rtl() =
        captureRtl("ReadingDrawer", "items", heightDp = 1800, content = content(state()))

    @Test fun syncUnavailable() =
        captureGolden("ReadingDrawer", "syncUnavailable", EDGE_MODE, heightDp = 1800,
            content = content(state(includeSync = false)))

    @Test fun searchAndSpeakDisabled() =
        captureGolden("ReadingDrawer", "searchAndSpeakDisabled", EDGE_MODE, heightDp = 1800,
            content = content(state(searchEnabled = false, speakEnabled = false)))

    @Test fun longTitles() =
        captureGolden("ReadingDrawer", "longTitles", EDGE_MODE, heightDp = 3000, content = content(
            state().copy(groups = state().groups.map { g ->
                g.copy(items = g.items.map {
                    it.copy(label = it.label + " — a very long translated label that must wrap onto several lines")
                })
            })
        ))

    @Test
    fun items_mono() {
        MONO_MODES.forEach { mode -> captureGolden("ReadingDrawer", "items", mode, heightDp = 1800, content = content(state())) }
    }

    @Test
    fun syncUnavailable_mono() {
        MONO_MODES.forEach { mode -> captureGolden("ReadingDrawer", "syncUnavailable", mode, heightDp = 1800,
            content = content(state(includeSync = false))) }
    }

    @Test
    fun searchAndSpeakDisabled_mono() {
        MONO_MODES.forEach { mode -> captureGolden("ReadingDrawer", "searchAndSpeakDisabled", mode, heightDp = 1800,
            content = content(state(searchEnabled = false, speakEnabled = false))) }
    }

    @Test
    fun longTitles_mono() {
        MONO_MODES.forEach { mode -> captureGolden("ReadingDrawer", "longTitles", mode, heightDp = 3000, content = content(
            state().copy(groups = state().groups.map { g ->
                g.copy(items = g.items.map {
                    it.copy(label = it.label + " — a very long translated label that must wrap onto several lines")
                })
            })
        )) }
    }
}
