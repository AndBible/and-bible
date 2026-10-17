/*
 * Copyright (c) 2023-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.cloudsync

import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import okio.Path
import okio.FileSystem
import net.bible.sharedcore.platform.cloud.CloudFile
import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.time.Clock
import net.bible.sharedcore.platform.CoreStrings

import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.sync.SyncScheduler
import androidx.annotation.VisibleForTesting
import net.bible.service.db.useWriterConnectionMarked
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.bible.android.control.event.UserMessages
import net.bible.android.database.SyncStatus
import net.bible.service.cloudsync.documents.DocumentSyncSettings
import net.bible.service.common.asyncMap
import net.bible.service.common.createTempFile
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.exec
import net.bible.service.db.openSqlite
import net.bible.service.db.queryLong
import net.bible.sharedcore.event.EventSource
import net.bible.sharedcore.event.Events
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import okio.IOException

const val GZIP_MIMETYPE = "application/gzip"

const val SYNC_FOLDER_FILE_ID_KEY = "syncId"
const val SYNC_DEVICE_FOLDER_FILE_ID_KEY = "deviceFolderId"
const val LAST_PATCH_WRITTEN_KEY = "lastPatchWritten"
const val LAST_SYNCHRONIZED_KEY = "lastSynchronized"
const val INITIAL_BACKUP_FILENAME = "initial.sqlite3.gz"
const val TAG = "DeviceSync"

class CancelStartedSync: Exception()

class CloudSync(
    private val dialogs: AppDialogController,
    private val strings: CoreStrings,
    private val env: net.bible.sharedcore.platform.AppEnvironment,
    private val settings: net.bible.sharedcore.platform.AppSettings,
    private val adapters: CloudAdapterFactory,
    private val scheduler: SyncScheduler,
    private val documentSyncStarter: net.bible.sharedcore.cloud.DocumentSyncStarter,
    private val documents: DocumentSyncProvider,
    private val documentSettings: DocumentSyncSettings,
) {
    var adapterId: String
        get() = settings.getString("sync_adapter") ?: adapters.available.first()
        set(value) = settings.setString("sync_adapter", value)
    val adapterChoices get() = adapters.available.map { SettingsItem.Choice(it, adapters.displayName(it)) }

    private var runningSource = EventSource<Boolean>()

    /** `true` when a cloud sync starts, `false` when it finishes (replaces `CloudSyncEvent`). */
    val runningChanged: Events<Boolean> get() = runningSource

    internal fun notifySyncRunning(running: Boolean) = runningSource.emit(running)

    private var refreshSource = EventSource<Unit>()

    /** The last concurrent database replace ended; the reading host reloads its workspace (replaces the retired WorkspaceRefreshRequired bus event). */
    val workspaceRefreshRequired: Events<Unit> get() = refreshSource

    @VisibleForTesting internal fun notifyWorkspaceRefreshRequired() {
        refreshSource.emit(Unit)
    }

    @VisibleForTesting
    fun resetSubscribersForTest() { runningSource = EventSource(); refreshSource = EventSource() }


    // AppDialogRequest.Options ids for initializeSync's "which way?" question (Task 24 Step 3).
    private val FETCH_VALUE = "fetch"
    private val CREATE_VALUE = "create"
    private val DISABLE_VALUE = "disable"

    private var _adapter: CloudAdapter? = null
    private val adapter: CloudAdapter get() = _adapter!!

    val signedIn get() = _adapter?.signedIn == true

    internal val cloudAdapter: CloudAdapter? get() = _adapter

    val DOCUMENTS_SYNC_FOLDER_NAME_SUFFIX = "documents"

    suspend fun documentsSyncFolderId(): String? {
        val adapter = _adapter ?: return null
        val name = "${env.packageName}-sync-$DOCUMENTS_SYNC_FOLDER_NAME_SUFFIX"
        val existing = adapter.listFiles(name = name).firstOrNull()
        return existing?.id ?: adapter.createNewFolder(name).id
    }

    private val signInMutex = Mutex()
    suspend fun signIn(): Boolean? {
        if(signInMutex.isLocked) {
            Log.i(TAG, "Already signing in!")
            return null
        }
        var errorMessage: String? = null
        var cancelledByUser = false
        val success = signInMutex.withLock {
            _adapter = adapters.create(adapterId)
            try {
                adapter.signIn()
            } catch (e: CloudSyncUserCancelledException) {
                cancelledByUser = true
                false
            } catch (e: Exception){
                errorMessage = e.message
                false
            }
        }
        if(!success) {
            _adapter = null
            if (!cancelledByUser) dialogs.await(AppDialogRequest.Message(null, strings.signInFailed + " " + (errorMessage?:""), strings.okay, cancellable = false))
        }
        return success
    }
    suspend fun signOut() {
        _adapter?.signOut()
        _adapter = null
        // Stop any in-flight document transfers before wiping their state: they belong to the
        // account just disconnected. store() already returns null now (_adapter == null), so a
        // running drain would no-op anyway, but cancelling it promptly avoids a needless drain.
        documentSyncStarter.stop()
        documents.get().onSignOut()
        DatabaseContainer.databaseAccessorFactories.asyncMap {
            val dbDef = it.invoke()
            val category = dbDef.category
            category.setSyncEnabled(settings, false)
            dbDef.dao.clearSyncStatus()
            dbDef.dao.clearSyncConfiguration()
        }
    }

    enum class InitialOperation {FETCH_INITIAL, CREATE_NEW}
    private val uiMutex = Mutex()

    /**
     * `initializeSync`'s "fetch from cloud, create new, or disable?" question (Task 24 Step 3): an
     * owner-less `AppDialogController` dialog in place of the old `android.app.AlertDialog`,
     * followed by its existing confirmation through the same dialog controller. Declining
     * that confirmation reverts to null, same as choosing "Cancel & disable synchronization"
     * outright. `internal`, not private, so a test can pin the mapping directly against just a
     * [SyncableDatabaseDefinition] -- without building a whole [SyncableDatabaseAccessor].
     */
    internal suspend fun askInitialSyncOperation(category: SyncableDatabaseDefinition): InitialOperation? {
        val containsStr = strings.syncCategoryContents(category.name)
        val dialogResult = dialogs.await(
            AppDialogRequest.Options(
                title = strings.cloudSyncTitle,
                message = strings.overrideBackup(containsStr),
                options = listOf(
                    SettingsItem.Choice(FETCH_VALUE, strings.cloudFetchAndRestoreInitial),
                    SettingsItem.Choice(CREATE_VALUE, strings.cloudCreateNew),
                    SettingsItem.Choice(DISABLE_VALUE, strings.cloudDisableSync),
                ),
                dismissText = null,
                asActionSheet = false,
                cancellable = false,
            ),
        )
        val q1 = when ((dialogResult as? AppDialogResult.Selected)?.value) {
            FETCH_VALUE -> InitialOperation.FETCH_INITIAL
            CREATE_VALUE -> InitialOperation.CREATE_NEW
            else -> null // "Cancel & disable synchronization", or (uncancellable) Cancel/back/scrim.
        }
        if (q1 != null) {
            val msgString = when (q1) {
                InitialOperation.FETCH_INITIAL -> strings.areYouSureResetLocal(containsStr)
                InitialOperation.CREATE_NEW -> strings.areYouSureResetCloud(containsStr)
            }
            val confirmed = dialogs.await(AppDialogRequest.Confirm(strings.areYouSure, msgString, strings.okay, strings.cancel)) == AppDialogResult.Ok
            if (!confirmed) return null
        }
        return q1
    }

    private suspend fun initializeSync(dbDef: SyncableDatabaseAccessor<*>) {
        var initialOperation: InitialOperation?= null
        val syncFolderName = "${env.packageName}-sync-${dbDef.categoryName}"

        var syncFolderId = dbDef.dao.getString(SYNC_FOLDER_FILE_ID_KEY)
        if (syncFolderId != null) {
            val syncFolderKnown = adapter.isSyncFolderKnown(dbDef, name=syncFolderName, id=syncFolderId)
            if (!syncFolderKnown) {
                syncFolderId = null
                dbDef.dao.removeConfig(SYNC_FOLDER_FILE_ID_KEY)
                dbDef.dao.removeConfig(SYNC_DEVICE_FOLDER_FILE_ID_KEY)
            }
        }

        var preliminarySyncFolderId: String? = null
        if(syncFolderId == null) {
            adapter.listFiles(name = syncFolderName)
                .firstOrNull()?.id?.also {
                    preliminarySyncFolderId = it
                    initialOperation = InitialOperation.FETCH_INITIAL
                }?:let {
                initialOperation = InitialOperation.CREATE_NEW
            }

            if (initialOperation == InitialOperation.FETCH_INITIAL) {
                Log.i(TAG, "uiMutex ahead ${dbDef.categoryName}...")
                initialOperation = uiMutex.withLock {
                    Log.i(TAG, "... got through uiMutex ${dbDef.categoryName}!")
                    withContext(Dispatchers.Main) {
                        askInitialSyncOperation(dbDef.category)
                    }
                }
                if (initialOperation == null) {
                    dbDef.category.setSyncEnabled(settings, false)
                    throw CancelStartedSync()
                } else {
                    dbDef.dao.setConfig(SYNC_FOLDER_FILE_ID_KEY, preliminarySyncFolderId!!)
                    syncFolderId = preliminarySyncFolderId
                    adapter.makeSyncFolderKnown(dbDef, syncFolderName, syncFolderId)
                }
            }
        }

        suspend fun createNewSyncFolder(): String {
            Log.i(TAG, "Creating new sync folder ${dbDef.categoryName} $syncFolderName")

            // If there is already sync folder, let's remove it (and its contents)
            if(preliminarySyncFolderId != null) {
                Log.i(TAG, "Deleting earlier sync folder $preliminarySyncFolderId")
                adapter.delete(preliminarySyncFolderId)
            }

            return adapter.createNewFolder(syncFolderName).id.also {
                Log.i(TAG, "Global sync folder id $it")
                adapter.makeSyncFolderKnown(dbDef, syncFolderName, it)
                dbDef.dao.setConfig(SYNC_FOLDER_FILE_ID_KEY, it)
                syncFolderId = it
            }
        }

        suspend fun createNewDeviceSyncFolder() {
            val deviceIdentifier = env.deviceIdentifier
            Log.i(TAG, "Creating new device sync folder $syncFolderName/$deviceIdentifier")
            adapter.createNewFolder(
                name = deviceIdentifier,
                parentId = syncFolderId,
            )
                .id.also {
                    Log.i(TAG, "This device sync folder id $it")
                    dbDef.dao.setConfig(SYNC_DEVICE_FOLDER_FILE_ID_KEY, it)
                }
        }

        when(initialOperation) {
            InitialOperation.CREATE_NEW -> {
                createNewSyncFolder()
                createNewDeviceSyncFolder()
                createAndUploadInitial(dbDef)
            }
            InitialOperation.FETCH_INITIAL -> {
                createNewDeviceSyncFolder()
                fetchAndRestoreInitial(dbDef)
            }
            null -> {}
        }
    }

    private suspend fun createAndUploadInitial(dbDef: SyncableDatabaseAccessor<*>) {
        dbDef.dao.clearLog()
        dbDef.dao.clearSyncStatus()
        dbDef.localDb.useWriterConnectionMarked { it.exec("VACUUM;") }
        val tmpFile = createTempFile(env.tmpDir.toPath(), "andbible-", ".tmp")
        val gzippedTmpFile = createTempFile(env.tmpDir.toPath(), "andbible-", ".tmp")
        FileSystem.SYSTEM.copy(dbDef.localDbFile.toOkioPath(), tmpFile) // L1b-handover(D2): SyncableDatabaseAccessor.localDbFile becomes Path
        net.bible.service.common.ArchiveUtils.gzipFile(tmpFile, gzippedTmpFile)
        FileSystem.SYSTEM.delete(tmpFile)

        Log.i(TAG, "uploading initial db, ${dbDef.categoryName}, ${gzippedTmpFile.size()}")
        val result = adapter.upload(
            name = INITIAL_BACKUP_FILENAME,
            file = gzippedTmpFile,
            parentId = dbDef.dao.getString(SYNC_FOLDER_FILE_ID_KEY)!!
        )
        dbDef.dao.addStatus(SyncStatus(env.deviceIdentifier, 0, result.size, result.createdTime))
        FileSystem.SYSTEM.delete(gzippedTmpFile)
    }

    private suspend fun fetchAndRestoreInitial(dbDef: SyncableDatabaseAccessor<*>) {
        val syncFolderId = dbDef.dao.getString(SYNC_FOLDER_FILE_ID_KEY)!!
        val deviceFolderId = dbDef.dao.getString(SYNC_DEVICE_FOLDER_FILE_ID_KEY)!!
        val adapterConfigs = adapter.getConfigs(dbDef)
        val initialFile = adapter
            .listFiles(
                parentsIds = listOf(dbDef.dao.getString(SYNC_FOLDER_FILE_ID_KEY)!!),
                name = INITIAL_BACKUP_FILENAME
            )
            .first()
        val gzippedTmpFile = createTempFile(env.tmpDir.toPath(), "andbible-", ".tmp")
        adapter.download(initialFile.id, FileSystem.SYSTEM.sink(gzippedTmpFile))
        Log.i(TAG, "Downloaded initial db for ${dbDef.categoryName}, ${gzippedTmpFile.size()}")
        val tmpFile = createTempFile(env.tmpDir.toPath(), "andbible-", ".tmp")
        net.bible.service.common.ArchiveUtils.gunzipFile(gzippedTmpFile, tmpFile)
        FileSystem.SYSTEM.delete(gzippedTmpFile)
        val initialDbVersion = openSqlite(tmpFile.toString()).use { it.queryLong("PRAGMA user_version")!!.toInt() }
        val localVersion = dbDef.version()
        if(initialDbVersion > localVersion) {
            runCatching { FileSystem.SYSTEM.delete(tmpFile) }
            dialogs.await(AppDialogRequest.Message(null, cantFetchString(strings.syncCategoryContents(dbDef.category.name)), strings.okay, cancellable = false))
            dbDef.category.setSyncEnabled(settings, false)
            Log.e(TAG, "Initial db version is newer than this app version: $initialDbVersion > $localVersion")
            throw CancelStartedSync()
        } else {
            swapInInitialDb(dbDef, tmpFile) {
                dbDef.dao.setConfig(SYNC_FOLDER_FILE_ID_KEY, syncFolderId)
                dbDef.dao.setConfig(SYNC_DEVICE_FOLDER_FILE_ID_KEY, deviceFolderId)
                dbDef.dao.setConfig(LAST_PATCH_WRITTEN_KEY, Clock.System.now().toEpochMilliseconds())
                dbDef.dao.setConfig(adapterConfigs)
                dropTriggers(dbDef)
                createTriggers(dbDef)
                dbDef.dao.addStatus(
                    SyncStatus(
                        env.deviceIdentifier,
                        0,
                        initialFile.size,
                        initialFile.createdTime
                    )
                )
            }
        }
    }

    /** The initial-download swap: close, copy the downloaded file over the local one, reopen. */
    internal suspend fun swapInInitialDb(
        dbDef: SyncableDatabaseAccessor<*>,
        downloaded: Path,
        replacement: net.bible.service.llm.AiDatabaseLifetime.Replacement? = null,
        afterReset: suspend () -> Unit,
    ) {
        try {
            // F113: the same race class as a backup restore. A save between the close and the live
            // repository's reload would write the pre-download windows over the downloaded file, and the
            // freshly created triggers would then log it for upload. The epoch bump freezes saving until
            // [workspaceRefreshRequired] -> `loadFromDb`.
            DatabaseContainer.replacingDatabases(replacement) { token ->
                dbDef.localDb.close()
                FileSystem.SYSTEM.delete(dbDef.localDbFile.toOkioPath(), mustExist = false) // replace the file, not rewrite it in place; L1b-handover(D2): SyncableDatabaseAccessor.localDbFile becomes Path
                FileSystem.SYSTEM.copy(downloaded, dbDef.localDbFile.toOkioPath()) // L1b-handover(D2): SyncableDatabaseAccessor.localDbFile becomes Path
                runCatching { FileSystem.SYSTEM.delete(downloaded) } // best-effort: must not leave the local db closed
                dbDef.resetLocalDbOwned(token)
                afterReset()
            }
        } finally {
            // Categories swap concurrently (`asyncMap`), and a restore may hold its own replace around a
            // sync: release once, when the last replace has ended. Also posts when the swap throws: the
            // epoch is already bumped, so the repository must reload to end the save freeze.
            if (!DatabaseContainer.replacing) notifyWorkspaceRefreshRequired()
        }
    }

    private val syncMutex = Mutex()
    private val startLock = SynchronizedObject()

    fun start(): kotlinx.coroutines.Deferred<Boolean> = synchronized(startLock) { // L1-common-ok: atomicfu lock
        syncStarted?.takeUnless { it.isCompleted }?.let { return@synchronized it }
        scheduler.startDatabaseSync().also { syncStarted = it }
    }

    @kotlin.concurrent.Volatile private var syncStarted: kotlinx.coroutines.Deferred<Boolean>? = null

    suspend fun waitUntilFinished(fromService: Boolean = false) {
        if(!fromService) {
            syncStarted?.await()
        }
        syncMutex.withLock {  }
    }

    /** Drops the adapter so the next foreground sync signs in again (and can prompt), keeping all sync state. */
    internal fun dropAdapterForReconnect() { _adapter = null }

    /**
     * Notifies once per sync. Only records [needsReconnect]; the adapter is dropped after every
     * category has finished (see [synchronize]) so concurrent categories never see a null adapter.
     */
    private fun reportUserFacing(
        e: CloudSyncUserFacingException,
        reported: AtomicBoolean,
        needsReconnect: AtomicBoolean,
    ) {
        if (e.requiresReconnect) needsReconnect.value = true
        if (reported.compareAndSet(false, true)) UserMessages.errorNotification(e.message ?: "", showReportButton = false)
    }

    internal suspend fun synchronize() = withContext(Dispatchers.IO) {
        documentSettings.ensureLoaded()
        if(!signedIn) {
            Log.i(TAG, "Not signed in")
            return@withContext
        }
        if(syncMutex.isLocked) {
            Log.i(TAG, "Already synchronizing")
            return@withContext
        }
        syncMutex.withLock {
            Log.i(TAG, "Synchronizing starts")
            val timerStart = Clock.System.now().toEpochMilliseconds()

            val reported = atomic(false)
            val needsReconnect = atomic(false)
            try {
                DatabaseContainer.databaseAccessorFactories.asyncMap {
                    val dbDef = it.invoke()
                    if(!dbDef.category.isSyncEnabled(settings)) return@asyncMap
                    if(dbDef.dao.getLong("disabledForVersion") == dbDef.version().toLong()) return@asyncMap
                    try {
                        initializeSync(dbDef)
                    } catch (e: CancelStartedSync) {
                        Log.e(TAG, "Sync cancelled ${dbDef.categoryName}")
                        return@asyncMap
                    } catch (e: IOException) {
                        Log.e(TAG, "IOException (probably network down)", e)
                        return@asyncMap
                    } catch (e: CloudSyncUserFacingException) {
                        reportUserFacing(e, reported, needsReconnect)
                        return@asyncMap
                    } catch (e: Exception) {
                        Log.e(TAG, "Some other exception happened in initializeSync!", e)
                        UserMessages.errorNotification(strings.syncError)
                        return@asyncMap
                    }
                    try {
                        createAndUploadNewPatch(dbDef)
                    } catch (e: IOException) {
                        Log.e(TAG, "IOException", e)
                        return@asyncMap
                    } catch (e: CloudSyncUserFacingException) {
                        reportUserFacing(e, reported, needsReconnect)
                        return@asyncMap
                    } catch (e: Exception) {
                        Log.e(TAG, "createAndUploadNewPatch failed due to error", e)
                        UserMessages.errorNotification(strings.syncError)
                    }
                    try {
                        try {
                            downloadAndApplyNewPatches(dbDef)
                        } catch (e: PatchFilesSkipped) {
                            Log.i(TAG, "Patch files skipped! Retrying download and apply!")
                            dbDef.dao.setConfig(LAST_SYNCHRONIZED_KEY, 0)
                            downloadAndApplyNewPatches(dbDef)
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "downloadAndApplyNewPatches failed due to IOException", e)
                    } catch (e: IncompatiblePatchVersion) {
                        UserMessages.errorNotification(cantFetchString(strings.syncCategoryContents(dbDef.category.name)), showReportButton = false)
                        dbDef.dao.setConfig("disabledForVersion", dbDef.version().toLong())
                        return@asyncMap
                    } catch (e: CloudSyncUserFacingException) {
                        reportUserFacing(e, reported, needsReconnect)
                        return@asyncMap
                    } catch (e: Exception) {
                        Log.e(TAG, "downloadAndApplyNewPatches failed due to error", e)
                        UserMessages.errorNotification(strings.syncError)
                    }
                }
                try {
                    documents.get().runSync(
                        download = documentSettings.autoDownload,
                        upload = documentSettings.autoUpload,
                        delete = documentSettings.autoDelete,
                        manual = false,
                    )
                } catch (e: CloudSyncUserFacingException) {
                    reportUserFacing(e, reported, needsReconnect)
                } catch (e: Exception) {
                    Log.e(TAG, "Document sync pull failed", e)
                }
            } finally {
                if (needsReconnect.value) dropAdapterForReconnect()
            }
            Log.i(TAG, "Synchronization complete in ${(Clock.System.now().toEpochMilliseconds() - timerStart)/1000.0} seconds.")
        }
    }

    private fun cantFetchString(contentDescription: String): String {
       val s1 = strings.syncCantFetch
       val s2 = strings.syncDisabling(contentDescription)
       val s3 = strings.syncUpdateApp
       return "$s1 $s2 $s3"
    }

    private val patchFilePattern = Regex("""(\d+)\.((\d+)\.)?sqlite3\.gz""")
    private fun patchNumber(name: String): Long = patchFilePattern.find(name)!!.groups[1]!!.value.toLong()
    private fun versionNumber(name: String): Long = patchFilePattern.find(name)?.groups?.get(3)?.value?.toLong() ?: 1

    class PatchFilesSkipped: Exception()
    class IncompatiblePatchVersion: Exception()
    private suspend fun downloadAndApplyNewPatches(dbDef: SyncableDatabaseAccessor<*>) = withContext(Dispatchers.IO) {
        val lastSynchronized = dbDef.dao.getLong(LAST_SYNCHRONIZED_KEY)?: 0
        val syncFolder = dbDef.dao.getString(SYNC_FOLDER_FILE_ID_KEY)!!
        val deviceSyncFolder = dbDef.dao.getString(SYNC_DEVICE_FOLDER_FILE_ID_KEY)!!

        Log.i(TAG, "Downloading new patches ${dbDef.categoryName}, last Synced: $lastSynchronized. ")
        Log.i(TAG, "This device ${env.deviceIdentifier} (id: ${deviceSyncFolder})")

        dbDef.dao.setConfig(LAST_SYNCHRONIZED_KEY, Clock.System.now().toEpochMilliseconds())

        val devicePatchFolders = adapter.getFolders(syncFolder)

        if (devicePatchFolders.isEmpty()) {
            Log.i(TAG, "No patch folders yet")
            return@withContext
        }
        Log.i(TAG, "Folders \n${devicePatchFolders.joinToString("\n") { "${it.id} ${it.name}" }}")

        val patchResults = adapter.listFiles(
            parentsIds = devicePatchFolders.map { it.id },
            createdTimeAtLeast = lastSynchronized
        ).sortedBy { it.createdTime }

        Log.i(TAG, "Number of patch files in result set: ${patchResults.size}")

        class FolderWithMeta(val folder: CloudFile, val loadedCount: Long)

        val folders = devicePatchFolders
            .map {
                FolderWithMeta(it, dbDef.dao.lastPatchNum(it.name) ?: 0)
            }.associateBy { it.folder.id }
        Log.i(TAG, "Folder counts: \n${folders.values.joinToString("\n") { "${it.folder.name}: ${it.loadedCount}" }}")
        Log.i(TAG, "Patches before filter: \n${patchResults.joinToString("\n") { it.name }}")

        class DriveFileWithMeta(val file: CloudFile, val parentFolderName: String)

        val localVersion = dbDef.version()
        val patches = patchResults.mapNotNull {
            val parentFolderId = it.parentId
            val folderWithMeta = folders[parentFolderId]!!
            val num = patchNumber(it.name)
            if(versionNumber(it.name) > localVersion) {
                // We need to load next time also last set of patches.
                dbDef.dao.setConfig(LAST_SYNCHRONIZED_KEY, lastSynchronized)
                throw IncompatiblePatchVersion()
            }
            val existing = dbDef.dao.syncStatus(folderWithMeta.folder.name, patchNumber(it.name))
            if (existing == null && num > folderWithMeta.loadedCount) {
                DriveFileWithMeta(it, folderWithMeta.folder.name)
            } else null
        }.sortedBy { it.file.createdTime }

        if(patches.isEmpty()) {
            Log.i(TAG, "No patches, returning")
            return@withContext
        }

        for(f in folders.values) {
            val firstPatch = patches.firstOrNull { it.parentFolderName == f.folder.name } ?: continue
            if(patchNumber(firstPatch.file.name) > f.loadedCount + 1) {
                // We are not in sync; need to load older patches.
                throw PatchFilesSkipped()
            }
        }

        Log.i(TAG, "Patches after filter: \n${patches.joinToString("\n") { "${it.file.name} ${it.parentFolderName}" }}")
        Log.i(TAG, "Number of patch files after filtering: ${patches.size}")

        val downloadedFiles = patches.asyncMap(6) { f ->
            Log.i(TAG, "Downloading ${f.file.name}, ${f.file.size} bytes")
            val tmpFile = createTempFile(env.tmpDir.toPath(), "andbible-", ".tmp")
            adapter.download(f.file.id, FileSystem.SYSTEM.sink(tmpFile))
            tmpFile
        }

        val syncStatuses = patches.map {
            SyncStatus(it.parentFolderName, patchNumber(it.file.name), it.file.size, it.file.createdTime)
        }

        applyPatchesForDatabase(dbDef, env, *downloadedFiles.toTypedArray())
        downloadedFiles.forEach { runCatching { FileSystem.SYSTEM.delete(it) } }
        dbDef.reactToUpdates(lastSynchronized)
        dbDef.dao.addStatuses(syncStatuses)
    }
    private suspend fun createAndUploadNewPatch(dbDef: SyncableDatabaseAccessor<*>) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Uploading new patches ${dbDef.categoryName}")
        val file = createPatchForDatabase(dbDef, env, false)?: return@withContext
        val syncDeviceFolderId = dbDef.dao.getString(SYNC_DEVICE_FOLDER_FILE_ID_KEY)!!
        val count = (dbDef.dao.lastPatchNum(env.deviceIdentifier)?: 0) + 1
        val fileName = "$count.${dbDef.version()}.sqlite3.gz"
        val lastWritten = Clock.System.now().toEpochMilliseconds();

        val result = try {
            adapter.upload(fileName, file, syncDeviceFolderId)
        } catch (e: Exception) {
            Log.e(TAG, "Uploading failed due to error", e)
            runCatching { FileSystem.SYSTEM.delete(file) }
            throw e
        }
        Log.i(TAG, "Uploaded ${dbDef.categoryName} $fileName, ${file.size()} bytes, ${result.createdTime}")
        dbDef.dao.setConfig(LAST_PATCH_WRITTEN_KEY, lastWritten)
        dbDef.dao.addStatus(SyncStatus(env.deviceIdentifier, count, file.size(), result.createdTime))
        runCatching { FileSystem.SYSTEM.delete(file) }
    }

    suspend fun hasChanges(): Boolean =
        DatabaseContainer.databaseAccessorFactories.asyncMap {
            val dbDef = it.invoke()
            dbDef.category.isSyncEnabled(settings) && dbDef.hasChanges()
        }.any { it }

    suspend fun bytesUsed(): Long =
        DatabaseContainer.databaseAccessorFactories.asyncMap {
            val dbDef = it.invoke()
            dbDef.bytesUsed()
        }.sum() + documents.get().cloudBytesUsed()

}

/** Size in bytes; 0 when the file is absent (as `File.length()`). */
private fun Path.size(): Long = FileSystem.SYSTEM.metadataOrNull(this)?.size ?: 0L
