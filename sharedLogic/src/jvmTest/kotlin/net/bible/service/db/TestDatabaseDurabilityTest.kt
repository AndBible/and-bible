package net.bible.service.db

import androidx.room3.useWriterConnection
import kotlinx.coroutines.runBlocking
import net.bible.android.database.SettingsDatabase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestDatabaseDurabilityTest {
    @Test fun fixtureDirectoriesCoverRoomAndRawConnections() = runBlocking {
        val dir = newTestDir()
        val db = buildAppDatabase<SettingsDatabase>("$dir/settings.sqlite3")
        try {
            db.useWriterConnection { assertEquals(0L, it.queryLong("PRAGMA synchronous")) }
            openSqlite("$dir/raw.sqlite3").use { assertEquals(0L, it.queryLong("PRAGMA synchronous")) }
        } finally {
            db.close()
        }
    }

    @Test fun recoveryDirectoriesKeepRoomAndRawDurability() = runBlocking {
        val dir = newTestDir(durable = true)
        val db = buildAppDatabase<SettingsDatabase>("$dir/settings.sqlite3")
        try {
            db.useWriterConnection { assertEquals(2L, it.queryLong("PRAGMA synchronous")) }
            openSqlite("$dir/raw.sqlite3").use { assertEquals(2L, it.queryLong("PRAGMA synchronous")) }
        } finally {
            db.close()
        }
    }
}
