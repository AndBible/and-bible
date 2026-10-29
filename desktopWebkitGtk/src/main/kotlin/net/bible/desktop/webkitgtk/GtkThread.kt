package net.bible.desktop.webkitgtk

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Drives a toolkit main loop. The real one (GTK, via JNA) comes later; tests inject a fake. */
interface MainLoopDriver {
    /** Runs the loop on the calling thread until [quit]; calls [onStarted] on that thread once the loop can accept work. */
    fun run(onStarted: () -> Unit)

    /** Thread-safe: schedules [task] to run on the loop thread. */
    fun invoke(task: Runnable)

    /** Thread-safe: makes [run] return. */
    fun quit()
}

/**
 * The one thread that owns the toolkit main loop. Every WebKit/GTK call must go through [call] or [post]:
 * GTK objects are not thread-safe and touching them from any other thread crashes the process.
 */
class GtkThread(private val driver: MainLoopDriver, name: String = "gtk-main") : AutoCloseable {
    @Volatile private var closed = false
    private val pending = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<Waiter<*>, Boolean>())
    private val thread: Thread

    private class Waiter<T> {
        val done = CountDownLatch(1)
        val outcome = AtomicReference<Result<T>?>()
        fun finish(r: Result<T>) { if (outcome.compareAndSet(null, r)) done.countDown() }
    }

    init {
        val started = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        thread = Thread({
            try {
                driver.run { started.countDown() }
            } catch (t: Throwable) {
                failure.set(t)
            } finally {
                closed = true
                started.countDown()
                // Unblock anyone still waiting in call().
                pending.toList().forEach { it.finish(Result.failure(IllegalStateException("GtkThread is closed"))) }
            }
        }, name).apply { isDaemon = true }
        thread.start()
        started.await()
        failure.get()?.let { throw IllegalStateException("Main loop failed to start", it) }
    }

    /** True on the loop thread. */
    val isCurrent: Boolean get() = Thread.currentThread() === thread

    /** Runs [block] on the loop thread (inline when already there) and returns its result; rethrows its exception. */
    fun <T> call(block: () -> T): T {
        if (isCurrent) return block()
        if (closed) throw IllegalStateException("GtkThread is closed")
        val waiter = Waiter<T>()
        pending += waiter
        try {
            driver.invoke { waiter.finish(runCatching(block)) }
            if (closed) waiter.finish(Result.failure(IllegalStateException("GtkThread is closed")))
            waiter.done.await()
            return waiter.outcome.get()!!.getOrThrow()
        } finally {
            pending -= waiter
        }
    }

    /** Schedules [block] on the loop thread without waiting. Dropped after close; an exception is printed, not fatal. */
    fun post(block: () -> Unit) {
        if (closed) return
        driver.invoke {
            try {
                block()
            } catch (t: Throwable) {
                System.err.println("GtkThread: posted task failed: $t")
                t.printStackTrace()
            }
        }
    }

    /** Stops the loop and joins its thread (up to 5 s). Idempotent. */
    override fun close() {
        if (!closed) {
            closed = true
            driver.quit()
        }
        if (!isCurrent) thread.join(TimeUnit.SECONDS.toMillis(5))
        pending.toList().forEach { it.finish(Result.failure(IllegalStateException("GtkThread is closed"))) }
    }
}
