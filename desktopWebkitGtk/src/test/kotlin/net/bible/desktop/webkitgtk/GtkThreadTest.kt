package net.bible.desktop.webkitgtk

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** A queue-based loop standing in for GTK's main loop. */
private class FakeDriver : MainLoopDriver {
    private val queue = LinkedBlockingQueue<Runnable>()
    private object Quit : Runnable { override fun run() = Unit }
    @Volatile var loopThread: Thread? = null

    override fun run(onStarted: () -> Unit) {
        loopThread = Thread.currentThread()
        onStarted()
        while (true) {
            val task = queue.take()
            if (task === Quit) return
            task.run()
        }
    }

    override fun invoke(task: Runnable) { queue.put(task) }
    override fun quit() { queue.put(Quit) }
}

class GtkThreadTest {
    @Test
    fun callFromOtherThreadRuns() {
        val driver = FakeDriver()
        GtkThread(driver).use { gtk ->
            val result = gtk.call { Thread.currentThread() to 42 }
            assertEquals(driver.loopThread, result.first)
            assertNotEquals(Thread.currentThread(), result.first)
            assertEquals(42, result.second)
            assertFalse(gtk.isCurrent)
        }
    }

    @Test
    fun callFromLoopThreadRunsInline() {
        GtkThread(FakeDriver()).use { gtk ->
            val value = gtk.call { assertTrue(gtk.isCurrent); gtk.call { 7 } + 1 }
            assertEquals(8, value)
        }
    }

    @Test
    fun callRethrowsException() {
        GtkThread(FakeDriver()).use { gtk ->
            val e = assertFailsWith<IllegalArgumentException> { gtk.call<Unit> { throw IllegalArgumentException("boom") } }
            assertEquals("boom", e.message)
            assertEquals(1, gtk.call { 1 }) // loop still alive
        }
    }

    @Test
    fun postRunsInOrder() {
        GtkThread(FakeDriver()).use { gtk ->
            val seen = mutableListOf<Int>()
            repeat(5) { i -> gtk.post { seen += i } }
            gtk.call { }
            assertEquals(listOf(0, 1, 2, 3, 4), gtk.call { seen.toList() })
        }
    }

    @Test
    fun postExceptionDoesNotKillLoop() {
        GtkThread(FakeDriver()).use { gtk ->
            gtk.post { throw RuntimeException("expected in test") }
            assertEquals(5, gtk.call { 5 })
        }
    }

    @Test
    fun closeJoinsThreadAndCallAfterwardsThrows() {
        val driver = FakeDriver()
        val gtk = GtkThread(driver)
        gtk.close()
        assertFalse(driver.loopThread!!.isAlive)
        assertFailsWith<IllegalStateException> { gtk.call { 1 } }
        var ran = false
        gtk.post { ran = true }
        gtk.close() // idempotent
        assertFalse(ran)
    }

    @Test
    fun concurrentCallsAllComplete() {
        GtkThread(FakeDriver()).use { gtk ->
            val pool = Executors.newFixedThreadPool(8)
            try {
                val done = CountDownLatch(50)
                var sum = 0
                repeat(50) { i -> pool.execute { gtk.call { sum += i }; done.countDown() } }
                assertTrue(done.await(10, TimeUnit.SECONDS))
                assertEquals((0 until 50).sum(), gtk.call { sum })
            } finally {
                pool.shutdownNow()
            }
        }
    }
}
