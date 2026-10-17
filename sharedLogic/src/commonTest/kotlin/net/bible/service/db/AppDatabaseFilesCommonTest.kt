package net.bible.service.db

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
