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
import net.bible.sharedcore.log.Log
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.concurrent.Volatile
import androidx.room3.RoomDatabase
import androidx.room3.useWriterConnection
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
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.cloudsync.SyncableDatabaseAccessor
import net.bible.service.cloudsync.createTriggers
import net.bible.service.cloudsync.dropTriggers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.bible.sharedcore.settings.store.SettingsStore
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

const val OLD_MONOLITHIC_DATABASE_NAME = "andBibleDatabase.db"
private const val TEMPORARY_DOWNLOAD_DB_FILENAME = "temporary.sqlite3"
private const val TEMPORARY_CHOOSE_DB_FILENAME = "choose-document.sqlite3"
private const val DOCUMENT_SYNC_DB_FILENAME = "document-sync.sqlite3"

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

class DatabaseContainer private constructor() {
    // The six syncable databases below are @Volatile (D1 final review I2): CloudSync's first sync swaps a field
    // (resetXDb) from an IO thread while other threads read it, and a stale read would reach the closed instance,
    // which under Room 3 throws instead of reopening.
    fun getBookmarkDb(filename: String = BookmarkDatabase.dbFileName) = buildAppDatabase<BookmarkDatabase>(filename, *bookmarkMigrations)

    @Volatile var bookmarkDb: BookmarkDatabase = getBookmarkDb()
    fun resetBookmarkDb(): BookmarkDatabase {
        bookmarkDb.close()
        bookmarkDb = getBookmarkDb()
        return bookmarkDb
    }

    fun getReadingPlanDb(filename: String = ReadingPlanDatabase.dbFileName) =
        buildAppDatabase<ReadingPlanDatabase>(filename, *readingPlanMigrations)

    @Volatile var readingPlanDb: ReadingPlanDatabase = getReadingPlanDb()
    fun resetReadingPlanDb(): ReadingPlanDatabase {
        readingPlanDb.close()
        readingPlanDb = getReadingPlanDb()
        return readingPlanDb
    }

    fun getWorkspaceDb(filename: String = WorkspaceDatabase.dbFileName) =
        buildAppDatabase<WorkspaceDatabase>(filename, *workspacesMigrations)

    @Volatile var workspaceDb: WorkspaceDatabase = getWorkspaceDb()

    fun resetWorkspaceDb(): WorkspaceDatabase {
        workspaceDb.close()
        workspaceDb = getWorkspaceDb()
        return workspaceDb
    }

    fun getMyDocumentDb(filename: String = MyDocumentDatabase.dbFileName) =
        buildAppDatabase<MyDocumentDatabase>(filename, *myDocumentMigrations)

    @Volatile var myDocumentDb: MyDocumentDatabase = getMyDocumentDb()

    fun resetMyDocumentDb(): MyDocumentDatabase {
        myDocumentDb.close()
        myDocumentDb = getMyDocumentDb()
        return myDocumentDb
    }

    fun getAiSettingsDb(filename: String = AiSettingsDatabase.dbFileName) =
        buildAppDatabase<AiSettingsDatabase>(filename, *aiSettingsMigrations)

    @Volatile var aiSettingsDb: AiSettingsDatabase = getAiSettingsDb()

    @VisibleForTesting
    fun resetAiSettingsDbOwned(token: net.bible.service.llm.AiDatabaseLifetime.Replacement): AiSettingsDatabase {
        aiLifetime.requireOwner(token)
        aiSettingsDb.close()
        aiSettingsDb = getAiSettingsDb()
        return aiSettingsDb
    }

    fun getProgressDb(filename: String = ProgressDatabase.dbFileName) =
        buildAppDatabase<ProgressDatabase>(filename, *progressMigrations)

    @Volatile var progressDb: ProgressDatabase = getProgressDb()

    fun resetProgressDb(): ProgressDatabase {
        progressDb.close()
        progressDb = getProgressDb()
        return progressDb
    }

    val downloadDocumentsDb: TemporaryDatabase =
        buildAppDatabase<TemporaryDatabase>(TEMPORARY_DOWNLOAD_DB_FILENAME, *temporaryMigrations)

    val chooseDocumentsDb: TemporaryDatabase =
        buildAppDatabase<TemporaryDatabase>(TEMPORARY_CHOOSE_DB_FILENAME, *temporaryMigrations)

