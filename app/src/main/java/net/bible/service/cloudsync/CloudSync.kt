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

import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.VisibleForTesting
import io.requery.android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.control.event.UserMessages
import net.bible.android.database.SyncStatus
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.base.Dialogs
import net.bible.service.cloudsync.nextcloud.NextCloudAdapter
import net.bible.service.cloudsync.webdav.AndroidWebDavSignInUi
import net.bible.service.cloudsync.webdav.PrefsWebDavStateStore
import net.bible.service.cloudsync.webdav.WebDavCloudAdapter
import net.bible.service.cloudsync.webdav.WebDavConfig
import net.bible.service.cloudsync.documents.DocumentSync
import net.bible.service.cloudsync.documents.DocumentSyncService
import net.bible.service.cloudsync.documents.DocumentSyncSettings
import net.bible.service.common.BuildVariant
import net.bible.service.common.CommonUtils
import net.bible.service.common.asyncMap
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.event.EventSource
import net.bible.sharedcore.event.Events
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import org.koin.java.KoinJavaComponent
import java.io.File
import java.io.IOException
import kotlin.IllegalStateException

const val GZIP_MIMETYPE = "application/gzip"

const val SYNC_FOLDER_FILE_ID_KEY = "syncId"
const val SYNC_DEVICE_FOLDER_FILE_ID_KEY = "deviceFolderId"
const val LAST_PATCH_WRITTEN_KEY = "lastPatchWritten"
const val LAST_SYNCHRONIZED_KEY = "lastSynchronized"
const val INITIAL_BACKUP_FILENAME = "initial.sqlite3.gz"
const val TAG = "DeviceSync"

class CancelStartedSync: Exception()

val app get() = BibleApplication.application

enum class CloudAdapters(val isEnabled: Boolean = true) {
    GOOGLE_DRIVE(!BuildVariant.DistributionChannel.isFdroid), NEXT_CLOUD, WEBDAV;

    val displayName: String get() = when(this) {
        GOOGLE_DRIVE -> app.getString(R.string.adapters_google_drive)
        NEXT_CLOUD -> app.getString(R.string.adapters_next_cloud)
        WEBDAV -> app.getString(R.string.adapters_webdav)
    }
    val newAdapter: CloudAdapter get() = when(this) {
        GOOGLE_DRIVE -> {
            val adapter = Class.forName("net.bible.service.cloudsync.googledrive.GoogleDriveCloudAdapter")
            val constructor = adapter.getDeclaredConstructor()
            constructor.newInstance() as CloudAdapter
        }
        NEXT_CLOUD -> CommonUtils.realSharedPreferences.let { prefs ->
            NextCloudAdapter(
                prefs.getString("cloud_sync_server_url", null),
                prefs.getString("cloud_sync_username", null),
                prefs.getString("cloud_sync_password", null),
                prefs.getString("cloud_sync_folder_path", null)
            )
        }
        WEBDAV -> CommonUtils.realSharedPreferences.let { prefs ->
            WebDavCloudAdapter(WebDavConfig.fromPreferences(prefs), PrefsWebDavStateStore(prefs), AndroidWebDavSignInUi())
        }
    }

    companion object {
        val allEnabled: List<CloudAdapters> get() = CloudAdapters.entries.filter { it.isEnabled }
        var current: CloudAdapters
            get() {
                val adapterStr = CommonUtils.settings.getString("sync_adapter")
                return adapterStr?.let { CloudAdapters.valueOf(it) }?: allEnabled.first()
            }
            set(value) {
                CommonUtils.settings.setString("sync_adapter", value.name)
            }
    }
}

object CloudSync {
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

    private val dialogs: AppDialogController get() = KoinJavaComponent.get(AppDialogController::class.java)

    // AppDialogRequest.Options ids for initializeSync's "which way?" question (Task 24 Step 3).
    private const val FETCH_VALUE = "fetch"
    private const val CREATE_VALUE = "create"
    private const val DISABLE_VALUE = "disable"

    private var _adapter: CloudAdapter? = null
    private val adapter: CloudAdapter get() = _adapter!!

    val signedIn get() = _adapter?.signedIn == true

    internal val cloudAdapter: CloudAdapter? get() = _adapter

    const val DOCUMENTS_SYNC_FOLDER_NAME_SUFFIX = "documents"

    suspend fun documentsSyncFolderId(): String? {
        val adapter = _adapter ?: return null
        val name = "${app.applicationInfo.packageName}-sync-$DOCUMENTS_SYNC_FOLDER_NAME_SUFFIX"
        val existing = adapter.listFiles(name = name).firstOrNull()
        return existing?.id ?: adapter.createNewFolder(name).id
    }

