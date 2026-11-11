package net.bible.service.legacymigration

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class LegacyMigrationMarkerTest {
    private val fs = FakeFileSystem()
    private val dir = "/support/LegacyMigration".toPath()

    private fun marker(json: String?) {
        fs.createDirectories(dir)
        if (json != null) fs.write(dir / "done.json") { writeUtf8(json) }
    }

    @Test
    fun migratedMarkerFromEarlierRunCountsAsUpgrade() {
        // An older run wrote no `nothingToMigrate` flag at all: data was migrated, so this is an upgrade.
        marker("""{"attempt":1,"adopted":[]}""")
        assertTrue(LegacyMigrator.migratedFromLegacyApp(fs, dir))
    }

    @Test
    fun explicitlyMigratedMarkerCountsAsUpgrade() {
        marker("""{"nothingToMigrate":false,"attempt":1}""")
        assertTrue(LegacyMigrator.migratedFromLegacyApp(fs, dir))
    }

    @Test
    fun nothingToMigrateMarkerIsFreshInstall() {
        marker("""{"nothingToMigrate":true,"attempt":0}""")
        assertFalse(LegacyMigrator.migratedFromLegacyApp(fs, dir))
    }

    @Test
    fun noMarkerIsNotUpgrade() {
        marker(null)
        assertFalse(LegacyMigrator.migratedFromLegacyApp(fs, dir))
        assertFalse(LegacyMigrator.migratedFromLegacyApp(fs, "/missing".toPath()))
    }

    @Test
    fun unreadableMarkerIsNotUpgrade() {
        marker("not json")
        assertFalse(LegacyMigrator.migratedFromLegacyApp(fs, dir))
    }
}
