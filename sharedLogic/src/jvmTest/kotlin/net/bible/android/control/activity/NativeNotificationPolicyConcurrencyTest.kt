package net.bible.android.control.activity

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.FlowCollector
import java.util.concurrent.Semaphore
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
import java.util.concurrent.atomic.AtomicLong
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
    fun concurrentPreferenceDrainerCannotLetOffTerminalOvertakePreference() = runTest {
        concurrentPreferenceDrainerScenario(terminal = true)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun concurrentPreferenceDrainerCannotHideRapidOffOnFromPendingTimer() = runTest {
        concurrentPreferenceDrainerScenario(terminal = false)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun kotlinx.coroutines.test.TestScope.concurrentPreferenceDrainerScenario(terminal: Boolean) {
        var stored = true
        val settings = object : net.bible.sharedcore.platform.AppSettings by
            net.bible.sharedlogic.settings.DatabaseAppSettings({ error("unused settings") }, { false }) {
            override fun getBoolean(key: String, default: Boolean) = stored
            override fun setBoolean(key: String, value: Boolean?) { stored = value ?: true }
        }
        val preference = SystemNotificationsPreference(settings, true)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        // Registered before the policy: pause the shared drainer before it delivers the policy edge.
        val blocker = preference.observe { if (!it) { entered.countDown(); release.await(5, TimeUnit.SECONDS) } }
        val calls = mutableListOf<String>()
        val notifier = object : NativeNotifier {
            override val supportsProgress = false
            override fun show(n: NativeNotification) { calls += "show:${n.id}" }
            override fun remove(id: String) { calls += "remove:$id" }
            override fun setOnActivated(handler: (String) -> Unit) = Unit
        }
        val policy = NativeNotificationPolicy(MutableStateFlow(emptyList()), notifier, { false }, preference.enabled,
            { currentTime }, backgroundScope, null, { "Finished" }, documentSyncLifecycleNotices = true,
            documentSyncNoticeText = { "Queue ${it.status}" }, notificationPreference = preference)
        policy.start(); runCurrent()
        policy.onDocumentSyncProgress(DocumentSyncProgress(1, 1, DocumentSyncProgressStatus.RUNNING, total = 3))
        runCurrent(); advanceTimeBy(if (terminal) 10_000 else 9_000); runCurrent()
        val drainer = Thread { preference.set(false) }.also { it.start() }
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS), "Preference drainer did not pause")
            // These setters return on this thread while another thread owns the blocked drainer.
            if (!terminal) preference.set(true) else preference.set(false)
            if (terminal) {
                policy.onDocumentSyncProgress(DocumentSyncProgress(1, 2, DocumentSyncProgressStatus.FINISHED, total = 3))
                runCurrent()
                assertEquals(listOf("show:andbible-document-sync:1", "remove:andbible-document-sync:1"), calls)
            } else {
                advanceTimeBy(1_000); runCurrent()
                assertTrue(calls.isEmpty(), "Pending timer ignored accepted rapid off/on while callbacks paused")
            }
        } finally {
            release.countDown(); drainer.join(2_000)
            assertTrue(!drainer.isAlive)
            blocker.cancel(); policy.close()
        }
    }

    @OptIn(kotlinx.coroutines.InternalCoroutinesApi::class)
    @Test
    fun documentByteListsDoNotAccumulateWhilePolicyConsumerIsBlocked() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val observed = Semaphore(0)
        val clockReads = AtomicInteger()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val drained = CountDownLatch(1)
        val centerClock = AtomicLong()
        val center = ActivityCenter(scope) { centerClock.get() }
        val lists = object : StateFlow<List<ActivityItem>> by center.items {
            override suspend fun collect(collector: FlowCollector<List<ActivityItem>>): Nothing =
                center.items.collect { collector.emit(it); observed.release() }
        }
        val notifier = object : NativeNotifier {
            override val supportsProgress = false
            override fun show(n: NativeNotification) {
                if (n.id == "andbible-block") { entered.countDown(); release.await(5, TimeUnit.SECONDS) }
                if (n.id == "andbible-after") drained.countDown()
            }
            override fun remove(id: String) = Unit
            override fun setOnActivated(handler: (String) -> Unit) = Unit
        }
        val policy = NativeNotificationPolicy(lists, notifier, { false }, MutableStateFlow(true),
            { clockReads.incrementAndGet(); 0L }, scope, null, { "Finished" }, documentSyncLifecycleNotices = true,
            documentSyncNoticeText = { "Queue" })
        try {
            policy.start()
            assertTrue(observed.tryAcquire(2, TimeUnit.SECONDS))
            center.startOrFail("block", ActivityKind.DOWNLOAD, "Block", "Error")
            assertTrue(observed.tryAcquire(2, TimeUnit.SECONDS))
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            // ActivityCenter is the real source route used by DocumentSyncActivitySource. Its list
            // collector keeps running independently while the notification consumer cannot drain.
            center.start("document-sync:1", ActivityKind.DOCUMENT_SYNC, "Documents")
            // RUNNING rows share the real center's 1 s visibility debounce. Advance its clock,
            // then mutate once to publish the visible row without sleeping or bypassing the source.
            centerClock.set(2_000)
            center.setMessage("document-sync:1", "Visible")
            assertTrue(observed.tryAcquire(2, TimeUnit.SECONDS), "Document row did not pass visibility debounce")
            repeat(200) {
                center.setMessage("document-sync:1", "Bytes $it /private/document")
                assertTrue(observed.tryAcquire(2, TimeUnit.SECONDS))
                center.update("document-sync:1", it % 100)
                assertTrue(observed.tryAcquire(2, TimeUnit.SECONDS))
            }
            center.startOrFail("after", ActivityKind.DOWNLOAD, "After", "Error")
            assertTrue(observed.tryAcquire(2, TimeUnit.SECONDS))
            release.countDown()
            assertTrue(drained.await(2, TimeUnit.SECONDS))
            assertTrue(clockReads.get() < 20, "Document byte lists entered lifecycle backlog: ${clockReads.get()} evaluations")
        } finally {
            release.countDown()
            policy.close()
            scope.cancel()
        }
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
