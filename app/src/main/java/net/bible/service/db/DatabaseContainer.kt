/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

import androidx.annotation.VisibleForTesting
import io.requery.android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.backup.DATABASE_BACKUP_SUFFIX
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.DocumentSyncDatabase
import net.bible.android.database.LogEntry
import net.bible.sharedcore.event.EventSource
import net.bible.sharedcore.event.Events
import net.bible.android.database.OldMonolithicAppDatabase
import net.bible.android.database.REPO_DATABASE_VERSION
import net.bible.android.database.ReadingPlanDatabase
import net.bible.android.database.RepoDatabase
import net.bible.android.database.SETTINGS_DATABASE_VERSION
import net.bible.android.database.SettingsDatabase
import net.bible.android.database.TemporaryDatabase
import net.bible.android.database.AiSettingsDatabase
import net.bible.android.database.AI_SETTINGS_DATABASE_VERSION
import net.bible.android.database.WorkspaceDatabase
import net.bible.android.database.progress.ProgressDatabase
import net.bible.android.database.progress.PROGRESS_DATABASE_VERSION
import net.bible.android.database.mydocument.MyDocumentDatabase
import net.bible.android.database.mydocument.MY_DOCUMENT_DATABASE_VERSION
import net.bible.android.database.migrations.BOOKMARK_DATABASE_VERSION
import net.bible.service.common.CommonUtils
import net.bible.android.database.migrations.DatabaseSplitMigrations
import net.bible.android.database.migrations.READING_PLAN_DATABASE_VERSION
import net.bible.android.database.migrations.WORKSPACE_DATABASE_VERSION
import net.bible.android.database.migrations.bookmarkMigrations
import net.bible.android.database.migrations.aiSettingsMigrations
import net.bible.android.database.migrations.myDocumentMigrations
import net.bible.android.database.migrations.progressMigrations
import net.bible.android.database.migrations.oldMonolithicAppDatabaseMigrations
import net.bible.android.database.migrations.readingPlanMigrations
import net.bible.android.database.migrations.workspacesMigrations
import net.bible.android.database.temporaryMigrations
import net.bible.service.db.oldmigrations.oldMigrations
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.cloudsync.SyncableDatabaseAccessor
import net.bible.service.cloudsync.createTriggers
import net.bible.service.cloudsync.dropTriggers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.bible.sharedcore.settings.store.SettingsStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

const val OLD_MONOLITHIC_DATABASE_NAME = "andBibleDatabase.db"

private const val TAG = "DbContainer"

val ALL_DB_FILENAMES = arrayOf(
    BookmarkDatabase.dbFileName,
    ReadingPlanDatabase.dbFileName,
    WorkspaceDatabase.dbFileName,
    RepoDatabase.dbFileName,
    SettingsDatabase.dbFileName,
    AiSettingsDatabase.dbFileName,
    MyDocumentDatabase.dbFileName,
    ProgressDatabase.dbFileName
)

class DataBaseNotReady: Exception()

class DatabaseContainer {
    init {
        backupDatabaseIfNeeded()
        // The cloud-document cache DB was renamed to document-sync.sqlite3; drop the orphaned file.
        if (!application.isRunningTests) application.deleteDatabase("cloud-documents-cache.sqlite3")
        migrateOldDatabaseIfNeeded()
    }

    private val dbFactory = if(application.isRunningTests) null else RequerySQLiteOpenHelperFactory()

    private fun getOldDatabase(): OldMonolithicAppDatabase =
        Room.databaseBuilder(
            application, OldMonolithicAppDatabase::class.java, OLD_MONOLITHIC_DATABASE_NAME
        )
            .allowMainThreadQueries()
            .openHelperFactory(dbFactory)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .addMigrations(
                *oldMonolithicAppDatabaseMigrations,
                *oldMigrations,
            )
            .build()

