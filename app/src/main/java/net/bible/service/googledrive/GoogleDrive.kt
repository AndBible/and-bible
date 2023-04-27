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
import android.util.Log
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.auth.api.identity.SignInCredential
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

const val webClientId = "533479479097-kk5bfksbgtfuq3gfkkrt2eb51ltgkvmn.apps.googleusercontent.com"

class GoogleDrive(val context: ActivityBase) {
    private lateinit var oneTapClient: SignInClient
    private lateinit var credentials: GoogleAccountCredential
    private suspend fun signInLegacy(): Account {
        Log.i(TAG, "Signing in")
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .build()

        val googleSignInClient = GoogleSignIn.getClient(context, signInOptions)
        val signInIntent = googleSignInClient.signInIntent
        val result = context.awaitIntent(signInIntent)
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data) as Task<GoogleSignInAccount>
        val account = task.await()
        return account.account!!
    }

    private suspend fun signInOneTap(): Account {
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
        val intent = context.awaitPendingIntent(beginSignInResult.pendingIntent).data
        val oneTapCredential: SignInCredential = oneTapClient.getSignInCredentialFromIntent(intent)
        return Account(oneTapCredential.id, context.packageName)
    }

    private suspend fun signIn() = signInOneTap()

    suspend fun googleDrive() = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting")
        oneTapClient = Identity.getSignInClient(context)
        var account: Account = GoogleSignIn.getLastSignedInAccount(context)?.account?:  signIn()

        credentials = GoogleAccountCredential
            .usingOAuth2(context, Collections.singleton(DriveScopes.DRIVE_APPDATA))
            .setSelectedAccount(account)


        Log.i(TAG, "canRead: ${checkCanRead()}")
        Log.i(TAG, "canRead: ${checkCanRead()}")
        Log.i(TAG, "canRead: ${checkCanRead()}")
        oneTapClient.signOut().await()
        //if(!checkCanRead()) {
        //    //account = signIn()
        //    //credentials = GoogleAccountCredential
        //    //    .usingOAuth2(context, Collections.singleton(DriveScopes.DRIVE_APPDATA))
        //    //    .setSelectedAccount(account)
        //    Log.i(TAG, "canRead: ${checkCanRead()}")
        //}

    }

    private suspend fun checkCanRead(): Boolean {
        val drive = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credentials
        ).setApplicationName("AndBible").build()
        try {
            drive.files().list()
                .setSpaces("appDataFolder")
                .setFields("nextPageToken, files(id, name)")
                .execute()
        } catch (e: UserRecoverableAuthIOException) {
            context.awaitIntent(e.intent)
            return false
        }
        return true
    }
    companion object {
        const val TAG = "GoogleDrive"
    }
}
