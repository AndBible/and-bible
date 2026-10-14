package net.bible.test

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.get
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `Window.loadText` launches into the scope of the repository currently held by the Koin
 * [WindowControl]; reset must own that scope even when no scope is passed explicitly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class WindowControlScopeResetTest {
    @Test fun resetCancelsAndJoinsCurrentWindowControlRepositoryScope() {
        val windowControl = get<WindowControl>(WindowControl::class.java)
        val repository = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository = repository
        val repoJob = repository.scope.coroutineContext[Job]!!
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val oldObserver = DatabaseResetter.drainObserver
        var jobsSeen: List<Job> = emptyList()
        var drainPendingWhenObserved = false
        // Mirrors Window.loadText: IO child of the repository scope that finishes only on release.
        val child = repository.scope.launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            try {
                entered.complete(Unit)
                awaitCancellation()
            } finally {
                withContext(NonCancellable) { release.await() }
            }
        }
        try {
            runBlocking { withTimeout(5000) { entered.await() } }
            DatabaseResetter.drainObserver = { jobs, drain ->
                jobsSeen = jobs
                drainPendingWhenObserved = !drain.isCompleted
                // The exact join is already suspended on the held child: let it finish now.
                release.complete(Unit)
            }
            DatabaseResetter.resetDatabase()
            assertTrue("reset must own the WindowControl repository job", jobsSeen.contains(repoJob))
            assertTrue("join must still be pending when observed", drainPendingWhenObserved)
            assertTrue("held window child must be complete at commit", child.isCompleted)
            assertTrue("repository scope must be cancelled", repoJob.isCancelled)
        } finally {
            DatabaseResetter.drainObserver = oldObserver
            release.complete(Unit)
            repoJob.cancel()
            runBlocking { withTimeout(5000) { repoJob.join() } }
            if (!child.isCompleted) runBlocking { child.join() }
        }
    }

    @Test fun peekNeverCreatesARepositoryAndResetWithoutOneIsHarmless() {
        val fresh = WindowControl()
        assertTrue(fresh.currentWindowRepositoryOrNull == null)
        assertTrue("peek must not trigger the lazy getter", fresh.currentWindowRepositoryOrNull == null)
        val assigned = WindowRepository(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        fresh.windowRepository = assigned
        assertTrue(fresh.currentWindowRepositoryOrNull === assigned)
        assigned.scope.coroutineContext[Job]!!.cancel()
        assertFalse(assigned.scope.coroutineContext[Job]!!.isActive)
        DatabaseResetter.resetDatabase()
    }
}
