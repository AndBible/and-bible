package net.bible.service.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport
import java.util.concurrent.locks.ReentrantLock

/**
 * Test-only guard against closing an SQLite connection under a thread that is inside SQLite on it (#3947).
 *
 * Room 3's `Pool.close()` closes every connection of the pool, also one that another coroutine has acquired, and
 * first finalizes that connection's cached statements. When that thread is inside `sqlite3_step` at that moment,
 * the bundled driver frees memory under it and the whole test JVM dies with SIGSEGV (executor exit 134): no test
 * fails, and the hs_err file names only the reader, never the closer. In production the app owns this through
 * `AiDatabaseLifetime` (cancel and join before close); a test or fixture that misses an owner hits it at random.
 *
 * Every connection a test opens through [sqliteDriverFactory] or [openSqlite] is wrapped (installed by
 * [TestSqliteDurability]). Each SQLite call on the connection or one of its statements holds the connection's
 * lock. A statement or connection close that finds another thread inside SQLite waits for that call to return
 * (bounded), records a violation with both stacks and only then closes, so the JVM survives and the stepping thread
 * gets the driver's ordinary "closed" error on its next call. `TestBibleApplication` fails the test through
 * [requireNoViolations]. Sequential use after close is left to the driver: it already throws, as in production.
 */
object TestSqliteUseGuard {
    private const val CLOSE_WAIT_SECONDS = 60L
    private val violations = ConcurrentLinkedQueue<String>()

    /** Wraps [connection] (opened for [path]); idempotent. */
    fun guard(path: String, connection: SQLiteConnection): SQLiteConnection =
        connection as? GuardedConnection ?: GuardedConnection(path, connection)

    fun isGuarded(connection: SQLiteConnection): Boolean = connection is GuardedConnection

    /** The thread currently inside SQLite on [connection], or null; for tests that must time a close. */
    fun threadInsideNative(connection: SQLiteConnection): Thread? = (connection as? GuardedConnection)?.inside

    /** Returns and clears the recorded violations. */
    fun takeViolations(): List<String> = buildList { while (true) add(violations.poll() ?: break) }

    /** Throws an [AssertionError] naming every recorded violation (and clears them). */
    fun requireNoViolations() {
        val recorded = takeViolations()
        if (recorded.isNotEmpty()) throw AssertionError(
            "HARNESS FAILURE: ${recorded.size} SQLite connection(s) closed while another thread was inside SQLite on " +
                "them (a background job outlived its database; without this guard the JVM dies with SIGSEGV).\n" +
                recorded.joinToString("\n\n"),
        )
    }

    private fun record(message: String) {
        violations += message
        System.err.println(message)
    }

    private class GuardedConnection(val path: String, val delegate: SQLiteConnection) : SQLiteConnection {
        private val lock = ReentrantLock()
        @Volatile var inside: Thread? = null
        @Volatile private var insideCall: String? = null

        /** Closes that have started on this connection and do not hold the lock yet; ordinary calls wait for them. */
        private val pendingCloses = AtomicInteger()

        fun <T> call(what: String, closing: Boolean = false, block: () -> T): T {
            if (closing) {
                pendingCloses.incrementAndGet()
                try {
                    if (!lock.tryLock()) awaitUserBeforeClose(what)
                } finally {
                    pendingCloses.decrementAndGet()
                }
            } else {
                awaitPendingCloses()
                lock.lock()
            }
            val previous = inside
            val previousCall = insideCall
            inside = Thread.currentThread()
            insideCall = what
            try {
                return block()
            } finally {
                inside = previous
                insideCall = previousCall
                lock.unlock()
            }
        }

        /**
         * A close that waits for a user runs before that user's next call. The non-fair lock alone would let the user
         * barge back in (it releases and re-acquires within nanoseconds, while the woken closer needs microseconds),
         * so the user kept working after the close was requested and got no "closed" error. A thread that already
         * holds the lock (a nested call) does not wait: the close is waiting for it.
         */
        private fun awaitPendingCloses() {
            while (pendingCloses.get() > 0 && !lock.isHeldByCurrentThread) LockSupport.parkNanos(100_000L)
        }

