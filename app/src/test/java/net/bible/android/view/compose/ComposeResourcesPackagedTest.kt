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
package net.bible.android.view.compose

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression: the compose resources of `:sharedUi` must be packaged as APK assets of `:app`. They once were not
 * (the library's `androidResources` were off), so `painterResource(Res.drawable.andbible_qr)` threw
 * `MissingResourceException` and "Show QR code" crashed the app on a real device, while every Robolectric test
 * stayed green because a test-only classpath shim hid it. This test reads through the Android asset manager.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ComposeResourcesPackagedTest {
    @Test fun sharedUiDrawablesAreReadableFromApkAssets() {
        val assets = ApplicationProvider.getApplicationContext<Context>().assets
        val path = "composeResources/net.bible.sharedui.generated.resources/drawable/andbible_qr.xml"
        val bytes = assets.open(path).use { it.readBytes() }
        assertTrue("andbible_qr.xml must be non-empty", bytes.isNotEmpty())
    }
}
