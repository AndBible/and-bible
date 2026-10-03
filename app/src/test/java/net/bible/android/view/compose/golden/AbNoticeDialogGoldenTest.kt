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

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import net.bible.android.TEST_SDK
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedui.components.AbNoticeDialog
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens for [AbNoticeDialog] (Task 28): the two shapes it was ported for --
 * `ReadingAppBootstrap.showStableNotice`'s body (`Html`, `Logo`, `Html`, `IconLine`) and
 * `CommonUtils.showHelp`'s (`Html`, `Html`, `IconLine`, `Html`, no `Logo`). [StandInLogo]/
 * [StandInMoney] are honest stand-ins -- drawn shapes, unmistakably not the real
 * `ic_logo`/`baseline_attach_money_24` drawables -- because `:sharedUi` stays resource-free
 * (spec constraint); the REAL painters only exist where `:app`'s `AppDialogOverlay` provides
 * `LocalNoticeIcons`, which this golden deliberately bypasses by passing painters straight into
 * [AbNoticeDialog] (same reason [AppDialogHostGoldenTest] and friends test their `Ab*Dialog`
 * directly rather than through the full `AppDialogHost`/`AppDialogOverlay` stack).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbNoticeDialogGoldenTest {

    /** A solid magenta circle -- stands in for the app logo. */
    private object StandInLogo : Painter() {
        override val intrinsicSize = Size(96f, 96f)
        override fun DrawScope.onDraw() {
            drawCircle(color = Color(0xFFE91E63))
        }
    }

    /** A solid cyan rounded square -- stands in for the tinted money icon. */
    private object StandInMoney : Painter() {
        override val intrinsicSize = Size(24f, 24f)
        override fun DrawScope.onDraw() {
            drawRoundRect(color = Color(0xFF00BCD4), cornerRadius = CornerRadius(4f, 4f))
        }
    }

    private val stableNoticeBlocks = listOf(
        AppDialogRequest.NoticeBlock.Html("AndBible 5.1 has been released. Thank you for using AndBible!"),
        AppDialogRequest.NoticeBlock.Logo,
        AppDialogRequest.NoticeBlock.Html("<big><a href=\"https://example.com\"><b>Watch what's new</b></a></big>"),
        AppDialogRequest.NoticeBlock.IconLine(
            AppDialogRequest.NoticeIcon.Money,
            "&nbsp;<small><a href=\"https://example.com\">Support AndBible development (Buy)</a></small>",
        ),
    )

    private val helpBlocks = listOf(
        AppDialogRequest.NoticeBlock.Html(
            "<b>Navigating around</b><br>Swipe left/right to change chapters, or tap the arrows.<br>" +
                "<br><b>Context menus</b><br>Long-press any word for a context menu.<br>",
        ),
        AppDialogRequest.NoticeBlock.Html("<a href=\"https://andbible.org/docs/\">Full documentation</a>"),
        AppDialogRequest.NoticeBlock.IconLine(
            AppDialogRequest.NoticeIcon.Money,
            "&nbsp;<b>Support AndBible</b>: <a href=\"https://example.com\">Buy me a coffee</a>",
        ),
        AppDialogRequest.NoticeBlock.Html("<i>Version 5.1.1117</i>"),
    )

    @Test fun stableNotice_matrix() =
        captureMatrix("AbNoticeDialog", "stableNotice") {
            AbNoticeDialog(
                title = "AndBible 5.1 released!", showTitleLogo = true, blocks = stableNoticeBlocks,
                confirmText = "Don't show again", onConfirm = {}, onDismissRequest = {},
                dismissText = null, neutralText = "Dismiss", onNeutral = {},
                logoPainter = StandInLogo, moneyPainter = StandInMoney,
            )
        }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun stableNotice_rtl() =
        captureRtl("AbNoticeDialog", "stableNotice") {
            AbNoticeDialog(
                title = "AndBible 5.1 released!", showTitleLogo = true, blocks = stableNoticeBlocks,
                confirmText = "Don't show again", onConfirm = {}, onDismissRequest = {},
                dismissText = null, neutralText = "Dismiss", onNeutral = {},
                logoPainter = StandInLogo, moneyPainter = StandInMoney,
            )
        }

    @Test fun help_matrix() =
        captureMatrix("AbNoticeDialog", "help") {
            AbNoticeDialog(
                title = "Help", showTitleLogo = true, blocks = helpBlocks,
                confirmText = "OK", onConfirm = {}, onDismissRequest = {},
                logoPainter = StandInLogo, moneyPainter = StandInMoney,
            )
        }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun help_rtl() =
        captureRtl("AbNoticeDialog", "help") {
            AbNoticeDialog(
                title = "Help", showTitleLogo = true, blocks = helpBlocks,
                confirmText = "OK", onConfirm = {}, onDismissRequest = {},
                logoPainter = StandInLogo, moneyPainter = StandInMoney,
            )
        }
}
