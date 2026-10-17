package net.bible.shareddata

import androidx.room3.RoomDatabase
import kotlinx.coroutines.test.runTest
import net.bible.shareddata.fixtures.CURRENT_DATABASES
import net.bible.shareddata.fixtures.CURRENT_DB_SPECS
import net.bible.shareddata.fixtures.createFromExport
import net.bible.shareddata.fixtures.exportedIdentityHash
import net.bible.shareddata.fixtures.exportedVersions
import net.bible.shareddata.fixtures.schemaShape
import net.bible.shareddata.fixtures.storedIdentityHash
import net.bible.shareddata.fixtures.userVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Builds a database file from every committed schema export of the 11 current databases and opens it through
 * Room with that database's production migrations array. Room's own identity check runs on open, so an open that
 * does not throw means the migration chain produced exactly the current schema (or, at the current version,
 * that an existing user's file opens without any migration).
 */
class MigrationChainCommonTest {
    /** Exported versions that have no migration path, each with its reason. */
    private val knownUnmigratable: Map<String, Set<Int>> = mapOf(
        // Version 17 never shipped: the database went from 16 straight to 18, so the 17.json export
        // (with the since-renamed builtInPromptCategories column) is a dev-only state.
        "net.bible.android.database.AiSettingsDatabase" to setOf(17),
    )

    @Test
    // Native runs the 76-version chain much slower than the JVM; the default 60 s wall-clock timeout would fail it on CI.
    fun everyExportedVersionOpensAtCurrent() = runTest(timeout = 10.minutes) {
        val dir = testDatabaseDir()
        val failures = mutableListOf<String>()
        var tested = 0
        var expected = 0
        val perDb = mutableMapOf<String, Int>()
        for (spec in CURRENT_DB_SPECS) {
            val skip = knownUnmigratable[spec.fqn].orEmpty()
            val versions = exportedVersions(spec.fqn).filter { it <= spec.currentVersion && it !in skip }
            assertTrue(spec.currentVersion in versions, "${spec.fqn}: current version ${spec.currentVersion} is not exported")
            expected += versions.size
            for (v in versions) {
                val name = "chain-${spec.fqn}-$v.sqlite3"
                createFromExport(spec.fqn, v, "$dir/$name")
                var db: RoomDatabase? = null
                try {
                    db = spec.open(dir, name)
                    assertEquals(spec.currentVersion, db.userVersion(), "${spec.fqn} v$v user_version after open")
                    assertEquals(
                        exportedIdentityHash(spec.fqn, spec.currentVersion), db.storedIdentityHash(),
                        "${spec.fqn} v$v identity hash after open",
                    )
                    // Extra safety check on top of Room's own validation: compare the migrated structure (columns,
                    // types, nullability, primary keys, index columns) with a file freshly created from the current
                    // export. Column defaults and index names are deliberately not compared (see schemaShape).
                    val reference = "reference-${spec.fqn}-$v.sqlite3"
                    createFromExport(spec.fqn, spec.currentVersion, "$dir/$reference")
                    val referenceDb = spec.open(dir, reference)
                    try {
                        val expectedShape = referenceDb.schemaShape()
                        val actualShape = db.schemaShape()
                        assertTrue(
                            expectedShape == actualShape,
                            "${spec.fqn} v$v migrated structure differs from the current export: missing ${expectedShape - actualShape}, unexpected ${actualShape - expectedShape}",
                        )
                    } finally {
                        referenceDb.close()
                    }
                    tested++
                    perDb[spec.fqn.substringAfterLast('.')] = (perDb[spec.fqn.substringAfterLast('.')] ?: 0) + 1
                } catch (e: Throwable) {
                    failures += "${spec.fqn} v$v: ${e::class.simpleName}: ${e.message?.take(600)}"
                } finally {
                    db?.close()
                }
            }
        }
        println("MigrationChainCommonTest: $tested exported versions opened at current across ${CURRENT_DB_SPECS.size} databases, ${failures.size} failed")
        println("MigrationChainCommonTest per database: $perDb")
        assertEquals(emptyList(), failures)
        assertEquals(CURRENT_DB_SPECS.size, perDb.size, "every current database must have opened at least one version")
        assertEquals(11, CURRENT_DATABASES.size)
        assertTrue(tested > 0, "no schema version was tested")
        assertEquals(expected, tested, "tested count differs from the exported versions that should have been tested")
    }

    @Test
    fun currentDatabaseListExcludesLegacy() {
        assertTrue(CURRENT_DATABASES.none { it.endsWith("OldMonolithicAppDatabase") })
        assertEquals(CURRENT_DATABASES.size, CURRENT_DATABASES.toSet().size)
    }
}
