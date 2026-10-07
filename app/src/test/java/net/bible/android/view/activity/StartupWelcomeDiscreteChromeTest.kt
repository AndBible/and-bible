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
package net.bible.android.view.activity

import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.service.common.CommonUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * F62: discrete mode ("calculator disguise") must reach the first-run welcome header's app name and
 * logo, exactly as it already reaches the reading chrome's toolbar icon and drawer identity
 * ([net.bible.android.view.compose.DiscreteChromeTest], F58) and classic's own splash spinner
 * (`StartupActivity.onCreate`, :199-205). `:sharedUi` stays platform-clean: [StartupWelcomeScreen]
 * only ever receives a ready-made `String`/`Painter`; the resource lookup happens host-side here.
 *
 * `discrete_mode` is read by [CommonUtils.isDiscrete] from [CommonUtils.realSharedPreferences], NOT
 * [CommonUtils.settings] -- same routing [DiscreteChromeTest]'s kdoc documents -- so the flag is set
 * directly on `realSharedPreferences` here too.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = TestBibleApplication::class)
class StartupWelcomeDiscreteChromeTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @After
    fun tearDown() {
        CommonUtils.realSharedPreferences.edit().remove("discrete_mode").apply()
    }

    @Test
    fun discreteModeOffUsesTheRealAppNameAndLogo() {
        CommonUtils.realSharedPreferences.edit().putBoolean("discrete_mode", false).apply()

        assertEquals(R.string.app_name_long, startupWelcomeAppNameRes())
        assertEquals(R.drawable.ic_logo, startupWelcomeLogoRes())
        assertEquals(R.string.app_name_andbible, startupWelcomeShortAppNameRes())
    }

    @Test
    fun discreteModeSwapsTheWelcomeHeaderAppNameAndLogo() {
        CommonUtils.realSharedPreferences.edit().putBoolean("discrete_mode", true).apply()

        assertEquals(
            "discrete mode must swap the welcome header's app name, as classic's splash spinner " +
                "does (StartupActivity.onCreate :199-205) -- it must not leak the real app name",
            R.string.app_name_calculator,
            startupWelcomeAppNameRes(),
        )
        assertEquals(
            "nor the real logo",
            R.drawable.ic_calculator_color,
            startupWelcomeLogoRes(),
        )
        assertEquals(
            "nor the zip-format line's short app name",
            R.string.app_name_calculator,
            startupWelcomeShortAppNameRes(),
        )
    }

    /**
     * Fix round 1 (review): the header alone being discrete-aware was not enough -- `loadInfo()`
     * independently built the welcome CARD's own body text and the "Supported formats" line from
     * the real app name unconditionally, so discrete mode's card still read "Thank you for
     * downloading AndBible..." / "Supported formats: AndBible zip, ..." even with the header fixed.
     * These two tests compose the exact same format strings (`welcome_message`, `format_zip`)
     * `loadInfo()` uses, through the same discrete-aware helpers, and assert on the resulting text
     * -- the "body text and formats line" the review asked to be covered, not just the resource-id
     * CHOICE the helpers alone would prove.
     */
    @Test
    fun discreteModeOffWelcomeCardTextNamesTheRealApp() {
        CommonUtils.realSharedPreferences.edit().putBoolean("discrete_mode", false).apply()

        val welcomeText = context.getString(R.string.welcome_message, context.getString(startupWelcomeAppNameRes()))
        val zip = context.getString(R.string.format_zip, context.getString(startupWelcomeShortAppNameRes()))

        assertTrue("welcome card text must name AndBible when discrete mode is off", welcomeText.contains("AndBible"))
        assertFalse(
            "welcome card text must not name the calculator when discrete mode is off",
            welcomeText.contains("Simple Calculator"),
        )
        assertTrue("the zip format line must name AndBible when discrete mode is off", zip.contains("AndBible"))
        assertFalse(
            "the zip format line must not name the calculator when discrete mode is off",
            zip.contains("Simple Calculator"),
        )
    }

    @Test
    fun discreteModeOnWelcomeCardTextDoesNotLeakTheRealAppName() {
        CommonUtils.realSharedPreferences.edit().putBoolean("discrete_mode", true).apply()

        val welcomeText = context.getString(R.string.welcome_message, context.getString(startupWelcomeAppNameRes()))
        val zip = context.getString(R.string.format_zip, context.getString(startupWelcomeShortAppNameRes()))

        assertTrue(
            "welcome card text must name the calculator when discrete mode is on",
            welcomeText.contains("Simple Calculator"),
        )
        assertFalse(
            "welcome card text must not leak the real app name when discrete mode is on",
            welcomeText.contains("AndBible"),
        )
        assertTrue("the zip format line must name the calculator when discrete mode is on", zip.contains("Simple Calculator"))
        assertFalse(
            "the zip format line must not leak the real app name when discrete mode is on",
            zip.contains("AndBible"),
        )
    }

    /** The intro replaced `welcome_message` and must stay name-free, or discrete mode leaks the identity again. */
    @Test
    fun welcomeIntroNamesNoApp() {
        val intro = context.getString(R.string.welcome_intro)
        assertFalse("welcome_intro must not take an app-name placeholder", intro.contains("%"))
        assertFalse("welcome_intro must not name AndBible", intro.contains("AndBible", ignoreCase = true))
    }
}
