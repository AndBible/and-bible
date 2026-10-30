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
package net.bible.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedui.assembly.StartupPhase
import net.bible.sharedui.host.app.AppHostPlatform
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

@OptIn(ExperimentalTestApi::class)
class DesktopAppRootTest {
    private val noPlatform: () -> AppHostPlatform = { fail("the app root must not be built before Ready") }

    @Test
    fun failedPhaseShowsMessage() = runComposeUiTest {
        val dataDir = "/home/someone/.local/share/andbible"
        val state = DesktopStartupState(
            language = "en",
            start = { StartupPhase.Failed(IllegalStateException("Data directory is not writable: $dataDir")) },
            platform = noPlatform,
        )
        setContent { DesktopAppRoot(state) }
        waitUntil(timeoutMillis = 10_000) { state.phase.value is StartupPhase.Failed }
        onNodeWithTag("desktop-startup-failed").assertExists()
        onNodeWithText(dataDir, substring = true).assertExists()
        onNodeWithTag("desktop-splash").assertDoesNotExist()
    }

    @Test
    fun splashShowsWhileStartupRuns() = runComposeUiTest {
        val release = CountDownLatch(1)
        val state = DesktopStartupState(
            language = "en",
            start = {
                release.await(10, TimeUnit.SECONDS)
                StartupPhase.Failed(IllegalStateException("stopped"))
            },
            platform = noPlatform,
        )
        try {
            setContent { DesktopAppRoot(state) }
            onNodeWithTag("desktop-splash").assertExists()
        } finally {
            release.countDown()
        }
    }

    @Test
    fun crashRecoveryGateHoldsTheStartupUntilResolved() = runComposeUiTest {
        val starts = AtomicInteger(0)
        val state = DesktopStartupState(
            language = "en",
            start = { starts.incrementAndGet(); StartupPhase.Failed(IllegalStateException("after the gate")) },
            platform = noPlatform,
            crashRecoveryRequired = { true },
        )
        setContent { DesktopAppRoot(state) }
        onNodeWithTag("crash-recovery").assertExists()
        waitForIdle()
        assertEquals(0, starts.get(), "the startup ran behind the crash-recovery gate")
        onNodeWithTag("crash-continue").performClick()
        waitUntil(timeoutMillis = 10_000) { state.phase.value is StartupPhase.Failed }
        onNodeWithText("after the gate", substring = true).assertExists()
        assertEquals(1, starts.get())
    }

    @Test
    fun closeWaitsForARunningStartupBounded() = kotlinx.coroutines.runBlocking {
        kotlinx.coroutines.withTimeout(20_000) {
            val release = CountDownLatch(1)
            val state = DesktopStartupState(
                language = "en",
                start = { release.await(10, TimeUnit.SECONDS); StartupPhase.Failed(IllegalStateException("done")) },
                platform = noPlatform,
            )
            kotlin.test.assertTrue(state.awaitStartupSettled(10), "nothing launched: nothing to wait for")
            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
            try {
                state.launch(scope)
                kotlin.test.assertFalse(state.awaitStartupSettled(200), "the startup is still running")
                release.countDown()
                kotlin.test.assertTrue(state.awaitStartupSettled(10_000))
            } finally {
                release.countDown()
                scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            }
        }
    }
}
