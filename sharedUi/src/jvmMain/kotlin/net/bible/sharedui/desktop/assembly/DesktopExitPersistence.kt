package net.bible.sharedui.desktop.assembly

import net.bible.sharedcore.log.Log
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * One persistence pass shared by window close and JVM shutdown. The hook waits only [shutdownTimeoutMs], even when
 * database or network I/O ignores coroutine cancellation. It never closes databases or touches UI/native engines.
 * The daemon worker is deliberately not interrupted on timeout: closing a database under an unfinished read can
 * crash the bundled SQLite driver. The OS reclaims it when the process exits.
 */
class DesktopExitPersistence(
    registerHook: (Thread) -> Unit = { Runtime.getRuntime().addShutdownHook(it) },
    private val persist: () -> Unit = { DesktopAppStartup.persistBeforeExit() },
    private val afterShutdown: () -> Unit = {},
    private val shutdownTimeoutMs: Long = 5_000,
) {
    private val lock = Any()
    private var pending: FutureTask<Unit>? = null

    init {
        registerHook(Thread({
            try {
                awaitPersistence(shutdownTimeoutMs)
            } finally {
                afterShutdown()
            }
        }, "andbible-shutdown-save"))
    }

    /** Normal close keeps the existing workspace/sync/settings timeouts; a later hook joins this same pass. */
    fun persistOnce(): Boolean = awaitPersistence(null)

    private fun awaitPersistence(timeoutMs: Long?): Boolean {
        val task = synchronized(lock) {
            pending ?: FutureTask<Unit> { persist() }.also {
                pending = it
                Thread(it, "andbible-exit-persistence").apply { isDaemon = true }.start()
            }
        }
        return try {
            if (timeoutMs == null) task.get() else task.get(timeoutMs, TimeUnit.MILLISECONDS)
            true
        } catch (e: TimeoutException) {
            Log.w("DesktopExitPersistence", "Shutdown persistence did not finish within $timeoutMs ms")
            false
        } catch (e: Exception) {
            if (e is InterruptedException) Thread.currentThread().interrupt()
            Log.e("DesktopExitPersistence", "Exit persistence failed", e)
            false
        }
    }
}
