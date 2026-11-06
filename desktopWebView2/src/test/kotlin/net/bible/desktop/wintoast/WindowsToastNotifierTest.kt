package net.bible.desktop.wintoast

import net.bible.desktop.webview2.MessagePump
import net.bible.desktop.webview2.StaThread
import net.bible.sharedcore.platform.NativeNotification
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsToastNotifierTest {
    private open class FakeApi : ToastApi {
        val calls = mutableListOf<String>()
        val data = mutableListOf<Map<String, String>>()
        var activated: (String) -> Unit = {}
        var result = 0
        var closes = 0
        override fun show(xml: String, tag: String, group: String, data: Map<String, String>): Int {
            calls += "show:$tag:$group"
            this.data += data
            return result
        }
        override fun update(tag: String, group: String, data: Map<String, String>): Int {
            calls += "update:$tag:$group"
            this.data += data
            return result
        }
        override fun remove(tag: String, group: String): Int {
            calls += "remove:$tag:$group"
            return result
        }
        override fun onActivated(handler: (String) -> Unit) { activated = handler }
        override fun close() { closes++ }
    }

    private fun notification(id: String = "job", progress: Int? = 1, body: String = "Working") =
        NativeNotification(id, "Title", body, progress, false, false)

    @Test
    fun firstShowThenInPlaceUpdate() {
        val api = FakeApi()
        val notifier = WindowsToastNotifier({ api }, { it.run() })
        notifier.show(notification())
        notifier.show(notification(progress = 42, body = "Downloading"))
        assertTrue(notifier.supportsProgress)
        assertEquals(listOf("show:job:andbible", "update:job:andbible"), api.calls)
        assertEquals(mapOf("progressValue" to "0.42", "progressText" to "42 %", "status" to "Downloading"), api.data.last())
    }

    @Test
    fun finalReplacesProgressWithSameTag() {
        val api = FakeApi()
        val notifier = WindowsToastNotifier({ api }, { it.run() })
        notifier.show(notification())
        notifier.show(notification(progress = null, body = "Finished"))
        assertEquals(listOf("show:job:andbible", "show:job:andbible"), api.calls)
    }

    @Test
    fun titleAndUrgencyChangesReshowEvenWhileProgressing() {
        val api = FakeApi()
        val notifier = WindowsToastNotifier({ api }, { it.run() })
        notifier.show(notification())
        notifier.show(notification().copy(title = "Different"))
        notifier.show(notification().copy(title = "Different", urgent = true))
        assertEquals(List(3) { "show:job:andbible" }, api.calls)
    }

    @Test
    fun removeUsesTagAndGroupAndForgetsActivation() {
        val api = FakeApi()
        val notifier = WindowsToastNotifier({ api }, { it.run() })
        val clicked = mutableListOf<String>()
        notifier.setOnActivated { clicked += it }
        notifier.show(notification())
        notifier.remove("job")
        api.activated("job")
        assertEquals(listOf("show:job:andbible", "remove:job:andbible"), api.calls)
        assertTrue(clicked.isEmpty())
    }

    @Test
    fun activationReturnsActivityId() {
        val api = FakeApi()
        val notifier = WindowsToastNotifier({ api }, { it.run() })
        val clicked = mutableListOf<String>()
        notifier.setOnActivated { clicked += it }
        notifier.show(notification(id = "a".repeat(65)))
        api.activated("unknown")
        api.activated("635361c48bb9eab14198e76ea8ab7f1a41685d6ad62aa9146d301d4f17eb0ae0")
        assertEquals(listOf("a".repeat(65)), clicked)
        notifier.close()
        api.activated("635361c48bb9eab14198e76ea8ab7f1a41685d6ad62aa9146d301d4f17eb0ae0")
        assertEquals(1, clicked.size)
    }

    @Test
    fun failureHresultLoggedOnceAndSwallowedAndFailedShowRetried() {
        val api = FakeApi().apply { result = 0x80004005.toInt() }
        val logs = mutableListOf<String>()
        val notifier = WindowsToastNotifier({ api }, { it.run() }, log = { logs += it })
        repeat(3) { notifier.show(notification()) }
        repeat(2) { notifier.remove("job") }
        assertEquals(2, logs.size)
        assertTrue(logs.any { "show" in it && "0x80004005" in it })
        assertTrue(logs.any { "remove" in it && "0x80004005" in it })
        assertEquals(List(3) { "show:job:andbible" } + List(2) { "remove:job:andbible" }, api.calls)
    }

    @Test
    fun initializationAndSchedulingExceptionsNeverEscape() {
        val logs = mutableListOf<String>()
        val notifier = WindowsToastNotifier({ error("no service") }, { it.run() }, log = { logs += it })
        repeat(2) { notifier.show(notification()) }
        notifier.close()
        assertEquals(1, logs.size)
        val rejected = WindowsToastNotifier({ FakeApi() }, { error("rejected") }, log = { logs += it })
        rejected.show(notification())
        rejected.remove("job")
        rejected.close()
        assertTrue(logs.any { "rejected" in it })
    }

    @Test
    fun dismissedProgressIsNotResurrectedByFurtherProgress() {
        val api = FakeApi()
        val notifier = WindowsToastNotifier({ api }, { it.run() })
        notifier.show(notification())
        api.result = 1 // S_FALSE: Update succeeded as a call, but the toast is no longer in history.
        notifier.show(notification(progress = 2))
        notifier.show(notification(progress = 3))
        api.result = 0
        notifier.show(notification(progress = null))
        assertEquals(listOf("show:job:andbible", "update:job:andbible", "show:job:andbible"), api.calls)
    }

    @Test
    fun closeRemovesOnlyProgressAndClosesApiOnceWithoutLateCalls() {
        val api = FakeApi()
        val notifier = WindowsToastNotifier({ api }, { it.run() })
        notifier.show(notification())
        notifier.show(notification(id = "finished", progress = null))
        notifier.close()
        notifier.close()
        notifier.show(notification())
        notifier.remove("finished")
        assertEquals(listOf("show:job:andbible", "show:finished:andbible", "remove:job:andbible"), api.calls)
        assertEquals(1, api.closes)
    }

    @Test
    fun unusedNotifierNeverInitializesApiEvenAtClose() {
        var created = 0
        val notifier = WindowsToastNotifier({ created++; FakeApi() }, { it.run() })
        notifier.close()
        assertEquals(0, created)
        // Construction of the native seam itself must not load Windows DLLs on Linux.
        WinRtToastApi(null).close()
        val errors = java.util.concurrent.CopyOnWriteArrayList<Throwable>()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, error -> errors += error }
        try { WindowsToastNotifier().close() }
        finally { Thread.setDefaultUncaughtExceptionHandler(previous) }
        assertTrue(errors.isEmpty(), "unused close loaded Windows libraries: $errors")
    }

    @Test
    fun closeRemovesProgressToastsAndIsBounded() {
        val pump = QueuePump()
        val sta = StaThread("toast-test-owner", pump)
        val entered = CountDownLatch(1)
        val unblock = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val apiThreads = mutableListOf<Thread>()
        val api = object : FakeApi() {
            override fun remove(tag: String, group: String): Int {
                apiThreads += Thread.currentThread()
                entered.countDown()
                check(unblock.await(5, TimeUnit.SECONDS))
                return super.remove(tag, group)
            }
            override fun close() {
                apiThreads += Thread.currentThread()
                closed.countDown()
            }
        }
        val notifier = WindowsToastNotifier(
            { apiThreads += Thread.currentThread(); api },
            { sta.post { it.run() } },
            closeThread = { sta.closeAndJoin(500) },
        )
        try {
            notifier.show(notification())
            sta.call { Unit }
            val start = System.nanoTime()
            notifier.close()
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            assertTrue(entered.await(1, TimeUnit.SECONDS), "cleanup never entered remove")
            assertTrue(elapsedMs < 1_000, "close blocked for $elapsedMs ms")
            assertFalse(closed.await(20, TimeUnit.MILLISECONDS), "remove was not actually blocked")
            unblock.countDown()
            assertTrue(closed.await(2, TimeUnit.SECONDS))
            sta.closeAndJoin(500)
            assertTrue(apiThreads.all { it === pump.owner })
        } finally {
            unblock.countDown()
            sta.closeAndJoin(500)
        }
    }

    @Test
    fun boundedJoinDrainsQueuedTasksAndIsSafeFromTheOwnerItself() {
        val pump = QueuePump()
        val sta = StaThread("toast-self-close", pump)
        val finished = CountDownLatch(1)
        try {
            sta.post {
                sta.closeAndJoin(500)
                finished.countDown()
            }
            assertTrue(finished.await(1, TimeUnit.SECONDS), "owner tried joining itself")
            sta.closeAndJoin(500)
            assertEquals(0L, pump.stopped.count)
        } finally { sta.closeAndJoin(500) }
    }

    @Test
    fun failedPumpReportsOnceRejectsFutureWorkAndCompletesQueuedCalls() {
        for (afterStart in listOf(false, true)) {
            val release = CountDownLatch(1)
            val entering = CountDownLatch(1)
            val queued = CountDownLatch(3) // Initial wake, post and the pending synchronous call.
            val reported = CountDownLatch(1)
            val reports = java.util.concurrent.CopyOnWriteArrayList<Throwable>()
            val pump = object : MessagePump {
                override fun run(drain: () -> Unit) {
                    if (afterStart) drain()
                    entering.countDown()
                    check(release.await(2, TimeUnit.SECONDS))
                    error("pump failed")
                }
                override fun wake() { queued.countDown() }
                override fun quit() = Unit
            }
            val sta = StaThread("toast-failing-pump", pump, onFailure = { reports += it; reported.countDown() })
            val callerDone = CountDownLatch(1)
            var callFailure: Throwable? = null
            var ran = false
            val caller = Thread {
                try { sta.call(10_000) { ran = true } }
                catch (t: Throwable) { callFailure = t }
                finally { callerDone.countDown() }
            }
            try {
                assertTrue(entering.await(1, TimeUnit.SECONDS))
                sta.post { ran = true }
                caller.start()
                assertTrue(queued.await(1, TimeUnit.SECONDS))
                release.countDown()
                assertTrue(reported.await(1, TimeUnit.SECONDS))
                assertTrue(callerDone.await(1, TimeUnit.SECONDS), "queued call was stranded by dead pump")
                repeat(100) { assertFalse(sta.post { ran = true }, "dead owner accepted work") }
                assertFalse(ran)
                assertTrue(callFailure != null)
                assertEquals(listOf("pump failed"), reports.map { it.message })
                assertFalse(sta.awaitStarted(50) && !afterStart)
            } finally { release.countDown(); sta.closeAndJoin(500); caller.join(500) }
        }
    }

    @Test
    fun productionOwnerReportsStartupFailureOnlyOnceWithoutNativeApiInitialization() {
        val failed = CountDownLatch(1)
        val logs = java.util.concurrent.CopyOnWriteArrayList<String>()
        val notifier = WindowsToastNotifier(null, pumpFactory = {
            object : MessagePump {
                override fun run(drain: () -> Unit): Unit = error("startup failure")
                override fun wake() = Unit
                override fun quit() = Unit
            }
        }, log = { logs += it; failed.countDown() })
        try {
            notifier.show(notification())
            assertTrue(failed.await(1, TimeUnit.SECONDS))
            repeat(100) { notifier.show(notification()); notifier.remove("job") }
        } finally { notifier.close() }
        assertEquals(1, logs.size)
        assertTrue("startup failure" in logs.single())
    }

    private class QueuePump : MessagePump {
        private val wakes = LinkedBlockingQueue<Boolean>()
        val stopped = CountDownLatch(1)
        @Volatile var owner: Thread? = null
        override fun run(drain: () -> Unit) {
            try {
                owner = Thread.currentThread()
                drain()
                while (wakes.take()) drain()
            } finally { stopped.countDown() }
        }
        override fun wake() { wakes.add(true) }
        override fun quit() { wakes.add(false) }
    }
}
