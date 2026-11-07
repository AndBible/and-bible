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
package net.bible.sharedui.app.notice

import kotlinx.coroutines.test.runTest
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedui.MemoryAppSettings
import net.bible.sharedui.strings.iosStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class BetaNoticeTest {
    private val strings = iosStrings("en")
    private val version = "6.0.1254-beta"

    private fun body(platform: BetaNoticePlatform): String =
        (BetaNotice.request(platform, strings, version).blocks.single() as AppDialogRequest.NoticeBlock.Html).html

    @Test
    fun everyPlatformCarriesTheSharedPartsAndTheStandardParagraphs() {
        BetaNoticePlatform.entries.forEach { platform ->
            val request = BetaNotice.request(platform, strings, version)
            val html = body(platform)
            assertEquals(strings.betaNoticeTitle, request.title, "$platform")
            assertTrue(request.showTitleLogo)
            assertEquals(strings.betaNoticeDismissUntilUpdate, request.confirmText)
            assertEquals(strings.dismiss, request.neutralText)
            assertEquals(null, request.dismissText)
            listOf(
                "DEVELOPER'S SPECIAL NOTICE FOR BETA TESTERS",
                "Android, iPhone, iPad and Apple Silicon Macs, Windows and Linux",
                "<b>AndBible on your other devices</b>",
                "href=\"https://andbible.org/docs/getting_started/#installation\"",
                "href=\"${BetaNotice.PLAY_BETA_LINK}\"",
                "href=\"${BetaNotice.RELEASES_LINK}\"",
                "TestFlight",
                ".msi", ".deb", "AppImage",
                "href=\"${BetaNotice.NEW_ISSUE_LINK}\"",
                "Main menu → Feedback / bug report",
                "Main menu → Backup &amp; Restore",
                "Best regards, Tuomas",
                "sponsoring development hours",
                strings.betaNoticeContent1,
                strings.betaNoticeGithubIssues,
                strings.versionText(version),
            ).forEach { assertTrue(html.contains(it), "$platform notice lacks '$it'") }
            // The two statements the November 2026 text corrected.
            assertFalse(html.contains("macOS"), "$platform: Mac is the iPad app, not a desktop build")
            assertFalse(html.contains("Report a bug"), "$platform: the menu row is 'Feedback / bug report'")
        }
    }

    @Test
    fun androidVariantNamesItsOwnSyncTargetsAndUpgrade() {
        val html = body(BetaNoticePlatform.ANDROID)
        assertTrue(html.contains("besides Google Drive and Nextcloud"))
        assertTrue(html.contains("differently from 5.x"))
        assertTrue(html.contains("Jetpack Compose"))
        assertFalse(html.contains("iCloud"))
    }

    @Test
    fun iosVariantIsWordedForAndBibleForIos10Users() {
        val html = body(BetaNoticePlatform.IOS)
        assertTrue(html.contains("AndBible for iOS 1.0"))
        assertTrue(html.contains("via Nextcloud, WebDAV"))
        assertTrue(html.contains("or iCloud Drive"))
        assertFalse(html.contains("Google Drive"), "no Google Drive sync on iOS")
        assertFalse(html.contains("search index"), "the index rebuild is an Android upgrade step")
        assertFalse(html.contains("5.x"))
        assertFalse(html.contains("Jetpack"))
    }

    @Test
    fun desktopVariantUsesTheDesktopSyncTargets() {
        val html = body(BetaNoticePlatform.DESKTOP)
        assertTrue(html.contains("AndBible on Windows and Linux"))
        assertTrue(html.contains("Nextcloud or WebDAV"))
        assertFalse(html.contains("Google Drive"))
        assertFalse(html.contains("iCloud"))
        assertFalse(html.contains("5.x"))
    }

    @Test
    fun variantsDiffer() {
        assertEquals(3, BetaNoticePlatform.entries.map { BetaNotice.specialNoticeHtml(it, strings) }.toSet().size)
    }

    @Test
    fun everySentenceComesFromTheStringResources() {
        // A translation replaces the text: nothing of the special notice is hard-coded English.
        val translated = object : net.bible.sharedui.strings.Strings by strings {
            override val betaSpecialHeading = "OTSIKKO"
            override fun betaSpecialReport(githubLink: String, menuItem: String) = "RAPORTOI $githubLink $menuItem"
            override val reportGeneralBugLabel = "Palaute & vikailmoitus"
            override val betaSpecialPlayBetaLink = "beetaohjelma"
        }
        val html = BetaNotice.specialNoticeHtml(BetaNoticePlatform.ANDROID, translated)
        assertTrue(html.contains("<b>OTSIKKO</b>"))
        assertTrue(html.contains("RAPORTOI <a href=\"${BetaNotice.NEW_ISSUE_LINK}\">GitHub</a> Palaute &amp; vikailmoitus"))
        assertTrue(html.contains("<a href=\"${BetaNotice.PLAY_BETA_LINK}\">beetaohjelma</a>"))
        assertFalse(html.contains("DEVELOPER'S SPECIAL NOTICE"))
        assertFalse(html.contains("Please report bugs"))
    }

    @Test
    fun gating() {
        assertTrue(BetaNotice.shouldShow(isBeta = true, discrete = false, dismissedVersion = 0))
        assertTrue(BetaNotice.shouldShow(isBeta = true, discrete = false, dismissedVersion = BetaNotice.ANNOUNCE_VERSION - 1))
        assertFalse(BetaNotice.shouldShow(isBeta = true, discrete = false, dismissedVersion = BetaNotice.ANNOUNCE_VERSION))
        assertFalse(BetaNotice.shouldShow(isBeta = false, discrete = false, dismissedVersion = 0))
        assertFalse(BetaNotice.shouldShow(isBeta = true, discrete = true, dismissedVersion = 0))
    }

    @Test
    fun announceVersionWasBumpedForTheNewText() {
        // Testers who dismissed the previous text (version 4) see this one once.
        assertEquals(5, BetaNotice.ANNOUNCE_VERSION)
    }

    private suspend fun show(
        settings: MemoryAppSettings,
        isBeta: Boolean = true,
        discrete: Boolean = false,
        answer: (AppDialogRequest) -> AppDialogResult,
    ) = BetaNotice.showIfNeeded(BetaNoticePlatform.IOS, isBeta, discrete, settings, strings, version) { answer(it) }

    @Test
    fun dismissUntilNextUpdatePersistsTheAnnounceVersion() = runTest {
        val settings = MemoryAppSettings()
        var asked: AppDialogRequest? = null
        assertTrue(show(settings) { asked = it; AppDialogResult.Ok })
        assertEquals(BetaNotice.request(BetaNoticePlatform.IOS, strings, version), asked)
        assertEquals(BetaNotice.ANNOUNCE_VERSION, settings.getInt(BetaNotice.DISPLAYED_KEY, 0))
        // Shown once: the next start asks nothing.
        assertFalse(show(settings) { fail("asked again after dismiss until next update") })
    }

    @Test
    fun plainDismissShowsItAgainNextStart() = runTest {
        val settings = MemoryAppSettings()
        assertFalse(show(settings) { AppDialogResult.Neutral })
        assertEquals(0, settings.getInt(BetaNotice.DISPLAYED_KEY, 0))
        var asked = 0
        show(settings) { asked++; AppDialogResult.Cancel }
        assertEquals(1, asked)
        assertEquals(0, settings.getInt(BetaNotice.DISPLAYED_KEY, 0))
    }

    @Test
    fun testersWhoDismissedTheOldTextSeeTheNewOnce() = runTest {
        val settings = MemoryAppSettings().apply { setInt(BetaNotice.DISPLAYED_KEY, 4) }
        var asked = 0
        assertTrue(show(settings) { asked++; AppDialogResult.Ok })
        assertEquals(1, asked)
    }

    @Test
    fun notShownForNonBetaOrDiscreteBuilds() = runTest {
        val settings = MemoryAppSettings()
        assertFalse(show(settings, isBeta = false) { fail("asked in a non-beta build") })
        assertFalse(show(settings, discrete = true) { fail("asked in discrete mode") })
        assertEquals(0, settings.getInt(BetaNotice.DISPLAYED_KEY, 0))
    }
}
