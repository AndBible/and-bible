package net.bible.service.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class AppDatabaseFilesCommonTest {
    private var previousHost: DatabaseHost? = null

    @BeforeTest
    fun rememberHost() { previousHost = try { DatabaseContainer.host } catch (_: IllegalStateException) { null } }

    /** [DatabaseContainer.installHost] is global; put back whatever was installed before the test (if anything). */
    @AfterTest
    fun restoreHost() { previousHost?.let { DatabaseContainer.installHost(it) } }

    private fun host(): Pair<String, FakeDatabaseHost> {
        val dir = newTestDir()
        return dir to FakeDatabaseHost(dir).also { DatabaseContainer.installHost(it) }
    }

    @Test
    fun recoverIfCorruptMovesAGarbageFileAside() = runTest(timeout = 10.minutes) {
        val (dir, _) = host()
        val path = "$dir/garbage.db"
        FileSystem.SYSTEM.write(path.toPath()) { writeUtf8("this is definitely not a sqlite database ".repeat(20)) }
        val moved = assertNotNull(recoverIfCorrupt(path))
        assertFalse(FileSystem.SYSTEM.exists(path.toPath()))
        assertTrue(FileSystem.SYSTEM.exists(moved.toPath()))
        assertTrue(moved.contains(".corrupt-20260101-000000"))
    }

    @Test
    fun recoverIfCorruptLeavesAValidDatabaseAlone() = runTest(timeout = 10.minutes) {
        val (dir, _) = host()
        val path = "$dir/ok.db"
        openSqlite(path).use { it.execSQL("CREATE TABLE t(a)") }
        assertNull(recoverIfCorrupt(path))
        assertTrue(FileSystem.SYSTEM.exists(path.toPath()))
    }

    @Test
    fun aTransientCorruptionReadDoesNotMoveAHealthyDatabase() {
        val (dir, _) = host()
        val path = "$dir/transient.db"
        openSqlite(path).use {
            it.execSQL("CREATE TABLE t(a)")
            it.execSQL("INSERT INTO t VALUES (42)")
        }
        FileSystem.SYSTEM.write("$path.lck".toPath()) { writeUtf8("room lock") }
        var opens = 0
        var firstClosed = false

        val moved = recoverIfCorrupt(path) { name ->
            opens++
            val connection = openSqlite(name)
            if (opens == 1) object : SQLiteConnection by connection {
                override fun prepare(sql: String): SQLiteStatement =
                    throw SQLiteException("Error code: 11, message: transient malformed read")
                override fun close() {
                    connection.close()
                    firstClosed = true
                }
            } else {
                assertTrue(firstClosed, "discard the suspect connection before reopening")
                connection
            }
        }

        assertNull(moved, "a single failed read must not discard user data")
        assertEquals(2, opens)
        assertEquals("room lock", FileSystem.SYSTEM.read("$path.lck".toPath()) { readUtf8() })
        openSqlite(path).use { assertEquals(42L, it.queryLong("SELECT a FROM t")) }
        assertFalse(FileSystem.SYSTEM.list(dir.toPath()).any { ".corrupt-" in it.name })
    }

    @Test
    fun aTransientOpenFailureDoesNotMoveAHealthyDatabase() {
        val (dir, _) = host()
        val path = "$dir/transient-open.db"
        openSqlite(path).use { it.execSQL("PRAGMA user_version = 7") }
        var opens = 0

        assertNull(recoverIfCorrupt(path) { name ->
            if (++opens == 1) throw SQLiteException("Error code: 26, message: transient not a database")
            openSqlite(name)
        })

        assertEquals(2, opens)
        assertEquals(7, readUserVersion(path))
    }

    @Test
    fun nonCorruptionErrorsOnEitherAttemptNeverMoveTheFile() {
        val (dir, _) = host()
        val path = "$dir/non-corrupt.db"
        openSqlite(path).use { it.execSQL("PRAGMA user_version = 7") }
        for (message in listOf(
            "Error code: 5, message: database is locked",
            "Error code: 6, message: table is locked",
            "Error code: 8, message: readonly database",
            "Error code: 10, message: disk I/O error",
            "Error code: 14, message: unable to open database file",
            "file is not a database",
        )) {
            for (failFirst in listOf(false, true)) {
                var opens = 0
                assertNull(recoverIfCorrupt(path) { _ ->
                    opens++
                    if (failFirst && opens == 1) {
                        throw SQLiteException("Error code: 779, message: malformed index")
                    }
                    throw SQLiteException(message)
                }, "must not recover on $message (retry: $failFirst)")
                assertEquals(if (failFirst) 2 else 1, opens)
                assertEquals(7, readUserVersion(path))
                assertFalse(FileSystem.SYSTEM.list(dir.toPath()).any { ".corrupt-" in it.name })
            }
        }
    }

    @Test
    fun anExclusivelyLockedDatabaseIsLeftAlone() {
        val (dir, _) = host()
        val path = "$dir/locked.db"
        openSqlite(path).use { holder ->
            holder.execSQL("CREATE TABLE t(a)")
            holder.execSQL("BEGIN EXCLUSIVE")
            try {
                holder.execSQL("INSERT INTO t VALUES (42)")
                assertNull(recoverIfCorrupt(path))
                assertTrue(FileSystem.SYSTEM.exists(path.toPath()))
                assertFalse(FileSystem.SYSTEM.list(dir.toPath()).any { ".corrupt-" in it.name })
            } finally {
                holder.execSQL("ROLLBACK")
            }
        }
    }

    @Test
    fun confirmedCorruptionMovesTheFileAndEveryRemainingSibling() {
        val (dir, _) = host()
        val path = "$dir/corrupt-siblings.db"
        val garbage = "this is definitely not a sqlite database ".repeat(20)
        FileSystem.SYSTEM.write(path.toPath()) { writeUtf8(garbage) }
        var opens = 0
        var closes = 0
        val suffixes = listOf("-journal", "-wal", "-shm", ".lck")

        val moved = assertNotNull(recoverIfCorrupt(path) { name ->
            opens++
            val connection = openSqlite(name)
            object : SQLiteConnection by connection {
                override fun close() {
                    connection.close()
                    closes++
                    // SQLite may consume invalid journals/WALs. Exercise all remaining siblings after the probe.
                    for (suffix in suffixes) {
                        FileSystem.SYSTEM.write((path + suffix).toPath()) { writeUtf8("saved $suffix") }
                    }
                }
            }
        })

        assertEquals(2, opens, "recovery is bounded even for a truly corrupt file")
        assertEquals(2, closes)
        assertFalse(FileSystem.SYSTEM.exists(path.toPath()))
        assertEquals(garbage, FileSystem.SYSTEM.read(moved.toPath()) { readUtf8() })
        for (suffix in suffixes) {
            assertFalse(FileSystem.SYSTEM.exists((path + suffix).toPath()))
            assertEquals("saved $suffix", FileSystem.SYSTEM.read((moved + suffix).toPath()) { readUtf8() })
        }
    }

    @Test
    fun userVersionRecoveringCorruptionOfAMissingFileIsZero() = runTest(timeout = 10.minutes) {
        val (dir, _) = host()
        assertEquals(0, userVersionRecoveringCorruption("$dir/missing.db"))
        assertFalse(FileSystem.SYSTEM.exists("$dir/missing.db".toPath()))
    }

    @Test
    fun userVersionRecoveringCorruptionOfAGarbageFileMovesItAsideAndIsZero() = runTest(timeout = 10.minutes) {
        val (dir, _) = host()
        val path = "$dir/garbage-version.db"
        FileSystem.SYSTEM.write(path.toPath()) { writeUtf8("this is definitely not a sqlite database ".repeat(20)) }
        assertEquals(0, userVersionRecoveringCorruption(path))
        assertFalse(FileSystem.SYSTEM.exists(path.toPath()), "corrupt file moved aside")
        val aside = FileSystem.SYSTEM.list(dir.toPath()).filter { it.name.startsWith("garbage-version.db.corrupt-") }
        assertEquals(1, aside.size, "moved-aside copy in $dir")
    }

    @Test
    fun readUserVersionReadsThePragma() = runTest(timeout = 10.minutes) {
        val (dir, _) = host()
        val path = "$dir/versioned.db"
        openSqlite(path).use { it.execSQL("PRAGMA user_version = 7") }
        assertEquals(7, readUserVersion(path))
    }
}
