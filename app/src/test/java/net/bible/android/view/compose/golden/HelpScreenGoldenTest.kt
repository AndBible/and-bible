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
import androidx.compose.ui.platform.LocalContext
import net.bible.android.TEST_SDK
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.docs.UserGuideToc
import net.bible.sharedui.app.ports.HelpAndTips
import net.bible.sharedui.help.HelpScreen
import net.bible.sharedui.help.nav.HelpNavDeps
import net.bible.sharedui.strings.LocalStrings
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens of the full-screen Help & tips ([HelpScreen]): the Tips tab and the User guide tab.
 *
 * Uses the real guide and episode catalog, including unpublished episodes that open the placeholder.
 * Each page and its episodes are sibling link rows, without publication stand-ins.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class HelpScreenGoldenTest {
    private val catalog = HelpNavDeps({}, {}, { emptyList() }, { "Version 6.0.0" }, {})

    private fun screen(tab: Int): @Composable () -> Unit = {
        val context = LocalContext.current
        val strings = LocalStrings.current
        HelpScreen(
            tips = HelpAndTips.toUi(strings, { context.getString(CommonUtils.helpTipStringIds.getValue(it)) }),
            sections = UserGuideToc.sections,
            tutorialVideos = catalog.tutorialVideos("en"),
            versionText = "Version 6.0.0",
            initialTab = tab,
            onOpenUrl = {},
            onUp = {},
        )
    }

    /** A page with four episodes (the AI assistant) at 320dp: separate rows even before publication. */
    private fun manyEpisodes(): @Composable () -> Unit = {
        val aiPage = UserGuideToc.sections.flatMap { it.pages }.first { it.stem == "ai" }
        check(aiPage.episodes.size == 4) { "the ai page is expected to have 4 episodes" }
        HelpScreen(
            tips = emptyList(),
            sections = listOf(UserGuideToc.Section("Study Tools", "user_guide_section_study_tools", listOf(aiPage))),
            tutorialVideos = emptyList(),
            versionText = "Version 6.0.0",
            initialTab = 1,
            onOpenUrl = {},
            onUp = {},
        )
    }

    @Test fun userGuideManyEpisodes_matrix() = captureMatrix("HelpScreen", "userGuideManyEpisodes", heightDp = 900, content = manyEpisodes())

    private fun tutorialCatalog(): @Composable () -> Unit = {
        HelpScreen(
            tips = emptyList(), sections = emptyList(), tutorialVideos = catalog.tutorialVideos("en"),
            versionText = "Version 6.0.0", initialTab = 1, onOpenUrl = {}, onUp = {},
        )
    }

    @Test fun tutorialCatalog_matrix() = captureMatrix("HelpScreen", "tutorialCatalog", heightDp = 3300, content = tutorialCatalog())

    @Test fun tips_matrix() = captureMatrix("HelpScreen", "tips", heightDp = 2600, content = screen(0))

    @Test fun userGuide_matrix() = captureMatrix("HelpScreen", "userGuide", heightDp = 3800, content = screen(1))

    @Test fun userGuide_mono() {
        MONO_MODES.forEach { mode -> captureGolden("HelpScreen", "userGuide", mode, heightDp = 3800, content = screen(1)) }
    }

    @Test fun userGuideManyEpisodes_mono() {
        MONO_MODES.forEach { mode -> captureGolden("HelpScreen", "userGuideManyEpisodes", mode, heightDp = 900, content = manyEpisodes()) }
    }

    @Test fun tutorialCatalog_mono() {
        MONO_MODES.forEach { mode -> captureGolden("HelpScreen", "tutorialCatalog", mode, heightDp = 3300, content = tutorialCatalog()) }
    }
}
