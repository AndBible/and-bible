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
import net.bible.android.TEST_SDK
import net.bible.sharedcore.reading.DrawerGroup
import net.bible.sharedcore.reading.DrawerItem
import net.bible.sharedcore.reading.DrawerMenuState
import net.bible.sharedui.reading.ReadingDrawerContent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures [ReadingDrawerContent] directly rather than opening the real `ModalNavigationDrawer`:
 * force-opening a drawer/popup inside a Roborazzi capture hangs the test (the same rationale as
 * [WindowPaneMenuGoldenTest] / [ReadingOverflowMenuGoldenTest]).
 *
 * Icons resolve to `null` here (the `icon` lambda returns `null`), so the goldens show the layout
 * and text without depending on Android drawable rasterization — the icons themselves (including
 * the header logo, which is simply absent from every golden below) are an on-device A/B item.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ReadingDrawerGoldenTest {

    private fun item(id: String, label: String, enabled: Boolean = true) =
        DrawerItem(id = id, label = label, iconKey = "ic_$id", enabled = enabled)

    private fun state(
        searchEnabled: Boolean = true,
        speakEnabled: Boolean = true,
        includeSync: Boolean = true,
    ) = DrawerMenuState(
        appName = "AndBible",
        versionText = "Version 5.1.1110",
        groups = listOfNotNull(
            DrawerGroup(null, listOf(
                item("chooseDocumentButton", "Choose document"),
                item("searchButton", "Search", enabled = searchEnabled),
                item("speakButton", "Speak", enabled = speakEnabled),
                item("bookmarksButton", "Bookmarks"),
                item("studyPadsButton", "StudyPads"),
                item("myDocumentsButton", "My documents"),
                item("dailyReadingPlanButton", "Reading plans"),
                item("readingProgressButton", "Reading progress"),
                item("historyButton", "History"),
            )),
            DrawerGroup("Administration", listOfNotNull(
                item("downloadButton", "Download documents"),
                item("backupMainMenu", "Backup & Restore"),
                if (includeSync) item("googleDriveSync", "Device synchronization") else null,
                item("managePrompts", "AI Settings"),
                item("settingsButton", "Application preferences"),
            )),
            DrawerGroup("Information", listOf(
                item("helpButton", "Help & tips"),
                item("buyDevelopment", "Sponsor development"),
                item("needHelp", "Questions"),
                item("howToContribute", "How to contribute"),
                item("appLicence", "Licence"),
            )),
            DrawerGroup("Contact", listOf(
                item("tellFriend", "Tell a friend"),
                item("rateButton", "Rate application"),
                item("bugReport", "Report a bug"),
            )),
        ),
    )

    private fun content(s: DrawerMenuState): @Composable () -> Unit = {
        ReadingDrawerContent(state = s, icon = { null }, onItemClick = {})
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
}
