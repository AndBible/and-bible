package net.bible.shareddata

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
import kotlinx.coroutines.runBlocking
import net.bible.android.database.SettingsDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import net.bible.service.db.TestSqliteDurability
import net.bible.service.db.openSqlite
import net.bible.service.db.queryLong
import net.bible.service.db.sqliteConnectionDecoratorForTest
import net.bible.service.db.sqliteDriverFactory
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class TestSqliteDurabilityTest {
    @Test fun rawTestConnectionsDisableFsyncOnEveryOpen() {
        val path = "${testDatabaseDir()}/raw.sqlite3"
        repeat(2) {
            openSqlite(path).use { connection ->
                assertEquals(0L, connection.queryLong("PRAGMA synchronous"))
            }
        }
    }

    @Test fun roomTestConnectionsStayNonDurableAfterRoomConfiguresThem() = runBlocking {
        val dir = testDatabaseDir()
        repeat(2) {
            val db = openTestDatabase<SettingsDatabase>(dir, "room.sqlite3")
            try {
                db.useWriterConnection { connection ->
                    assertEquals(0L, connection.usePrepared("PRAGMA synchronous") {
                        it.step()
                        it.getLong(0)
                    })
                }
            } finally {
                db.close()
            }
        }
    }

    @Test fun walReaderAndWriterConnectionsAlsoSkipFsync() = runBlocking {
        val dir = testDatabaseDir()
        val db = Room.databaseBuilder<SettingsDatabase>(name = "$dir/wal.sqlite3")
            .setDriver(sqliteDriverFactory())
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()
        try {
            db.useWriterConnection { connection ->
                assertEquals(0L, connection.usePrepared("PRAGMA synchronous") { it.step(); it.getLong(0) })
                assertEquals("wal", connection.usePrepared("PRAGMA journal_mode") { it.step(); it.getText(0) })
            }
            db.useReaderConnection { connection ->
                assertEquals(0L, connection.usePrepared("PRAGMA synchronous") { it.step(); it.getLong(0) })
            }
        } finally {
            db.close()
        }
    }

    @Test fun optOutRestoresDurabilityEvenAfterDirectoryRegistration() {
        val dir = testDatabaseDir()
        TestSqliteDurability.keepDurableDirectory(dir)
        openSqlite("$dir/durable.sqlite3").use {
            assertEquals(2L, it.queryLong("PRAGMA synchronous"))
        }
    }

    @Test fun nestedDurableDirectoryWinsOverRegisteredParent() {
        val dir = testDatabaseDir()
        val child = Files.createDirectory(java.nio.file.Path.of(dir, "recovery")).toString()
        TestSqliteDurability.keepDurableDirectory(child)
        openSqlite("$child/durable.sqlite3").use {
            assertEquals(2L, it.queryLong("PRAGMA synchronous"))
        }
        openSqlite("$dir/ordinary.sqlite3").use {
            assertEquals(0L, it.queryLong("PRAGMA synchronous"))
        }
    }

    @Test fun readOnlyOpensStillRejectWrites() {
        val path = "${testDatabaseDir()}/read-only.sqlite3"
        openSqlite(path).use { it.execSQL("CREATE TABLE t(value)") }
        openSqlite(path, readOnly = true).use {
            assertEquals(2L, it.queryLong("PRAGMA synchronous"))
            assertFailsWith<androidx.sqlite.SQLiteException> { it.execSQL("INSERT INTO t VALUES(1)") }
        }
    }

    @Test fun readOnlyProbeCanOpenWhileAWriterOwnsTheSchemaLock() {
        val path = "${testDatabaseDir()}/locked.sqlite3"
        openSqlite(path).use { writer ->
            writer.execSQL("CREATE TABLE t(value)")
            writer.execSQL("BEGIN EXCLUSIVE")
            try {
                // Opening a read-only probe must not introduce a PRAGMA requiring the writer's lock.
                openSqlite(path, readOnly = true).close()
            } finally {
                writer.execSQL("ROLLBACK")
            }
        }
    }

    @Test fun uninstalledSeamReturnsTheProductionBundledDriver() {
        val previous = sqliteConnectionDecoratorForTest
        try {
            sqliteConnectionDecoratorForTest = null
            val driver = assertIs<BundledSQLiteDriver>(sqliteDriverFactory())
            driver.open(":memory:").use { assertEquals(2L, it.queryLong("PRAGMA synchronous")) }
        } finally {
            sqliteConnectionDecoratorForTest = previous
        }
    }

    @Test fun productionFactoryKeepsDurabilityOutsideTestDirectories() = runBlocking {
        // Install the test decorator for another path first: this must not weaken unrelated databases.
        testDatabaseDir()
        val dir = Files.createTempDirectory("production-sqlite-").toFile()
        try {
            openSqlite("$dir/database.sqlite3").use { connection ->
                assertEquals(2L, connection.queryLong("PRAGMA synchronous"))
            }
            val db = openTestDatabase<SettingsDatabase>(dir.path, "production-room.sqlite3")
            try {
                db.useWriterConnection { connection ->
                    assertEquals(2L, connection.usePrepared("PRAGMA synchronous") { it.step(); it.getLong(0) })
                }
            } finally {
                db.close()
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
