package net.bible.service.legacymigration.stage

import androidx.room3.RoomDatabase
import net.bible.android.database.AiSettingsDatabase
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.ReadingPlanDatabase
import net.bible.android.database.RepoDatabase
import net.bible.android.database.SettingsDatabase
import net.bible.android.database.WorkspaceDatabase
import net.bible.android.database.mydocument.MyDocumentDatabase
import net.bible.android.database.progress.ProgressDatabase
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * The fresh, current-schema databases a migration run fills, in the staging directory [dir].
 *
 * Each database is created lazily on first access, under the same file name the app uses for it
 * (`bookmarks.sqlite3`, `workspaces.sqlite3`, ...), so that the adopter can move the files into the databases
 * directory unchanged. A database no mapper touched is never created and so never adopted.
 *
 * [close] closes every database that was opened and then removes the SQLite side files (`-journal`, `-wal`,
 * `-shm` and Room 3's `.lck`): with `JournalMode.TRUNCATE` a closed database holds all its data in the main file, so
 * [files] (the main files) is everything that has to be moved.
 */
class StagingDatabases(private val dir: Path, private val factory: LegacyDatabaseFactory) : AutoCloseable {
    private val opened = mutableListOf<Pair<Path, RoomDatabase>>()

    val bookmarks: BookmarkDatabase by lazy { open(BookmarkDatabase.dbFileName, factory::bookmarks) }
    val workspaces: WorkspaceDatabase by lazy { open(WorkspaceDatabase.dbFileName, factory::workspaces) }
    val readingPlans: ReadingPlanDatabase by lazy { open(ReadingPlanDatabase.dbFileName, factory::readingPlans) }
    val myDocuments: MyDocumentDatabase by lazy { open(MyDocumentDatabase.dbFileName, factory::myDocuments) }
    val progress: ProgressDatabase by lazy { open(ProgressDatabase.dbFileName, factory::progress) }
    val aiSettings: AiSettingsDatabase by lazy { open(AiSettingsDatabase.dbFileName, factory::aiSettings) }
    val settings: SettingsDatabase by lazy { open(SettingsDatabase.dbFileName, factory::settings) }
    val repositories: RepoDatabase by lazy { open(RepoDatabase.dbFileName, factory::repositories) }

    /** The main file of every database that was opened and has been written to disk (side files excluded). */
    val files: List<Path>
        get() = opened.map { it.first }.filter { FileSystem.SYSTEM.exists(it) }

    /** The open database of [name], a staging file name such as `workspaces.sqlite3`, or null when it was never opened. */
    fun openedDatabase(name: String): RoomDatabase? = opened.firstOrNull { it.first.name == name }?.second

    private fun <T : RoomDatabase> open(fileName: String, create: (String) -> T): T {
        FileSystem.SYSTEM.createDirectories(dir)
        val path = dir / fileName
        return create(path.toString()).also { opened += path to it }
    }

    override fun close() {
        var failure: Throwable? = null
        for ((path, db) in opened) {
            try {
                db.close()
            } catch (e: Throwable) {
                failure = failure ?: e
            }
            for (suffix in SIDE_FILE_SUFFIXES) {
                FileSystem.SYSTEM.delete((path.toString() + suffix).toPath(), mustExist = false)
            }
        }
        failure?.let { throw it }
    }

    private companion object {
        val SIDE_FILE_SUFFIXES = listOf("-journal", "-wal", "-shm", ".lck")
    }
}
