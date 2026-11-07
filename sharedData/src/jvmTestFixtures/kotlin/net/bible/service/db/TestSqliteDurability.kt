package net.bible.service.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL
import java.io.File

/**
 * Shared only as JVM test source, never packaged in a production artifact. Only explicitly registered fixture
 * directories skip fsync; recovery/WAL/crash fixtures keep using unregistered directories and the real settings.
 */
object TestSqliteDurability {
    private val roots = mutableSetOf<String>()
    private val durableRoots = mutableSetOf<String>()

    @Synchronized
    fun registerDirectory(directory: String) {
        val root = File(directory).canonicalPath + File.separator
        durableRoots -= root
        roots += root
        install()
    }

    /** Explicit opt-out, also safe if Robolectric reuses a directory registered by an earlier application. */
    @Synchronized
    fun keepDurableDirectory(directory: String) {
        val root = File(directory).canonicalPath + File.separator
        roots -= root
        durableRoots += root
        install()
    }

    /** Every test connection also gets the [TestSqliteUseGuard], durable or not. */
    private fun install() {
        sqliteConnectionDecoratorForTest = { path, connection -> TestSqliteUseGuard.guard(path, decorate(path, connection)) }
    }

    /** True when [path] lies under a registered directory and under no durable opt-out. */
    @Synchronized
    private fun isNonDurable(path: String): Boolean {
        if (path == ":memory:") return false
        var parent = File(path).canonicalFile.parentFile
        var enabled = false
        while (parent != null) {
            val root = parent.path + File.separator
            if (root in durableRoots) return false
            if (root in roots) enabled = true
            parent = parent.parentFile
        }
        return enabled
    }

    /** Not synchronized: the lock wait below must not block other tests' opens on this object's monitor. */
    private fun decorate(path: String, connection: SQLiteConnection): SQLiteConnection {
        if (!isNonDurable(path)) return connection
        try {
            // `PRAGMA synchronous` reads the schema and so needs a SHARED lock, unlike `PRAGMA busy_timeout`. A fresh
            // connection has no busy handler, so another connection's commit in that instant (in the app suite Room's
            // invalidation-tracker refresh, a few microseconds after every Room write) failed this test-only open at
            // once with "database is locked". Wait for such a holder here, then hand the caller the driver's own
            // busy timeout unchanged, so lock behavior stays as in production.
            val driverBusyTimeout = connection.queryLong("PRAGMA busy_timeout") ?: 0L
            connection.execSQL("PRAGMA busy_timeout=$DECORATION_BUSY_TIMEOUT_MS")
            connection.execSQL("PRAGMA synchronous=OFF")
            connection.execSQL("PRAGMA busy_timeout=$driverBusyTimeout")
        } catch (failure: Throwable) {
            connection.close()
            throw failure
        }
        return object : SQLiteConnection by connection {
            override fun prepare(sql: String): SQLiteStatement {
                // Room configures FULL/NORMAL after opening, including migration/reopen connections. Keep OFF
                // at that boundary without changing the journal mode or swallowing any other PRAGMA.
                val statement = if (SYNCHRONOUS_ASSIGNMENT.matches(sql)) "PRAGMA synchronous=OFF" else sql
                return connection.prepare(statement)
            }
        }
    }

    /** Bounds the wait for another connection's lock while decorating; a holder longer than this is a real hang. */
    private const val DECORATION_BUSY_TIMEOUT_MS = 10_000

    private val SYNCHRONOUS_ASSIGNMENT = Regex(
        "\\s*PRAGMA\\s+synchronous\\s*=.*", RegexOption.IGNORE_CASE,
    )
}