    private fun migrateOldDatabaseIfNeeded() {
        val oldDbFile = application.getDatabasePath(OLD_MONOLITHIC_DATABASE_NAME)
        if(oldDbFile.exists()) {
            for (name in application.databaseList().filterNot { it == OLD_MONOLITHIC_DATABASE_NAME }) {
                application.deleteDatabase(name)
            }
            // Room opens the old file only to run its legacy migrations; its connection must be closed
            // before the split reopens the same file outside Room.
            getOldDatabase().apply {
                try { openHelper.writableDatabase } finally { close() }
            }
            openSqlite(oldDbFile.path).use {
                val migrations = DatabaseSplitMigrations(it, application)
                migrations.migrateAll()
            }
            application.deleteDatabase(OLD_MONOLITHIC_DATABASE_NAME) // the file and any journal/WAL leftovers
        }
    }
    fun getBookmarkDb(filename: String = BookmarkDatabase.dbFileName) = Room.databaseBuilder(
        application, BookmarkDatabase::class.java, filename
    )
        .allowMainThreadQueries()
        .addMigrations(*bookmarkMigrations)
        .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
        .openHelperFactory(dbFactory)
        .build()

    var bookmarkDb: BookmarkDatabase = getBookmarkDb()
    fun resetBookmarkDb(): BookmarkDatabase {
        bookmarkDb.close()
        bookmarkDb = getBookmarkDb()
        return bookmarkDb
    }

    fun getReadingPlanDb(filename: String = ReadingPlanDatabase.dbFileName) =
        Room.databaseBuilder(
            application, ReadingPlanDatabase::class.java, filename
        )
            .openHelperFactory(dbFactory)
            .allowMainThreadQueries()
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .addMigrations(*readingPlanMigrations)
            .build()

    var readingPlanDb: ReadingPlanDatabase = getReadingPlanDb()
    fun resetReadingPlanDb(): ReadingPlanDatabase {
        readingPlanDb.close()
        readingPlanDb = getReadingPlanDb()
        return readingPlanDb
    }

    fun getWorkspaceDb(filename: String = WorkspaceDatabase.dbFileName) =
        Room.databaseBuilder(
            application, WorkspaceDatabase::class.java, filename
        )
            .allowMainThreadQueries()
            .addMigrations(*workspacesMigrations)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .openHelperFactory(dbFactory)
            .build()

    var workspaceDb: WorkspaceDatabase = getWorkspaceDb()

    fun resetWorkspaceDb(): WorkspaceDatabase {
        workspaceDb.close()
        workspaceDb = getWorkspaceDb()
        return workspaceDb
    }

    fun getMyDocumentDb(filename: String = MyDocumentDatabase.dbFileName) =
        Room.databaseBuilder(
            application, MyDocumentDatabase::class.java, filename
        )
            .allowMainThreadQueries()
            .addMigrations(*myDocumentMigrations)
            .openHelperFactory(dbFactory)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()

    var myDocumentDb: MyDocumentDatabase = getMyDocumentDb()

    fun resetMyDocumentDb(): MyDocumentDatabase {
        myDocumentDb.close()
        myDocumentDb = getMyDocumentDb()
        return myDocumentDb
    }

    fun getAiSettingsDb(filename: String = AiSettingsDatabase.dbFileName) =
        Room.databaseBuilder(
            application, AiSettingsDatabase::class.java, filename
        )
            .allowMainThreadQueries()
            .addMigrations(*aiSettingsMigrations)
            .openHelperFactory(dbFactory)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()

    var aiSettingsDb: AiSettingsDatabase = getAiSettingsDb()

    fun resetAiSettingsDb(): AiSettingsDatabase {
        aiSettingsDb.close()
        aiSettingsDb = getAiSettingsDb()
        return aiSettingsDb
    }

    fun getProgressDb(filename: String = ProgressDatabase.dbFileName) =
        Room.databaseBuilder(
            application, ProgressDatabase::class.java, filename
        )
            .allowMainThreadQueries()
            .addMigrations(*progressMigrations)
            .openHelperFactory(dbFactory)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()

    var progressDb: ProgressDatabase = getProgressDb()

    fun resetProgressDb(): ProgressDatabase {
        progressDb.close()
        progressDb = getProgressDb()
        return progressDb
    }

    init {
        if(!application.isRunningTests) {
            blockingDb {
                for (dbDef in getDatabaseAccessorFactories(this@DatabaseContainer).map { it.invoke() }) {
                    dropTriggers(dbDef)
                    createTriggers(dbDef)
                }
            }
        }
    }

