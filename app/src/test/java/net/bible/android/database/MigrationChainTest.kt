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

package net.bible.android.database

import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Builds a database file from every committed older schema export and opens it through the
 * production builder, so the whole migration chain up to the current version must succeed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class MigrationChainTest {
    /** Exported versions that have no migration path, keyed by schemaDir, each with its reason. */
    private val knownUnmigratable: Map<String, Set<Int>> = mapOf(
        // Version 17 never shipped: commit 7f548fab9 took the database from 16 straight to 18, so the
        // 17.json export (with the since-renamed builtInPromptCategories column) is a dev-only state.
        "net.bible.android.database.AiSettingsDatabase" to setOf(17),
    )

    /**
     * Versions whose migration chain runs `ALTER TABLE ... DROP COLUMN`. Production opens the databases
     * with requery SQLite 3.49 (`dbFactory`); under Robolectric the framework SQLite predates 3.35 and
     * rejects the syntax, so these cannot be exercised here until the tests run on the bundled driver
     * (D1 Tasks 16-17). Remove this set then.
     */
    private val needsModernSqlite: Map<String, Set<Int>> = mapOf(
        "net.bible.android.database.BookmarkDatabase" to (1..10).toSet(),
        "net.bible.android.database.WorkspaceDatabase" to setOf(1, 2),
    )

    @Test
    fun everyExportedVersionMigratesToCurrent() {
        val failures = mutableListOf<String>()
        var tested = 0
        for (db in DB_UNDER_TEST) {
            val skip = knownUnmigratable[db.schemaDir].orEmpty() + needsModernSqlite[db.schemaDir].orEmpty()
            for (v in SchemaExportFixtures.exportedVersions(db.schemaDir).filter { it < db.currentVersion && it !in skip }) {
                val name = "chain-${db.schemaDir}-$v.sqlite3"
                application.deleteDatabase(name)
                SchemaExportFixtures.createFromExport(db.schemaDir, v, application.getDatabasePath(name))
                try {
                    db.open(name).also { it.openHelper.writableDatabase }.close()  // Task 16: useWriterConnection
                    tested++
                } catch (e: Throwable) {
                    failures += "${db.schemaDir} v$v: ${e::class.simpleName}: ${e.message?.take(200)}"
                }
            }
        }
        println("MigrationChainTest: $tested old versions migrated, ${failures.size} failed")
        assertEquals(failures.joinToString("\n"), 0, failures.size)
    }
}
