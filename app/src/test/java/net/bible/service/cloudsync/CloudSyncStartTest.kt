package net.bible.service.cloudsync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.sharedcore.cloud.DocumentSyncStarter
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.test.testAppSettings
import net.bible.test.testCoreStrings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.get
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class CloudSyncStartTest {
    private fun sync(scheduler: SyncScheduler) = CloudSync(
        AppDialogController(), testCoreStrings(), get(AppEnvironment::class.java), testAppSettings(),
        object : CloudAdapterFactory {
            override val available = listOf("NEXT_CLOUD")
            override fun displayName(id: String) = id
            override fun create(id: String): CloudAdapter = error("No sign-in in start test")
        }, scheduler, get(DocumentSyncStarter::class.java), DocumentSyncProvider { error("No document operations in start test") },
        get(net.bible.service.cloudsync.documents.DocumentSyncSettings::class.java),
    )

    @Test
    fun concurrentStartsShareOneScheduledRun() {
        val calls = AtomicInteger()
        val scheduled = CompletableDeferred<Boolean>()
        val cloudSync = sync(SyncScheduler { calls.incrementAndGet(); scheduled })
        val pool = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val go = CountDownLatch(1)
        try {
            val futures = (1..2).map {
                pool.submit<kotlinx.coroutines.Deferred<Boolean>> {
                    ready.countDown()
                    check(go.await(5, TimeUnit.SECONDS))
                    cloudSync.start()
                }
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            go.countDown()
            val results = futures.map { it.get(5, TimeUnit.SECONDS) }
            assertEquals(1, calls.get())
            assertSame(results[0], results[1])
            scheduled.complete(true)
            runBlocking { results.forEach { assertTrue(it.await()) }; withTimeout(1000) { cloudSync.waitUntilFinished() } }
        } finally {
            go.countDown()
            pool.shutdownNow()
        }
    }

    @Test
    fun refusedScheduleReturnsFalseAndDoesNotLeaveWaiterOrBlockRetry() = runBlocking {
        val calls = AtomicInteger()
        val cloudSync = sync(SyncScheduler { calls.incrementAndGet(); CompletableDeferred(false) })
        assertFalse(cloudSync.start().await())
        withTimeout(1000) { cloudSync.waitUntilFinished() }
        assertFalse(cloudSync.start().await())
        assertEquals(2, calls.get())
    }
}
