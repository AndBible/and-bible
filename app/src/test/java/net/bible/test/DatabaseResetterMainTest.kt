package net.bible.test

import net.bible.service.db.reset
import kotlinx.coroutines.*
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.DocumentSyncPreferences
import net.bible.service.cloudsync.documents.DocumentSyncSettings
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.platform.OrderedLauncher
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.get
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DatabaseResetterMainTest {
    @Test fun mainProducerCancellationFinallyWriteFinishesBeforeReset() {
        val settings = get<DocumentSyncSettings>(DocumentSyncSettings::class.java)
        runBlocking { settings.ensureLoaded() }
        val producerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var finished = false
        producerScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally {
                withContext(NonCancellable) {
                    withContext(Dispatchers.Default) { delay(20) }
                    withContext(Dispatchers.Main) {
                        settings.enabled = true
                        finished = true
                    }
                }
            }
        }
        DatabaseResetter.resetDatabase(producerScope)
        assertTrue("Main cancellation continuation must run before DB reset", finished)
        runBlocking {
            DatabaseContainer.instance.documentSyncDb.documentSyncPreferencesDao().set(
                DocumentSyncPreferences(enabled = false, wifiOnly = false))
            settings.ensureLoaded()
            get<OrderedLauncher>(OrderedLauncher::class.java).join("document-sync-settings")
            assertFalse(settings.enabled)
            assertFalse(settings.wifiOnly)
            assertFalse(DatabaseContainer.instance.documentSyncDb.documentSyncPreferencesDao().get()!!.enabled)
        }
        DatabaseResetter.resetDatabase()
    }

    @Test fun resetJoinsTheLauncherTheSettingsObjectActuallyOwns() {
        val settings = get<DocumentSyncSettings>(DocumentSyncSettings::class.java)
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val owned = OrderedLauncher(owner)
        val koinLauncher = get<OrderedLauncher>(OrderedLauncher::class.java)
        val field = settings.javaClass.getDeclaredField("launcher").apply { isAccessible = true }
        val previous = settings.fixtureLauncher()
        val release = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val oldDrain = DatabaseResetter.settingsDrainObserver
        val oldClose = DatabaseResetter.beforeCloseObserver
        var attempted = false
        var preClose = false
        var heldTail: Job? = null
        var primary: Throwable? = null
        // The substituted queue remains live until reset's exact settings-tail barrier.
        try {
            DatabaseResetter.runResetBlocking { previous.join("document-sync-settings") }
            assertTrue("the fixture launcher and the owned launcher must be distinct", previous !== owned)
            field.set(settings, owned)
            runBlocking { settings.ensureLoaded() }
            settings.wifiOnly = false
            heldTail = owned.launch("document-sync-settings") {
                withContext(NonCancellable) { entered.complete(Unit); release.await() }
            }
            runBlocking { withTimeout(5000) { entered.await() } }
            // This queue is intentionally not a producer: reset must drain it, not cancel it.
            DatabaseResetter.settingsDrainObserver = { launcher, drain ->
                attempted = true
                assertSame("reset must attempt the constructor-owned queue", owned, launcher)
                assertFalse("exact join must suspend while queue is held", drain.isCompleted)
                release.complete(Unit)
            }
            DatabaseResetter.beforeCloseObserver = {
                assertTrue("safe pre-close oracle: exact attempted join is required", attempted)
                assertTrue("safe pre-close oracle: held queue must complete", heldTail!!.isCompleted)
                preClose = true
            }
            // Main is pumped by the actual reset caller, never starved by a sleeping Java worker.
            DatabaseResetter.resetDatabase()
            assertTrue(attempted)
            assertTrue(preClose)
            assertFalse(koinLauncher === owned)
        } catch (e: Throwable) { primary = e; throw e }
        finally {
            release.complete(Unit)
            DatabaseResetter.settingsDrainObserver = oldDrain
            DatabaseResetter.beforeCloseObserver = oldClose
            try {
                DatabaseResetter.runResetBlocking {
                    owned.join("document-sync-settings")
                    owner.coroutineContext[Job]!!.cancelAndJoin()
                }
                ReadingFixtureOwners.retireCompleted()
                // All reset workers are complete before restoring the captured constructor field.
                DatabaseResetter.requireNoRetainedWorkers()
                field.set(settings, previous)
            } catch (e: Throwable) {
                ReadingFixtureOwners.retain(listOf(owner))
                // Keep the restoration obligation as well as the queue if a pump cannot finish.
                ReadingFixtureOwners.retainRestoration { field.set(settings, previous) }
                if (primary == null) throw e else if (primary !== e) primary.addSuppressed(e)
            }
        }
    }

    @Test fun slowCleanupAttemptsBothOwnersAndRetainsIncompleteWorkBeforeReset() {
        val first = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val second = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val release = CompletableDeferred<Unit>()
        val child = first.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) { release.await() }
        }
        second.launch(start = CoroutineStart.UNDISPATCHED) { awaitCancellation() }
        val primary = AssertionError("original assertion")
        val old = DatabaseContainer.instance
        try {
            finishFixtureScopes(listOf(first, second), primary, timeoutMs = 50)
            assertTrue("cleanup timeout is suppressed onto primary", primary.suppressed.isNotEmpty())
            assertFalse(first.coroutineContext[Job]!!.isCompleted)
            assertTrue("second disposal is attempted despite first timeout", second.coroutineContext[Job]!!.isCompleted)
            assertThrows(IllegalStateException::class.java) { ReadingFixtureOwners.requireEmpty() }
            var reset = false
            assertThrows(IllegalStateException::class.java) {
                DatabaseResetter.runResetBlocking(timeoutMs = 50) {
                    ReadingFixtureOwners.drain()
                    commitReset { reset = true; DatabaseContainer.reset() }
                }
            }
            assertFalse("no close under retained work", reset)
            assertSame(old, DatabaseContainer.instance)
            release.complete(Unit)
            DatabaseResetter.runResetBlocking { ReadingFixtureOwners.drain() }
            assertTrue(child.isCompleted)
            ReadingFixtureOwners.requireEmpty()
        } finally {
            release.complete(Unit)
            finishFixtureScopes(listOf(first, second))
            DatabaseResetter.runResetBlocking { ReadingFixtureOwners.drain() }
        }
    }

    @Test fun committedActualDbResetWaitsPastDeadlineForDatabaseMonitor() {
        val settings = get<DocumentSyncSettings>(DocumentSyncSettings::class.java)
        runBlocking { settings.ensureLoaded() }
        val launcher = get<OrderedLauncher>(OrderedLauncher::class.java)
        val old = DatabaseContainer.instance
        val monitorHeld = java.util.concurrent.CountDownLatch(1)
        val resetCalled = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val holder = Thread {
            synchronized(DatabaseContainer.Companion) {
                monitorHeld.countDown()
                release.await()
            }
        }.apply { start() }
        assertTrue(monitorHeld.await(5, java.util.concurrent.TimeUnit.SECONDS))
        val releaser = Thread {
            resetCalled.await(5, java.util.concurrent.TimeUnit.SECONDS)
            Thread.sleep(250)
            release.countDown()
        }.apply { start() }
        try {
            DatabaseResetter.runResetBlocking(timeoutMs = 100) {
                DatabaseResetter.resetDocumentSyncFixture(settings, launcher) {
                    resetCalled.countDown()
                    DatabaseContainer.reset()
                }
            }
            assertEquals("committed reset cannot return before monitor release", 0L, release.count)
            assertNotSame(old, DatabaseContainer.instance)
            runBlocking { settings.ensureLoaded() }
        } finally {
            release.countDown()
            holder.join(5_000)
            releaser.join(5_000)
            DatabaseResetter.resetDatabase()
        }
    }

    @Test fun abortedPreparationCannotCommitActualResetLater() {
        val old = DatabaseContainer.instance
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val finished = java.util.concurrent.CountDownLatch(1)
        val denied = java.util.concurrent.atomic.AtomicBoolean(false)
        try {
            assertThrows(IllegalStateException::class.java) {
                DatabaseResetter.runResetBlocking(timeoutMs = 100) {
                    withContext(NonCancellable) {
                        entered.complete(Unit)
                        release.await()
                        try { commitReset { DatabaseContainer.reset() } }
                        catch (e: IllegalStateException) { denied.set(true) }
                        finally { finished.countDown() }
                    }
                }
            }
            assertTrue(entered.isCompleted)
            assertThrows(IllegalStateException::class.java) { DatabaseResetter.requireNoRetainedWorkers() }
            var nextPreparation = false
            assertThrows(IllegalStateException::class.java) {
                DatabaseResetter.runResetBlocking(timeoutMs = 50) { nextPreparation = true }
            }
            assertFalse("retry must not enter new preparation while aborted worker is still held", nextPreparation)
            release.complete(Unit)
            assertTrue(finished.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue("abort decision must reject even a NonCancellable late commit", denied.get())
            assertSame("actual DB must remain open after aborted reset", old, DatabaseContainer.instance)
            DatabaseResetter.runResetBlocking { nextPreparation = true }
            assertTrue(nextPreparation)
            DatabaseResetter.requireNoRetainedWorkers()
        } finally {
            release.complete(Unit)
            DatabaseResetter.runResetBlocking { }
        }
    }

    @Test fun throwingMainCallbackBeforeCommitPreventsLateActualReset() {
        val old = DatabaseContainer.instance
        val failure = IllegalArgumentException("Main callback failed")
        val release = CompletableDeferred<Unit>()
        val finished = java.util.concurrent.CountDownLatch(1)
        val denied = java.util.concurrent.atomic.AtomicBoolean(false)
        try {
            val thrown = assertThrows(IllegalArgumentException::class.java) {
                DatabaseResetter.runResetBlocking {
                    withContext(NonCancellable) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post { throw failure }
                        release.await()
                        try { commitReset { DatabaseContainer.reset() } }
                        catch (e: IllegalStateException) { denied.set(true) }
                        finally { finished.countDown() }
                    }
                }
            }
            assertSame(failure, thrown)
            release.complete(Unit)
            assertTrue(finished.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue("pump failure must abort late commit", denied.get())
            assertSame(old, DatabaseContainer.instance)
        } finally { release.complete(Unit) }
    }

    @Test fun throwingMainCallbackDuringCommittedResetAwaitsActualMonitorRelease() {
        val old = DatabaseContainer.instance
        val failure = IllegalArgumentException("Main failed during reset")
        val held = java.util.concurrent.CountDownLatch(1)
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val holder = Thread {
            synchronized(DatabaseContainer.Companion) { held.countDown(); release.await() }
        }.apply { start() }
        assertTrue(held.await(5, java.util.concurrent.TimeUnit.SECONDS))
        val releaser = Thread {
            entered.await(5, java.util.concurrent.TimeUnit.SECONDS)
            Thread.sleep(250)
            release.countDown()
        }.apply { start() }
        try {
            val thrown = assertThrows(IllegalArgumentException::class.java) {
                DatabaseResetter.runResetBlocking {
                    commitReset {
                        entered.countDown()
                        android.os.Handler(android.os.Looper.getMainLooper()).post { throw failure }
                        DatabaseContainer.reset()
                    }
                }
            }
            assertSame(failure, thrown)
            assertEquals("cannot return pump error while reset still waits", 0L, release.count)
            assertNotSame(old, DatabaseContainer.instance)
        } finally {
            release.countDown()
            holder.join(5_000)
            releaser.join(5_000)
            DatabaseResetter.resetDatabase()
        }
    }

    @Test fun timeoutDoesNotRunResetAfterNonCancellableProducerEventuallyFinishes() {
        val release = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val producer = scope.launch {
            try { entered.complete(Unit); awaitCancellation() }
            finally { withContext(NonCancellable) { release.await() } }
        }
        runBlocking { entered.await() }
        var reset = false
        try {
            assertThrows(IllegalStateException::class.java) {
                DatabaseResetter.runResetBlocking(timeoutMs = 100) {
                    producer.cancelAndJoin()
                    currentCoroutineContext().ensureActive()
                    reset = true
                }
            }
            assertFalse(reset)
        } finally {
            release.complete(Unit)
            runBlocking { producer.join() }
            scope.cancel()
        }
        assertFalse("a timed-out cleanup must not reset later", reset)
    }
}
