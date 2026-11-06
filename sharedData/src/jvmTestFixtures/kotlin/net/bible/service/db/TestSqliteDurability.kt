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

    @Synchronized
    private fun decorate(path: String, connection: SQLiteConnection): SQLiteConnection {
        if (path == ":memory:") return connection
        var parent = File(path).canonicalFile.parentFile
        var enabled = false
        while (parent != null) {
            val root = parent.path + File.separator
            if (root in durableRoots) return connection
            if (root in roots) enabled = true
            parent = parent.parentFile
        }
        if (!enabled) return connection
        try {
            connection.execSQL("PRAGMA synchronous=OFF")
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

    private val SYNCHRONOUS_ASSIGNMENT = Regex(
        "\\s*PRAGMA\\s+synchronous\\s*=.*", RegexOption.IGNORE_CASE,
    )
}
