package net.bible.service.db

import kotlinx.coroutines.test.runTest
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.service.cloudsync.SyncableDatabaseDefinition

import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class DatabaseContainerCommonTest {
    private var previousHost: DatabaseHost? = null

    @BeforeTest
    fun rememberHost() { previousHost = try { DatabaseContainer.host } catch (_: IllegalStateException) { null } }

    /** [DatabaseContainer.installHost] is global; put back whatever was installed before the test (if anything). */
    @AfterTest
    fun restoreHost() { previousHost?.let { DatabaseContainer.installHost(it) } }

    private fun host(): FakeDatabaseHost = FakeDatabaseHost(newTestDir()).also { DatabaseContainer.installHost(it) }

    private suspend fun DatabaseContainer.closeOwned() =
        DatabaseContainer.aiLifetime.replace { closeAllOwned(it) }

    @Test
    fun createOpensEveryDatabaseAndInstallsSyncTriggers() = runTest(timeout = 10.minutes) {
        val host = host()
        val container = DatabaseContainer.create()
        try {
            // Room opens lazily; touch every database so its file and user_version exist.
            for (name in ALL_DB_FILENAMES) container.dbByFilename.getValue(name).useWriterConnectionMarked { }
            for (name in ALL_DB_FILENAMES) {
                assertTrue(FileSystem.SYSTEM.exists(host.databasePath(name).toPath()), "$name exists")
            }
            val bookmarks = host.databasePath(SyncableDatabaseDefinition.BOOKMARKS.filename)
            val triggers = openSqlite(bookmarks).use { c ->
                c.queryRows("SELECT name FROM sqlite_master WHERE type = 'trigger'") { it.getText(0) }.toSet()
            }
            for (table in SyncableDatabaseDefinition.BOOKMARKS.tables) {
                for (kind in listOf("inserts", "updates", "deletes")) {
                    assertTrue("${table.tableName}_$kind" in triggers, "trigger ${table.tableName}_$kind in $triggers")
                }
            }
        } finally {
            container.closeOwned()
        }
        for (name in ALL_DB_FILENAMES) {
            assertEquals(DatabaseContainer.maxDatabaseVersion(name), readUserVersion(host.databasePath(name)), "user_version of $name")
        }
    }

    @Test
    fun aSettingWrittenAndFlushedSurvivesResetAndReopen() = runTest(timeout = 10.minutes) {
        host()
        val container = DatabaseContainer.create()
        DatabaseContainer.containerFactory = { container }
        DatabaseContainer.ready = true
        try {
            DatabaseContainer.instance.settingsStore.setString("a-key", "a value")
            DatabaseContainer.instance.settingsStore.flush()
            DatabaseContainer.aiLifetime.replace { DatabaseContainer.resetOwned(it) }
            val reopened = DatabaseContainer.create()
            try {
                assertEquals("a value", reopened.settingsStore.getString("a-key", null))
            } finally {
                reopened.closeOwned()
            }
        } finally {
            DatabaseContainer.ready = false
            DatabaseContainer.forgetReplacesForTest()
            DatabaseContainer.detachInstanceForTest()
        }
    }

    @Test
    fun aBookmarkLabelWrittenSurvivesReopen() = runTest(timeout = 10.minutes) {
        host()
        val label = BookmarkEntities.Label(name = "Persisted label")
        val first = DatabaseContainer.create()
        try { first.bookmarkDb.bookmarkDao().insert(label) } finally { first.closeOwned() }
        val second = DatabaseContainer.create()
        try {
            assertEquals("Persisted label", assertNotNull(second.bookmarkDb.bookmarkDao().labelById(label.id)).name)
        } finally {
            second.closeOwned()
        }
    }

    @Test
    fun closeAllClosesEveryDatabase() = runTest(timeout = 10.minutes) {
        host()
        val container = DatabaseContainer.create()
        container.closeOwned()
        // Every database that closeAllOwned closes (dbByFilename lists all eleven) must refuse further use.
        for ((name, db) in container.dbByFilename) {
            assertFailsWith<IllegalStateException>("$name still usable after closeAll") { db.useWriterConnectionMarked { } }
        }
    }
}