    val downloadDocumentsDb: TemporaryDatabase =
        Room.databaseBuilder(
            application, TemporaryDatabase::class.java, "temporary.sqlite3"
        )
            .allowMainThreadQueries()
            .addMigrations(*temporaryMigrations)
            .openHelperFactory(dbFactory)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()

    val chooseDocumentsDb: TemporaryDatabase =
        Room.databaseBuilder(
            application, TemporaryDatabase::class.java, "choose-document.sqlite3"
        )
            .allowMainThreadQueries()
            .addMigrations(*temporaryMigrations)
            .openHelperFactory(dbFactory)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()

    val documentSyncDb: DocumentSyncDatabase =
        Room.databaseBuilder(
            application, DocumentSyncDatabase::class.java, "document-sync.sqlite3"
        )
            .allowMainThreadQueries()
            .openHelperFactory(dbFactory)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()

    val repoDb: RepoDatabase =
        Room.databaseBuilder(
            application, RepoDatabase::class.java, RepoDatabase.dbFileName
        )
            .allowMainThreadQueries()
            .addMigrations()
            .openHelperFactory(dbFactory)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()

    val settingsDb: SettingsDatabase =
        Room.databaseBuilder(
            application, SettingsDatabase::class.java, SettingsDatabase.dbFileName
        )
            .allowMainThreadQueries()
            .addMigrations()
            .openHelperFactory(dbFactory)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()

    /** `internal` only so a test can occupy the writer thread; not for production use. */
    internal val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    /** Settings cache for this container instance; a new container (restore/reset) reloads from disk. */
    val settingsStore: SettingsStore = SettingsStore(
        RoomSettingsBackend(settingsDb), settingsScope,
        onWriteError = { w, e -> Log.e(TAG, "Settings write failed for key ${w.key}", e) },
    ).also { blockingDb { it.load() } }

    private fun backupDatabaseIfNeeded() {
        if(application.isRunningTests) return
        val oldDb = application.getDatabasePath(OLD_MONOLITHIC_DATABASE_NAME)
        if(oldDb.exists()) {
            backupOldDatabase(oldDb)
        } else {
            backupNewDatabaseIfNeeded()
        }
    }

    private fun backupOldDatabase(oldDb: File) {
        val dbVersion =
            SQLiteDatabase.openDatabase(oldDb.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.version }
        Log.i(TAG, "backupping old database of version $dbVersion)")
        val backupPath = CommonUtils.dbBackupPath
        val timeStamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
        val backupFile = File(backupPath, "dbBackup-$dbVersion-$timeStamp.db")
        oldDb.copyTo(backupFile, true)
    }

    private fun backupNewDatabaseIfNeeded() {
        Log.i(TAG, "backupDatabaseIfNeeded")
        val versions = ALL_DB_FILENAMES.map {
            val file = application.getDatabasePath(it)
            if(file.exists()) {
                SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.version }
            } else {
                0
            }
        }

        val maxVersions = ALL_DB_FILENAMES.map { maxDatabaseVersion(it) }
        val needBackup = maxVersions != versions

