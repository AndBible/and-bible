package net.bible.desktop.webview2

import net.bible.sharedcore.log.Log
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
class StaThread(
    name: String,
    private val pump: MessagePump,
    private val onFailure: (Throwable) -> Unit = { Log.e("WebView2", "COM message pump failed", it) },
) {
    private val queue = ConcurrentLinkedQueue<Runnable>()
    private val started = CountDownLatch(1)
    private val lifecycleLock = Any()
    private val pending = mutableSetOf<CompletableFuture<*>>()
    @Volatile private var closed = false
    @Volatile private var terminated = false
    @Volatile private var didStart = false
    @Volatile private var failure: Throwable? = null

    private val thread = Thread({
        var error: Throwable? = null
        try {
            pump.run {
                didStart = true
                started.countDown()
                drain()
            }
        } catch (t: Throwable) {
            error = t
        } finally {
            synchronized(lifecycleLock) {
                if (error == null && !closed) error = IllegalStateException("COM message pump exited unexpectedly")
                failure = error
                terminated = true
                queue.clear()
                pending.forEach {
                    it.completeExceptionally(
                        if (error != null) IllegalStateException("COM message pump failed", error)
                        else IllegalStateException("StaThread stopped")
                    )
                }
                pending.clear()
                started.countDown()
            }
            error?.let { runCatching { onFailure(it) } }
        }
    }, name).apply { isDaemon = true }

    init {
        thread.start()
        pump.wake()
    }

    val isCurrent: Boolean get() = Thread.currentThread() === thread

    /** Runs [task] later; returns false after close or pump failure without retaining the task. */
    fun post(task: () -> Unit): Boolean = synchronized(lifecycleLock) {
        if (closed || terminated) return false
        queue.add(Runnable(task))
        pump.wake()
        true
    }

    /** Runs [task] on the thread and returns its result; directly when already on it. Throws the task's exception. */
    fun <T> call(timeoutMs: Long = CALL_TIMEOUT_MS, task: () -> T): T {
        if (isCurrent) return task()
        val result = CompletableFuture<T>()
        synchronized(lifecycleLock) {
            failure?.let { throw IllegalStateException("COM message pump failed", it) }
            check(!closed && !terminated) { "StaThread is stopped" }
            pending += result
            queue.add(Runnable {
                try {
                    result.complete(task())
                } catch (t: Throwable) {
                    result.completeExceptionally(t)
                } finally {
                    synchronized(lifecycleLock) { pending -= result }
                }
            })
            pump.wake()
        }
        return try {
            result.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }

    /** Waits until the pump runs. */
    fun awaitStarted(timeoutMs: Long = CALL_TIMEOUT_MS): Boolean =
        started.await(timeoutMs, TimeUnit.MILLISECONDS) && didStart

    /** Stops the pump after the queued tasks. */
    fun close() = synchronized(lifecycleLock) {
        if (closed) return
        if (!terminated) {
            queue.add(Runnable { pump.quit() })
            pump.wake()
        }
        closed = true
    }

    /** Stops after queued cleanup and waits at most [timeoutMs]; never joins the owner from itself. */
    fun closeAndJoin(timeoutMs: Long) {
        require(timeoutMs > 0)
        close()
        if (!isCurrent) {
            try {
                thread.join(timeoutMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private fun drain() {
        while (true) {
            val task = queue.poll() ?: return
            try {
                task.run()
            } catch (t: Throwable) {
                Log.e("WebView2", "webview2: task on the STA thread failed", t)
            }
        }
    }

    companion object {
        const val CALL_TIMEOUT_MS = 30_000L
    }
}
