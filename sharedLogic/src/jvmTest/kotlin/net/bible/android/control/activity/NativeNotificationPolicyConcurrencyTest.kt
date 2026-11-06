package net.bible.android.control.activity

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
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

    private class TrackingNotifier : NativeNotifier {
        override val supportsProgress = true
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
