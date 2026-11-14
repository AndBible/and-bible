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

import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import net.bible.android.TEST_SDK
import net.bible.android.view.activity.settings.AndroidSettingsPlatformFlags
import net.bible.service.common.DisplayColorMode
import net.bible.sharedui.theme.abColorScheme
import net.bible.sharedui.theme.DefaultThemeProposal
import net.bible.sharedui.theme.systemColorSchemeProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Android's Material You provider: present on API 31+, absent (and the settings row hidden) below. */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class SystemDynamicColorsTest {
    private fun <T> preservingProvider(block: () -> T): T {
        val saved = systemColorSchemeProvider
        try { return block() } finally { systemColorSchemeProvider = saved }
    }

    @Test
    @Config(sdk = [TEST_SDK]) // 33, any API >= 31 offers dynamic colour
    fun android12InstallsTheWallpaperSchemeAndOffersTheSetting() = preservingProvider {
        val app = RuntimeEnvironment.getApplication()
        SystemDynamicColors.install(app, videoLook = false)
        assertNotNull(systemColorSchemeProvider)
        val provider = systemColorSchemeProvider!!
        assertEquals(dynamicLightColorScheme(app).toString(), provider(false).toString())
        assertEquals(dynamicDarkColorScheme(app).toString(), provider(true).toString())
        assertEquals(
            dynamicLightColorScheme(app).toString(),
            abColorScheme(null, false, DisplayColorMode.NORMAL, useSystemColors = true).toString(),
        )
        assertTrue(AndroidSettingsPlatformFlags().systemColorsAvailable)
    }

    /** A video capture shows the built-in default theme even with "Use system colors" left on. */
    @Test
    @Config(sdk = [TEST_SDK])
    fun videoLookBuildIgnoresMaterialYou() = preservingProvider {
        val app = RuntimeEnvironment.getApplication()
        SystemDynamicColors.install(app, videoLook = true)
        assertNull(systemColorSchemeProvider)
        for (dark in listOf(false, true)) {
            assertEquals(
                DefaultThemeProposal.DEFAULT.scheme(dark).toString(),
                abColorScheme(null, dark, DisplayColorMode.NORMAL, useSystemColors = true).toString(),
            )
        }
    }

    @Test
    @Config(sdk = [30])
    fun android11HasNoProviderAndHidesTheSetting() = preservingProvider {
        SystemDynamicColors.install(RuntimeEnvironment.getApplication(), videoLook = false)
        assertNull(systemColorSchemeProvider)
        assertFalse(AndroidSettingsPlatformFlags().systemColorsAvailable)
        assertEquals(
            abColorScheme(null, false, DisplayColorMode.NORMAL).toString(),
            abColorScheme(null, false, DisplayColorMode.NORMAL, useSystemColors = true).toString(),
        )
    }
}
