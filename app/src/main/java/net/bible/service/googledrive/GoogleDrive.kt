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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.android.control.backup.FOLDER_MIMETYPE
import net.bible.android.control.backup.GZIP_MIMETYPE
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseCategory
import net.bible.service.db.DatabaseDefinition
import net.bible.service.db.DatabasePatching
import java.io.File
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

const val LOCK_FILENAME = "lock.txt"

const val SYNC_FOLDER_FILE_ID_KEY = "syncId"

const val DRIVE_LOCK_FILE_ID_KEY = "driveLockFileId"

suspend fun <T, V> Collection<T>.asyncMap(action: suspend (T) -> V): Collection<V> = withContext(Dispatchers.IO) {
    awaitAll( *map { async { action(it) }}.toTypedArray() )
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
                }
            }
        }

        if(syncFolderId == null) {
            service.files().list()
                .setQ("name = '$syncFolderName'")
                .setSpaces("appDataFolder")
                .setFields("files(id, name)")
                .execute().files.firstOrNull()?.id?.also {
                    dbDef.dao.setConfig(SYNC_FOLDER_FILE_ID_KEY, it)
                    initialOperation = InitialOperation.FETCH_INITIAL
                } ?: service.files()
                .create(DriveFile().apply {
                    name = syncFolderName
                    mimeType = FOLDER_MIMETYPE
                    parents = listOf("appDataFolder")
                })
                .execute().id.also {
                    dbDef.dao.setConfig(SYNC_FOLDER_FILE_ID_KEY, it)
                    initialOperation = InitialOperation.CREATE_NEW
                }
        }

        if(initialOperation == InitialOperation.FETCH_INITIAL) {
            val activity = CurrentActivityHolder.currentActivity ?: throw CancelSync()
            initialOperation = withContext(Dispatchers.Main) {
                suspendCoroutine {
                    val containsStr = activity.getString(dbDef.category.contentDescription)
                    AlertDialog.Builder(activity)
                        .setTitle(activity.getString(R.string.overrideBackup, containsStr))
                        .setPositiveButton(R.string.fetch_and_restore_initial) { _, _ -> it.resume(InitialOperation.FETCH_INITIAL) }
                        .setNegativeButton(R.string.create_new) { _, _ -> it.resume(InitialOperation.CREATE_NEW) }
                        .setNeutralButton(R.string.cancel) { _, _ -> it.resume(null) }
                        .create()
                        .show()
                }
            }
            if(initialOperation == null) {
                disablePref(dbDef.category)
                throw CancelSync()
            }
        }

        when(initialOperation) {
            InitialOperation.CREATE_NEW -> createAndUploadInitial(dbDef)
            InitialOperation.FETCH_INITIAL -> fetchAndRestoreInitial(dbDef)
            null -> {}
        }
    }

    private fun disablePref(category: DatabaseCategory) {
        val pref = CommonUtils.settings.getStringSet("google_drive_sync", emptySet())!!.toMutableSet()
        if(pref.contains(category.name)) {
            pref.remove(category.name)
            CommonUtils.settings.setStringSet("google_drive_sync", pref)
        }
    }

    private fun prefEnabled(category: DatabaseCategory): Boolean {
        val pref = CommonUtils.settings.getStringSet("google_drive_sync", emptySet())!!.toMutableSet()
        return pref.contains(category.name)
    }
    private fun createAndUploadInitial(dbDef: DatabaseDefinition<*>) {
        dbDef.writableDb.query("VACUUM;").use {  }
        val tmpFile = CommonUtils.tmpFile
        val gzippedTmpFile = CommonUtils.tmpFile
        dbDef.localDbFile.copyTo(tmpFile)
        CommonUtils.gzipFile(tmpFile, gzippedTmpFile)
        tmpFile.delete()

        service.files().create(
            DriveFile().apply {
                name = "initial.sqlite3.gz"
                parents = listOf(dbDef.dao.getString(SYNC_FOLDER_FILE_ID_KEY)!!)
            },
            FileContent(GZIP_MIMETYPE, gzippedTmpFile)
        )
        gzippedTmpFile.delete()
    }

    private fun fetchAndRestoreInitial(dbDef: DatabaseDefinition<*>) {
        val fileId = service.files().list()
            .setQ("'${dbDef.dao.getString(SYNC_FOLDER_FILE_ID_KEY)}' in parents and name = 'initial.sqlite3.gz'")
            .execute().files.first().id
        val gzippedTmpFile = CommonUtils.tmpFile

        gzippedTmpFile.outputStream().use {
            service.files().get(fileId).executeMediaAndDownloadTo(it)
        }
        val tmpFile = CommonUtils.tmpFile
        CommonUtils.gunzipFile(gzippedTmpFile, tmpFile)
        gzippedTmpFile.delete()
        dbDef.localDb.close()
        tmpFile.copyTo(dbDef.localDbFile)
        tmpFile.delete()
        dbDef.resetLocalDb()
        dbDef.writableDb // let's initialize db
    }

    val syncMutex = Mutex()
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
            Log.i(TAG, "Synchronizing starts, let's set up the file lock")
            val timerStart = System.currentTimeMillis()

            DatabasePatching.dbFactories.asyncMap {
                val dbDef = it.invoke()
                if(!prefEnabled(dbDef.category)) return@asyncMap
                val lastSynchronized = dbDef.dao.getLong("lastSynchronized")?: 0
                try {
                    initializeSync(dbDef)
                } catch (e: CancelSync) {
                    return@asyncMap
                }

                Log.i(TAG, "Last synchronized $lastSynchronized")
                cleanupLocalPatchDirectories(dbDef)
                downloadNewPatches(dbDef, lastSynchronized)
                DatabasePatching.applyPatchesForDatabase(dbDef)
                val now = System.currentTimeMillis()
                DatabasePatching.createPatchForDatabase(dbDef, lastSynchronized/1000)
                val lastTimestamp = uploadNewPatches(dbDef, now)
                dbDef.dao.setConfig("lastSynchronized", lastTimestamp?: now)
            }
            Log.i(TAG, "Synchronization complete in ${(System.currentTimeMillis() - timerStart)/1000.0} seconds.")
        }
    }

    private fun cleanupLocalPatchDirectories(dbDef: DatabaseDefinition<*>) {
        dbDef.patchInDir.deleteRecursively()
        dbDef.patchOutDir.deleteRecursively()
    }

    fun timeStampFromPatchFileName(fileName: String): Long {
        // file name is <category>.<timestamp>.sqlite3.gz
        return fileName.split(".")[1].toLong()
    }

    private fun categoryFromPatchFileName(fileName: String): String {
        // file name is <category>.<timestamp>.sqlite3.gz
        return fileName.split(".")[0]
    }

    private suspend fun downloadNewPatches(dbDef: DatabaseDefinition<*>, lastSynchronized: Long) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Downloading new patches")
        var pageToken: String?
        val allFiles = mutableListOf<DriveFile>()
        val syncFolderId = dbDef.dao.getString(SYNC_FOLDER_FILE_ID_KEY)!!
        do {
            val result = service.files().list()
                .setSpaces("appDataFolder")
                .setQ("'${syncFolderId}' in parents and createdTime > '${DateTime(lastSynchronized).toStringRfc3339()}'")
                .setOrderBy("createdTime asc")
                .setFields("nextPageToken, files(id, name, size, createdTime)")
                .execute()
            allFiles.addAll(result.files)
            pageToken = result.nextPageToken
        } while(pageToken != null)

        allFiles.asyncMap { file ->
            Log.i(TAG, "Downloading ${file.name}, ${file.getSize()} bytes")
            File(dbDef.patchInDir, file.name).outputStream().use {
                service
                    .files()
                    .get(file.id)
                    .executeMediaAndDownloadTo(it)
            }
        }
    }

    private suspend fun uploadNewPatches(dbDef: DatabaseDefinition<*>, now: Long): Long? = withContext(Dispatchers.IO) {
        Log.i(TAG, "Uploading new patches")
        val files = dbDef.patchOutDir.listFiles()?: emptyArray()
        val syncFolderId = dbDef.dao.getString(SYNC_FOLDER_FILE_ID_KEY)!!
        val result = files.asList().asyncMap {file ->
            val content = FileContent(GZIP_MIMETYPE, file)
            val category = categoryFromPatchFileName(file.name)
            val driveFile = DriveFile().apply {
                name = "$category.${now}.sqlite3.gz"
                parents = listOf(syncFolderId)
            }
            Log.i(TAG, "Uploading ${file.name} as ${driveFile.name}, ${file.length()} bytes")
            service
                .files()
                .create(driveFile, content)
                .setFields("id,createdTime")
                .execute()
        }
        result.maxByOrNull { it.createdTime.value }?.createdTime?.value
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

    const val TAG = "GoogleDrive"
}
