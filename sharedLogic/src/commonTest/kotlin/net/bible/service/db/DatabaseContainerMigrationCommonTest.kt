package net.bible.service.db

import kotlinx.coroutines.test.runTest
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.migrations.BOOKMARK_DATABASE_VERSION
import net.bible.shareddata.fixtures.createFromExport
import net.bible.shareddata.fixtures.exportedIdentityHash
import net.bible.shareddata.fixtures.exportedVersions
import net.bible.shareddata.fixtures.storedIdentityHash
import net.bible.shareddata.fixtures.userVersion
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class DatabaseContainerMigrationCommonTest {
    private var previousHost: DatabaseHost? = null

    @BeforeTest
    fun rememberHost() { previousHost = try { DatabaseContainer.host } catch (_: IllegalStateException) { null } }

    /** [DatabaseContainer.installHost] is global; put back whatever was installed before the test (if anything). */
    @AfterTest
    fun restoreHost() { previousHost?.let { DatabaseContainer.installHost(it) } }

    @Test
    fun anOldBookmarkDatabaseIsMigratedOnOpen() = runTest(timeout = 10.minutes) {
        val fqn = "net.bible.android.database.BookmarkDatabase"
        val dir = newTestDir()
        val oldVersion = exportedVersions(fqn).first()
        assertTrue(oldVersion < BOOKMARK_DATABASE_VERSION, "the oldest export must predate the current version")
        createFromExport(fqn, oldVersion, "$dir/${BookmarkDatabase.dbFileName}")
        DatabaseContainer.installHost(FakeDatabaseHost(dir))

        val container = DatabaseContainer.create()
        try {
            assertEquals(BOOKMARK_DATABASE_VERSION, container.bookmarkDb.userVersion())
            assertEquals(exportedIdentityHash(fqn, BOOKMARK_DATABASE_VERSION), container.bookmarkDb.storedIdentityHash())
        } finally {
            DatabaseContainer.aiLifetime.replace { container.closeAllOwned(it) }
        }
    }
}
