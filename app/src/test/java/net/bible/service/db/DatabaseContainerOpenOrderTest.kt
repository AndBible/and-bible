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

import kotlinx.coroutines.test.runTest
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.SchemaExportFixtures
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The cold-open order of [DatabaseContainer.create] and the host factory behind `instance` (D2 Task 3). */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DatabaseContainerOpenOrderTest {
    private lateinit var originalHost: DatabaseHost
    private val calls = mutableListOf<String>()
    private var opened: DatabaseContainer? = null

    private fun recordingHost(notRunningTests: Boolean) = object : DatabaseHost by originalHost {
        override val isRunningTests: Boolean get() = if (notRunningTests) false else originalHost.isRunningTests
        override val dbBackupPath: String get() = originalHost.dbBackupPath.also { calls += "dbBackupPath" }
        override fun deleteDatabase(name: String): Boolean = originalHost.deleteDatabase(name).also { calls += "deleteDatabase($name)" }
        override val legacyMonolithicMigrations get() = originalHost.legacyMonolithicMigrations.also { calls += "legacyMonolithicMigrations" }
        override fun openContainer(): DatabaseContainer = originalHost.openContainer().also {
            calls += "openContainer"
            opened = it
        }
    }

    @Before fun setUp() {
        originalHost = DatabaseContainer.host
        net.bible.test.DatabaseResetter.resetDatabase()
    }

    @After fun tearDown() {
        DatabaseContainer.installHost(originalHost)
        net.bible.test.DatabaseResetter.resetDatabase()
        ALL_DB_FILENAMES.forEach { application.deleteDatabase(it) }
        application.deleteDatabase(OLD_MONOLITHIC_DATABASE_NAME)
    }

    @Test fun openOrderIsBackupThenOrphanDeleteThenLegacySplitThenTriggersThenSettingsLoad() = runTest {
        val oldFile = application.getDatabasePath(OLD_MONOLITHIC_DATABASE_NAME)
        application.deleteDatabase(OLD_MONOLITHIC_DATABASE_NAME)
        val schemaDir = "net.bible.android.database.OldMonolithicAppDatabase"
        SchemaExportFixtures.createFromExport(schemaDir, 68, oldFile) // the last real pre-split export
        openSqlite(oldFile.path).use { db ->
            db.prepare("INSERT INTO LongSetting (`key`, value) VALUES ('d2-order-key', 77)").use { it.step() }
        }
        DatabaseContainer.installHost(recordingHost(notRunningTests = true))
        val container = try { DatabaseContainer.create() } finally { DatabaseContainer.installHost(originalHost) }
        try {
            val backup = calls.indexOf("dbBackupPath")
            val orphan = calls.indexOf("deleteDatabase(cloud-documents-cache.sqlite3)")
            val legacy = calls.indexOf("legacyMonolithicMigrations")
            assertTrue("calls: $calls", backup >= 0 && backup < orphan && orphan < legacy)

            val triggers = openSqlite(application.getDatabasePath(BookmarkDatabase.dbFileName).path, readOnly = true).use {
                it.queryRows("SELECT name FROM sqlite_master WHERE type = 'trigger'") { st -> st.getText(0) }.toSet()
            }
            for (t in SyncableDatabaseDefinition.BOOKMARKS.tables) {
                assertTrue("missing ${t.tableName}_inserts in $triggers", "${t.tableName}_inserts" in triggers)
            }
            assertEquals(77L, container.settingsStore.getLong("d2-order-key", -1L))
        } finally {
            container.closeAll()
        }
    }

    @Test fun flushSettingsBeforeExitWithoutAnInstanceOpensNothing() {
        DatabaseContainer.installHost(recordingHost(notRunningTests = false))
        DatabaseContainer.ready = true
        DatabaseContainer.flushSettingsBeforeExit(100)
        assertTrue("calls: $calls", "openContainer" !in calls)
    }

    @Test fun instanceUsesTheHostFactory() {
        DatabaseContainer.forgetReplacesForTest()
        DatabaseContainer.installHost(recordingHost(notRunningTests = false))
        val first = DatabaseContainer.instance
        assertEquals(listOf("openContainer"), calls.filter { it == "openContainer" })
        assertNotNull(opened)
        assertSame(opened, first)
        assertSame(first, DatabaseContainer.instance)
        assertEquals(1, calls.count { it == "openContainer" })
    }
}