        /**
         * [ReentrantLock.tryLock] with a timeout that an interrupt does not cut short: Room may close from a cancelled
         * coroutine or an interrupted thread, and an [InterruptedException] escaping here would skip both the close and
         * the violation record. The interrupt status is restored afterwards.
         */
        private fun tryLockUninterruptibly(seconds: Long): Boolean {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
            var interrupted = false
            try {
                while (true) {
                    try {
                        return lock.tryLock(maxOf(0L, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)
                    } catch (_: InterruptedException) {
                        interrupted = true
                    }
                }
            } finally {
                if (interrupted) Thread.currentThread().interrupt()
            }
        }

        /** A close found the lock taken: name the user, wait for its call to return, then let the close proceed. */
        private fun awaitUserBeforeClose(what: String) {
            val closer = Thread.currentThread()
            val user = inside
            val userCall = insideCall
            val userStack = user?.stackTrace?.joinToString("\n") { "\tat $it" } ?: "\t(the call returned before its stack was read)"
            val closerStack = closer.stackTrace.drop(1).joinToString("\n") { "\tat $it" }
            val acquired = tryLockUninterruptibly(CLOSE_WAIT_SECONDS)
            record(
                "SQLite $what on $path by thread '${closer.name}' while thread '${user?.name}' was inside " +
                    "SQLite ($userCall) on the same connection" +
                    (if (acquired) "; the close waited for that call" else
                        "; that call did not return within $CLOSE_WAIT_SECONDS s, the close is SKIPPED (leaked)") +
                    ".\nUser '${user?.name}':\n$userStack\nCloser '${closer.name}':\n$closerStack",
            )
            if (!acquired) throw SkipClose()
        }

        override fun prepare(sql: String): SQLiteStatement = call("prepare") { GuardedStatement(this, delegate.prepare(sql)) }
        override fun inTransaction(): Boolean = call("inTransaction") { delegate.inTransaction() }
        override fun close() {
            try { call("connection close", closing = true) { delegate.close() } } catch (_: SkipClose) { }
        }
    }

    /** Thrown inside the guard only, when closing would free memory a stuck thread is still using. */
    private class SkipClose : RuntimeException()

    private class GuardedStatement(val connection: GuardedConnection, val delegate: SQLiteStatement) : SQLiteStatement {
        @Volatile private var closed = false
        private inline fun <T> call(what: String, crossinline block: () -> T): T = connection.call(what) { block() }

        override fun bindBlob(index: Int, value: ByteArray) = call("bindBlob") { delegate.bindBlob(index, value) }
        override fun bindDouble(index: Int, value: Double) = call("bindDouble") { delegate.bindDouble(index, value) }
        override fun bindLong(index: Int, value: Long) = call("bindLong") { delegate.bindLong(index, value) }
        override fun bindText(index: Int, value: String) = call("bindText") { delegate.bindText(index, value) }
        override fun bindNull(index: Int) = call("bindNull") { delegate.bindNull(index) }
        override fun getBlob(index: Int): ByteArray = call("getBlob") { delegate.getBlob(index) }
        override fun getDouble(index: Int): Double = call("getDouble") { delegate.getDouble(index) }
        override fun getLong(index: Int): Long = call("getLong") { delegate.getLong(index) }
        override fun getText(index: Int): String = call("getText") { delegate.getText(index) }
        override fun isNull(index: Int): Boolean = call("isNull") { delegate.isNull(index) }
        override fun getColumnCount(): Int = call("getColumnCount") { delegate.getColumnCount() }
        override fun getColumnName(index: Int): String = call("getColumnName") { delegate.getColumnName(index) }
        override fun getColumnType(index: Int): Int = call("getColumnType") { delegate.getColumnType(index) }
        override fun step(): Boolean = call("step") { delegate.step() }
        override fun reset() = call("reset") { delegate.reset() }
        override fun clearBindings() = call("clearBindings") { delegate.clearBindings() }
        override fun close() {
            if (closed) return
            try {
                connection.call("statement close", closing = true) {
                    delegate.close()
                    closed = true
                }
            } catch (_: SkipClose) { }
        }
    }
}
