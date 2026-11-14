/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.service.db

import androidx.sqlite.execSQL
import kotlinx.coroutines.test.runTest
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.WorkspaceDatabase
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * #4112: a damaged user-data database at startup must surface as [CorruptDatabasesException] (the hosts show the
 * error with report, Backup & Restore and reset choices) instead of being moved aside and silently recreated empty.
 * Resetting is the user's explicit choice and keeps the damaged bytes as `<name>.corrupt-<timestamp>`.
 */
class CorruptDatabaseStartupCommonTest {
    private var previousHost: DatabaseHost? = null

    @BeforeTest
    fun rememberHost() { previousHost = try { DatabaseContainer.host } catch (_: IllegalStateException) { null } }

    @AfterTest
    fun restoreHost() { previousHost?.let { DatabaseContainer.installHost(it) } }

    private val garbage = "this is definitely not a sqlite database ".repeat(100)
    private val fs = FileSystem.SYSTEM

    private fun host(): Pair<String, FakeDatabaseHost> {
        val dir = newTestDir(durable = true)
        return dir to FakeDatabaseHost(dir).also { DatabaseContainer.installHost(it) }
    }

    private fun writeGarbage(path: String) {
        path.toPath().parent?.let { fs.createDirectories(it) }
        fs.write(path.toPath()) { writeUtf8(garbage) }
    }

    private fun corruptCopies(dir: String, name: String) =
        fs.list(dir.toPath()).filter { Regex(Regex.escape(name) + "\\.corrupt-[0-9-]+").matches(it.name) }

    private suspend fun DatabaseContainer.closeOwned() = DatabaseContainer.aiLifetime.replace { closeAllOwned(it) }

    @Test
    fun aCorruptUserDatabaseAtStartupIsReportedAndLeftUntouched() = runTest(timeout = 10.minutes) {
        val (dir, host) = host()
        val bookmarks = host.databasePath(BookmarkDatabase.dbFileName)
        writeGarbage(bookmarks)

        val error = assertFailsWith<CorruptDatabasesException> { DatabaseContainer.create() }

        assertEquals(listOf(BookmarkDatabase.dbFileName), error.databases.map { it.fileName })
        assertEquals(bookmarks, error.databases.single().path)
        assertEquals(garbage, fs.read(bookmarks.toPath()) { readUtf8() }, "the damaged file stays in place, unchanged")
        assertTrue(corruptCopies(dir, BookmarkDatabase.dbFileName).isEmpty(), "nothing is moved before the user decides")
        assertSame(error, (RuntimeException("wrapped", error) as Throwable).corruptDatabasesOrNull())
    }

    @Test
    fun everyCorruptUserDatabaseIsReportedAtOnce() = runTest(timeout = 10.minutes) {
        val (_, host) = host()
        writeGarbage(host.databasePath(BookmarkDatabase.dbFileName))
        writeGarbage(host.databasePath(WorkspaceDatabase.dbFileName))

        val error = assertFailsWith<CorruptDatabasesException> { DatabaseContainer.create() }

        assertEquals(
            setOf(BookmarkDatabase.dbFileName, WorkspaceDatabase.dbFileName),
            error.databases.map { it.fileName }.toSet(),
        )
    }

    @Test
    fun resettingKeepsTheDamagedFileAndTheNextStartOpensAnEmptyDatabase() = runTest(timeout = 10.minutes) {
        val (dir, host) = host()
        // A healthy database with data, which the reset of another database must not touch.
        DatabaseContainer.create().also {
            it.settingsStore.setString("survivor", "kept")
            it.settingsStore.flush()
            it.closeOwned()
        }
        val bookmarks = host.databasePath(BookmarkDatabase.dbFileName)
        writeGarbage(bookmarks)
        val error = assertFailsWith<CorruptDatabasesException> { DatabaseContainer.create() }

        val moved = DatabaseContainer.resetCorruptDatabases(error.databases)

        val copy = corruptCopies(dir, BookmarkDatabase.dbFileName).single()
        assertEquals(listOf(copy.toString()), moved)
        assertEquals(garbage, fs.read(copy) { readUtf8() }, "the damaged bytes are kept")
        assertFalse(fs.exists(bookmarks.toPath()), "the damaged file is no longer at the database path")
        val container = DatabaseContainer.create()
        try {
            assertEquals(0, container.bookmarkDb.bookmarkDao().allBookmarks().size)
            assertEquals("kept", container.settingsStore.getString("survivor", null))
        } finally {
            container.closeOwned()
        }
    }