    private val signInMutex = Mutex()
    suspend fun signIn(activity: ActivityBase): Boolean? {
        if(signInMutex.isLocked) {
            Log.i(TAG, "Already signing in!")
            return null
        }
        var errorMessage: String? = null
        val success = signInMutex.withLock {
            _adapter = CloudAdapters.current.newAdapter
            try {
                adapter.signIn(activity)
            } catch (e: Exception){
                errorMessage = e.message
                false
            }
        }
        if(!success) {
            _adapter = null
            Dialogs.showMsg2(activity, activity.getString(R.string.sign_in_failed) + " " + (errorMessage?:""))
        }
        return success
    }
    suspend fun signOut() {
        _adapter?.signOut()
        _adapter = null
        // Stop any in-flight document transfers before wiping their state: they belong to the
        // account just disconnected. store() already returns null now (_adapter == null), so a
        // running drain would no-op anyway, but cancelling it promptly avoids a needless drain.
        DocumentSyncService.stop(app)
        DocumentSync.onSignOut()
        DatabaseContainer.databaseAccessorFactories.asyncMap {
            val dbDef = it.invoke()
            val category = dbDef.category
            category.syncEnabled = false
            dbDef.dao.clearSyncStatus()
            dbDef.dao.clearSyncConfiguration()
        }
    }

    enum class InitialOperation {FETCH_INITIAL, CREATE_NEW}
    private val uiMutex = Mutex()

