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
        isUpgrade: Boolean = true,
        isBeta: Boolean = false,
        discrete: Boolean = false,
        actions: MutableList<ReleaseNoticeAction> = mutableListOf(),
        answer: (AppDialogRequest) -> AppDialogResult,
    ) = ReleaseNotice.showIfNeeded(
        platform, isBeta, discrete, isUpgrade, mainVersion, settings,
        ask = { answer(it) }, onAction = { actions += it },
    )

    private fun noAsk(request: AppDialogRequest): AppDialogResult = fail("asked: $request")

    @Test
    fun showsForUpgraderOnStableBuild() {
        assertTrue(ReleaseNotice.shouldShow(false, false, true, "6.0", ""))
    }

    @Test
    fun hiddenInBetaAndDiscrete() {
        assertFalse(ReleaseNotice.shouldShow(true, false, true, "6.0", ""))
        assertFalse(ReleaseNotice.shouldShow(false, true, true, "6.0", ""))
    }

    @Test
    fun freshInstallMarksSeenSoLaterPatchDoesNotShow() = runTest {
        val settings = MemoryAppSettings()
        assertFalse(show(settings, isUpgrade = false, answer = ::noAsk))
        assertEquals("6.0", settings.getString(key, ""))
        assertFalse(show(settings, isUpgrade = true, answer = ::noAsk))
    }

    @Test
    fun betaFreshInstallDoesNotStoreStableVersion() = runTest {
        val freshBeta = MemoryAppSettings()
        assertFalse(show(freshBeta, isUpgrade = false, isBeta = true, answer = ::noAsk))
        assertEquals("", freshBeta.getString(key, ""))

        for ((beta, discrete) in listOf(true to false, false to true)) {
            val upgrader = MemoryAppSettings()
            assertFalse(show(upgrader, isUpgrade = true, isBeta = beta, discrete = discrete, answer = ::noAsk))
            assertEquals("", upgrader.getString(key, ""))
        }
    }

    @Test
    fun freshBetaDoesNotSuppressStableUpgradeNotice() = runTest {
        val settings = MemoryAppSettings()
        assertFalse(show(settings, isUpgrade = false, isBeta = true, answer = ::noAsk))
        assertEquals("", settings.getString(key, ""))

        var asked = 0
        assertFalse(show(settings, isUpgrade = true) {
            asked++
            AppDialogResult.ReleaseNoticeAnswer(null, false)
        })
        assertEquals(1, asked)
    }

    @Test
    fun freshBetaThenFreshLookingStableShowsNotice() = runTest {
        val settings = MemoryAppSettings()
        show(settings, isUpgrade = false, isBeta = true, answer = ::noAsk)
        var asked = false
        show(settings, isUpgrade = false) {
            asked = true
            AppDialogResult.ReleaseNoticeAnswer(null, false)
        }
        assertTrue(asked)
    }

    @Test
    fun discreteBetaDoesNotMarkBetaRun() = runTest {
        val settings = MemoryAppSettings()
        show(settings, isUpgrade = false, isBeta = true, discrete = true, answer = ::noAsk)
        assertEquals("", settings.getString("stable-notice-beta-ran", ""))
        show(settings, isUpgrade = false, answer = ::noAsk)
        assertEquals("6.0", settings.getString(key, ""))
    }

    @Test
    fun betaPreservesExistingDisplayedVersionAndDifferentBetaVersionDoesNotCount() = runTest {
        val settings = MemoryAppSettings()
        settings.setString(key, "5.9")
        show(settings, mainVersion = "6.0", isUpgrade = false, isBeta = true, answer = ::noAsk)
        assertEquals("5.9", settings.getString(key, ""))
        settings.removeString(key)
        show(settings, mainVersion = "6.1", isUpgrade = false, answer = ::noAsk)
        assertEquals("6.1", settings.getString(key, ""))
    }

    @Test
    fun nonUpgradeWithStoredVersionDoesNotAskOrStore() = runTest {
        val settings = MemoryAppSettings()
        settings.setString(key, "6.0")
        assertFalse(show(settings, mainVersion = "6.1", isUpgrade = false, answer = ::noAsk))
        assertEquals("6.0", settings.getString(key, ""))
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
    fun checkboxStopsUntilNextMinor() = runTest {
        val settings = MemoryAppSettings()
        assertTrue(show(settings) { AppDialogResult.ReleaseNoticeAnswer(null, true) })
        assertEquals("6.0", settings.getString(key, ""))
        assertFalse(show(settings, "6.0", answer = ::noAsk))
        var asked = 0
        show(settings, "6.1") { asked++; AppDialogResult.ReleaseNoticeAnswer(null, false) }
        assertEquals(1, asked)
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
    fun sponsorOnlyOnAndroid() = runTest {
        ReleaseNoticePlatform.entries.forEach { platform ->
            var captured: AppDialogRequest? = null
            show(MemoryAppSettings(), platform = platform) { captured = it; AppDialogResult.Cancel }
            val request = assertIs<AppDialogRequest.ReleaseNotice>(captured)
            assertEquals(platform == ReleaseNoticePlatform.ANDROID, request.sponsorAllowed, "$platform")
            assertEquals("6.0", request.version)
        }
    }
}
