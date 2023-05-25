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
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
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
import net.bible.android.SharedConstants
import net.bible.android.activity.R
import net.bible.android.control.backup.FOLDER_MIMETYPE
import net.bible.android.control.backup.GZIP_MIMETYPE
import net.bible.android.control.backup.TEXT_MIMETYPE
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.ToastEvent
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabasePatching
import java.io.Closeable
import java.io.File
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resumeWithException

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
const val PATCH_FOLDER_FILENAME = "patches"

suspend fun <T, V> Collection<T>.asyncMap(action: suspend (T) -> V): Collection<V> = withContext(Dispatchers.IO) {
    awaitAll( *map { async { action(it) }}.toTypedArray() )
}

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

    private val lockFileId: String? get() =
        CommonUtils.realSharedPreferences.getString("driveLockFileId", null)
            ?: service.files().list()
                .setSpaces("appDataFolder")
                .setFields("nextPageToken, files(id, name)")
                .execute().files.firstOrNull { it.name == LOCK_FILENAME }?.id?.also {
                    CommonUtils.realSharedPreferences.edit().putString("driveLockFileId", it).apply()
                }
    private val patchFolderId: String get() =
        CommonUtils.realSharedPreferences.getString("patchFolderId", null)
            ?: service.files().list()
                .setSpaces("appDataFolder")
                .setFields("files(id, name)")
                .execute().files.firstOrNull { it.name == PATCH_FOLDER_FILENAME }?.id?.also {
                    CommonUtils.realSharedPreferences.edit().putString("patchFolderId", it).apply()
                }?:
            service.files()
                .create(DriveFile().apply {
                    name = PATCH_FOLDER_FILENAME
                    mimeType = FOLDER_MIMETYPE
                    parents = listOf("appDataFolder")
                })
                .execute().id.also {
                    CommonUtils.realSharedPreferences.edit().putString("patchFolderId", it).apply()
                }

    private val fileLock: Closeable? get() {
        val ourContent = ByteArrayContent(TEXT_MIMETYPE, CommonUtils.deviceIdentifier.toByteArray())
        var fileId = lockFileId

        fun createNewLock(): String {
            val driveFile = DriveFile().apply {
                name = LOCK_FILENAME
                parents = listOf("appDataFolder")
            }
            val newLock = service.files().create(driveFile, ourContent).execute()
            CommonUtils.realSharedPreferences.edit()
                .putString("driveLockFileId", newLock.id)
                .remove("patchFolderId")
                .apply()
            return newLock.id
        }

        fun updateLock() {
            service.files().update(fileId, DriveFile(), ourContent).execute()
        }

        if(fileId == null) {
            fileId = createNewLock()
        } else {
            val deviceIdInLockFile = try {
                String(
                    service
                        .files()
                        .get(fileId)
                        .executeMediaAsInputStream()
                        .readBytes()
                )
            } catch (e: GoogleJsonResponseException) {
                if(e.statusCode == 404) {
                    fileId = createNewLock()
                } else {
                    throw e;
                }
                ""
            }
            when(deviceIdInLockFile) {
                "" -> updateLock() // lock is released
                CommonUtils.deviceIdentifier -> {} // lock is owned by us already
                else -> return null // lock is owned by someone else, we can't lock
            }
        }

        return Closeable {
            val emptyContent = ByteArrayContent(TEXT_MIMETYPE, "".toByteArray())
            service.files().update(fileId, DriveFile(), emptyContent).execute()
        }
    }

    val patchOutFilesDir: File
        get() {
            val file = File(SharedConstants.internalFilesDir, "/patch-out")
            file.mkdirs()
            return file
        }
    val patchInFilesDir: File
        get() {
            val file = File(SharedConstants.internalFilesDir, "/patch-in")
            file.mkdirs()
            return file
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
            val timerNow = System.currentTimeMillis()
            val lock = fileLock
            if(lock == null) {
                Log.i(TAG, "Lock file present, can't synchronize")
                ABEventBus.post(ToastEvent(R.string.sync_locked))
                return@withContext
            }
            lock.use {
                val lastSynchronized = CommonUtils.settings.getLong("lastSynchronized", 0)
                Log.i(TAG, "Last synchronized $lastSynchronized")
                cleanupLocalPatchDirectories()
                downloadNewPatches(lastSynchronized)
                DatabasePatching.applyPatchFiles()
                val now = System.currentTimeMillis()
                DatabasePatching.createPatchFiles(lastSynchronized/1000)
                uploadNewPatches(now)
                CommonUtils.settings.setLong("lastSynchronized", now)
                Log.i(TAG, "Synchronization complete in ${(System.currentTimeMillis() - timerNow)/1000.0} seconds. Now: $now")
            }
        }
    }

    private fun cleanupLocalPatchDirectories() {
        patchInFilesDir.deleteRecursively()
        patchInFilesDir.mkdirs()
        patchOutFilesDir.deleteRecursively()
        patchOutFilesDir.mkdirs()
    }

    fun timeStampFromPatchFileName(fileName: String): Long {
        // file name is <category>.<timestamp>.sqlite3.gz
        return fileName.split(".")[1].toLong()
    }

    private fun categoryFromPatchFileName(fileName: String): String {
        // file name is <category>.<timestamp>.sqlite3.gz
        return fileName.split(".")[0]
    }

    private fun timeStampStr(timeStampLong: Long): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val date = Date(timeStampLong)
        return dateFormat.format(date)
    }

    private suspend fun downloadNewPatches(lastSynchronized: Long) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Downloading new patches")
        val newPatchFiles = service.files().list()
            .setSpaces("appDataFolder")
            .setQ("'${patchFolderId}' in parents and createdTime > '${timeStampStr(lastSynchronized)}'")
            .setOrderBy("createdTime asc")
            .setFields("nextPageToken, files(id, name, size, createdTime)")
            .execute().files
        newPatchFiles.asyncMap {file ->
            Log.i(TAG, "Downloading ${file.name}, ${file.getSize()} bytes")
            File(patchInFilesDir, file.name).outputStream().use {
                service
                    .files()
                    .get(file.id)
                    .executeMediaAndDownloadTo(it)
            }
        }
    }

    private suspend fun uploadNewPatches(now: Long)  = withContext(Dispatchers.IO) {
        Log.i(TAG, "Uploading new patches")
        val files = patchOutFilesDir.listFiles()?: emptyArray()
        files.asList().asyncMap {file ->
            val content = FileContent(GZIP_MIMETYPE, file)
            val category = categoryFromPatchFileName(file.name)
            val driveFile = DriveFile().apply {
                name = "$category.${now}.sqlite3.gz"
                parents = listOf(patchFolderId)
            }
            Log.i(TAG, "Uploading ${file.name} as ${driveFile.name}, ${file.length()} bytes")
            service.files().create(driveFile, content).execute()
        }
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
        val lst = try {
            service.files().list()
                .setSpaces("appDataFolder")
                .setFields("nextPageToken, files(id, name, size)")
                .execute()
        } catch (e: UserRecoverableAuthIOException) {
            val result = activity.awaitIntent(e.intent)
            return result.resultCode == Activity.RESULT_OK
        }
        lst.files.forEach {
            Log.i(TAG, "Files in Drive: ${it.name} (${it.id}) ${it.getSize()} bytes")
            //service.files().delete(it.id).execute()
        }

        return true
    }

    const val TAG = "GoogleDrive"
}
