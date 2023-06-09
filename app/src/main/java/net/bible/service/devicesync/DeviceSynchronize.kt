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

package net.bible.service.devicesync

import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.api.client.util.DateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.database.SyncStatus
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.android.view.activity.page.application
import net.bible.service.common.CommonUtils
import net.bible.service.common.asyncMap
import net.bible.service.db.DatabaseContainer
import java.io.FileNotFoundException
import java.net.SocketTimeoutException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

const val webClientId = "533479479097-kk5bfksbgtfuq3gfkkrt2eb51ltgkvmn.apps.googleusercontent.com"
const val FOLDER_MIMETYPE = "application/vnd.google-apps.folder"
const val GZIP_MIMETYPE = "application/gzip"

suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        continuation.resume(result, null)
    }
    addOnFailureListener { exception ->
        continuation.resumeWithException(exception)
    }
    addOnCanceledListener {
        continuation.cancel()
    }
}

const val SYNC_FOLDER_FILE_ID_KEY = "syncId"
const val SYNC_DEVICE_FOLDER_FILE_ID_KEY = "deviceFolderId"
const val LAST_PATCH_WRITTEN_KEY = "lastPatchWritten"
const val LAST_SYNCHRONIZED_KEY = "lastSynchronized"
const val INITIAL_BACKUP_FILENAME = "initial.sqlite3.gz"

class CancelSync: Exception()

object DeviceSynchronize {
    val adapter: CloudAdapter = GoogleDrive()

    val signedIn get() = adapter.signedIn

    suspend fun signIn(activity: ActivityBase) = adapter.signIn(activity)
    suspend fun signOut() = adapter.signOut()

    enum class InitialOperation {FETCH_INITIAL, CREATE_NEW}
    private val uiMutex = Mutex()
    private suspend fun initializeSync(dbDef: SyncableDatabaseDefinition<*>) {
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
                Log.i(TAG, "uiMutex ahead...")
                initialOperation = uiMutex.withLock {
                    Log.i(TAG, "... got through uiMutex!")
                    val activity = CurrentActivityHolder.currentActivity ?: throw CancelSync()
                    withContext(Dispatchers.Main) {
                        suspendCoroutine {
                            val containsStr = activity.getString(dbDef.category.contentDescription)
                            AlertDialog.Builder(activity)
                                .setTitle(R.string.gdrive_title)
                                .setMessage(activity.getString(R.string.overrideBackup, containsStr))
                                .setPositiveButton(R.string.gdrive_fetch_and_restore_initial) { _, _ ->
                                    it.resume(InitialOperation.FETCH_INITIAL)
                                }
                                .setNegativeButton(R.string.gdrive_create_new) { _, _ ->
                                    it.resume(InitialOperation.CREATE_NEW)
                                }
                                .setNeutralButton(R.string.gdrive_disable_sync) { _, _ ->
                                    it.resume(null)
                                }
                                .create()
                                .show()
                        }
                    }
                }
                if (initialOperation == null) {
                    dbDef.category.enabled = false
                    throw CancelSync()
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

    private fun createAndUploadInitial(dbDef: SyncableDatabaseDefinition<*>) {
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
        dbDef.dao.addStatus(SyncStatus(CommonUtils.deviceIdentifier, 0, result.size, result.createdTime.value))
        gzippedTmpFile.delete()
    }

    private fun fetchAndRestoreInitial(dbDef: SyncableDatabaseDefinition<*>) {
        val deviceFolderId = dbDef.dao.getString(SYNC_DEVICE_FOLDER_FILE_ID_KEY)!!
        val fileId = adapter
            .listFiles(
                parentsIds = listOf(dbDef.dao.getString(SYNC_FOLDER_FILE_ID_KEY)!!),
                name = INITIAL_BACKUP_FILENAME
            )
            .first()
            .id
        val gzippedTmpFile = CommonUtils.tmpFile
        gzippedTmpFile.outputStream().use { adapter.download(fileId, it) }
        Log.i(TAG, "Downloaded initial db for ${dbDef.categoryName}, ${gzippedTmpFile.length()}")
        val tmpFile = CommonUtils.tmpFile
        CommonUtils.gunzipFile(gzippedTmpFile, tmpFile)
        gzippedTmpFile.delete()
        dbDef.localDb.close()
        tmpFile.copyTo(dbDef.localDbFile, overwrite = true)
        tmpFile.delete()
        dbDef.resetLocalDb()
        dbDef.dao.setConfig(SYNC_DEVICE_FOLDER_FILE_ID_KEY, deviceFolderId)
        dbDef.dao.setConfig(LAST_PATCH_WRITTEN_KEY, System.currentTimeMillis())
        DatabaseSync.dropTriggers(dbDef)
        DatabaseSync.createTriggers(dbDef)
        ABEventBus.post(MainBibleActivity.MainBibleAfterRestore())
    }

    private val syncMutex = Mutex()

    fun start() {
        val intent = Intent(application, SyncService::class.java)
        intent.action = SyncService.START_SERVICE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            application.startForegroundService(intent)
            Log.i(TAG, "Foreground service started")
        }
        else {
            application.startService(intent)
        }
    }

    suspend fun waitUntilFinished() {
        syncMutex.withLock {  }
    }

    suspend fun synchronize() = withContext(Dispatchers.IO) {
        if(!adapter.signedIn) {
            Log.i(TAG, "Not signed in")
            return@withContext
        }
        if(syncMutex.isLocked) {
            Log.i(TAG, "Already synchronizing")
            return@withContext
        }
        syncMutex.withLock {
            Log.i(TAG, "Synchronizing starts")
            val timerStart = System.currentTimeMillis()

            DatabaseContainer.dbDefFactories.asyncMap {
                val dbDef = it.invoke()
                if(!dbDef.category.enabled) return@asyncMap
                try {
                    initializeSync(dbDef)
                } catch (e: CancelSync) {
                    Log.i(TAG, "Sync cancelled ${dbDef.categoryName}")
                    return@asyncMap
                } catch (e: SocketTimeoutException) {
                    Log.i(TAG, "Socket timed out")
                    return@asyncMap
                } catch (e: Throwable) {
                    Log.e(TAG, "Some other exception happened!", e)
                    return@asyncMap
                }
                createAndUploadNewPatch(dbDef)
                try {
                    downloadAndApplyNewPatches(dbDef)
                } catch (e: PatchFilesSkipped) {
                    Log.i(TAG, "Patch files skipped! Retrying download and apply!")
                    dbDef.dao.setConfig(LAST_SYNCHRONIZED_KEY, 0)
                    downloadAndApplyNewPatches(dbDef)
                }
            }
            Log.i(TAG, "Synchronization complete in ${(System.currentTimeMillis() - timerStart)/1000.0} seconds.")
        }
    }

    private val patchFilePattern = Regex("""(\d+)\.((\d+)\.)?sqlite3\.gz""")
    private fun patchNumber(name: String): Long = patchFilePattern.find(name)!!.groups[1]!!.value.toLong()
    private fun versionNumber(name: String): Long = patchFilePattern.find(name)?.groups?.get(3)?.value?.toLong() ?: 1

    class PatchFilesSkipped: Exception()

    private suspend fun downloadAndApplyNewPatches(dbDef: SyncableDatabaseDefinition<*>) = withContext(Dispatchers.IO) {
        val lastSynchronized = dbDef.dao.getLong(LAST_SYNCHRONIZED_KEY)?: 0
        val lastSynchronizedDateTime = DateTime(lastSynchronized)
        val syncFolder = dbDef.dao.getString(SYNC_FOLDER_FILE_ID_KEY)!!
        val deviceSyncFolder = dbDef.dao.getString(SYNC_DEVICE_FOLDER_FILE_ID_KEY)!!

        Log.i(TAG, "Downloading new patches ${dbDef.categoryName}, last Synced: $lastSynchronizedDateTime. ")
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
            createdTimeAtLeast = lastSynchronizedDateTime
        ).sortedBy { it.createdTime.value }

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
            if(versionNumber(it.name) > dbDef.version) return@mapNotNull null
            val existing = dbDef.dao.syncStatus(folderWithMeta.folder.name, patchNumber(it.name))
            if (existing == null && num > folderWithMeta.loadedCount) {
                DriveFileWithMeta(it, folderWithMeta.folder.name)
            } else null
        }.sortedBy { it.file.createdTime.value }

