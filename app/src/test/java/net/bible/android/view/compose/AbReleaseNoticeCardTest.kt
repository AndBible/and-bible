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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.espresso.Espresso
import net.bible.android.TEST_SDK
import net.bible.sharedcore.ui.dialog.ReleaseNoticeAction
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.components.AbReleaseNoticeCard
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Behaviour of [AbReleaseNoticeCard]: what each control answers, and that the footer stays reachable. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbReleaseNoticeCardTest {
    @get:Rule val compose = createComposeRule()

    private val answers = mutableListOf<Pair<ReleaseNoticeAction?, Boolean>>()

    private fun show(sponsorAllowed: Boolean = true, fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                ProvideAppLocals { AbTheme { Card(sponsorAllowed) } }
            }
        }
    }

    @Composable private fun Card(sponsorAllowed: Boolean) =
        AbReleaseNoticeCard("6.0", sponsorAllowed, null) { action, dontShowAgain -> answers += action to dontShowAgain }

    @Test fun closeWithoutCheckboxAnswersNullFalse() {
        show()
        compose.onNodeWithText("Close").performClick()
        assertEquals(listOf<Pair<ReleaseNoticeAction?, Boolean>>(null to false), answers)
    }

    @Test fun checkboxThenCloseAnswersNullTrue() {
        show()
        compose.onNodeWithText("Don't show again").performClick()
        compose.onNodeWithText("Close").performClick()
        assertEquals(listOf<Pair<ReleaseNoticeAction?, Boolean>>(null to true), answers)
    }

    @Test fun scrimDismissCarriesCheckboxState() {
        show()
        compose.onNodeWithText("Don't show again").performClick()
        compose.waitForIdle()
        Espresso.pressBack()
        compose.waitForIdle()
        assertEquals(listOf<Pair<ReleaseNoticeAction?, Boolean>>(null to true), answers)
    }

    @Test fun rowTapAnswersActionWithCheckbox() {
        show()
        compose.onNodeWithText("Don't show again").performClick()
        compose.onNodeWithText("Share AndBible with a friend").performScrollTo().performClick()
        assertEquals(listOf<Pair<ReleaseNoticeAction?, Boolean>>(ReleaseNoticeAction.SHARE to true), answers)
    }

    @Test fun untouchedVideoRowAnswersVideoFalse() {
        show()
        compose.onNodeWithText("See what's new in 6.0").performClick()
        assertEquals(listOf<Pair<ReleaseNoticeAction?, Boolean>>(ReleaseNoticeAction.VIDEO to false), answers)
    }

    @Test fun iosVariantShowsWebsiteNotSponsor() {
        show(sponsorAllowed = false)
        compose.onNodeWithText("Sponsor development").assertDoesNotExist()
        compose.onNodeWithText("Visit andbible.org").performScrollTo().performClick()
        assertEquals(listOf<Pair<ReleaseNoticeAction?, Boolean>>(ReleaseNoticeAction.WEBSITE to false), answers)
    }

    @Test fun androidVariantSponsorAnswersSponsor() {
        show()
        compose.onNodeWithText("Visit andbible.org").assertDoesNotExist()
        compose.onNodeWithText("Sponsor development").performScrollTo().performClick()
        assertEquals(listOf<Pair<ReleaseNoticeAction?, Boolean>>(ReleaseNoticeAction.SPONSOR to false), answers)
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "w320dp")
    fun footerVisibleAtLargeFont() {
        show(fontScale = 1.5f)
        // The body overflows and is clipped: its last item is off screen until scrolled to ...
        compose.onNodeWithText("Sponsor development").assertIsNotDisplayed()
        // ... while the footer sits outside the scroll column and stays visible.
        compose.onNodeWithText("Close").assertIsDisplayed()
        compose.onNodeWithText("Don't show again").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "w320dp-h568dp")
    fun scrollCueAppearsForOverflowAndDisappearsAtBottom() {
        show(sponsorAllowed = false, fontScale = 1.5f)

        val cue = compose.onNodeWithTag("release-notice-scroll-cue")
        cue.assertIsDisplayed()
        compose.onNodeWithText("Visit andbible.org").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("AndBible is free and has no ads").performScrollTo()
        cue.assertDoesNotExist()
        compose.onNodeWithText("Close").assertIsDisplayed()
        compose.onNodeWithText("Don't show again").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "w320dp-h760dp")
    fun scrollCueIsAbsentWhenContentFits() {
        show(fontScale = 1f)

        compose.onNodeWithTag("release-notice-scroll-cue").assertDoesNotExist()
        compose.onNodeWithText("Close").assertIsDisplayed()
        compose.onNodeWithText("Don't show again").assertIsDisplayed()
    }
}
