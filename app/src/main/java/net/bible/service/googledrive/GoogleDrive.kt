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

import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.bible.android.view.activity.base.ActivityBase
import java.util.Collections
import kotlin.coroutines.resumeWithException

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


class GoogleDrive(val context: ActivityBase) {
    private lateinit var credentials: GoogleAccountCredential
    private suspend fun signIn(): GoogleSignInAccount {
        Log.i(TAG, "Signing in")
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .build()

        val googleSignInClient = GoogleSignIn.getClient(context, signInOptions)
        val signInIntent = googleSignInClient.signInIntent
        val result = context.awaitIntent(signInIntent)
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.resultData) as Task<GoogleSignInAccount>
        val account = task.await()
        return account
    }
    suspend fun googleDrive() = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting")
        var account: GoogleSignInAccount = GoogleSignIn.getLastSignedInAccount(context) ?: signIn()

        credentials = GoogleAccountCredential
            .usingOAuth2(context, Collections.singleton(DriveScopes.DRIVE_APPDATA))
            .setSelectedAccount(account.account)

        if(!checkCanRead()) {
            account = signIn()
            credentials = GoogleAccountCredential
                .usingOAuth2(context, Collections.singleton(DriveScopes.DRIVE_APPDATA))
                .setSelectedAccount(account.account)
        }

        Log.i(TAG, "canRead: ${checkCanRead()}")
    }

    private fun checkCanRead(): Boolean {
        val jsonFactory = GsonFactory.getDefaultInstance()

        val drive = Drive.Builder(
            NetHttpTransport(),
            jsonFactory,
            credentials
        ).build()
        try {
            drive.files().list()
                .setSpaces("appDataFolder")
                .setFields("nextPageToken, files(id, name)")
                .execute()
        } catch (e: UserRecoverableAuthIOException) {
            return false
        }
        return true
    }
    companion object {
        const val TAG = "GoogleDrive"
    }
}
