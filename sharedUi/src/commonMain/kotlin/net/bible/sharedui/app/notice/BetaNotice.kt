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

package net.bible.sharedui.app.notice

import net.bible.sharedcore.docs.DocsLinks
import net.bible.sharedcore.docs.SiteLinks
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedui.strings.Strings

/** The platform whose wording the beta notice uses ([BetaNotice.specialNoticeHtml]). */
@kotlin.native.HiddenFromObjC
enum class BetaNoticePlatform { ANDROID, IOS, DESKTOP }

/**
 * The beta notice shown once per [ANNOUNCE_VERSION] at startup of a beta build (Android: `ReadingAppBootstrap`,
 * iOS and desktop: the host app root). One source for every platform: the developer's special notice (the
 * `beta_special_*` resources, one whole paragraph or list item each, platform variants where the wording differs)
 * with the standard beta warning, bug report and contribution lines folded in.
 *
 * "Dismiss until next update" ([AppDialogResult.Ok]) stores [ANNOUNCE_VERSION] under [DISPLAYED_KEY] in the shared
 * settings; "Dismiss" shows it again on the next start. Never shown in discrete mode: it names AndBible and links
 * github.com/AndBible.
 */
@kotlin.native.HiddenFromObjC
object BetaNotice {
    /** The settings key holding the last dismissed [ANNOUNCE_VERSION] (the name predates this object). */
    const val DISPLAYED_KEY = "beta-notice-displayed2"

    /** Bump to show a changed notice once more to testers who dismissed the previous one. 5: November 2026 text. */
    const val ANNOUNCE_VERSION = 5

    const val NEW_ISSUE_LINK = "https://github.com/AndBible/and-bible/issues/new/choose"
    const val RELEASES_LINK = "https://github.com/AndBible/and-bible/releases"
    /** The public TestFlight beta; keep equal to `ios_beta_link` in website/content/en/site.yaml. */
    const val TESTFLIGHT_LINK = "https://testflight.apple.com/join/sB7guYsk"
    const val PLAY_BETA_LINK = "https://play.google.com/apps/testing/net.bible.android.activity"
    val INSTALLATION_GUIDE_LINK: String = DocsLinks.page("getting_started", "installation")

    /** Whether to show the notice: a beta build, not discrete, and this [ANNOUNCE_VERSION] not dismissed yet. */
    fun shouldShow(isBeta: Boolean, discrete: Boolean, dismissedVersion: Int): Boolean =
        isBeta && !discrete && dismissedVersion < ANNOUNCE_VERSION

    /**
     * Shows the notice through [ask] when [shouldShow] says so. Returns true when the user chose "Dismiss until next
     * update", which is then persisted in [settings].
     */
    suspend fun showIfNeeded(
        platform: BetaNoticePlatform,
        isBeta: Boolean,
        discrete: Boolean,
        settings: AppSettings,
        strings: Strings,
        version: String,
        ask: suspend (AppDialogRequest) -> AppDialogResult,
    ): Boolean {
        if (!shouldShow(isBeta, discrete, settings.getInt(DISPLAYED_KEY, 0))) return false
        return if (ask(request(platform, strings, version)) == AppDialogResult.Ok) {
            settings.setInt(DISPLAYED_KEY, ANNOUNCE_VERSION)
            true
        } else {
            false
        }
    }

    /** The dialog: the platform's special notice and the [version] line. */
    fun request(platform: BetaNoticePlatform, strings: Strings, version: String): AppDialogRequest.Notice {
        val html = "${specialNoticeHtml(platform, strings)}<br><br><i>${strings.versionText(version)}</i>"
        return AppDialogRequest.Notice(
            title = strings.betaNoticeTitle,
            showTitleLogo = true,
            blocks = listOf(AppDialogRequest.NoticeBlock.Html(html)),
            confirmText = strings.betaNoticeDismissUntilUpdate,
            neutralText = strings.settingsEditorClose,
        )
    }

    /**
     * The developer's special notice for beta testers, worded for [platform]'s users: paragraphs separated by a blank
     * line, list items with a bullet. Every sentence is one resource; links and menu item names are its arguments.
     */
    fun specialNoticeHtml(platform: BetaNoticePlatform, strings: Strings): String = with(strings) {
        val github = link(NEW_ISSUE_LINK, betaNoticeGithubIssues)
        val bugReportItem = escape(reportGeneralBugLabel)
        val intro: String
        val news: List<String>
        val report: String
        val leaveBeta: String?
        when (platform) {
            BetaNoticePlatform.ANDROID -> {
                intro = betaSpecialIntroAndroid
                news = listOf("$betaSpecialRedesign $betaSpecialDataKept", betaSpecialPlatforms, betaSpecialAdditionsAndroid)
                report = betaSpecialReportAndroid(github, bugReportItem)
                leaveBeta = betaSpecialLeaveBetaAndroid(link(PLAY_BETA_LINK, betaSpecialPlayBetaLink))
            }
            BetaNoticePlatform.IOS -> {
                intro = betaSpecialIntroIos
                news = listOf(betaSpecialRedesign, betaSpecialOneApp, betaSpecialSyncIos)
                report = betaSpecialReport(github, bugReportItem)
                leaveBeta = null
            }
            BetaNoticePlatform.DESKTOP -> {
                intro = betaSpecialIntroDesktop
                news = listOf(betaSpecialRedesign, betaSpecialOneApp, betaSpecialSyncDesktop)
                report = betaSpecialReport(github, bugReportItem)
                leaveBeta = null
            }
        }
        val releases = link(RELEASES_LINK, "GitHub")
        val otherDevices = listOf(
            betaSpecialOtherAndroid(link(PLAY_BETA_LINK, betaSpecialPlayBetaLink), releases),
            betaSpecialOtherApple(link(TESTFLIGHT_LINK, betaSpecialTestflightLink)),
            betaSpecialOtherDesktop(releases),
        )
        listOf(
            "<b>$betaSpecialHeading</b>",
            intro,
            bullets(news),
            listOfNotNull(betaSpecialBetaWarning, leaveBeta).joinToString(" "),
            "$betaSpecialFeedback ${betaSpecialBackup(escape(backupAndRestoreTitle))}",
            report,
            "<b>$betaSpecialOtherDevicesTitle</b><br>$betaSpecialOtherDevicesIntro<br>" + bullets(otherDevices) + "<br>" +
                betaSpecialInstallationGuide(link(INSTALLATION_GUIDE_LINK, betaSpecialInstallationGuideLink)),
            betaSpecialRegards,
            betaSpecialSponsor(link(SiteLinks.SHOP_URL, betaSpecialSponsorLink)),
        ).joinToString("<br><br>")
    }

    private fun bullets(items: List<String>): String = items.joinToString("<br>") { "• $it" }

    private fun link(url: String, text: String): String = "<a href=\"$url\">$text</a>"

    /** A menu item name inside the HTML ("Backup & Restore"). */
    private fun escape(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;")
}
