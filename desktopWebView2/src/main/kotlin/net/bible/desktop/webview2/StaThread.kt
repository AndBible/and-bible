package net.bible.desktop.webview2

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * The thread loop under [StaThread]: on Windows a COM single-threaded apartment with a Win32 message pump
 * ([Win32MessagePump]); in tests a plain queue loop.
 */
interface MessagePump {
    /** Runs on the pump thread until [quit]: sets up the apartment, then pumps, calling [drain] after every [wake]. */
    fun run(drain: () -> Unit)

    /** Asks the pump thread to call `drain` soon; callable from any thread. */
    fun wake()

    /** Ends [run]; callable from any thread. */
    fun quit()
}

/**
 * The one thread that owns every WebView2 object. WebView2 must be created and called on an STA thread that pumps
 * messages, and it calls its handlers back on that same thread. Callers on other threads (the AWT event thread, coroutine
 * workers) never touch WebView2 directly: [post] and [call] marshal the work onto this thread. The thread never waits on
 * the AWT event thread, so a synchronous [call] from the EDT cannot deadlock.
 */
class StaThread(name: String, private val pump: MessagePump) {
    private val queue = ConcurrentLinkedQueue<Runnable>()
    private val started = CountDownLatch(1)

    @Volatile
    private var closed = false

    private val thread = Thread({
        pump.run {
            started.countDown()
            drain()
        }
    }, name).apply { isDaemon = true }

    init {
        thread.start()
        pump.wake()
    }

    val isCurrent: Boolean get() = Thread.currentThread() === thread

    /** Runs [task] on the thread, later (also when called on it). Ignored after [close]. */
    fun post(task: () -> Unit) {
        if (closed) return
        queue.add(Runnable(task))
        pump.wake()
    }

    /** Runs [task] on the thread and returns its result; directly when already on it. Throws the task's exception. */
    fun <T> call(timeoutMs: Long = CALL_TIMEOUT_MS, task: () -> T): T {
        if (isCurrent) return task()
        check(!closed) { "StaThread is closed" }
        val result = CompletableFuture<T>()
        queue.add(Runnable {
            try {
                result.complete(task())
            } catch (t: Throwable) {
                result.completeExceptionally(t)
            }
        })
        pump.wake()
        return try {
            result.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }

    /** Waits until the pump runs. */
    fun awaitStarted(timeoutMs: Long = CALL_TIMEOUT_MS): Boolean = started.await(timeoutMs, TimeUnit.MILLISECONDS)

    /** Stops the pump after the queued tasks. */
    fun close() {
        if (closed) return
        post { pump.quit() }
        closed = true
    }

    private fun drain() {
        while (true) {
            val task = queue.poll() ?: return
            try {
                task.run()
            } catch (t: Throwable) {
                System.err.println("webview2: task on the STA thread failed: $t")
            }
        }
    }

    companion object {
        const val CALL_TIMEOUT_MS = 30_000L
    }
}
