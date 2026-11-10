package net.bible.android.control.activity

import kotlinx.coroutines.CoroutineExceptionHandler
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import net.bible.sharedcore.cloud.DocumentSyncProgress
import net.bible.sharedcore.cloud.DocumentSyncProgressStatus
import net.bible.sharedcore.platform.NativeNotification
import net.bible.sharedcore.platform.NativeNotifier
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
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
        val events = CopyOnWriteArrayList<String>()
        @Volatile var firstCall = true
        override fun show(n: NativeNotification) {
            events += "show:${n.body}"
            if (firstCall) {
                firstCall = false
                entered.countDown()
                release.await(10, TimeUnit.SECONDS)
            }
            currentlyShown += n.id
        }
        override fun remove(id: String) { events += "remove:$id"; currentlyShown -= id }
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
    fun closeRacingWithLifecycleStartCannotLeaveOrResurrectNotification() {
        repeat(200) { iteration ->
            try {
                lifecycleCloseScenario()
            } catch (failure: AssertionError) {
                throw AssertionError("Lifecycle close stress iteration ${iteration + 1}/200", failure)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun lifecycleCloseScenario() {
        // No runTest work runner drives this scheduler concurrently. Hand it to the show thread
        // only while this thread is waiting, then join before reusing it.
        val scheduler = TestCoroutineScheduler()
        val failures = CopyOnWriteArrayList<Throwable>()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler) +
            CoroutineExceptionHandler { _, failure -> failures += failure })
        val notifier = TrackingNotifier(supportsProgress = false)
        val resumeConsumer = CountDownLatch(1)
        val pauseAfterShow = AtomicBoolean()
        val testThread = Thread.currentThread()
        val policy = NativeNotificationPolicy(
            items = MutableStateFlow(emptyList()), notifier = notifier, foreground = { false },
            enabled = MutableStateFlow(true), clock = {
                if (Thread.currentThread() !== testThread && pauseAfterShow.compareAndSet(true, false)) {
                    check(resumeConsumer.await(10, TimeUnit.SECONDS)) { "Consumer was not released" }
                }
                scheduler.currentTime
            }, scope = scope,
            attentionText = null, finishedText = { "Finished" }, documentSyncLifecycleNotices = true,
            documentSyncNoticeText = { "Queue ${it.status}: ${it.processed}/${it.total}" },
        )
        policy.start(); scheduler.runCurrent()
        policy.onDocumentSyncProgress(DocumentSyncProgress(1, 1, DocumentSyncProgressStatus.RUNNING, total = 3))
        scheduler.runCurrent(); scheduler.advanceTimeBy(10_000)
        val showThread = Thread { scheduler.runCurrent() }.apply {
            uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, failure -> failures += failure }
            start()
        }
        var closer: Thread? = null
        try {
            assertTrue(notifier.entered.await(2, TimeUnit.SECONDS), "lifecycle start never reached show()")
            pauseAfterShow.set(true)
            closer = Thread { policy.close() }.apply {
                uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, failure -> failures += failure }
                start()
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (closer.state != Thread.State.BLOCKED && closer.isAlive && System.nanoTime() < deadline) Thread.yield()
            assertEquals(Thread.State.BLOCKED, closer.state, "close must wait for the in-flight show")
            // Hold the consumer at its next clock read, after show releases closeLock. Otherwise a
            // terminal can legitimately win the lock and become a persistent final before close.
            policy.onDocumentSyncProgress(DocumentSyncProgress(1, 2, DocumentSyncProgressStatus.INTERRUPTED, total = 3))
            policy.onDocumentSyncProgress(DocumentSyncProgress(2, 3, DocumentSyncProgressStatus.RUNNING, total = 3))
            notifier.release.countDown()
            closer.join(2_000)
            assertTrue(!closer.isAlive, "close did not complete")
            assertEquals(emptySet(), notifier.currentlyShown, "Events: ${notifier.events}")
            resumeConsumer.countDown()
            showThread.join(2_000)
            assertTrue(!showThread.isAlive, "show consumer did not complete")
            policy.onDocumentSyncProgress(DocumentSyncProgress(3, 4, DocumentSyncProgressStatus.RUNNING, total = 3))
            scheduler.runCurrent(); scheduler.advanceTimeBy(60_000); scheduler.runCurrent()
            failures.firstOrNull()?.let { throw AssertionError("Policy worker failed", it) }
            assertEquals(listOf("show:Queue RUNNING: 0/3", "remove:andbible-document-sync:1"), notifier.events)
            assertEquals(emptySet(), notifier.currentlyShown)
        } finally {
            notifier.release.countDown()
            resumeConsumer.countDown()
            showThread.join(2_000)
            closer?.join(2_000)
            policy.close()
            scope.cancel()
            if (!showThread.isAlive && closer?.isAlive != true) scheduler.runCurrent()
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