        if(needBackup) {
            val backupZipFile = withReadyCleared { BackupControl.makeDatabaseBackupFile() }
            backupZipFile ?: return
            val versionString = versions.joinToString("-")
            Log.i(TAG, "backupping database of version $versionString (current: ${maxVersions.joinToString("-") })")
            val backupPath = CommonUtils.dbBackupPath
            val timeStamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
            val backupFile = File(backupPath, "dbBackup-${CommonUtils.applicationVersionNumber}-$versionString-$timeStamp$DATABASE_BACKUP_SUFFIX")
            backupZipFile.copyTo(backupFile, true)
            backupZipFile.delete()
        }
    }

    private val backedUpDatabases = arrayOf(bookmarkDb, readingPlanDb, workspaceDb, repoDb, settingsDb, myDocumentDb, aiSettingsDb, progressDb)
    // documentSyncDb is intentionally NOT backed up or vacuumed (device-local, sign-out-scoped cache),
    // but it must still be closed by closeAll() on reset()/restore — otherwise the old Room handle
    // leaks and the next container opens a second handle to the same file (SQLite lock risk).
    private val allDatabases = arrayOf(*backedUpDatabases, downloadDocumentsDb, chooseDocumentsDb, documentSyncDb)

    val dbByFilename = allDatabases.associateBy { it.openHelper.databaseName }

    internal fun sync() = allDatabases.forEach {
        it.openHelper.writableDatabase
            // we are not using WAL mode any more, but it does not hurt either. Just in case we switch back to WAL.
            .query("PRAGMA wal_checkpoint(FULL)").use { c -> c.moveToFirst() }
    }

    internal fun vacuum() {
        backedUpDatabases.forEach {
            it.openHelper.writableDatabase
                .query("VACUUM;").use { c -> c.moveToFirst() }
        }
    }

    /**
     * Closes one database by file name ahead of its file being replaced or deleted. The settings store is
     * flushed first: a write still queued would otherwise reopen the closed handle and land in the restored file.
     * Writes made by other threads after the flush update this (old) store's memory only; [reset] discards them,
     * and a later DAO write would reopen the handle on whatever file is then in place.
     */
    internal fun closeForReplace(fileName: String) {
        if (fileName == SettingsDatabase.dbFileName) blockingDb { settingsStore.flush() }
        dbByFilename[fileName]?.close()
    }

    /**
     * Flushes the settings store, cancels its writer and closes every database. Setting writes made by other
     * threads between the flush/scope-cancel and `_instance = null` update only the old store's memory and are
     * dropped (previously a DAO write would have reopened the file). The window is milliseconds, and restore
     * paths already treat writes there as a hazard.
     */
    internal fun closeAll() {
        // Flush BEFORE cancelling the scope: writes still queued at cancel are dropped.
        blockingDb { settingsStore.flush() }
        settingsScope.cancel()
        allDatabases.forEach { it.close() }
    }

    companion object {
        var ready: Boolean = false

        private val _readingPlansSynced = EventSource<List<LogEntry>>()
        /** Fires on the sync thread after a cloud sync applied reading-plan changes (replaces `ReadingPlansUpdatedViaSyncEvent`). */
        val readingPlansSynced: Events<List<LogEntry>> get() = _readingPlansSynced

        private val _myDocumentsSynced = EventSource<List<LogEntry>>()
        /**
         * Fires on the sync thread after a cloud sync applied MyDocument changes.
         * Not reset between tests: the `MyDocumentBookManager` object subscribes once per JVM.
         */
        val myDocumentsSynced: Events<List<LogEntry>> get() = _myDocumentsSynced

        private var _workspacesSynced = EventSource<List<LogEntry>>()
        /** Fires on the sync thread after a cloud sync applied workspace changes (replaces the retired WorkspacesUpdatedViaSyncEvent bus event). */
        val workspacesSynced: Events<List<LogEntry>> get() = _workspacesSynced
        private var _databaseRestored = EventSource<Unit>()
        /** A restore or a sync sign-in replaced the databases; the reading host reloads (replaces the retired MainBibleAfterRestore bus event). */
        val databaseRestored: Events<Unit> get() = _databaseRestored
        fun notifyDatabaseRestored() {
            _databaseRestored.emit(Unit)
        }
        @VisibleForTesting internal fun emitWorkspacesSyncedForTest(entries: List<LogEntry>) = _workspacesSynced.emit(entries)
        @VisibleForTesting fun resetPhase8StreamsForTest() { _workspacesSynced = EventSource(); _databaseRestored = EventSource() }

        private var _bookmarksSynced = EventSource<List<LogEntry>>()
        /**
         * Fires on the sync thread after a cloud sync applied bookmark-database changes (replaces
         * `BookmarksUpdatedViaSyncEvent`). Subscribers: [BookmarkControl] (synchronous, re-emits the
         * domain changes on the sync thread) and the bookmarks list (on main).
         */
        val bookmarksSynced: Events<List<LogEntry>> get() = _bookmarksSynced
        @VisibleForTesting fun resetBookmarksSyncedForTest() { _bookmarksSynced = EventSource() }

        /**
         * Fix batch 5 §1.1. True while a restore has database files closed or overwritten. A save in
         * that window writes into a closed or replaced file.
         */
        private val replaceDepth = java.util.concurrent.atomic.AtomicInteger(0)
        /** Backs [replaceEpoch]; atomic because CloudSync categories replace concurrently. */
        private val replaceEpochCounter = java.util.concurrent.atomic.AtomicLong(0L)

        /** Batch 6 §1.2: true while ANY replace is running; replaces can nest and overlap. */
        val replacing: Boolean get() = replaceDepth.get() > 0

        /**
         * Bumped by every [replacingDatabases]. A repository loaded before the bump holds pre-restore
         * state and must not save it over the restored database ([WindowRepository.saveIntoDb]) until
         * it has reloaded. Deliberately NOT bumped by [reset]: tests reset between classes (C1).
         */
        val replaceEpoch: Long get() = replaceEpochCounter.get()

        /** Every BackupControl path that closes, copies over or deletes database files runs inside this. */
        suspend fun <T> replacingDatabases(block: suspend () -> T): T {
            replaceDepth.incrementAndGet()
            replaceEpochCounter.incrementAndGet()
            var completed = false
            try {
                return block().also { completed = true }
            } finally {
                // F115: a block that throws or is cancelled mid-copy may leave `_instance` holding a database it
                // closed. Drop it before the depth falls, so the reload reopens from disk.
                try {
                    if (!completed) dropInstanceWithoutOpening()
                } finally {
                    replaceDepth.decrementAndGet()
                }
            }
        }

        /** Tests share one JVM: `DatabaseResetter.resetDatabase()` calls this so no epoch leaks into the next class (C1). */
        @VisibleForTesting
        internal fun forgetReplacesForTest() { replaceDepth.set(0); replaceEpochCounter.set(0L); containerFactory = { DatabaseContainer() } }

        /** Test seam: lets a test make opening the container throw (a migration failing after a restore). */
        @VisibleForTesting
        internal var containerFactory: () -> DatabaseContainer = { DatabaseContainer() }

        /**
         * Failure-path variant of [reset]: closes only an instance that already exists. [reset] goes through
         * the `instance` getter, which BUILDS a container (open, migrate, backup) when `_instance` is null,
         * just to close it; a restore whose migration threw would then throw again from its own cleanup.
         * Also the old monolithic restore's close before its snapshot (F120).
         */
        internal fun dropInstanceWithoutOpening() {
            synchronized(this) {
                try {
                    _instance?.closeAll()
                } finally {
                    _instance = null
                }
            }
        }

        /**
         * Runs [block] with [ready] cleared, and restores it however [block] ends. The clearing makes
         * `makeDatabaseBackupFile` skip vacuum/sync, which would otherwise re-enter [instance] mid-
         * construction; without the `finally`, a throwing backup left `ready = false` forever and
         * every later settings read silently answered its default (fix batch 1 §2.7). The failure
         * itself propagates: no migration without the safety-net backup (maintainer decision).
         */
        internal inline fun <T> withReadyCleared(block: () -> T): T {
            ready = false
            try { return block() } finally { ready = true }
        }

        /**
         * Opens the databases for use: what `StartupActivity.initializeDatabase` has always done
         * inline. Also the Welcome flow's entry when its host was restored after process death and
         * never passed through StartupActivity (fix batch 1 §2.6). Opening the DB is not
         * `initializeApp`, so a Welcome host stays "uninitialised" in slice 8's sense.
         */
        fun openForUse(): DatabaseContainer {
            ready = true
            return instance
        }
        private var _instance: DatabaseContainer? = null
        val instance: DatabaseContainer get() {
            if(!ready && !application.isRunningTests) throw DataBaseNotReady()
            return _instance ?: synchronized(this) {
                _instance ?: try { containerFactory() } catch (e: Exception) {
                    Log.e(TAG, "Can't open database", e)
                    throw e
                }
                    .also {
                        _instance = it
                        CommonUtils.migrateOldSettingsKeys()
                    }
            }
        }

        /** Persists queued settings writes; call before a path that ends the process (`exitProcess`). No-op if the database is not open. */
        fun flushSettingsBeforeExit() {
            if (!ready) return
            try { blockingDb { _instance?.settingsStore?.flush() } } catch (e: Exception) { Log.e(TAG, "Settings flush failed", e) }
        }

        fun sync() = instance.sync()
        fun vacuum() = instance.vacuum()
        fun reset() {
            synchronized(this) {
                try {
                    instance.closeAll()
                } catch (e: DataBaseNotReady) {
                    Log.i(TAG, "Can't close, database not ready")
                }
                _instance = null
            }
        }

        fun maxDatabaseVersion(filename: String): Int = when(filename) {
            BookmarkDatabase.dbFileName -> BOOKMARK_DATABASE_VERSION
            ReadingPlanDatabase.dbFileName -> READING_PLAN_DATABASE_VERSION
            WorkspaceDatabase.dbFileName -> WORKSPACE_DATABASE_VERSION
            RepoDatabase.dbFileName -> REPO_DATABASE_VERSION
            SettingsDatabase.dbFileName -> SETTINGS_DATABASE_VERSION
            AiSettingsDatabase.dbFileName -> AI_SETTINGS_DATABASE_VERSION
            MyDocumentDatabase.dbFileName -> MY_DOCUMENT_DATABASE_VERSION
            ProgressDatabase.dbFileName -> PROGRESS_DATABASE_VERSION
            else -> throw IllegalStateException("Unknown database file: $filename")
        }

        val databaseAccessorFactories get() = getDatabaseAccessorFactories(instance)
        val databaseAccessors get() = databaseAccessorFactories.map { it.invoke() }

        val databaseAccessorsByCategory get() = databaseAccessors.associateBy { it.category }
        fun getDatabaseAccessorFactories(container: DatabaseContainer): List<() -> SyncableDatabaseAccessor<*>> = container.run {
            listOf(
                { SyncableDatabaseAccessor(
                    localDb = bookmarkDb,
                    dbFactory = { n -> getBookmarkDb(n) }, _resetLocalDb = { resetBookmarkDb() },
                    localDbFile = application.getDatabasePath(BookmarkDatabase.dbFileName),
                    category = SyncableDatabaseDefinition.BOOKMARKS,
                    _reactToUpdates = { entries ->
                        _bookmarksSynced.emit(entries)
                    },
                ) },
                { SyncableDatabaseAccessor(
                    localDb = workspaceDb,
                    dbFactory = { n -> getWorkspaceDb(n) }, _resetLocalDb = { resetWorkspaceDb() },
                    localDbFile = application.getDatabasePath(WorkspaceDatabase.dbFileName),
                    category = SyncableDatabaseDefinition.WORKSPACES,
                    _reactToUpdates = {
                        _workspacesSynced.emit(it)
                    },
                ) },
                { SyncableDatabaseAccessor(
                    localDb = readingPlanDb,
                    dbFactory = { n -> getReadingPlanDb(n) },
                    _resetLocalDb = { resetReadingPlanDb() },
                    localDbFile = application.getDatabasePath(ReadingPlanDatabase.dbFileName),
                    category = SyncableDatabaseDefinition.READINGPLANS,
                    _reactToUpdates = { _readingPlansSynced.emit(it) },
                )
                },
                { SyncableDatabaseAccessor(
                    localDb = myDocumentDb,
                    dbFactory = { n -> getMyDocumentDb(n) },
                    _resetLocalDb = { resetMyDocumentDb() },
                    localDbFile = application.getDatabasePath(MyDocumentDatabase.dbFileName),
                    category = SyncableDatabaseDefinition.MYDOCUMENTS,
                    _reactToUpdates = { _myDocumentsSynced.emit(it) },
                ) },
                { SyncableDatabaseAccessor(
                    localDb = aiSettingsDb,
                    dbFactory = { n -> getAiSettingsDb(n) },
                    _resetLocalDb = { resetAiSettingsDb() },
                    localDbFile = application.getDatabasePath(AiSettingsDatabase.dbFileName),
                    category = SyncableDatabaseDefinition.AI_SETTINGS,
                ) },
                { SyncableDatabaseAccessor(
                    localDb = progressDb,
                    dbFactory = { n -> getProgressDb(n) },
                    _resetLocalDb = { resetProgressDb() },
                    localDbFile = application.getDatabasePath(ProgressDatabase.dbFileName),
                    category = SyncableDatabaseDefinition.PROGRESS,
                ) },
            )
        }

    }
}

