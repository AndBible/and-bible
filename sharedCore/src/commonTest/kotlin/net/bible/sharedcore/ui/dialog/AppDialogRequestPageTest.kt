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

package net.bible.sharedcore.ui.dialog

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals

/** A page request cannot carry fields a text page has no place for; the request itself refuses them. */
class AppDialogRequestPageTest {
    private fun message(neutral: String?, presentation: DialogPresentation) =
        AppDialogRequest.Message("T", "m", "OK", neutralText = neutral, presentation = presentation)

    private fun notice(neutral: String? = null, blocks: List<AppDialogRequest.NoticeBlock>, presentation: DialogPresentation) =
        AppDialogRequest.Notice("T", false, blocks, "OK", neutralText = neutral, presentation = presentation)

    @Test fun messagePageRejectsNeutralButton() {
        assertFailsWith<IllegalArgumentException> { message("More", DialogPresentation.Page) }
    }

    @Test fun messageDialogKeepsNeutralButton() {
        assertEquals("More", message("More", DialogPresentation.Dialog).neutralText)
    }

    @Test fun messagePageWithoutNeutralIsFine() {
        assertEquals(DialogPresentation.Page, message(null, DialogPresentation.Page).presentation)
    }

    @Test fun noticePageRejectsNeutralButton() {
        assertFailsWith<IllegalArgumentException> {
            notice("More", listOf(AppDialogRequest.NoticeBlock.Html("x")), DialogPresentation.Page)
        }
    }

    @Test fun noticePageRejectsNonHtmlBlocks() {
        listOf(
            AppDialogRequest.NoticeBlock.Logo,
            AppDialogRequest.NoticeBlock.QrCode,
            AppDialogRequest.NoticeBlock.IconLine(AppDialogRequest.NoticeIcon.Money, "x"),
        ).forEach { block ->
            assertFailsWith<IllegalArgumentException> {
                notice(blocks = listOf(AppDialogRequest.NoticeBlock.Html("x"), block), presentation = DialogPresentation.Page)
            }
        }
    }

    @Test fun noticeDialogMayHaveFurnitureAndNeutral() {
        val n = notice("More", listOf(AppDialogRequest.NoticeBlock.Logo, AppDialogRequest.NoticeBlock.QrCode), DialogPresentation.Dialog)
        assertEquals(2, n.blocks.size)
    }

    @Test fun noticePageWithOnlyHtmlIsFine() {
        val n = notice(blocks = listOf(AppDialogRequest.NoticeBlock.Html("a"), AppDialogRequest.NoticeBlock.Html("b")), presentation = DialogPresentation.Page)
        assertEquals(2, n.blocks.size)
    }
}
