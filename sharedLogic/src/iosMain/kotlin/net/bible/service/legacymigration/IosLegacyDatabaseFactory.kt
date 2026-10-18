package net.bible.service.legacymigration

import androidx.room3.Room
import androidx.room3.RoomDatabase
import net.bible.android.database.AiSettingsDatabase
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.ReadingPlanDatabase
import net.bible.android.database.RepoDatabase
import net.bible.android.database.SettingsDatabase
import net.bible.android.database.WorkspaceDatabase
import net.bible.android.database.mydocument.MyDocumentDatabase
import net.bible.android.database.progress.ProgressDatabase
import net.bible.service.db.productionConfig
import net.bible.service.legacymigration.stage.LegacyDatabaseFactory

/** iOS [LegacyDatabaseFactory]: fresh files built with the production configuration (bundled driver, TRUNCATE). */
class IosLegacyDatabaseFactory : LegacyDatabaseFactory {
    private inline fun <reified T : RoomDatabase> build(path: String): T =
        Room.databaseBuilder<T>(name = path).productionConfig().build()

    override fun bookmarks(path: String) = build<BookmarkDatabase>(path)
    override fun workspaces(path: String) = build<WorkspaceDatabase>(path)
    override fun readingPlans(path: String) = build<ReadingPlanDatabase>(path)
    override fun myDocuments(path: String) = build<MyDocumentDatabase>(path)
    override fun progress(path: String) = build<ProgressDatabase>(path)
    override fun aiSettings(path: String) = build<AiSettingsDatabase>(path)
    override fun settings(path: String) = build<SettingsDatabase>(path)
    override fun repositories(path: String) = build<RepoDatabase>(path)
}