    @Test
    fun aSecondResetDoesNotOverwriteAnEarlierDamagedCopy() = runTest(timeout = 10.minutes) {
        val (dir, host) = host()
        val bookmarks = host.databasePath(BookmarkDatabase.dbFileName)
        repeat(2) {
            writeGarbage(bookmarks)
            val error = assertFailsWith<CorruptDatabasesException> { DatabaseContainer.create() }
            DatabaseContainer.resetCorruptDatabases(error.databases)
        }
        assertEquals(2, corruptCopies(dir, BookmarkDatabase.dbFileName).size)
    }

    @Test
    fun resettingADatabaseThatIsHealthyAgainLeavesItAlone() = runTest(timeout = 10.minutes) {
        val (dir, host) = host()
        val bookmarks = host.databasePath(BookmarkDatabase.dbFileName)
        writeGarbage(bookmarks)
        val error = assertFailsWith<CorruptDatabasesException> { DatabaseContainer.create() }
        // E.g. the user restored a backup from the error screen before choosing reset.
        fs.delete(bookmarks.toPath())
        openSqlite(bookmarks).use { it.execSQL("CREATE TABLE t(a)") }

        assertEquals(emptyList(), DatabaseContainer.resetCorruptDatabases(error.databases))
        assertTrue(fs.exists(bookmarks.toPath()))
        assertTrue(corruptCopies(dir, BookmarkDatabase.dbFileName).isEmpty())
    }

    @Test
    fun aCorruptCacheDatabaseIsStillRecreatedWithoutAsking() = runTest(timeout = 10.minutes) {
        val (dir, host) = host()
        writeGarbage(host.databasePath("temporary.sqlite3"))

        val container = DatabaseContainer.create()
        try {
            assertEquals(0L, container.downloadDocumentsDb.documentSearchDao().count())
        } finally {
            container.closeOwned()
        }
        assertEquals(1, corruptCopies(dir, "temporary.sqlite3").size)
    }

    @Test
    fun openAskingOnCorruptionResetsOnlyWhenTheUserChoosesItAndThenOpens() = runTest(timeout = 10.minutes) {
        val (dir, host) = host()
        val bookmarks = host.databasePath(BookmarkDatabase.dbFileName)
        writeGarbage(bookmarks)
        val asked = mutableListOf<List<String>>()

        val container = openAskingOnCorruption(
            open = { DatabaseContainer.create() },
            ask = { e ->
                asked += e.databases.map { it.fileName }
                // While the user decides, the damaged file is still in place, unchanged.
                assertEquals(garbage, fs.read(bookmarks.toPath()) { readUtf8() })
                CorruptDatabaseChoice.RESET
            },
        )

        try {
            assertEquals(listOf(listOf(BookmarkDatabase.dbFileName)), asked, "asked once, then the retry opened")
            assertEquals(0, container!!.bookmarkDb.bookmarkDao().allBookmarks().size)
            assertEquals(garbage, fs.read(corruptCopies(dir, BookmarkDatabase.dbFileName).single()) { readUtf8() })
        } finally {
            container?.closeOwned()
        }
    }

    @Test
    fun openAskingOnCorruptionClosingChangesNothing() = runTest(timeout = 10.minutes) {
        val (dir, host) = host()
        val bookmarks = host.databasePath(BookmarkDatabase.dbFileName)
        writeGarbage(bookmarks)

        val container = openAskingOnCorruption(open = { DatabaseContainer.create() }, ask = { CorruptDatabaseChoice.CLOSE })

        assertEquals(null, container)
        assertEquals(garbage, fs.read(bookmarks.toPath()) { readUtf8() })
        assertTrue(corruptCopies(dir, BookmarkDatabase.dbFileName).isEmpty())
    }

    @Test
    fun openAskingOnCorruptionRetriesWhenTheUserRestoredABackupBeforeResetting() = runTest(timeout = 10.minutes) {
        val (dir, host) = host()
        val bookmarks = host.databasePath(BookmarkDatabase.dbFileName)
        writeGarbage(bookmarks)
        var asks = 0

        var resets = 0

        val container = openAskingOnCorruption(
            open = { DatabaseContainer.create() },
            ask = {
                asks++
                // The user opened Backup & Restore from the question and restored a backup there (here: the damaged
                // file replaced by a healthy one). Leaving Backup & Restore is the RETRY answer, never a reset.
                fs.delete(bookmarks.toPath())
                openSqlite(bookmarks).use { it.execSQL("PRAGMA user_version = 0") }
                CorruptDatabaseChoice.RETRY
            },
            reset = { resets++ },
        )
        try {
            assertEquals(1, asks, "the retry after Backup & Restore opened the restored data without asking again")
            assertEquals(0, resets, "returning from Backup & Restore never resets")
            assertTrue(container != null)
            assertTrue(corruptCopies(dir, BookmarkDatabase.dbFileName).isEmpty())
        } finally {
            container?.closeOwned()
        }
    }

