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
package net.bible.sharedui.app.notice

import kotlinx.coroutines.test.runTest
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedcore.ui.dialog.ReleaseNoticeAction
import net.bible.sharedui.MemoryAppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class ReleaseNoticeTest {
    private val key = ReleaseNotice.DISPLAYED_KEY

    private suspend fun show(
        settings: MemoryAppSettings,
        mainVersion: String = "6.0",
        platform: ReleaseNoticePlatform = ReleaseNoticePlatform.ANDROID,
        isBeta: Boolean = false,
        discrete: Boolean = false,
        actions: MutableList<ReleaseNoticeAction> = mutableListOf(),
        answer: (AppDialogRequest) -> AppDialogResult,
    ) = ReleaseNotice.showIfNeeded(
        platform, isBeta, discrete, mainVersion, settings,
        ask = { answer(it) }, onAction = { actions += it },
    )

    private fun noAsk(request: AppDialogRequest): AppDialogResult = fail("asked: $request")

    @Test
    fun stableLaunchWithEmptyKeyShowsNotice() = runTest {
        val settings = MemoryAppSettings()
        var asked = 0
        show(settings) { asked++; AppDialogResult.Cancel }
        assertEquals(1, asked)
        assertTrue(settings.values.isEmpty())
    }

    /**
     * The internal 6.0.1266 test build's older gate silently stored "6.0" under the old key `stable-notice-displayed`
     * on an undetected beta -> stable update, without showing anything. That value must not suppress the notice.
     */
    @Test
    fun legacyStableNoticeKeyDoesNotSuppressNotice() = runTest {
        val settings = MemoryAppSettings().apply { setString("stable-notice-displayed", "6.0") }
        var asked = 0
        show(settings) { asked++; AppDialogResult.Cancel }
        assertEquals(1, asked)
    }

    @Test
    fun betaAndDiscreteNeitherAskNorWriteEvenOnWelcome() = runTest {
        for ((beta, discrete) in listOf(true to false, false to true, true to true)) {
            val settings = MemoryAppSettings()
            assertFalse(show(settings, isBeta = beta, discrete = discrete, answer = ::noAsk))
            ReleaseNotice.dismissForWelcome(beta, discrete, "6.0", settings)
            assertTrue(settings.values.isEmpty(), "beta=$beta discrete=$discrete")
            settings.setString(key, "5.9")
            val before = settings.values.toMap()
            show(settings, isBeta = beta, discrete = discrete, answer = ::noAsk)
            ReleaseNotice.dismissForWelcome(beta, discrete, "6.0", settings)
            assertEquals(before, settings.values)
        }
    }

    @Test
    fun welcomeSilentlyDismissesCurrentVersionIncludingLaterPatch() = runTest {
        val settings = MemoryAppSettings()
        ReleaseNotice.dismissForWelcome(false, false, "6.0", settings)
        assertEquals("6.0", settings.getString(key, ""))
        assertFalse(show(settings, answer = ::noAsk))
        assertFalse(show(settings, mainVersion = "6.0", answer = ::noAsk))
    }

    @Test
    fun welcomeAgainUpdatesAnOlderDismissal() = runTest {
        val settings = MemoryAppSettings()
        settings.setString(key, "5.9")
        ReleaseNotice.dismissForWelcome(false, false, "6.0", settings)
        assertEquals("6.0", settings.getString(key, ""))
        show(settings, answer = ::noAsk)
    }

    @Test
    fun staleBetaMarkerDoesNotChangeStableOrWelcomeBehavior() = runTest {
        for (marker in listOf("6.0", "6.1", "5.9")) {
            val settings = MemoryAppSettings()
            settings.setString("stable-notice-beta-ran", marker)
            var asked = 0
            show(settings) { asked++; AppDialogResult.Cancel }
            assertEquals(1, asked)
            assertEquals("", settings.getString(key, ""))
            ReleaseNotice.dismissForWelcome(false, false, "6.0", settings)
            show(settings, answer = ::noAsk)
            assertEquals(marker, settings.getString("stable-notice-beta-ran", ""))
        }
    }

    @Test
    fun closeWithoutCheckboxShowsAgain() = runTest {
        val settings = MemoryAppSettings()
        assertFalse(show(settings) { AppDialogResult.ReleaseNoticeAnswer(null, false) })
        assertEquals("", settings.getString(key, ""))
        var asked = 0
        show(settings) { asked++; AppDialogResult.ReleaseNoticeAnswer(null, false) }
        assertEquals(1, asked)
    }

    @Test
    fun checkboxStopsUntilNextMainVersion() = runTest {
        val settings = MemoryAppSettings()
        assertTrue(show(settings) { AppDialogResult.ReleaseNoticeAnswer(null, true) })
        assertEquals("6.0", settings.getString(key, ""))
        assertFalse(show(settings, "6.0", answer = ::noAsk))
        var asked = 0
        show(settings, "6.1") { asked++; AppDialogResult.ReleaseNoticeAnswer(null, false) }
        assertEquals(1, asked)
    }

    @Test
    fun nextMainVersionAfterWelcomeShowsAgain() = runTest {
        val settings = MemoryAppSettings()
        ReleaseNotice.dismissForWelcome(false, false, "6.0", settings)
        var asked = 0
        show(settings, "6.1") { asked++; AppDialogResult.Cancel }
        assertEquals(1, asked)
        assertEquals("6.0", settings.getString(key, ""))
    }

    @Test
    fun actionRunsAfterCloseAndHonoursCheckbox() = runTest {
        val settings = MemoryAppSettings()
        val actions = mutableListOf<ReleaseNoticeAction>()
        assertFalse(show(settings, actions = actions) { AppDialogResult.ReleaseNoticeAnswer(ReleaseNoticeAction.SHARE, false) })
        assertEquals(listOf(ReleaseNoticeAction.SHARE), actions)
        assertEquals("", settings.getString(key, ""))
        assertTrue(show(settings, actions = actions) { AppDialogResult.ReleaseNoticeAnswer(ReleaseNoticeAction.VIDEO, true) })
        assertEquals(listOf(ReleaseNoticeAction.SHARE, ReleaseNoticeAction.VIDEO), actions)
        assertEquals("6.0", settings.getString(key, ""))
    }

    @Test
    fun cancelNeverPersists() = runTest {
        val settings = MemoryAppSettings()
        val actions = mutableListOf<ReleaseNoticeAction>()
        assertFalse(show(settings, actions = actions) { AppDialogResult.Cancel })
        assertEquals("", settings.getString(key, ""))
        assertTrue(actions.isEmpty())
    }

    @Test
    fun actionsMatchPlatform() = runTest {
        ReleaseNoticePlatform.entries.forEach { platform ->
            var captured: AppDialogRequest? = null
            show(MemoryAppSettings(), platform = platform) { captured = it; AppDialogResult.Cancel }
            val request = assertIs<AppDialogRequest.ReleaseNotice>(captured)
            assertEquals(platform != ReleaseNoticePlatform.IOS, request.sponsorAllowed, "$platform")
            assertEquals(platform != ReleaseNoticePlatform.DESKTOP, request.computerAllowed, "$platform")
            assertEquals("6.0", request.version)
        }
    }
}
