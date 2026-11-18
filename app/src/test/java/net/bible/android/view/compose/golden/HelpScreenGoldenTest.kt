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
import net.bible.sharedcore.docs.DocsLink
import net.bible.sharedcore.docs.TutorialVideos
import net.bible.sharedcore.docs.UserGuideToc
import net.bible.sharedui.app.ports.HelpAndTips
import net.bible.sharedui.help.HelpScreen
import net.bible.sharedui.help.VideoRowUi
import net.bible.sharedui.strings.LocalStrings
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens of the full-screen Help & tips ([HelpScreen]): the Tips tab and the User guide tab.
 *
 * The real [UserGuideToc.sections] are used; a page's published episodes are indented rows below it. No episode is published yet, so the fixture's
 * **stand-in** [publishedStandIn] pretends that one episode is, to show the play button; the video
 * list uses [standInVideos] for the same reason.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class HelpScreenGoldenTest {
    private val publishedStandIn = TutorialVideos.Episode.TUTORIAL_01_GETTING_STARTED
    private val standInVideos = listOf(
        VideoRowUi("1. Getting started", DocsLink.GETTING_STARTED.url),
        VideoRowUi("2. Finding your way", DocsLink.NAVIGATION.url),
    )

    private fun screen(tab: Int): @Composable () -> Unit = {
        val context = LocalContext.current
        val strings = LocalStrings.current
        HelpScreen(
            tips = HelpAndTips.toUi(strings, { context.getString(CommonUtils.helpTipStringIds.getValue(it)) }),
            sections = UserGuideToc.sections,
            tutorialVideos = standInVideos,
            versionText = "Version 6.0.0",
            initialTab = tab,
            onOpenUrl = {},
            onUp = {},
            isPublished = { it == publishedStandIn },
        )
    }

    /** A page with 4 published episodes (the AI assistant) at the standard 320dp width: separate rows, no icon strip. */
    private fun manyEpisodes(): @Composable () -> Unit = {
        val aiPage = UserGuideToc.sections.flatMap { it.pages }.first { it.stem == "ai" }
        check(aiPage.episodes.size == 4) { "the ai page is expected to have 4 episodes" }
        HelpScreen(
            tips = emptyList(),
            sections = listOf(UserGuideToc.Section("Study Tools", listOf(aiPage))),
            tutorialVideos = emptyList(),
            versionText = "Version 6.0.0",
            initialTab = 1,
            onOpenUrl = {},
            onUp = {},
            isPublished = { true },
        )
    }

    @Test fun userGuideManyEpisodes_matrix() = captureMatrix("HelpScreen", "userGuideManyEpisodes", heightDp = 900, content = manyEpisodes())

    @Test fun tips_matrix() = captureMatrix("HelpScreen", "tips", heightDp = 2600, content = screen(0))

    @Test fun userGuide_matrix() = captureMatrix("HelpScreen", "userGuide", heightDp = 3800, content = screen(1))

    @Test fun userGuide_mono() {
        MONO_MODES.forEach { mode -> captureGolden("HelpScreen", "userGuide", mode, heightDp = 3800, content = screen(1)) }
    }
}