    @Test
    fun openAskingOnCorruptionAsksAgainWhenTheRetryStillFindsDamage() = runTest(timeout = 10.minutes) {
        val (_, host) = host()
        writeGarbage(host.databasePath(BookmarkDatabase.dbFileName))
        val answers = ArrayDeque(listOf(CorruptDatabaseChoice.RETRY, CorruptDatabaseChoice.CLOSE))
        var asks = 0

        val container = openAskingOnCorruption(open = { DatabaseContainer.create() }, ask = { asks++; answers.removeFirst() })

        assertEquals(null, container)
        assertEquals(2, asks, "a backup screen left without a restore brings the question back")
    }

    /** #4112 review: a reset that cannot move the file must not loop silently on the same question. */
    @Test
    fun aFailedResetIsReportedAndTheUserIsAskedAgain() = runTest(timeout = 10.minutes) {
        val (_, host) = host()
        writeGarbage(host.databasePath(BookmarkDatabase.dbFileName))
        val failures = mutableListOf<Throwable>()
        val answers = ArrayDeque(listOf(CorruptDatabaseChoice.RESET, CorruptDatabaseChoice.CLOSE))

        val container = openAskingOnCorruption(
            open = { DatabaseContainer.create() },
            ask = { answers.removeFirst() },
            reset = { throw CorruptDatabaseResetException(it.first(), null) },
            resetFailed = { failures += it },
        )

        assertEquals(null, container)
        assertEquals(1, failures.size)
        assertIs<CorruptDatabaseResetException>(failures.single())
        assertTrue(answers.isEmpty(), "asked again after the failed reset")
    }

    @Test
    fun resetCorruptDatabasesThrowsWhenTheDamagedFileCannotBeMoved() = runTest(timeout = 10.minutes) {
        val (dir, host) = host()
        val bookmarks = host.databasePath(BookmarkDatabase.dbFileName)
        writeGarbage(bookmarks)
        val error = assertFailsWith<CorruptDatabasesException> { DatabaseContainer.create() }

        val failure = assertFailsWith<CorruptDatabaseResetException> {
            DatabaseContainer.resetCorruptDatabases(error.databases, moveAside = { null })
        }

        assertEquals(BookmarkDatabase.dbFileName, failure.database.fileName)
        assertEquals(garbage, fs.read(bookmarks.toPath()) { readUtf8() })
        assertTrue(corruptCopies(dir, BookmarkDatabase.dbFileName).isEmpty())
    }

    /**
     * #4112 review: corruption that the schema read cannot see (a damaged table page) is found later in `create()`;
     * it must reach the same recovery as [CorruptDatabasesException], and the reset must then move that file aside.
     */
    @Test
    fun corruptionFoundLaterInCreateIsReportedAsDamagedDataAndCanBeReset() = runTest(timeout = 10.minutes) {
        val (dir, host) = host()
        DatabaseContainer.create().also {
            it.settingsStore.setString("lost", "value")
            it.settingsStore.flush()
            it.closeOwned()
        }
        val settings = host.databasePath(net.bible.android.database.SettingsDatabase.dbFileName)
        damageTablePage(settings, "room_master_table")
        assertEquals(null, confirmedCorruption(settings), "precondition: the early schema read does not see this damage")

        val error = assertFailsWith<CorruptDatabasesException> { DatabaseContainer.create() }

        assertEquals(listOf(net.bible.android.database.SettingsDatabase.dbFileName), error.databases.map { it.fileName })
        assertTrue(corruptCopies(dir, net.bible.android.database.SettingsDatabase.dbFileName).isEmpty())
        DatabaseContainer.resetCorruptDatabases(error.databases)
        assertEquals(1, corruptCopies(dir, net.bible.android.database.SettingsDatabase.dbFileName).size)
        DatabaseContainer.create().closeOwned()
    }