    val documentSyncDb: DocumentSyncDatabase =
        buildAppDatabase<DocumentSyncDatabase>(DOCUMENT_SYNC_DB_FILENAME)

    val repoDb: RepoDatabase =
        buildAppDatabase<RepoDatabase>(RepoDatabase.dbFileName)

    val settingsDb: SettingsDatabase =
        buildAppDatabase<SettingsDatabase>(SettingsDatabase.dbFileName)

    /** Visible only so a test can occupy the writer thread; not for production use. */
    @VisibleForTesting
    val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    /** Settings cache for this container instance; a new container (restore/reset) reloads from disk. */
    val settingsStore: SettingsStore = SettingsStore(
        RoomSettingsBackend(settingsDb), settingsScope,
        onWriteError = { w, e -> Log.e(TAG, "Settings write failed for key ${w.key}", e) },
    )

    // Computed, not captured: the resetXDb() functions (a first cloud sync swapping in the downloaded file) replace
    // the instances, and a captured array would keep vacuuming/closing the closed old one (a closed Room 3 database
    // throws) while never closing the live one.
    private val backedUpDatabases get() = arrayOf(bookmarkDb, readingPlanDb, workspaceDb, repoDb, settingsDb, myDocumentDb, aiSettingsDb, progressDb)
    // documentSyncDb is intentionally NOT backed up or vacuumed (device-local, sign-out-scoped cache),
    // but it must still be closed by closeAll() on reset()/restore — otherwise the old Room handle
    // leaks and the next container opens a second handle to the same file (SQLite lock risk).
    private val allDatabases get() = arrayOf(*backedUpDatabases, downloadDocumentsDb, chooseDocumentsDb, documentSyncDb)

    /** Keyed by the file name each database was built with; the current instances (see [backedUpDatabases]). */
    val dbByFilename: Map<String, RoomDatabase> get() = mapOf(
        BookmarkDatabase.dbFileName to bookmarkDb,
        ReadingPlanDatabase.dbFileName to readingPlanDb,
        WorkspaceDatabase.dbFileName to workspaceDb,
        RepoDatabase.dbFileName to repoDb,
        SettingsDatabase.dbFileName to settingsDb,
        MyDocumentDatabase.dbFileName to myDocumentDb,
        AiSettingsDatabase.dbFileName to aiSettingsDb,
        ProgressDatabase.dbFileName to progressDb,
        TEMPORARY_DOWNLOAD_DB_FILENAME to downloadDocumentsDb,
        TEMPORARY_CHOOSE_DB_FILENAME to chooseDocumentsDb,
        DOCUMENT_SYNC_DB_FILENAME to documentSyncDb,
    )

    @VisibleForTesting
    suspend fun sync() {
        // we are not using WAL mode any more, but it does not hurt either. Just in case we switch back to WAL.
        allDatabases.forEach { it.useWriterConnectionMarked { c -> c.exec("PRAGMA wal_checkpoint(FULL)") } }
    }

    @VisibleForTesting
    suspend fun vacuum() {
        backedUpDatabases.forEach {
            it.useWriterConnectionMarked { c -> c.exec("VACUUM;") }
        }
    }

    /**
     * Closes one database by file name ahead of its file being replaced or deleted. For the settings database
     * the settings store is flushed first, so no write still queued is lost or lands in the replaced file.
     *
     * A Room 3 database never reopens after `close()`: a DAO call on it afterwards throws `IllegalStateException`
     * ("Database is closed"); a call already under way when the close lands gets `androidx.sqlite.SQLiteException`
     * (SQLITE_MISUSE, "Connection pool is closed"), which code catching `SQLiteException` around a database call
     * (e.g. `insertOr`, which returns -1) swallows silently. Between this call and the `reset` that builds a new
     * container (the restore flow's hazard window), a DAO call on the closed database (a settings write from another
     * thread reaching the store's writer, a UI save) throws instead of reaching either file; settings writes made
     * then are logged by the store's `onWriteError` and dropped.
     * Closing an already closed database is a no-op, so `closeAll` after this is safe.
     */
    suspend fun closeForReplaceOwned(fileName: String, token: net.bible.service.llm.AiDatabaseLifetime.Replacement) {
        aiLifetime.requireOwner(token)
        if (fileName == SettingsDatabase.dbFileName) settingsStore.flush()
        dbByFilename[fileName]?.close()
    }

