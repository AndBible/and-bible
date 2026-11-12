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

package net.bible.sharedui.app.recommend

import kotlinx.coroutines.test.runTest
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedui.strings.iosStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecommendAppTest {
    private val strings = iosStrings("en")

    @Test
    fun messageIsPlatformNeutral() {
        val text = RecommendApp.message(strings.recommendIntro, strings.recommendBody, strings.recommendGetIt(SITE))
        assertTrue(text.contains("https://andbible.org"))
        listOf("Android", "Play Store", "Google Play", "iPhone").forEach {
            assertFalse(text.contains(it), "message names '$it'")
        }
        assertEquals(
            "${strings.recommendIntro} ${strings.recommendBody}\n\n${strings.recommendGetIt(SITE)}",
            text,
        )
    }

    private val shared = mutableListOf<Pair<String, String>>()

    private suspend fun run(answer: AppDialogResult): List<AppDialogRequest> {
        val asked = mutableListOf<AppDialogRequest>()
        shared.clear()
        RecommendApp.run(strings, { asked += it; answer }, { text, title -> shared += text to title })
        return asked
    }

    @Test
    fun dialogShowsQrDirectlyWithShareLinkAndClose() {
        val request = RecommendApp.request(strings)
        assertEquals(strings.tellFriendTitle, request.title)
        assertTrue(request.blocks.contains(AppDialogRequest.NoticeBlock.QrCode))
        assertEquals(strings.recommendShareLink, request.confirmText)
        assertEquals(strings.settingsEditorClose, request.dismissText)
    }

    @Test
    fun shareLinkSharesMessageAfterOneDialog() = runTest {
        val asked = run(AppDialogResult.Ok)
        assertEquals(1, asked.size)
        assertEquals(
            listOf(
                RecommendApp.message(strings.recommendIntro, strings.recommendBody, strings.recommendGetIt(SITE)) to
                    strings.tellFriendTitle,
            ),
            shared,
        )
    }

    @Test
    fun closeSharesNothing() = runTest {
        val asked = run(AppDialogResult.Cancel)
        assertEquals(1, asked.size)
        assertTrue(shared.isEmpty())
    }

    private companion object { const val SITE = "https://andbible.org" }
}