    /** #4112 review: a downloaded sync file is checked before it replaces the local database. */
    @Test
    fun requireIntactDatabaseAcceptsOnlyAWholeDatabase() = runTest(timeout = 10.minutes) {
        val (dir, _) = host()
        val healthy = "$dir/download-ok.sqlite3"
        openSqlite(healthy).use {
            it.execSQL("CREATE TABLE t(a)")
            repeat(50) { _ -> it.execSQL("INSERT INTO t VALUES(randomblob(500))") }
        }
        requireIntactDatabase(healthy)

        val garbageFile = "$dir/download-garbage.sqlite3"
        writeGarbage(garbageFile)
        assertFailsWith<DamagedDatabaseFileException> { requireIntactDatabase(garbageFile) }

        damageTablePage(healthy, "t")
        assertFailsWith<DamagedDatabaseFileException> { requireIntactDatabase(healthy) }
    }

    /** Overwrites the root page of [table] in [path] with garbage, leaving the schema (`sqlite_master`) readable. */
    private fun damageTablePage(path: String, table: String) {
        val (pageSize, rootPage) = openSqlite(path).use {
            it.queryLong("PRAGMA page_size")!! to it.queryLong("SELECT rootpage FROM sqlite_master WHERE name = ?", table)!!
        }
        val bytes = fs.read(path.toPath()) { readByteArray() }
        val start = ((rootPage - 1) * pageSize).toInt()
        for (i in start until start + pageSize.toInt()) bytes[i] = 0x5A
        fs.write(path.toPath()) { write(bytes) }
    }

    /** #4112 review: a cold process without the UI (widget, media button) must not rescan up to 18 files per access. */
    @Test
    fun aFoundCorruptionIsRememberedUntilTheUserActs() = runTest(timeout = 10.minutes) {
        host()
        DatabaseContainer.detachInstanceForTest()?.closeOwned()
        val wasReady = DatabaseContainer.ready
        var opens = 0
        val damaged = CorruptDatabasesException(listOf(CorruptDatabase("bookmarks.sqlite3", "/x/bookmarks.sqlite3")))
        DatabaseContainer.containerFactory = { opens++; throw damaged }
        DatabaseContainer.ready = true
        try {
            repeat(3) { assertSame(damaged, assertFailsWith<CorruptDatabasesException> { DatabaseContainer.instance }) }
            assertEquals(1, opens, "the damage is remembered: no reopen per access")

            DatabaseContainer.forgetCorruption()
            assertFailsWith<CorruptDatabasesException> { DatabaseContainer.instance }
            assertEquals(2, opens, "a retry looks again")

            DatabaseContainer.resetCorruptDatabases(emptyList())
            assertFailsWith<CorruptDatabasesException> { DatabaseContainer.instance }
            assertEquals(3, opens, "a reset looks again")
        } finally {
            DatabaseContainer.ready = wasReady
            DatabaseContainer.forgetReplacesForTest()
        }
    }

    @Test
    fun openAskingOnCorruptionLooksAgainOnEveryRetry() = runTest(timeout = 10.minutes) {
        host()
        DatabaseContainer.detachInstanceForTest()?.closeOwned()
        val wasReady = DatabaseContainer.ready
        var opens = 0
        val damaged = CorruptDatabasesException(listOf(CorruptDatabase("bookmarks.sqlite3", "/x/bookmarks.sqlite3")))
        DatabaseContainer.containerFactory = { opens++; if (opens < 3) throw damaged else error("opened") }
        DatabaseContainer.ready = true
        val answers = ArrayDeque(listOf(CorruptDatabaseChoice.RETRY, CorruptDatabaseChoice.RETRY))
        try {
            assertFailsWith<IllegalStateException> {
                openAskingOnCorruption(open = { DatabaseContainer.instance }, ask = { answers.removeFirst() })
            }
            assertEquals(3, opens)
        } finally {
            DatabaseContainer.ready = wasReady
            DatabaseContainer.forgetReplacesForTest()
        }
    }

    @Test
    fun openAskingOnCorruptionLetsOtherFailuresThrough() = runTest(timeout = 10.minutes) {
        assertFailsWith<IllegalStateException> {
            openAskingOnCorruption<Unit>(open = { error("not corruption") }, ask = { error("must not ask") })
        }
    }

    /** Makes index `i` of a new table `t` in [path] disagree with its table: `quick_check` says ok, writes fail. */
    private fun mismatchIndex(path: String) {
        openSqlite(path).use {
            it.execSQL("CREATE TABLE t(a)")
            it.execSQL("CREATE INDEX i ON t(a)")
            it.execSQL("WITH RECURSIVE c(x) AS (SELECT 1 UNION ALL SELECT x + 1 FROM c WHERE x < 200) INSERT INTO t SELECT x FROM c")
            it.execSQL("PRAGMA writable_schema = ON")
            it.execSQL("UPDATE sqlite_master SET sql = 'CREATE INDEX i ON t(a + 1)' WHERE name = 'i'")
        }
    }

