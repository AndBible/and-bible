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

        val container = openAskingOnCorruption(
            open = { DatabaseContainer.create() },
            ask = {
                asks++
                // The user restored a backup from Backup & Restore (here: the damaged file replaced by a healthy one)
                // and then picked reset anyway: the healthy file is not moved aside.
                fs.delete(bookmarks.toPath())
                CorruptDatabaseChoice.RESET
            },
        )
        try {
            assertEquals(1, asks)
            assertTrue(corruptCopies(dir, BookmarkDatabase.dbFileName).isEmpty())
        } finally {
            container?.closeOwned()
        }
    }

    @Test
    fun openAskingOnCorruptionLetsOtherFailuresThrough() = runTest(timeout = 10.minutes) {
        assertFailsWith<IllegalStateException> {
            openAskingOnCorruption<Unit>(open = { error("not corruption") }, ask = { error("must not ask") })
        }
    }
}
