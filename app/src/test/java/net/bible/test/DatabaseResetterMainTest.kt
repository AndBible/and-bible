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