    /**
     * Flushes the settings store, cancels its writer and closes every database. Setting writes made by other
     * threads between the flush/scope-cancel and `_instance = null` update only the old store's memory and are
     * dropped (a closed Room 3 database does not reopen). The window is milliseconds, and restore paths already
     * treat writes there as a hazard. Databases already closed by `closeForReplace` are closed again harmlessly.
     */
    @VisibleForTesting
    suspend fun closeAllOwned(token: net.bible.service.llm.AiDatabaseLifetime.Replacement) {
        aiLifetime.requireOwner(token)
        // Flush BEFORE cancelling the scope: writes still queued at cancel are dropped.
        settingsStore.flush()
        synchronized(Companion) { closeDatabasesAfterFlush() } // L1-common-ok: atomicfu lock
    }

    private fun closeDatabasesAfterFlush() {
        settingsScope.cancel()
        allDatabases.forEach { it.close() }
    }

    companion object : SynchronizedObject() {
        /**
         * Opens the container in the one order that matters: backup, orphan delete, legacy split, construct,
         * trigger setup, settings load. Suspends instead of blocking; the host's `openContainer` bridges it.
         */
        suspend fun create(): DatabaseContainer {
            backupDatabaseIfNeeded()
            // The cloud-document cache DB was renamed to document-sync.sqlite3; drop the orphaned file.
            if (!host.isRunningTests) host.deleteDatabase("cloud-documents-cache.sqlite3")
            migrateOldDatabaseIfNeeded()
            val container = DatabaseContainer()
            if (!host.isRunningTests) {
                for (dbDef in getDatabaseAccessorFactories(container).map { it.invoke() }) {
                    dropTriggers(dbDef)
                    createTriggers(dbDef)
                }
            }
            container.settingsStore.load()
            return container
        }

        private fun getOldDatabase(): OldMonolithicAppDatabase =
            buildAppDatabase<OldMonolithicAppDatabase>(OLD_MONOLITHIC_DATABASE_NAME, *oldMonolithicAppDatabaseMigrations, *host.legacyMonolithicMigrations)

        private suspend fun migrateOldDatabaseIfNeeded() {
            val oldDbFile = host.databasePath(OLD_MONOLITHIC_DATABASE_NAME)
            if(FileSystem.SYSTEM.exists(oldDbFile.toPath())) {
                for (name in host.databaseList().filterNot { it == OLD_MONOLITHIC_DATABASE_NAME }) {
                    host.deleteDatabase(name)
                }
                // Room opens the old file only to run its legacy migrations; its connection must be closed
                // before the split reopens the same file outside Room.
                getOldDatabase().apply {
                    try { useWriterConnection { } } finally { close() }
                }
                openSqlite(oldDbFile).use {
                    val migrations = DatabaseSplitMigrations(it) { name -> host.databasePath(name) }
                    migrations.migrateAll()
                }
                deleteAppDatabase(OLD_MONOLITHIC_DATABASE_NAME) // the file, any journal/WAL leftovers and Room's .lck
            }
        }

        private fun backupDatabaseIfNeeded() {
            if(host.isRunningTests) return
            val oldDb = host.databasePath(OLD_MONOLITHIC_DATABASE_NAME)
            if(FileSystem.SYSTEM.exists(oldDb.toPath()) && recoverIfCorrupt(oldDb) == null) {
                backupOldDatabase(oldDb)
            } else {
                backupNewDatabaseIfNeeded()
            }
        }

        private fun backupOldDatabase(oldDb: String) {
            val dbVersion = readUserVersion(oldDb)
            Log.i(TAG, "backupping old database of version $dbVersion)")
            val backupPath = host.dbBackupPath
            val timeStamp = host.formatTimestamp(kotlin.time.Clock.System.now().toEpochMilliseconds(), "yyyyMMdd-HHmmss", deviceLocale = true)
            copyOverwriting(oldDb.toPath(), backupPath.toPath() / "dbBackup-$dbVersion-$timeStamp.db")
        }

        private fun backupNewDatabaseIfNeeded() {
            Log.i(TAG, "backupDatabaseIfNeeded")
            val versions = ALL_DB_FILENAMES.map { userVersionRecoveringCorruption(host.databasePath(it)) }

            val maxVersions = ALL_DB_FILENAMES.map { maxDatabaseVersion(it) }
            val needBackup = maxVersions != versions

            if(needBackup) {
                // Not makeDatabaseBackupFile: with ready cleared it would skip its vacuum and checkpoint anyway.
                val backupZipFile = withReadyCleared { host.zipDatabaseFilesForUpgradeBackup()?.toPath() }
                backupZipFile ?: return
                val versionString = versions.joinToString("-")
                Log.i(TAG, "backupping database of version $versionString (current: ${maxVersions.joinToString("-") })")
                val backupPath = host.dbBackupPath
                val timeStamp = host.formatTimestamp(kotlin.time.Clock.System.now().toEpochMilliseconds(), "yyyyMMdd-HHmmss", deviceLocale = true)
                val backupFile = backupPath.toPath() / "dbBackup-${host.applicationVersionNumber}-$versionString-$timeStamp${host.databaseBackupSuffix}"
                copyOverwriting(backupZipFile, backupFile)
                FileSystem.SYSTEM.delete(backupZipFile, mustExist = false)
            }
        }

        /** `File.copyTo(target, overwrite = true)`: creates the parent directory and replaces an existing target. */
        private fun copyOverwriting(source: Path, target: Path) {
            val fs = FileSystem.SYSTEM
            target.parent?.let { fs.createDirectories(it) }
            fs.delete(target, mustExist = false)
            fs.copy(source, target)
        }

        /** The platform side of the container; installed once at startup by [installHost]. */
        @Volatile private var _host: DatabaseHost? = null
        val host: DatabaseHost get() = _host ?: throw IllegalStateException("DatabaseHost not installed")

        fun installHost(host: DatabaseHost) { _host = host }

        val aiLifetime = net.bible.service.llm.AiDatabaseLifetime()
        @Volatile var ready: Boolean = false

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
        @VisibleForTesting fun emitWorkspacesSyncedForTest(entries: List<LogEntry>) = _workspacesSynced.emit(entries)
        @VisibleForTesting fun resetPhase8StreamsForTest() { _workspacesSynced = EventSource(); _databaseRestored = EventSource() }

        private var _bookmarksSynced = EventSource<List<LogEntry>>()
        /**
         * Fires on the sync thread after a cloud sync applied bookmark-database changes (replaces
         * `BookmarksUpdatedViaSyncEvent`). Subscribers: [BookmarkControl] (queues the DAO reads and the
         * re-emitted domain changes on its `"bookmark-sync"` [net.bible.sharedcore.platform.OrderedLauncher]
         * key, so they arrive asynchronously, in sync order, off the sync thread) and the bookmarks list (on main).
         */
        val bookmarksSynced: Events<List<LogEntry>> get() = _bookmarksSynced
        @VisibleForTesting fun resetBookmarksSyncedForTest() { _bookmarksSynced = EventSource() }

        /**
         * Fix batch 5 §1.1. True while a restore has database files closed or overwritten. A save in
         * that window writes into a closed or replaced file.
         */
        private val replaceDepth = atomic(0)
        /** Backs [replaceEpoch]; atomic because CloudSync categories replace concurrently. */
        private val replaceEpochCounter = atomic(0L)

        /** Batch 6 §1.2: true while ANY replace is running; replaces can nest and overlap. */
        val replacing: Boolean get() = replaceDepth.value > 0

        /**
         * Bumped by every [replacingDatabases]. A repository loaded before the bump holds pre-restore
         * state and must not save it over the restored database ([WindowRepository.saveIntoDb]) until
         * it has reloaded. Deliberately NOT bumped by `reset`: tests reset between classes (C1).
         */
        val replaceEpoch: Long get() = replaceEpochCounter.value

        /** Every BackupControl path that closes, copies over or deletes database files runs inside this. */
        suspend fun <T> replacingDatabases(block: suspend (net.bible.service.llm.AiDatabaseLifetime.Replacement) -> T): T =
            aiLifetime.replace { replacingDatabasesOwned(it, block) }

        /** Test seam: called when a restore starts waiting for the workspace write queue. */
        @VisibleForTesting
        var onWorkspaceDrainEntered: (() -> Unit)? = null

        private suspend fun drainWorkspaceWrites() {
            onWorkspaceDrainEntered?.invoke()
            host.awaitWorkspacePersisted()
        }

        /** Nested replacement must pass the active owner's token; independent calls queue at admission. */
        suspend fun <T> replacingDatabases(
            token: net.bible.service.llm.AiDatabaseLifetime.Replacement?,
            block: suspend (net.bible.service.llm.AiDatabaseLifetime.Replacement) -> T,
        ): T = if (token == null) replacingDatabases(block)
        else aiLifetime.replace(token) { replacingDatabasesOwned(it, block) }

        private suspend fun <T> replacingDatabasesOwned(
            token: net.bible.service.llm.AiDatabaseLifetime.Replacement,
            block: suspend (net.bible.service.llm.AiDatabaseLifetime.Replacement) -> T,
        ): T {
            aiLifetime.requireOwner(token)
            replaceDepth.incrementAndGet()
            replaceEpochCounter.incrementAndGet()
            var completed = false
            try {
                // No new workspace write passes its guard from here on; one that already did may still be
                // mid-DAO-call, so let it finish before the files are touched. The restore never runs inside
                // the "workspace" key (queued blocks are plain DAO calls), so this cannot wait on itself.
                drainWorkspaceWrites()
                return block(token).also { completed = true }
            } finally {
                // F115: a block that throws or is cancelled mid-copy may leave `_instance` holding a database it
                // closed. Drop it before the depth falls, so the reload reopens from disk.
                try {
                    if (!completed) kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { dropInstanceWithoutOpeningOwned(token) }
                } finally {
                    replaceDepth.decrementAndGet()
                }
            }
        }

        /** Tests share one JVM: `DatabaseResetter.resetDatabase()` calls this so no epoch leaks into the next class (C1). */
        @VisibleForTesting
        fun forgetReplacesForTest() { onWorkspaceDrainEntered = null; replaceDepth.value = 0; replaceEpochCounter.value = 0L; containerFactory = { host.openContainer() } }

        /** Test seam: lets a test make opening the container throw (a migration failing after a restore). */
        @VisibleForTesting
        var containerFactory: () -> DatabaseContainer = { host.openContainer() }

        /**
         * Failure-path variant of `reset`: closes only an instance that already exists (the test bridge builds on
         * this). A restore whose migration threw must not build a container just to close it. Also the old
         * monolithic restore's close before its snapshot (F120).
         */
        suspend fun dropInstanceWithoutOpeningOwned(token: net.bible.service.llm.AiDatabaseLifetime.Replacement) {
            aiLifetime.requireOwner(token)
            val current = synchronized(Companion) { _instance } // L1-common-ok: atomicfu lock
            try { current?.settingsStore?.flush() }
            finally {
                synchronized(Companion) { // L1-common-ok: atomicfu lock
                    try { current?.closeDatabasesAfterFlush() }
                    finally { if (_instance === current) _instance = null }
                }
            }
        }

        /**
         * Test-only: forgets the current instance WITHOUT closing it and returns it; never builds one. The caller
         * closes it later (`closeAll`). Used by the test application's after-test net, which must not close
         * databases that a finished test's background work (a document render on Dispatchers.Default) may still be
         * using: a closed Room 3 database throws instead of reopening, and the exception would surface as an
         * uncaught coroutine exception in an unrelated later test.
         */
        @VisibleForTesting
        fun detachInstanceForTest(): DatabaseContainer? = synchronized(Companion) { _instance.also { _instance = null } } // L1-common-ok: atomicfu lock

        /**
         * Runs [block] with [ready] cleared, and restores it however [block] ends. The clearing makes
         * `makeDatabaseBackupFile` skip vacuum/sync, which would otherwise re-enter [instance] mid-
         * construction; without the `finally`, a throwing backup left `ready = false` forever and
         * every later settings read silently answered its default (fix batch 1 §2.7). The failure
         * itself propagates: no migration without the safety-net backup (maintainer decision).
         */
        @VisibleForTesting
        inline fun <T> withReadyCleared(block: () -> T): T {
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
        @Volatile private var _instance: DatabaseContainer? = null
        val instance: DatabaseContainer get() {
            if(!ready && !host.isRunningTests) throw DataBaseNotReady()
            return _instance ?: synchronized(Companion) { // L1-common-ok: atomicfu lock
                _instance ?: try { containerFactory() } catch (e: Exception) {
                    Log.e(TAG, "Can't open database", e)
                    throw e
                }
                    .also {
                        _instance = it
                        host.migrateOldSettingsKeys()
                    }
            }
        }

        /**
         * Persists queued settings writes; call before a path that ends the process (`exitProcess`). No-op if the
         * database is not open; never builds a container and never throws. With [timeoutMs] it gives up waiting
         * after that long (the crash handler: a dying process must not hang on a stuck writer; the wait for the
         * writer is a cancellable await, so the timeout takes effect).
         */
        fun flushSettingsBeforeExit(timeoutMs: Long? = null) {
            if (!ready) return
            host.flushSettingsBeforeExit(_instance?.settingsStore, timeoutMs)
        }

        suspend fun sync() = instance.sync()
        suspend fun vacuum() = instance.vacuum()
        suspend fun resetOwned(token: net.bible.service.llm.AiDatabaseLifetime.Replacement) {
            aiLifetime.requireOwner(token)
            val current = synchronized(Companion) { // L1-common-ok: atomicfu lock
                try { instance }
                catch (e: DataBaseNotReady) { Log.i(TAG, "Can't close, database not ready"); null }
            }
            current?.settingsStore?.flush()
            // Keep native close and publication atomic with respect to container construction.
            // Only the suspendable settings flush is outside the original monitor contract.
            synchronized(Companion) { // L1-common-ok: atomicfu lock
                current?.closeDatabasesAfterFlush()
                if (_instance === current) _instance = null
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
                    deviceId = host.deviceIdentifier,
                    localDb = bookmarkDb,
                    dbFactory = { n -> getBookmarkDb(n) }, _resetLocalDb = { resetBookmarkDb() },
                    localDbPath = host.databasePath(BookmarkDatabase.dbFileName),
                    category = SyncableDatabaseDefinition.BOOKMARKS,
                    _reactToUpdates = { entries ->
                        _bookmarksSynced.emit(entries)
                    },
                ) },
                { SyncableDatabaseAccessor(
                    deviceId = host.deviceIdentifier,
                    localDb = workspaceDb,
                    dbFactory = { n -> getWorkspaceDb(n) }, _resetLocalDb = { resetWorkspaceDb() },
                    localDbPath = host.databasePath(WorkspaceDatabase.dbFileName),
                    category = SyncableDatabaseDefinition.WORKSPACES,
                    _reactToUpdates = {
                        _workspacesSynced.emit(it)
                    },
                ) },
                { SyncableDatabaseAccessor(
                    deviceId = host.deviceIdentifier,
                    localDb = readingPlanDb,
                    dbFactory = { n -> getReadingPlanDb(n) },
                    _resetLocalDb = { resetReadingPlanDb() },
                    localDbPath = host.databasePath(ReadingPlanDatabase.dbFileName),
                    category = SyncableDatabaseDefinition.READINGPLANS,
                    _reactToUpdates = { _readingPlansSynced.emit(it) },
                )
                },
                { SyncableDatabaseAccessor(
                    deviceId = host.deviceIdentifier,
                    localDb = myDocumentDb,
                    dbFactory = { n -> getMyDocumentDb(n) },
                    _resetLocalDb = { resetMyDocumentDb() },
                    localDbPath = host.databasePath(MyDocumentDatabase.dbFileName),
                    category = SyncableDatabaseDefinition.MYDOCUMENTS,
                    _reactToUpdates = { _myDocumentsSynced.emit(it) },
                ) },
                { SyncableDatabaseAccessor(
                    deviceId = host.deviceIdentifier,
                    localDb = aiSettingsDb,
                    dbFactory = { n -> getAiSettingsDb(n) },
                    _resetLocalDb = { error("AI settings database resets only through resetOwned (replacingDatabases)") },
                    localDbPath = host.databasePath(AiSettingsDatabase.dbFileName),
                    category = SyncableDatabaseDefinition.AI_SETTINGS,
                    resetOwned = { token -> resetAiSettingsDbOwned(token) },
                ) },
                { SyncableDatabaseAccessor(
                    deviceId = host.deviceIdentifier,
                    localDb = progressDb,
                    dbFactory = { n -> getProgressDb(n) },
                    _resetLocalDb = { resetProgressDb() },
                    localDbPath = host.databasePath(ProgressDatabase.dbFileName),
                    category = SyncableDatabaseDefinition.PROGRESS,
                ) },
            )
        }

    }
}

