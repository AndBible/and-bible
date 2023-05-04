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
import com.google.api.client.http.InputStreamContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.SharedConstants
import net.bible.android.control.backup.BackupControl
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.db.OLD_MONOLITHIC_DATABASE_NAME
import java.io.File
import java.util.Collections
import java.util.zip.GZIPOutputStream
import kotlin.coroutines.resumeWithException

const val webClientId = "533479479097-kk5bfksbgtfuq3gfkkrt2eb51ltgkvmn.apps.googleusercontent.com"
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

object GoogleDrive {
    private var oneTapClient: SignInClient = Identity.getSignInClient(application)
    private var account: Account? = null

    val signedIn get() = account != null
    private val service: Drive get() {
        if (!signedIn) {
            throw IllegalStateException("Not signed in")
        }
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            GoogleAccountCredential
                .usingOAuth2(application, Collections.singleton(DriveScopes.DRIVE_APPDATA))
                .setSelectedAccount(account)
        ).setApplicationName("AndBible").build()
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

    private const val backupFileName = "${OLD_MONOLITHIC_DATABASE_NAME}.gz"
    suspend fun writeToDrive() = withContext(Dispatchers.IO) {
        Log.i(TAG, "writeToDrive")
        service.files().list()
            .setSpaces("appDataFolder")
            .setFields("nextPageToken, files(id, name)")
            .execute().files.filter { it.name == backupFileName}.forEach {
                Log.i(TAG, "Deleting existing file ${it.name} (${it.id})")
                service.files().delete(it.id).execute()
            }

        val fileMetaData = DriveFile().apply {
            name = backupFileName
            parents = Collections.singletonList("appDataFolder")
        }
        val tmpFile = File(SharedConstants.internalFilesDir, backupFileName)
        val dbFile = application.getDatabasePath(OLD_MONOLITHIC_DATABASE_NAME)
        GZIPOutputStream(tmpFile.outputStream()).use {
            dbFile.inputStream().copyTo(it)
        }

        tmpFile.inputStream().use { inputStream ->
            val content = InputStreamContent(GZIP_MIMETYPE, inputStream)
            val file: DriveFile = service.files().create(fileMetaData, content)
                .setFields("id, name, size")
                .execute()
            Log.d(TAG, "Upload success into File ID: ${file.id} ${file.name}, size ${file.getSize()}, compress ratio: ${file.getSize() / dbFile.length().toDouble()}")
        }
        tmpFile.delete()
    }

    suspend fun loadFromDrive() = withContext(Dispatchers.IO) {
        val file = service.files().list()
            .setSpaces("appDataFolder")
            .setFields("nextPageToken, files(id, name, size)")
            .execute().files.firstOrNull { it.name == backupFileName}?: return@withContext
        Log.i(TAG, "Downloading file ${file.name} (${file.id}) ${file.getSize()}")
        BackupControl.restoreDatabaseFromInputStream(service.files().get(file.id).executeMediaAsInputStream())
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
                .setFields("nextPageToken, files(id, name)")
                .execute()
        } catch (e: UserRecoverableAuthIOException) {
            val result = activity.awaitIntent(e.intent)
            return result.resultCode == Activity.RESULT_OK
        }
        lst.files.forEach {
            Log.i(TAG, "Files in Drive: ${it.name} (${it.id})")
        }

        return true
    }

    const val TAG = "GoogleDrive"
}
