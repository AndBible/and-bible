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

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import net.bible.android.TEST_SDK
import net.bible.sharedui.components.AbReleaseNoticeCard
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens for [AbReleaseNoticeCard]: the Android card in every colour mode (monochrome is audited, not
 * recorded), the iOS variant (website pill, no sponsor) and the card at 320 dp wide with font scale 1.5.
 * [StandInLogo] is an honest stand-in: `:sharedUi` is resource-free and the real logo painter comes from
 * `:app`'s `AppDialogOverlay` through `LocalNoticeIcons`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbReleaseNoticeCardGoldenTest {

    /** A solid magenta circle -- stands in for the app logo. */
    private object StandInLogo : Painter() {
        override val intrinsicSize = Size(52f, 52f)
        override fun DrawScope.onDraw() {
            drawCircle(color = Color(0xFFE91E63))
        }
    }

    @Test fun android_matrix() =
        captureMatrix("AbReleaseNoticeCard", "android", heightDp = 760) {
            AbReleaseNoticeCard("6.0", sponsorAllowed = true, logoPainter = StandInLogo) { _, _ -> }
        }

    @Test fun ios() =
        captureGolden("AbReleaseNoticeCard", "ios", EDGE_MODE, heightDp = 760) {
            AbReleaseNoticeCard("6.0", sponsorAllowed = false, logoPainter = StandInLogo) { _, _ -> }
        }

    @Test fun largeFont() =
        captureGolden("AbReleaseNoticeCard", "largeFont", EDGE_MODE, heightDp = 760) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.5f)) {
                AbReleaseNoticeCard("6.0", sponsorAllowed = true, logoPainter = StandInLogo) { _, _ -> }
            }
        }
}
