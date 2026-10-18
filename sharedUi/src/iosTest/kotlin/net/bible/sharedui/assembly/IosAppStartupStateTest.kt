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
package net.bible.sharedui.assembly

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSThread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame

class IosAppStartupStateTest {
    private fun scope() = CoroutineScope(Job())

    @Test
    fun aSuccessfulOpenGoesFromStartingToReady() = runBlocking<Unit> {
        val gate = CompletableDeferred<Unit>()
        val state = IosAppStartupState(open = { gate.await() })
        val scope = scope()
        state.launch(scope)
        assertEquals(StartupPhase.Starting, state.phase.value)
        gate.complete(Unit)
        withTimeout(10_000) { state.phase.first { it != StartupPhase.Starting } }
        assertEquals(StartupPhase.Ready, state.phase.value)
        scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun aFailingOpenEndsInFailedWithThatError() = runBlocking<Unit> {
        val error = IllegalStateException("corrupt")
        val state = IosAppStartupState(open = { throw error })
        val scope = scope()
        state.launch(scope)
        val phase = withTimeout(10_000) { state.phase.first { it != StartupPhase.Starting } }
        assertIs<StartupPhase.Failed>(phase)
        assertSame(error, phase.error)
        scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun anOpenThatNeverFinishesStaysStarting() = runBlocking<Unit> {
        val state = IosAppStartupState(open = { CompletableDeferred<Unit>().await() })
        val scope = scope()
        state.launch(scope)
        // Real time on purpose: runTest's virtual time would skip the wait without letting Default threads run.
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { delay(300) }
        assertEquals(StartupPhase.Starting, state.phase.value)
        scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun theOpenRunsOffTheMainThread() = runBlocking<Unit> {
        var onMain: Boolean? = null
        val state = IosAppStartupState(open = { onMain = NSThread.isMainThread })
        val scope = scope()
        state.launch(scope)
        withTimeout(10_000) { state.phase.first { it != StartupPhase.Starting } }
        assertEquals(false, onMain)
        scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun aSecondLaunchDoesNotRunOpenAgain() = runBlocking<Unit> {
        var calls = 0
        val state = IosAppStartupState(open = { calls++ })
        val scope = scope()
        state.launch(scope)
        withTimeout(10_000) { state.phase.first { it != StartupPhase.Starting } }
        state.launch(scope)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { delay(100) }
        assertEquals(1, calls)
        scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun failBeforeLaunchShowsTheErrorAndNeverOpens() = runBlocking<Unit> {
        var opened = false
        val state = IosAppStartupState(open = { opened = true })
        val error = IllegalStateException("start failed")
        state.fail(error)
        val scope = scope()
        state.launch(scope)
        // Real time on purpose: give a wrongly started open a chance to run on a Default thread.
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { delay(300) }
        assertEquals(StartupPhase.Failed(error), state.phase.value)
        assertFalse(opened)
        scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun aCancellationThrownByOpenWhileTheScopeIsActiveIsAFailure() = runBlocking<Unit> {
        val state = IosAppStartupState(open = { withTimeout(50) { CompletableDeferred<Unit>().await() } })
        val scope = scope()
        state.launch(scope)
        val phase = withTimeout(10_000) { state.phase.first { it != StartupPhase.Starting } }
        assertIs<StartupPhase.Failed>(phase)
        assertIs<kotlinx.coroutines.TimeoutCancellationException>(phase.error)
        scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun failAfterASuccessfulLaunchKeepsReady() = runBlocking<Unit> {
        val state = IosAppStartupState(open = { })
        val scope = scope()
        state.launch(scope)
        withTimeout(10_000) { state.phase.first { it != StartupPhase.Starting } }
        state.fail(IllegalStateException("late"))
        assertEquals(StartupPhase.Ready, state.phase.value)
        scope.coroutineContext[Job]!!.cancel()
    }
}
