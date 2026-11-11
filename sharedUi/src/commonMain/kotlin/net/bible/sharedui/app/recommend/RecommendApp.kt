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

package net.bible.sharedui.app.recommend

import net.bible.sharedcore.docs.SiteLinks
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedui.strings.Strings

/**
 * "Recommend to a friend": one platform-neutral message (it names no store and no platform, only the
 * homepage) shared as a link, or the homepage as a QR code a friend can scan from the screen.
 */
@kotlin.native.HiddenFromObjC
object RecommendApp {
    const val SHARE_LINK = "share_link"
    const val SHOW_QR = "show_qr"

    /** The text shared with a friend: intro and description on one paragraph, then the link line. */
    fun message(intro: String, body: String, getIt: String): String = "$intro $body\n\n$getIt"

    private fun message(strings: Strings): String =
        message(strings.recommendIntro, strings.recommendBody, strings.recommendGetIt(SiteLinks.HOME_URL))

    /** The question "how to share": a link or a QR code. Cancel (back/scrim) shares nothing. */
    fun chooserRequest(strings: Strings): AppDialogRequest.Options = AppDialogRequest.Options(
        title = strings.tellFriendTitle,
        message = null,
        options = listOf(
            SettingsItem.Choice(SHARE_LINK, strings.recommendShareLink),
            SettingsItem.Choice(SHOW_QR, strings.recommendShowQr),
        ),
        dismissText = null,
        asActionSheet = false,
    )

    /** The QR code of the homepage with its caption. */
    fun qrRequest(strings: Strings): AppDialogRequest.Notice = AppDialogRequest.Notice(
        title = strings.tellFriendTitle,
        showTitleLogo = false,
        blocks = listOf(
            AppDialogRequest.NoticeBlock.QrCode,
            AppDialogRequest.NoticeBlock.Html(strings.recommendQrCaption),
        ),
        confirmText = strings.settingsEditorClose,
    )

    /** Asks the chooser through [ask]; a link goes to [shareText] (text, chooser title), a QR code is shown. */
    suspend fun run(
        strings: Strings,
        ask: suspend (AppDialogRequest) -> AppDialogResult,
        shareText: (text: String, title: String) -> Unit,
    ) {
        when ((ask(chooserRequest(strings)) as? AppDialogResult.Selected)?.value) {
            SHARE_LINK -> shareText(message(strings), strings.tellFriendTitle)
            SHOW_QR -> ask(qrRequest(strings))
        }
    }
}
