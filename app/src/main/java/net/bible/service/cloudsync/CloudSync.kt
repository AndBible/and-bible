/*
 * Copyright (c) 2023 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.util.Log
import io.requery.android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.database.SyncStatus
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.page.application
import net.bible.service.common.CommonUtils
import net.bible.service.common.asyncMap
import net.bible.service.db.DatabaseContainer
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.IllegalStateException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

const val GZIP_MIMETYPE = "application/gzip"

const val SYNC_FOLDER_FILE_ID_KEY = "syncId"
const val SYNC_DEVICE_FOLDER_FILE_ID_KEY = "deviceFolderId"
const val LAST_PATCH_WRITTEN_KEY = "lastPatchWritten"
const val LAST_SYNCHRONIZED_KEY = "lastSynchronized"
const val INITIAL_BACKUP_FILENAME = "initial.sqlite3.gz"
const val TAG = "DeviceSync"

class CancelStartedSync: Exception()
class WorkspaceRefreshRequired {}

enum class CloudAdapters {
    GOOGLE_DRIVE;

    val displayName: Int get() = when(this) {
        GOOGLE_DRIVE -> R.string.adapters_google_drive
    }
    val newAdapter: CloudAdapter get() = when(this) {
        GOOGLE_DRIVE -> {
            val adapter = Class.forName("net.bible.service.cloudsync.googledrive.GoogleDriveCloudAdapter")
            val constructor = adapter.getDeclaredConstructor()
            constructor.newInstance() as CloudAdapter
        }
    }

    companion object {
        var current: CloudAdapters
            get() {
                val adapterStr = CommonUtils.settings.getString("sync_adapter", "GOOGLE_DRIVE")!!
                return CloudAdapters.valueOf(adapterStr)
            }
            set(value) {
                CommonUtils.settings.setString("sync_adapter", value.name)
            }
    }
}

object CloudSync {
    private var _adapter: CloudAdapter? = null
    private val adapter: CloudAdapter get() = _adapter!!

    val signedIn get() = _adapter != null && adapter.signedIn

    private val signInMutex = Mutex()
    suspend fun signIn(activity: ActivityBase) {
        if(signInMutex.isLocked) {
            Log.i(TAG, "Already signing in!")
            return
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
            Dialogs.showMsg2(activity, activity.getString(R.string.sign_in_failed) + " " + (errorMessage?:""))
        }
    }
    suspend fun signOut() {
        adapter.signOut()
        _adapter = null
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
    private suspend fun initializeSync(dbDef: SyncableDatabaseAccessor<*>) {
        var initialOperation: InitialOperation?= null

        val syncFolderName = "${application.applicationInfo.packageName}-sync-${dbDef.categoryName}"
        var syncFolderId = dbDef.dao.getString(SYNC_FOLDER_FILE_ID_KEY)
        if(syncFolderId != null) {
            // Verify if id is found in Drive
            try {
                adapter.get(syncFolderId)
            } catch (e: FileNotFoundException) {
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
                        val q1 = suspendCoroutine {
                            val containsStr = activity.getString(dbDef.category.contentDescription)
                            AlertDialog.Builder(activity)
                                .setTitle(R.string.cloud_sync_title)
                                .setMessage(activity.getString(R.string.overrideBackup, containsStr))
                                .setPositiveButton(R.string.cloud_fetch_and_restore_initial) { _, _ ->
                                    it.resume(InitialOperation.FETCH_INITIAL)
                                }
                                .setNegativeButton(R.string.cloud_create_new) { _, _ ->
                                    it.resume(InitialOperation.CREATE_NEW)
                                }
                                .setNeutralButton(R.string.cloud_disable_sync) { _, _ ->
                                    it.resume(null)
                                }
                                .setCancelable(false)
                                .create()
                                .show()
                        }
                        if(q1 != null) {
                            val message = when(q1) {
                                InitialOperation.FETCH_INITIAL -> R.string.are_you_sure_reset_local
                                InitialOperation.CREATE_NEW -> R.string.are_you_sure_reset_cloud
                            }
                            val msgString = activity.getString(message, activity.getString(dbDef.category.contentDescription))
                            val confirmed = Dialogs.simpleQuestion(activity, msgString)
                            if(!confirmed) {
                                return@withContext null
                            }
                        }
                        q1
                    }
                }
                if (initialOperation == null) {
                    dbDef.category.syncEnabled = false
                    throw CancelStartedSync()
                } else {
                    dbDef.dao.setConfig(SYNC_FOLDER_FILE_ID_KEY, preliminarySyncFolderId!!)
                    syncFolderId = preliminarySyncFolderId
                }
            }
        }

        fun createNewSyncFolder(): String {
            Log.i(TAG, "Creating new sync folder ${dbDef.categoryName} $syncFolderName")

            // If there is already sync folder, let's remove it (and its contents)
            if(preliminarySyncFolderId != null) {
                Log.i(TAG, "Deleting earlier sync folder $preliminarySyncFolderId")
                adapter.delete(preliminarySyncFolderId!!)
            }

            return adapter.createNewFolder(syncFolderName).id.also {
                Log.i(TAG, "Global sync folder id $it")
                dbDef.dao.setConfig(SYNC_FOLDER_FILE_ID_KEY, it)
                syncFolderId = it
            }
        }

        fun createNewDeviceSyncFolder() {
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
        val deviceFolderId = dbDef.dao.getString(SYNC_DEVICE_FOLDER_FILE_ID_KEY)!!
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
            dbDef.localDb.close()
            tmpFile.copyTo(dbDef.localDbFile, overwrite = true)
            tmpFile.delete()
            dbDef.resetLocalDb()
            dbDef.dao.setConfig(SYNC_DEVICE_FOLDER_FILE_ID_KEY, deviceFolderId)
            dbDef.dao.setConfig(LAST_PATCH_WRITTEN_KEY, System.currentTimeMillis())
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
            ABEventBus.post(WorkspaceRefreshRequired())
        }
    }

    private val syncMutex = Mutex()

    fun start(): CompletableDeferred<Boolean> = synchronized(this) {
        if (syncStarted != null || syncMutex.isLocked) {
            Log.i(TAG, "Sync already started!")
        }
        val syncStarted = CompletableDeferred<Boolean>()
        this.syncStarted = syncStarted

        val intent = Intent(application, SyncService::class.java)
        intent.action = SyncService.START_SERVICE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                application.startForegroundService(intent)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Could not start sync due to ", e)
                syncStarted.complete(false)
                this.syncStarted = null
                return@synchronized syncStarted
            }
            Log.i(TAG, "Foreground service started")
        } else {
            application.startService(intent)
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
                } catch (e: Exception) {
                    Log.e(TAG, "Some other exception happened in initializeSync!", e)
                    ABEventBus.post(BibleApplication.ErrorNotificationEvent(R.string.sync_error))
                    return@asyncMap
                }
                try {
                    createAndUploadNewPatch(dbDef)
                } catch (e: IOException) {
                    Log.e(TAG, "IOException", e)
                    return@asyncMap
                } catch (e: Exception) {
                    Log.e(TAG, "createAndUploadNewPatch failed due to error", e)
                    ABEventBus.post(BibleApplication.ErrorNotificationEvent(R.string.sync_error))
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
                    ABEventBus.post(BibleApplication.ErrorNotificationEvent(cantFetchString(dbDef.category.contentDescription), showReportButton = false))
                    dbDef.dao.setConfig("disabledForVersion", dbDef.version.toLong())
                    return@asyncMap
                } catch (e: Exception) {
                    Log.e(TAG, "downloadAndApplyNewPatches failed due to error", e)
                    ABEventBus.post(BibleApplication.ErrorNotificationEvent(R.string.sync_error))
                }
            }
            Log.i(TAG, "Synchronization complete in ${(System.currentTimeMillis() - timerStart)/1000.0} seconds.")
        }
    }

    private fun cantFetchString(contentDescriptionId: Int): String {
       val app = application
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

        val folderResult = adapter.getFolders(syncFolder)

        if (folderResult.isEmpty()) {
            Log.i(TAG, "No patch folders yet")
            return@withContext
        }
        Log.i(TAG, "Folders \n${folderResult.joinToString("\n") { "${it.id} ${it.name}" }}")

        val patchResults = adapter.listFiles(
            parentsIds = folderResult.map { it.id },
            createdTimeAtLeast = lastSynchronized
        ).sortedBy { it.createdTime }

        Log.i(TAG, "Number of patch files in result set: ${patchResults.size}")

        class FolderWithMeta(val folder: CloudFile, val loadedCount: Long)

        val folders = folderResult
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
        }.sum()

}
