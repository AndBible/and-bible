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
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedui.strings.Strings

/**
 * "Recommend to a friend": one platform-neutral message (it names no store and no platform, only the
 * homepage). One dialog shows the homepage as a QR code a friend can scan from the screen, with a "Share a link"
 * button that hands the message to the platform's share sheet.
 */
@kotlin.native.HiddenFromObjC
object RecommendApp {
    /** The text shared with a friend: intro and description on one paragraph, then the link line. */
    fun message(intro: String, body: String, getIt: String): String = "$intro $body\n\n$getIt"

    private fun message(strings: Strings): String =
        message(strings.recommendIntro, strings.recommendBody, strings.recommendGetIt(SiteLinks.HOME_URL))

    /**
     * The dialog: the QR code of the homepage with its caption; confirm ([AppDialogResult.Ok]) is "Share a link",
     * the dismiss button closes. Back/scrim cancels and shares nothing.
     */
    fun request(strings: Strings): AppDialogRequest.Notice = AppDialogRequest.Notice(
        title = strings.tellFriendTitle,
        showTitleLogo = false,
        blocks = listOf(
            AppDialogRequest.NoticeBlock.QrCode,
            AppDialogRequest.NoticeBlock.Html(strings.recommendQrCaption),
        ),
        confirmText = strings.recommendShareLink,
        dismissText = strings.settingsEditorClose,
    )

    /** Shows the dialog through [ask]; "Share a link" goes to [shareText] (text, chooser title). */
    suspend fun run(
        strings: Strings,
        ask: suspend (AppDialogRequest) -> AppDialogResult,
        shareText: (text: String, title: String) -> Unit,
    ) {
        if (ask(request(strings)) == AppDialogResult.Ok) shareText(message(strings), strings.tellFriendTitle)
    }
}
