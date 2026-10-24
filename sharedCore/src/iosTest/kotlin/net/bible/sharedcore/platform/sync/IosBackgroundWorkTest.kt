package net.bible.sharedcore.platform.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.log.LogLevel
import kotlin.test.Test
import kotlin.test.assertSame
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

    @Test fun expectedExpirationAndScopeCancellationDoNotWarn() = runTest {
        val previous = Log.sinkOverride
        val warnings = mutableListOf<String>()
        Log.sinkOverride = { level, _, message, _ ->
            if (level == LogLevel.WARN) warnings += message
        }
        try {
            var expire: () -> Unit = {}
            val completions = mutableListOf<Boolean>()
            val work = IosBackgroundWork(this, { CompletableDeferred<Boolean>().await() }) { completions += it }
            work.start { expire = it }
            runCurrent()
            expire()
            expire()
            runCurrent()
            assertFalse(work.result.await())
            assertEquals(listOf(false), completions)

            val owner = SupervisorJob()
            val cancelled = IosBackgroundWork(CoroutineScope(coroutineContext + owner), {
                CompletableDeferred<Boolean>().await()
            }) { completions += it }
            cancelled.start { }
            runCurrent()
            owner.cancel()
            runCurrent()
            assertFalse(cancelled.result.await())
            assertEquals(listOf(false, false), completions)
            assertEquals(emptyList(), warnings)
        } finally {
            Log.sinkOverride = previous
        }
    }

    @Test fun realFailureWarnsExactlyOnceAndCompletesFalse() = runTest {
        val previous = Log.sinkOverride
        val errors = mutableListOf<Throwable?>()
        val failure = IllegalStateException("real sync failure")
        Log.sinkOverride = { level, _, _, error ->
            if (level == LogLevel.WARN) errors += error
        }
        try {
            val completions = mutableListOf<Boolean>()
            val work = IosBackgroundWork(this, { throw failure }) { completions += it }
            work.start { }
            runCurrent()
            assertFalse(work.result.await())
            assertEquals(listOf(false), completions)
            assertEquals(1, errors.size)
            assertSame(failure, errors.single())
        } finally {
            Log.sinkOverride = previous
        }
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