    /** #4112 review 2: an index that disagrees with its table is damage too, though `quick_check` cannot see it. */
    @Test
    fun anIndexThatDisagreesWithItsTableIsDamageAndCanBeReset() = runTest(timeout = 10.minutes) {
        val (dir, host) = host()
        val bookmarks = host.databasePath(BookmarkDatabase.dbFileName)
        mismatchIndex(bookmarks)
        openSqlite(bookmarks).use {
            assertEquals("ok", it.prepare("PRAGMA quick_check").use { st -> st.step(); st.getText(0) }, "precondition")
            val write = assertFailsWith<Exception> { it.execSQL("DELETE FROM t WHERE a = 5") }
            assertTrue(isCorruptionError(write), "precondition: a write meets the damage as SQLITE_CORRUPT")
        }

        assertTrue(isDamagedDatabase(bookmarks))
        DatabaseContainer.resetCorruptDatabases(listOf(CorruptDatabase(BookmarkDatabase.dbFileName, bookmarks)))
        assertEquals(1, corruptCopies(dir, BookmarkDatabase.dbFileName).size)
    }

    /**
     * #4112 review 2: corruption in `create()` that no check can pin on a file still reaches the recovery: every
     * existing user-data file is named as a suspect, and the user's reset moves the suspects aside (kept).
     */
    @Test
    fun corruptionNoCheckCanPinNamesTheUserDatabasesAsSuspectsAndTheResetMovesThem() = runTest(timeout = 10.minutes) {
        val (dir, host) = host()
        DatabaseContainer.create().closeOwned()
        val corrupt = Exception("Error code: 11, message: database disk image is malformed")

        val error = DatabaseContainer.damageFoundLater(corrupt, isDamaged = { false })!!

        val existing = USER_DATA_DB_FILENAMES.filter { fs.exists(host.databasePath(it).toPath()) }.toSet()
        assertTrue(existing.isNotEmpty())
        assertEquals(existing, error.databases.map { it.fileName }.toSet())
        assertTrue(error.databases.none { it.confirmed })
        assertSame(corrupt, error.cause)

        DatabaseContainer.resetCorruptDatabases(error.databases)
        for (name in existing) assertEquals(1, corruptCopies(dir, name).size, name)
        assertEquals(null, DatabaseContainer.damageFoundLater(Exception("not corruption"), isDamaged = { false }))
    }

    /** #4112 review 2: a restore through Backup & Restore replaces the files, so the remembered damage is forgotten. */
    @Test
    fun aRestoreForgetsARememberedCorruption() = runTest(timeout = 10.minutes) {
        host()
        DatabaseContainer.detachInstanceForTest()?.closeOwned()
        val wasReady = DatabaseContainer.ready
        var opens = 0
        val damaged = CorruptDatabasesException(listOf(CorruptDatabase("bookmarks.sqlite3", "/x/bookmarks.sqlite3")))
        DatabaseContainer.containerFactory = { opens++; throw damaged }
        DatabaseContainer.ready = true
        try {
            assertFailsWith<CorruptDatabasesException> { DatabaseContainer.instance }
            DatabaseContainer.replacingDatabases { }
            assertFailsWith<CorruptDatabasesException> { DatabaseContainer.instance }
            assertEquals(2, opens, "the restored files are looked at again")
        } finally {
            DatabaseContainer.ready = wasReady
            DatabaseContainer.forgetReplacesForTest()
        }
    }

    /** #4112 review 2: a failed open leaves the databases "not ready"; it never clears an open that succeeded. */
    @Test
    fun openForUseClearsReadyOnlyWhileNothingIsOpen() = runTest(timeout = 10.minutes) {
        host()
        DatabaseContainer.detachInstanceForTest()?.closeOwned()
        val wasReady = DatabaseContainer.ready
        val damaged = CorruptDatabasesException(listOf(CorruptDatabase("bookmarks.sqlite3", "/x/bookmarks.sqlite3")))
        DatabaseContainer.containerFactory = { throw damaged }
        try {
            assertFailsWith<CorruptDatabasesException> { DatabaseContainer.openForUse() }
            assertFalse(DatabaseContainer.ready)
        } finally {
            DatabaseContainer.ready = wasReady
            DatabaseContainer.forgetReplacesForTest()
        }
    }
}
