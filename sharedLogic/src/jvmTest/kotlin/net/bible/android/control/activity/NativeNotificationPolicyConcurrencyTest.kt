package net.bible.android.control.activity

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.sharedcore.cloud.DocumentSyncProgress
import net.bible.sharedcore.cloud.DocumentSyncProgressStatus
import net.bible.sharedcore.platform.NativeNotification
import net.bible.sharedcore.platform.NativeNotifier
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NativeNotificationPolicyConcurrencyTest {

    private class TrackingNotifier(override val supportsProgress: Boolean = true) : NativeNotifier {
        val currentlyShown: MutableSet<String> = ConcurrentHashMap.newKeySet()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        @Volatile var firstCall = true
        override fun show(n: NativeNotification) {
            if (firstCall) {
                firstCall = false
                entered.countDown()
                release.await(10, TimeUnit.SECONDS)
            }
            currentlyShown += n.id
        }
        override fun remove(id: String) { currentlyShown -= id }
        override fun setOnActivated(handler: (id: String) -> Unit) {}
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun closeRacingWithLifecycleStartCannotLeaveOrResurrectNotification() = runTest {
        val notifier = TrackingNotifier(supportsProgress = false)
        val policy = NativeNotificationPolicy(
            items = MutableStateFlow(emptyList()), notifier = notifier, foreground = { false },
            enabled = MutableStateFlow(true), clock = { currentTime }, scope = backgroundScope,
            attentionText = null, finishedText = { "Finished" }, documentSyncLifecycleNotices = true,
            documentSyncNoticeText = { "Queue ${it.status}: ${it.processed}/${it.total}" },
        )
        policy.start(); runCurrent()
        policy.onDocumentSyncProgress(DocumentSyncProgress(1, 1, DocumentSyncProgressStatus.RUNNING, total = 3))
        runCurrent(); advanceTimeBy(10_000)
        val scheduler = Thread { runCurrent() }.also { it.start() }
        var closer: Thread? = null
        try {
            assertTrue(notifier.entered.await(2, TimeUnit.SECONDS), "lifecycle start never reached show()")
            closer = Thread { policy.close() }.also { it.start() }
            // Queue inputs while both show and close are in flight; none may land after close returns.
            policy.onDocumentSyncProgress(DocumentSyncProgress(1, 2, DocumentSyncProgressStatus.INTERRUPTED, total = 3))
            policy.onDocumentSyncProgress(DocumentSyncProgress(2, 3, DocumentSyncProgressStatus.RUNNING, total = 3))
            notifier.release.countDown()
            scheduler.join(2_000)
            closer.join(2_000)
            assertTrue(!scheduler.isAlive && !closer.isAlive, "close/show did not complete")
            assertEquals(emptySet(), notifier.currentlyShown)
            runCurrent(); advanceTimeBy(60_000); runCurrent()
            assertEquals(emptySet(), notifier.currentlyShown)
        } finally {
            notifier.release.countDown()
            scheduler.join(2_000)
            closer?.join(2_000)
            policy.close()
        }
    }

    @Test
    fun closeRacingWithAShowInFlightLeavesNothingShown() {
        val notifier = TrackingNotifier()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val item = ActivityItem("a", ActivityKind.DOWNLOAD, "T", ActivityState.RUNNING, 5, null, null, 0, null, true)
            // Progress shows 10 s after the policy first saw the item. The first clock read (first sight) is 0 and every
            // later one is 100_000, so the wake-up scheduled after the first evaluation fires at once and show() runs.
            val reads = AtomicInteger()
            val clock = { if (reads.getAndIncrement() == 0) 0L else 100_000L }
            val policy = NativeNotificationPolicy(
                MutableStateFlow(listOf(item)), notifier, { false }, MutableStateFlow(true), clock, scope, { "Finished" },
            )
            policy.start()
            assertTrue(notifier.entered.await(10, TimeUnit.SECONDS), "collector never reached show()")
            val closer = Thread { policy.close() }.also { it.start() }
            // close() must wait for the in-flight show (lock held); give it time to block, then let the show finish.
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (closer.state != Thread.State.BLOCKED && closer.state != Thread.State.WAITING && System.nanoTime() < deadline) Thread.sleep(5)
            notifier.release.countDown()
            closer.join(10_000)
            assertEquals(emptySet(), notifier.currentlyShown)
        } finally {
            scope.cancel()
        }
    }
}
