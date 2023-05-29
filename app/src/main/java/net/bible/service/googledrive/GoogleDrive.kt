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

package net.bible.service.googledrive

import android.accounts.Account
import android.app.Activity
import android.app.AlertDialog
import android.util.Log
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.auth.api.identity.SignInCredential
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
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
import net.bible.android.control.backup.FOLDER_MIMETYPE
import net.bible.android.control.backup.GZIP_MIMETYPE
import net.bible.android.database.SyncStatus
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.util.Hourglass
import net.bible.service.common.CommonUtils
import net.bible.service.common.asyncMap
import net.bible.service.db.DatabaseCategory
import net.bible.service.db.DatabaseDefinition
import net.bible.service.db.DatabasePatching
import java.util.Collections
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

const val webClientId = "533479479097-kk5bfksbgtfuq3gfkkrt2eb51ltgkvmn.apps.googleusercontent.com"

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

    suspend fun signIn(activity: ActivityBase): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting")
        try {
            account = GoogleSignIn.getLastSignedInAccount(application)?.account ?: oneTapSignIn(activity)
        } catch (e: ApiException) {
            Log.e(TAG, "Error signing in", e)
            account = null
            return@withContext false
        }
        val success = ensureDriveAccess(activity)
        if(!success) {
            account = null
        }
        return@withContext success
    }

    enum class InitialOperation {FETCH_INITIAL, CREATE_NEW}
    private val uiMutex = Mutex()
    private suspend fun initializeSync(dbDef: DatabaseDefinition<*>) {
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
                    disablePref(dbDef.category)
                    throw CancelSync()
                } else {
                    dbDef.dao.setConfig(SYNC_FOLDER_FILE_ID_KEY, preliminarySyncFolderId!!)
                }
            }
        }
        fun createNewSyncFolder(): String {
            Log.i(TAG, "Creating new sync folder ${dbDef.categoryName} $syncFolderName")
            return service.files()
                .create(DriveFile().apply {
                    name = syncFolderName
                    mimeType = FOLDER_MIMETYPE
                    parents = listOf("appDataFolder")
                })
                .execute().id.also {
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

    private fun disablePref(category: DatabaseCategory) {
        val pref = CommonUtils.settings.getStringSet("google_drive_sync", emptySet()).toMutableSet()
        if(pref.contains(category.name)) {
            pref.remove(category.name)
            CommonUtils.settings.setStringSet("google_drive_sync", pref)
        }
    }

    private fun prefEnabled(category: DatabaseCategory): Boolean {
        val pref = CommonUtils.settings.getStringSet("google_drive_sync", emptySet()).toMutableSet()
        return pref.contains(category.name)
    }

    private fun createAndUploadInitial(dbDef: DatabaseDefinition<*>) {
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

    private fun fetchAndRestoreInitial(dbDef: DatabaseDefinition<*>) {
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
        dbDef.writableDb // let's initialize db
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

            DatabasePatching.dbFactories.asyncMap {
                val dbDef = it.invoke()
                if(!prefEnabled(dbDef.category)) return@asyncMap
                try {
                    initializeSync(dbDef)
                } catch (e: CancelSync) {
                    Log.i(TAG, "Sync cancelled ${dbDef.categoryName}")
                    return@asyncMap
                }
                val syncStarted = System.currentTimeMillis()
                downloadAndApplyNewPatches(dbDef)
                createAndUploadNewPatch(dbDef)
                dbDef.dao.setConfig("lastSynchronized", syncStarted)
            }
            Log.i(TAG, "Synchronization complete in ${(System.currentTimeMillis() - timerStart)/1000.0} seconds.")
        }
    }

    private suspend fun downloadAndApplyNewPatches(dbDef: DatabaseDefinition<*>) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Downloading new patches ${dbDef.categoryName}")
        val lastSynchronized = dbDef.dao.getLong("lastSynchronized")?: 0
        val syncFolder = dbDef.dao.getString(SYNC_FOLDER_FILE_ID_KEY)!!
        val syncDeviceFolder = dbDef.dao.getString(SYNC_DEVICE_FOLDER_FILE_ID_KEY)!!

        val filterDeviceFolders = "mimeType='$FOLDER_MIMETYPE'"
        val filterPatchFiles = "mimeType='$GZIP_MIMETYPE' and not name = '$INITIAL_BACKUP_FILENAME' and not '$syncDeviceFolder' in parents and createdTime > '${DateTime(lastSynchronized).toStringRfc3339()}'"
        val result = service.files().list()
            .setSpaces("appDataFolder")
            .setQ("'$syncFolder' in parents and (($filterDeviceFolders) or ($filterPatchFiles))")
            .setOrderBy("createdTime asc")
            .setFields("nextPageToken, files(id, name, size, mimeType, createdTime, parents)")
            .collectAll()


        val folders = result.filter { it.mimeType == FOLDER_MIMETYPE }.associate { Pair(it.id, it.name) }
        val patches = result.filter { it.name.endsWith(".sqlite3.gz") }

        // TODO: do not download / apply files that have been applied (check SyncStatus table!)

        val downloadedFiles = patches.asyncMap { file ->
            Log.i(TAG, "Downloading ${file.name}, ${file.getSize()} bytes")
            val tmpFile = CommonUtils.tmpFile
            tmpFile.outputStream().use {
                service
                    .files()
                    .get(file.id)
                    .executeMediaAndDownloadTo(it)
            }
            Pair(tmpFile, file.createdTime.value)
        }

        val syncStatuses = patches.map {
            val parentFolderId = it.parents.first()
            val parentFolderName = folders[parentFolderId]!!
            SyncStatus(it.name, it.getSize(), parentFolderName, it.createdTime.value)
        }

        dbDef.dao.addStatuses(syncStatuses)
        DatabasePatching.applyPatchesForDatabase(dbDef, downloadedFiles)
    }
    private suspend fun createAndUploadNewPatch(dbDef: DatabaseDefinition<*>) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Uploading new patches ${dbDef.categoryName}")
        val file = DatabasePatching.createPatchForDatabase(dbDef)?: return@withContext
        val syncDeviceFolderId = dbDef.dao.getString(SYNC_DEVICE_FOLDER_FILE_ID_KEY)!!

        val content = FileContent(GZIP_MIMETYPE, file)
        val now = System.currentTimeMillis()
        val fileName = "$now.sqlite3.gz"
        val driveFile = DriveFile().apply {
            name = fileName
            parents = listOf(syncDeviceFolderId)
        }
        Log.i(TAG, "Uploading ${dbDef.categoryName} $fileName, ${file.length()} bytes")
        val result = service
            .files()
            .create(driveFile, content)
            .setFields("id,createdTime")
            .execute()
        dbDef.dao.addStatus(SyncStatus(fileName, file.length(), CommonUtils.deviceIdentifier, result.createdTime.value))
        file.delete()
    }

    suspend fun signOut() {
        oneTapClient.signOut().await()
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
        }
        return true
    }
    suspend fun synchronizeWithHourGlass(activity: ActivityBase) {
        val hourglass = Hourglass(activity)
        hourglass.show(R.string.synchronizing)
        synchronize()
        syncMutex.withLock {  }
        hourglass.dismiss()
    }

    const val TAG = "GoogleDrive"
}
