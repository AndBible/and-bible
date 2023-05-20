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
import com.google.android.gms.tasks.Task
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Deferred
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
import net.bible.android.control.backup.GZIP_MIMETYPE
import net.bible.android.control.backup.TEXT_MIMETYPE
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.ToastEvent
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.DatabasePatching
import java.io.Closeable
import java.io.File
import java.util.Collections
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
        account = GoogleSignIn.getLastSignedInAccount(application)?.account?:oneTapSignIn(activity)
        val success = ensureDriveAccess(activity)
        if(!success) {
            account = null
        }
        return@withContext success
    }

    private val lockFileHandle get() = service.files().list()
            .setSpaces("appDataFolder")
            .setFields("nextPageToken, files(id, name)")
            .execute().files.firstOrNull { it.name == LOCK_FILENAME }

    private val fileLock: Closeable? get() {
        val fileHandle = lockFileHandle
        val canLock = if(fileHandle != null) {
            val lockingDeviceId = String(
                service
                    .files()
                    .get(fileHandle.id)
                    .executeMediaAsInputStream()
                    .readBytes()
            )
            lockingDeviceId == CommonUtils.deviceIdentifier
        } else {
            true
        }
        return if(!canLock) {
            null
        } else {
            if(fileHandle != null) service.files().delete(fileHandle.id).execute()
            val content = ByteArrayContent(TEXT_MIMETYPE, CommonUtils.deviceIdentifier.toByteArray())

            service.files().create(
                DriveFile().apply {
                    name = LOCK_FILENAME
                    parents = listOf("appDataFolder")
                }, content).execute()

            Closeable {
                val fileHandle2 = lockFileHandle
                if(fileHandle2 == null) {
                    Log.e(TAG, "Lock file not found. Should have been there!!!")
                    return@Closeable
                } else {
                    service.files().delete(fileHandle2.id).execute()
                }
            }
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
        if(syncMutex.isLocked) {
            Log.i(TAG, "Already synchronizing")
            return@withContext
        }
        syncMutex.withLock {
            val lock = fileLock
            if(fileLock == null) {
                Log.i(TAG, "Lock file present, can't synchronize")
                ABEventBus.post(ToastEvent(R.string.sync_locked))
                return@withContext
            }
            lock.use {
                Log.i(TAG, "Synchronizing")
                val timerNow = System.currentTimeMillis()
                val lastSynchronized = CommonUtils.settings.getLong("lastSynchronized", 0)
                cleanupPatchFolder()
                downloadNewPatches(lastSynchronized)
                DatabasePatching.applyPatchFiles()
                val now = System.currentTimeMillis()
                DatabasePatching.createPatchFiles()
                uploadNewPatches(now)
                CommonUtils.settings.setLong("lastSynchronized", now)
                Log.i(TAG, "Synchronization complete in ${(System.currentTimeMillis() - timerNow)/1000.0} seconds")
            }
        }
    }

    private fun cleanupPatchFolder() {
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

    private suspend fun downloadNewPatches(lastSynchronized: Long) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Downloading new patches")
        val newPatchFiles = service.files().list()
            .setSpaces("appDataFolder")
            .setFields("nextPageToken, files(id, name, size)")
            .execute().files.filter {
                if (!it.name.endsWith(".sqlite3.gz"))
                    false
                else {
                    timeStampFromPatchFileName(it.name) > lastSynchronized
                }
            }
        newPatchFiles.asyncMap {file ->
            Log.i(TAG, "Downloading ${file.name}, ${file.size} bytes")
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
                parents = listOf("appDataFolder")
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
