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

import android.accounts.Account
import android.app.Activity
import android.app.AlertDialog
import android.os.Parcel
import android.util.Base64
import android.util.Log
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.auth.api.identity.SignInCredential
import com.google.android.gms.tasks.Task
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import com.google.api.services.drive.Drive.Files.List as DriveList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.database.SyncStatus
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.service.common.CommonUtils
import net.bible.service.common.asyncMap
import net.bible.service.db.DatabaseContainer
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Collections
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

fun DriveList.collectAll(): List<DriveFile> {
    val result = mutableListOf<DriveFile>()
    var pageToken: String? = null
    do {
        val lst = setPageToken(pageToken).execute()
        result.addAll(lst.files)
        pageToken = lst.nextPageToken
    } while(pageToken != null)
    return result
}

class CancelSync: Exception()

object GoogleDrive {
    private var oneTapClient: SignInClient = Identity.getSignInClient(application)
    private var account: Account? = null

    val signedIn get() = account != null
    private var _service: Drive? = null
    private val service: Drive get() {
        if (!signedIn) {
            throw IllegalStateException("Not signed in")
        }
        return _service?: Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            GoogleAccountCredential
                .usingOAuth2(application, Collections.singleton(DriveScopes.DRIVE_APPDATA))
                .setSelectedAccount(account)
        ).setApplicationName("AndBible").build().also {
            _service = it
        }
    }

    private var lastAccount: Account?
        get() {
            val s = CommonUtils.realSharedPreferences.getString("lastAccount", null)?: return null
            return try {
                val bytes = Base64.decode(s, Base64.DEFAULT)
                val p = Parcel.obtain()
                p.unmarshall(bytes, 0, bytes.size)
                p.setDataPosition(0)
                val account = Account(p)
                account
            } catch (e: Exception) {
                CommonUtils.realSharedPreferences.edit().remove("lastAccount").apply()
                null
            }
        }
        set(value) {
            if(value == null) {
                CommonUtils.realSharedPreferences.edit().remove("lastAccount").apply()
            } else {
                val p = Parcel.obtain()
                value.writeToParcel(p, 0)
                val s = String(Base64.encode(p.marshall(), Base64.DEFAULT))
                CommonUtils.realSharedPreferences.edit().putString("lastAccount", s).apply()
            }
        }

    suspend fun signIn(activity: ActivityBase): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting")
        try {
            account = lastAccount ?: oneTapSignIn(activity).also {lastAccount = it }
        } catch (e: Throwable) {
            Log.e(TAG, "Error signing in", e)
            account = null
            return@withContext false
        }
        val success = ensureDriveAccess(activity)
        if(!success) {
            account = null
            lastAccount = null
        }
        return@withContext success
    }

    enum class InitialOperation {FETCH_INITIAL, CREATE_NEW}
    private val uiMutex = Mutex()
    private suspend fun initializeSync(dbDef: SyncableDatabaseDefinition<*>) {
        var initialOperation: InitialOperation?= null

        val syncFolderName = "sync-${dbDef.categoryName}"
        var syncFolderId = dbDef.dao.getString(SYNC_FOLDER_FILE_ID_KEY)
        if(syncFolderId != null) {
            // Verify if id is found in Drive
            try {
                service.files().get(syncFolderId).execute()
            } catch (e: GoogleJsonResponseException) {
                if(e.statusCode == 404) {
                    syncFolderId = null
                    dbDef.dao.removeConfig(SYNC_FOLDER_FILE_ID_KEY)
                    dbDef.dao.removeConfig(SYNC_DEVICE_FOLDER_FILE_ID_KEY)
                }
            }
        }

        var preliminarySyncFolderId: String? = null

        if(syncFolderId == null) {
            service.files().list()
                .setQ("name = '$syncFolderName'")
                .setSpaces("appDataFolder")
                .setFields("files(id)")
                .execute().files.firstOrNull()?.id?.also {
                    preliminarySyncFolderId = it
                    initialOperation = InitialOperation.FETCH_INITIAL
                }?:let {
                initialOperation = InitialOperation.CREATE_NEW
            }

            if (initialOperation == InitialOperation.FETCH_INITIAL) {
                uiMutex.withLock {
                    val activity = CurrentActivityHolder.currentActivity ?: throw CancelSync()
                    initialOperation = withContext(Dispatchers.Main) {
                        suspendCoroutine {
                            val containsStr = activity.getString(dbDef.category.contentDescription)
                            AlertDialog.Builder(activity)
                                .setTitle(R.string.gdrive_title)
                                .setMessage(activity.getString(R.string.overrideBackup, containsStr))
                                .setPositiveButton(R.string.gdrive_fetch_and_restore_initial) { _, _ ->
                                    it.resume(
                                        InitialOperation.FETCH_INITIAL
                                    )
                                }
                                .setNegativeButton(R.string.gdrive_create_new) { _, _ -> it.resume(InitialOperation.CREATE_NEW) }
                                .setNeutralButton(R.string.gdrive_disable_sync) { _, _ -> it.resume(null) }
                                .create()
                                .show()
                        }
                    }
                }
                if (initialOperation == null) {
                    dbDef.category.setStatus(false)
                    throw CancelSync()
                } else {
                    dbDef.dao.setConfig(SYNC_FOLDER_FILE_ID_KEY, preliminarySyncFolderId!!)
                }
            }
        }

        fun createNewSyncFolder(): String {
            Log.i(TAG, "Creating new sync folder ${dbDef.categoryName} $syncFolderName")

            // If there is already sync folder, let's remove it (and its contents)
            if(preliminarySyncFolderId != null) {
                service.files().delete(preliminarySyncFolderId).execute()
            }

            return service.files()
                .create(DriveFile().apply {
                    name = syncFolderName
                    mimeType = FOLDER_MIMETYPE
                    parents = listOf("appDataFolder")
                })
                .execute().id.also {
                    Log.i(TAG, "Global sync folder id $it")
                    dbDef.dao.setConfig(SYNC_FOLDER_FILE_ID_KEY, it!!)
                }
        }
        fun createNewDeviceSyncFolder() {
            Log.i(TAG, "Creating new device sync folder $syncFolderName/${CommonUtils.deviceIdentifier}")
            service.files()
                .create(DriveFile().apply {
                    name = CommonUtils.deviceIdentifier
                    mimeType = FOLDER_MIMETYPE
                    parents = listOf(dbDef.dao.getString(SYNC_FOLDER_FILE_ID_KEY)!!)
                })
                .execute().id.also {
                    Log.i(TAG, "This device sync folder id $it")
                    dbDef.dao.setConfig(SYNC_DEVICE_FOLDER_FILE_ID_KEY, it!!)
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
        service.files().create(
            DriveFile().apply {
                name = INITIAL_BACKUP_FILENAME
                parents = listOf(dbDef.dao.getString(SYNC_FOLDER_FILE_ID_KEY)!!)
            },
            FileContent(GZIP_MIMETYPE, gzippedTmpFile)
        ).execute()
        gzippedTmpFile.delete()
    }

    private fun fetchAndRestoreInitial(dbDef: SyncableDatabaseDefinition<*>) {
        val deviceFolderId = dbDef.dao.getString(SYNC_DEVICE_FOLDER_FILE_ID_KEY)!!
        val fileId = service.files().list()
            .setSpaces("appDataFolder")
            .setQ("'${dbDef.dao.getString(SYNC_FOLDER_FILE_ID_KEY)!!}' in parents and name = '${INITIAL_BACKUP_FILENAME}'")
            .execute().files.first().id
        val gzippedTmpFile = CommonUtils.tmpFile

        gzippedTmpFile.outputStream().use {
            service.files().get(fileId).executeMediaAndDownloadTo(it)
        }
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
    suspend fun synchronize() = withContext(Dispatchers.IO) {
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
        val lastSynchronizedString = DateTime(lastSynchronized).toStringRfc3339()
        val syncFolder = dbDef.dao.getString(SYNC_FOLDER_FILE_ID_KEY)!!
        val deviceSyncFolder = dbDef.dao.getString(SYNC_DEVICE_FOLDER_FILE_ID_KEY)!!

        Log.i(TAG, "Downloading new patches ${dbDef.categoryName}, last Synced: $lastSynchronizedString. ")
        Log.i(TAG, "This device ${CommonUtils.deviceIdentifier} (id: ${deviceSyncFolder})")

        dbDef.dao.setConfig(LAST_SYNCHRONIZED_KEY, System.currentTimeMillis())

        val folderResult = service.files().list()
            .setSpaces("appDataFolder")
            .setQ("'$syncFolder' in parents and mimeType='$FOLDER_MIMETYPE'")
            .setFields("nextPageToken, files(id, name)")
            .collectAll()
        if (folderResult.isEmpty()) {
            Log.i(TAG, "No patch folders yet")
            return@withContext
        }
        Log.i(TAG, "Folders \n${folderResult.joinToString("\n") { "${it.id} ${it.name}" }}")
        val folderFilter = folderResult.map { it.id }.joinToString(" or ") { "'$it' in parents" }
        val filterPatchFiles = "($folderFilter) and mimeType='$GZIP_MIMETYPE' and createdTime > '$lastSynchronizedString'"
        Log.i(TAG, "filterPatchFiles: $filterPatchFiles")

        val patchResults = service.files().list()
            .setSpaces("appDataFolder")
            .setPageSize(1000) // maximum page size
            .setQ(filterPatchFiles)
            .setOrderBy("createdTime asc")
            .setFields("nextPageToken, files(id, name, size, mimeType, createdTime, parents)")
            .collectAll()

        Log.i(TAG, "Number of patch files in result set: ${patchResults.size}")

        class FolderWithMeta(val folder: DriveFile, val loadedCount: Long)

        val folders = folderResult
            .map {
                FolderWithMeta(it, dbDef.dao.lastPatchNum(it.name) ?: 0)
            }.associateBy { it.folder.id }
        Log.i(TAG, "Folder counts: \n${folders.values.joinToString("\n") { "${it.folder.name}: ${it.loadedCount}" }}")
        Log.i(TAG, "Patches before filter: \n${patchResults.joinToString("\n") { it.name }}")

        class DriveFileWithMeta(val file: DriveFile, val parentFolderName: String)

        val patches = patchResults.mapNotNull {
            val parentFolderId = it.parents.first()
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
            Log.i(TAG, "Downloading ${f.file.name}, ${f.file.getSize()} bytes")
            val tmpFile = CommonUtils.tmpFile
            tmpFile.outputStream().use {
                service
                    .files()
                    .get(f.file.id)
                    .executeMediaAndDownloadTo(it)
            }
            tmpFile
        }

        val syncStatuses = patches.map {
            SyncStatus(it.parentFolderName, patchNumber(it.file.name), it.file.getSize(), it.file.createdTime.value)
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

        val content = FileContent(GZIP_MIMETYPE, file)
        val count = (dbDef.dao.lastPatchNum(CommonUtils.deviceIdentifier)?: 0) + 1

        val fileName = "$count.${dbDef.version}.sqlite3.gz"
        val driveFile = DriveFile().apply {
            name = fileName
            parents = listOf(syncDeviceFolderId)
        }
        val result = service
            .files()
            .create(driveFile, content)
            .setFields("id,createdTime")
            .execute()
        Log.i(TAG, "Uploaded ${dbDef.categoryName} $fileName, ${file.length()} bytes, ${result.createdTime.toStringRfc3339()}")
        dbDef.dao.addStatus(SyncStatus(CommonUtils.deviceIdentifier, count, file.length(), result.createdTime.value))
        file.delete()
    }

    suspend fun signOut() {
        oneTapClient.signOut().await()
        lastAccount = null
        account = null
    }

    private suspend fun oneTapSignIn(activity: ActivityBase): Account {
        Log.i(TAG, "Signing in (one tap)")
        val signInRequest = BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    .setServerClientId(webClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .build()
            )
            .setAutoSelectEnabled(true)
            .build()
        val beginSignInResult = oneTapClient.beginSignIn(signInRequest).await()
        val intent = activity.awaitPendingIntent(beginSignInResult.pendingIntent).data
        val oneTapCredential: SignInCredential = oneTapClient.getSignInCredentialFromIntent(intent)
        return Account(oneTapCredential.id, application.packageName)
    }

    private suspend fun ensureDriveAccess(activity: ActivityBase): Boolean {
        try {
            service.files().list()
                .setSpaces("appDataFolder")
                .setFields("nextPageToken, files(id, name, size)")
                .execute()
        } catch (e: UserRecoverableAuthIOException) {
            val result = activity.awaitIntent(e.intent)
            return result.resultCode == Activity.RESULT_OK
        } catch (e: IOException) {
            Log.e(TAG, "Network unavailable", e)
            return false
        } catch (e: Throwable) {
            Log.e(TAG, "ensureDriveAccess error", e)
            return false
        }
        return true
    }

    const val TAG = "GoogleDrive"
}
