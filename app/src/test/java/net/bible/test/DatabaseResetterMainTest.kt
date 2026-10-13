package net.bible.test

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
        val owned = OrderedLauncher(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        val koinLauncher = get<OrderedLauncher>(OrderedLauncher::class.java)
        val field = settings.javaClass.getDeclaredField("launcher").apply { isAccessible = true }
        val previous = field.get(settings)
        assertTrue("the fixture launcher and the owned launcher must be distinct", previous !== owned)
        field.set(settings, owned)
        val release = CompletableDeferred<Unit>()
        val resetDone = CompletableDeferred<Throwable?>()
        val thread = Thread {
            resetDone.complete(runCatching { DatabaseResetter.resetDatabase() }.exceptionOrNull())
        }
        try {
            runBlocking { settings.ensureLoaded() }
            settings.wifiOnly = false
            owned.launch("document-sync-settings") { release.await() }
            thread.isDaemon = true
            thread.start()
            Thread.sleep(800)
            assertFalse(
                "reset joined the Koin launcher and closed the database while the owned queue was still held",
                resetDone.isCompleted,
            )
            release.complete(Unit)
            runBlocking { withTimeout(15_000) { resetDone.await() } }?.let { throw it }
            assertFalse(koinLauncher === owned)
        } finally {
            if (!release.isCompleted) release.complete(Unit)
            thread.join(15_000)
            field.set(settings, previous)
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
            release.complete(Unit)
            assertTrue(finished.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue("abort decision must reject even a NonCancellable late commit", denied.get())
            assertSame("actual DB must remain open after aborted reset", old, DatabaseContainer.instance)
        } finally { release.complete(Unit) }
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
