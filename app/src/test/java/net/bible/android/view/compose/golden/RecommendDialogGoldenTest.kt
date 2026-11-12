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

import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.sharedui.app.recommend.RecommendApp
import net.bible.sharedui.components.AbNoticeDialog
import net.bible.sharedui.strings.AndroidStrings
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens for the "Recommend to a friend" dialog (QR code shown directly, "Share a link" and "Close" buttons) in
 * light, dark and monochrome ([captureMatrix] records the colour modes and audits monochrome) plus the iOS look
 * (light). The code is black on white in every theme, so it stays scannable. It renders the REAL `andbible_qr`
 * drawable through compose resources.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class RecommendDialogGoldenTest {
    private val content: @androidx.compose.runtime.Composable () -> Unit = {
        val request = RecommendApp.request(AndroidStrings(ApplicationProvider.getApplicationContext()))
        AbNoticeDialog(
            title = request.title, showTitleLogo = request.showTitleLogo, blocks = request.blocks,
            confirmText = request.confirmText, onConfirm = {}, onDismissRequest = {},
            dismissText = request.dismissText,
        )
    }

    @Test fun android_matrix() = captureMatrix("RecommendDialog", "android", content = content)

    @Test fun ios() = captureGolden("RecommendDialog", "ios", EDGE_MODE, content = content)
}