    /**
     * `initializeSync`'s "fetch from cloud, create new, or disable?" question (Task 24 Step 3): an
     * owner-less `AppDialogController` dialog in place of the old `android.app.AlertDialog`,
     * followed by its existing confirmation (`Dialogs.simpleQuestion`, already a shim). Declining
     * that confirmation reverts to null, same as choosing "Cancel & disable synchronization"
     * outright. `internal`, not private, so a test can pin the mapping directly against just a
     * [SyncableDatabaseDefinition] -- without building a whole [SyncableDatabaseAccessor].
     */
    internal suspend fun askInitialSyncOperation(activity: ActivityBase, category: SyncableDatabaseDefinition): InitialOperation? {
        val containsStr = activity.getString(category.contentDescription)
        val dialogResult = dialogs.await(
            AppDialogRequest.Options(
                title = activity.getString(R.string.cloud_sync_title),
                message = activity.getString(R.string.overrideBackup, containsStr),
                options = listOf(
                    SettingsItem.Choice(FETCH_VALUE, activity.getString(R.string.cloud_fetch_and_restore_initial)),
                    SettingsItem.Choice(CREATE_VALUE, activity.getString(R.string.cloud_create_new)),
                    SettingsItem.Choice(DISABLE_VALUE, activity.getString(R.string.cloud_disable_sync)),
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
            val message = when (q1) {
                InitialOperation.FETCH_INITIAL -> R.string.are_you_sure_reset_local
                InitialOperation.CREATE_NEW -> R.string.are_you_sure_reset_cloud
            }
            val msgString = activity.getString(message, activity.getString(category.contentDescription))
            val confirmed = Dialogs.simpleQuestion(activity, msgString)
            if (!confirmed) return null
        }
        return q1
    }

    private suspend fun initializeSync(dbDef: SyncableDatabaseAccessor<*>) {
        var initialOperation: InitialOperation?= null
        val syncFolderName = "${app.applicationInfo.packageName}-sync-${dbDef.categoryName}"

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
                    val activity = CurrentActivityHolder.currentActivity ?: throw CancelStartedSync()
                    withContext(Dispatchers.Main) {
                        askInitialSyncOperation(activity, dbDef.category)
                    }
                }
                if (initialOperation == null) {
                    dbDef.category.syncEnabled = false
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
                adapter.delete(preliminarySyncFolderId!!)
            }

            return adapter.createNewFolder(syncFolderName).id.also {
                Log.i(TAG, "Global sync folder id $it")
                adapter.makeSyncFolderKnown(dbDef, syncFolderName, it)
                dbDef.dao.setConfig(SYNC_FOLDER_FILE_ID_KEY, it)
                syncFolderId = it
            }
        }

        suspend fun createNewDeviceSyncFolder() {
            val deviceIdentifier = CommonUtils.deviceIdentifier
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
        dbDef.writableDb.query("VACUUM;").use {  }
        val tmpFile = CommonUtils.tmpFile
        val gzippedTmpFile = CommonUtils.tmpFile
        dbDef.localDbFile.copyTo(tmpFile, overwrite = true)
        CommonUtils.gzipFile(tmpFile, gzippedTmpFile)
        tmpFile.delete()

        Log.i(TAG, "uploading initial db, ${dbDef.categoryName}, ${gzippedTmpFile.length()}")
        val result = adapter.upload(
            name = INITIAL_BACKUP_FILENAME,
            file = gzippedTmpFile,
            parentId = dbDef.dao.getString(SYNC_FOLDER_FILE_ID_KEY)!!
        )
        dbDef.dao.addStatus(SyncStatus(CommonUtils.deviceIdentifier, 0, result.size, result.createdTime))
        gzippedTmpFile.delete()
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
        val gzippedTmpFile = CommonUtils.tmpFile
        gzippedTmpFile.outputStream().use { adapter.download(initialFile.id, it) }
        Log.i(TAG, "Downloaded initial db for ${dbDef.categoryName}, ${gzippedTmpFile.length()}")
        val tmpFile = CommonUtils.tmpFile
        CommonUtils.gunzipFile(gzippedTmpFile, tmpFile)
        gzippedTmpFile.delete()
        val initialDbVersion = SQLiteDatabase.openDatabase(tmpFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.version }
        if(initialDbVersion > dbDef.version) {
            tmpFile.delete()
            val activity = CurrentActivityHolder.currentActivity ?: throw CancelStartedSync()
            Dialogs.showMsg2(activity, cantFetchString(dbDef.category.contentDescription))
            dbDef.category.syncEnabled = false
            Log.e(TAG, "Initial db version is newer than this app version: $initialDbVersion > ${dbDef.version}")
            throw CancelStartedSync()
        } else {
            swapInInitialDb(dbDef, tmpFile) {
                dbDef.dao.setConfig(SYNC_FOLDER_FILE_ID_KEY, syncFolderId)
                dbDef.dao.setConfig(SYNC_DEVICE_FOLDER_FILE_ID_KEY, deviceFolderId)
                dbDef.dao.setConfig(LAST_PATCH_WRITTEN_KEY, System.currentTimeMillis())
                dbDef.dao.setConfig(adapterConfigs)
                dropTriggers(dbDef)
                createTriggers(dbDef)
                dbDef.dao.addStatus(
                    SyncStatus(
                        CommonUtils.deviceIdentifier,
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
        downloaded: File,
        afterReset: suspend () -> Unit,
    ) {
        try {
            // F113: the same race class as a backup restore. A save between the close and the live
            // repository's reload would write the pre-download windows over the downloaded file, and the
            // freshly created triggers would then log it for upload. The epoch bump freezes saving until
            // [workspaceRefreshRequired] -> `loadFromDb`.
            DatabaseContainer.replacingDatabases {
                dbDef.localDb.close()
                downloaded.copyTo(dbDef.localDbFile, overwrite = true)
                downloaded.delete()
                dbDef.resetLocalDb()
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

    fun start(): CompletableDeferred<Boolean> = synchronized(this) {
        if (syncStarted != null || syncMutex.isLocked) {
            Log.i(TAG, "Sync already started!")
        }
        val syncStarted = CompletableDeferred<Boolean>()
        this.syncStarted = syncStarted

        val intent = Intent(app, SyncService::class.java)
        intent.action = SyncService.START_SERVICE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                app.startForegroundService(intent)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Could not start sync due to ", e)
                syncStarted.complete(false)
                this.syncStarted = null
                return@synchronized syncStarted
            }
            Log.i(TAG, "Foreground service started")
        } else {
            app.startService(intent)
        }
        syncStarted
    }

    private var syncStarted: CompletableDeferred<Boolean>? = null

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
        reported: java.util.concurrent.atomic.AtomicBoolean,
        needsReconnect: java.util.concurrent.atomic.AtomicBoolean,
    ) {
        if (e.requiresReconnect) needsReconnect.set(true)
        if (reported.compareAndSet(false, true)) UserMessages.errorNotification(e.message ?: "", showReportButton = false)
    }

    internal suspend fun synchronize() = withContext(Dispatchers.IO) {
        if(!signedIn) {
            Log.i(TAG, "Not signed in")
            return@withContext
        }
        if(syncMutex.isLocked) {
            Log.i(TAG, "Already synchronizing")
            return@withContext
        }
        syncMutex.withLock {
            syncStarted?.complete(true)
            syncStarted = null
            Log.i(TAG, "Synchronizing starts")
            val timerStart = System.currentTimeMillis()

            val reported = java.util.concurrent.atomic.AtomicBoolean(false)
            val needsReconnect = java.util.concurrent.atomic.AtomicBoolean(false)
            DatabaseContainer.databaseAccessorFactories.asyncMap {
                val dbDef = it.invoke()
                if(!dbDef.category.syncEnabled) return@asyncMap
                if(dbDef.dao.getLong("disabledForVersion") == dbDef.version.toLong()) return@asyncMap
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
                    UserMessages.errorNotification(R.string.sync_error)
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
                    UserMessages.errorNotification(R.string.sync_error)
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
                    UserMessages.errorNotification(cantFetchString(dbDef.category.contentDescription), showReportButton = false)
                    dbDef.dao.setConfig("disabledForVersion", dbDef.version.toLong())
                    return@asyncMap
                } catch (e: CloudSyncUserFacingException) {
                    reportUserFacing(e, reported, needsReconnect)
                    return@asyncMap
                } catch (e: Exception) {
                    Log.e(TAG, "downloadAndApplyNewPatches failed due to error", e)
                    UserMessages.errorNotification(R.string.sync_error)
                }
            }
            try {
                DocumentSync.runSync(
                    download = DocumentSyncSettings.autoDownload,
                    upload = DocumentSyncSettings.autoUpload,
                    delete = DocumentSyncSettings.autoDelete,
                    manual = false,
                )
            } catch (e: CloudSyncUserFacingException) {
                reportUserFacing(e, reported, needsReconnect)
            } catch (e: Exception) {
                Log.e(TAG, "Document sync pull failed", e)
            }
            if (needsReconnect.get()) dropAdapterForReconnect()
            Log.i(TAG, "Synchronization complete in ${(System.currentTimeMillis() - timerStart)/1000.0} seconds.")
        }
    }

    private fun cantFetchString(contentDescriptionId: Int): String {
       val s1 = app.getString(R.string.sync_cant_fetch)
       val s2 = app.getString(R.string.sync_disabling, app.getString(contentDescriptionId))
       val s3 = app.getString(R.string.sync_update_app)
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
        Log.i(TAG, "This device ${CommonUtils.deviceIdentifier} (id: ${deviceSyncFolder})")

        dbDef.dao.setConfig(LAST_SYNCHRONIZED_KEY, System.currentTimeMillis())

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

        val patches = patchResults.mapNotNull {
            val parentFolderId = it.parentId
            val folderWithMeta = folders[parentFolderId]!!
            val num = patchNumber(it.name)
            if(versionNumber(it.name) > dbDef.version) {
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
            val tmpFile = CommonUtils.tmpFile
            tmpFile.outputStream().use {
                adapter.download(f.file.id, it)
            }
            tmpFile
        }

        val syncStatuses = patches.map {
            SyncStatus(it.parentFolderName, patchNumber(it.file.name), it.file.size, it.file.createdTime)
        }

        applyPatchesForDatabase(dbDef, *downloadedFiles.toTypedArray())
        downloadedFiles.forEach { it.delete() }
        dbDef.reactToUpdates(lastSynchronized)
        dbDef.dao.addStatuses(syncStatuses)
    }
    private suspend fun createAndUploadNewPatch(dbDef: SyncableDatabaseAccessor<*>) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Uploading new patches ${dbDef.categoryName}")
        val file = createPatchForDatabase(dbDef, false)?: return@withContext
        val syncDeviceFolderId = dbDef.dao.getString(SYNC_DEVICE_FOLDER_FILE_ID_KEY)!!
        val count = (dbDef.dao.lastPatchNum(CommonUtils.deviceIdentifier)?: 0) + 1
        val fileName = "$count.${dbDef.version}.sqlite3.gz"
        val lastWritten = System.currentTimeMillis();

        val result = try {
            adapter.upload(fileName, file, syncDeviceFolderId)
        } catch (e: Exception) {
            Log.e(TAG, "Uploading failed due to error", e)
            file.delete()
            throw e
        }
        Log.i(TAG, "Uploaded ${dbDef.categoryName} $fileName, ${file.length()} bytes, ${result.createdTime}")
        dbDef.dao.setConfig(LAST_PATCH_WRITTEN_KEY, lastWritten)
        dbDef.dao.addStatus(SyncStatus(CommonUtils.deviceIdentifier, count, file.length(), result.createdTime))
        file.delete()
    }

    suspend fun hasChanges(): Boolean =
        DatabaseContainer.databaseAccessorFactories.asyncMap {
            val dbDef = it.invoke()
            dbDef.category.syncEnabled && dbDef.hasChanges
        }.any { it }

    suspend fun bytesUsed(): Long =
        DatabaseContainer.databaseAccessorFactories.asyncMap {
            val dbDef = it.invoke()
            dbDef.bytesUsed
        }.sum() + DocumentSync.cloudBytesUsed()

}
