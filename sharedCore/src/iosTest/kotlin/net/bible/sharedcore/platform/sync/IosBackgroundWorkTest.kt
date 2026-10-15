package net.bible.sharedcore.platform.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Exercises the same completion gate used by BGTask without constructing a fake system task. */
@OptIn(ExperimentalCoroutinesApi::class)
class IosBackgroundWorkTest {
    @Test fun expirationAndCancellationCompleteSystemTaskOnlyOnce() = runTest {
        val owner = SupervisorJob()
        val scope = CoroutineScope(coroutineContext + owner)
        val completions = mutableListOf<Boolean>()
        var expire: () -> Unit = {}
        val work = IosBackgroundWork(scope, { CompletableDeferred<Boolean>().await() }) { completions += it }
        work.start { expire = it }
        runCurrent()
        expire()
        owner.cancel()
        expire()
        runCurrent()
        assertFalse(work.result.await())
        assertEquals(listOf(false), completions)
    }

    @Test fun alreadyCancelledScopeCompletesAfterExpirationIsInstalled() = runTest {
        val owner = SupervisorJob().apply { cancel() }
        var installed = false
        var completions = 0
        val work = IosBackgroundWork(CoroutineScope(coroutineContext + owner), { true }) {
            kotlin.test.assertTrue(installed)
            completions++
        }
        work.start { installed = true }
        runCurrent()
        assertFalse(work.result.await())
        assertEquals(1, completions)
    }

    @Test fun normalCompletionWinsAgainstLateExpiration() = runTest {
        val completions = mutableListOf<Boolean>()
        var expire: () -> Unit = {}
        val work = IosBackgroundWork(this, { true }) { completions += it }
        work.start { expire = it }
        runCurrent()
        expire()
        runCurrent()
        assertEquals(listOf(true), completions)
    }
}
