package net.bible.service.legacymigration.stage

import net.bible.android.database.AiSettingsDatabase
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.ReadingPlanDatabase
import net.bible.android.database.RepoDatabase
import net.bible.android.database.SettingsDatabase
import net.bible.android.database.WorkspaceDatabase
import net.bible.android.database.mydocument.MyDocumentDatabase
import net.bible.android.database.progress.ProgressDatabase

/**
 * Opens a brand-new Room database file of the current schema at [path] for each database the migration fills.
 *
 * The migration never writes hand-made DDL of an old schema: every staged file is created by Room itself, so it is
 * valid for the app's own builders. Implementations must build the database with the bundled SQLite driver and
 * `JournalMode.TRUNCATE` (the production configuration is `productionConfig()`), so that a closed staging database
 * is a single self-contained file. Per-platform implementations live next to the platform glue; the JVM unit tests
 * use their own.
 */
interface LegacyDatabaseFactory {
    fun bookmarks(path: String): BookmarkDatabase
    fun workspaces(path: String): WorkspaceDatabase
    fun readingPlans(path: String): ReadingPlanDatabase
    fun myDocuments(path: String): MyDocumentDatabase
    fun progress(path: String): ProgressDatabase
    fun aiSettings(path: String): AiSettingsDatabase
    fun settings(path: String): SettingsDatabase
    fun repositories(path: String): RepoDatabase
}
