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
import org.junit.Assert.assertTrue
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

    @Test
    fun everyExportedVersionMigratesToCurrent() {
        val failures = mutableListOf<String>()
        var tested = 0
        for (db in DB_UNDER_TEST) {
            val skip = knownUnmigratable[db.schemaDir].orEmpty() + NEEDS_MODERN_SQLITE[db.schemaDir].orEmpty()
            for (v in SchemaExportFixtures.exportedVersions(db.schemaDir).filter { it < db.currentVersion && it !in skip }) {
                val name = "chain-${db.schemaDir}-$v.sqlite3"
                application.deleteDatabase(name)
                SchemaExportFixtures.createFromExport(db.schemaDir, v, application.getDatabasePath(name))
                try {
                    db.open(name).also { it.identityHash() }.close()  // opening the file runs the migration chain
                    tested++
                } catch (e: Throwable) {
                    failures += "${db.schemaDir} v$v: ${e::class.simpleName}: ${e.message?.take(200)}"
                }
            }
        }
        println("MigrationChainTest: $tested old versions migrated, ${failures.size} failed")
        assertEquals(failures.joinToString("\n"), 0, failures.size)
        // Not vacuous: the count is derived from the data, not from the loop above. Every exported version below
        // current is tested except the known-unmigratable and the needs-modern-SQLite ones (an empty
        // NEEDS_MODERN_SQLITE after Task 17 simply subtracts nothing).
        val expected = DB_UNDER_TEST.sumOf { db ->
            val below = SchemaExportFixtures.exportedVersions(db.schemaDir).filter { it < db.currentVersion }.toSet()
            val excluded = knownUnmigratable[db.schemaDir].orEmpty() + NEEDS_MODERN_SQLITE[db.schemaDir].orEmpty()
            below.size - below.count { it in excluded }
        }
        assertTrue("no old schema version was tested", tested > 0)
        assertEquals("tested count differs from the exported versions that should have been tested", expected, tested)
    }
}