        for(f in folders.values) {
            val firstPatch = patches.firstOrNull { it.parentFolderName == f.folder.name } ?: continue
            if(patchNumber(firstPatch.file.name) > f.loadedCount + 1) {
                // We are not in sync; need to load older patches.
                throw PatchFilesSkipped()
            }
        }

        Log.i(TAG, "Patches after filter: \n${patches.joinToString("\n") { "${it.file.name} ${it.parentFolderName}" }}")
        Log.i(TAG, "Number of patch files after filtering: ${patches.size}")

        val downloadedFiles = patches.asyncMap { f ->
            Log.i(TAG, "Downloading ${f.file.name}, ${f.file.size} bytes")
            val tmpFile = CommonUtils.tmpFile
            tmpFile.outputStream().use {
                adapter.download(f.file.id, it)
            }
            tmpFile
        }

        val syncStatuses = patches.map {
            SyncStatus(it.parentFolderName, patchNumber(it.file.name), it.file.size, it.file.createdTime.value)
        }

        DatabaseSync.applyPatchesForDatabase(dbDef, *downloadedFiles.toTypedArray())
        downloadedFiles.forEach { it.delete() }
        dbDef.reactToUpdates(lastSynchronized)
        dbDef.dao.addStatuses(syncStatuses)
    }
    private suspend fun createAndUploadNewPatch(dbDef: SyncableDatabaseDefinition<*>) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Uploading new patches ${dbDef.categoryName}")
        val file = DatabaseSync.createPatchForDatabase(dbDef)?: return@withContext
        val syncDeviceFolderId = dbDef.dao.getString(SYNC_DEVICE_FOLDER_FILE_ID_KEY)!!
        val count = (dbDef.dao.lastPatchNum(CommonUtils.deviceIdentifier)?: 0) + 1
        val fileName = "$count.${dbDef.version}.sqlite3.gz"
        val result = adapter.upload(fileName, file, syncDeviceFolderId)
        Log.i(TAG, "Uploaded ${dbDef.categoryName} $fileName, ${file.length()} bytes, ${result.createdTime.toStringRfc3339()}")
        dbDef.dao.addStatus(SyncStatus(CommonUtils.deviceIdentifier, count, file.length(), result.createdTime.value))
        file.delete()
    }

    suspend fun hasChanges(): Boolean =
        DatabaseContainer.dbDefFactories.asyncMap {
            val dbDef = it.invoke()
            dbDef.category.enabled && dbDef.hasChanges
        }.any { it }

    const val TAG = "GoogleDrive"
}
