/*
 * Copyright (c) 2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.cloudsync.googledrive

import android.accounts.Account
import android.app.Activity
import android.os.Parcel
import android.util.Base64
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.auth.api.identity.SignInCredential
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.BibleApplication
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.service.cloudsync.CloudSyncUserCancelledException
import net.bible.service.cloudsync.TAG
import net.bible.service.cloudsync.drive.GoogleAuth
import net.bible.service.cloudsync.drive.GoogleAuthRequiredException
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.log.Log
import java.io.IOException

private const val LAST_ACCOUNT_KEY = "cloud_sync_last_account"

private val defaultTokenSource: (Account) -> String = { account ->
    GoogleAccountCredential
        .usingOAuth2(BibleApplication.application, setOf(DriveScopes.DRIVE_APPDATA))
        .setSelectedAccount(account)
        .token
}

private val defaultClearToken: (String) -> Unit = { token ->
    GoogleAuthUtil.clearToken(BibleApplication.application, token)
}

/**
 * [GoogleAuth] over the existing One Tap sign-in. The sign-in logic is a deliberate copy of
 * `GoogleDriveCloudAdapter` (same stored account, same cancel mapping); only the Drive access check
 * is replaced by asking [tokenSource] for an access token. Lives in `googledrive/` so the F-Droid
 * exclusion removes it with the rest.
 */
class AndroidGoogleAuth(
    private val tokenSource: (Account) -> String = defaultTokenSource,
    private val clearToken: (String) -> Unit = defaultClearToken,
) : GoogleAuth {
    private val oneTapClient: SignInClient by lazy { Identity.getSignInClient(BibleApplication.application) }

    @Volatile private var account: Account? = null

    override val signedIn get() = account != null || lastAccount != null

    private val prefs get() = CommonUtils.realSharedPreferences

    private var lastAccount: Account?
        get() {
            val s = prefs.getString(LAST_ACCOUNT_KEY, null) ?: return null
            return try {
                val bytes = Base64.decode(s, Base64.DEFAULT)
                val p = Parcel.obtain()
                p.unmarshall(bytes, 0, bytes.size)
                p.setDataPosition(0)
                Account(p)
            } catch (e: Exception) {
                prefs.edit().remove(LAST_ACCOUNT_KEY).apply()
                null
            }
        }
        set(value) {
            if (value == null) {
                prefs.edit().remove(LAST_ACCOUNT_KEY).apply()
            } else {
                val p = Parcel.obtain()
                value.writeToParcel(p, 0)
                val s = String(Base64.encode(p.marshall(), Base64.DEFAULT))
                prefs.edit().putString(LAST_ACCOUNT_KEY, s).apply()
            }
        }

    override suspend fun signIn(): Boolean = withContext(Dispatchers.IO) {
        val activity = CurrentActivityHolder.currentActivity ?: return@withContext false
        Log.i(TAG, "Starting")
        try {
            account = lastAccount ?: oneTapSignIn(activity).also { lastAccount = it }
        } catch (e: Exception) {
            Log.e(TAG, "Error signing in", e)
            account = null
            throw e
        }
        val success = try {
            ensureDriveAccess(activity, account!!)
        } catch (e: CloudSyncUserCancelledException) {
            account = null
            lastAccount = null
            throw e
        }
        if (!success) {
            account = null
            lastAccount = null
        }
        return@withContext success
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
        val result = activity.awaitPendingIntent(beginSignInResult.pendingIntent)
        if (result.resultCode == Activity.RESULT_CANCELED) throw CloudSyncUserCancelledException()
        val oneTapCredential: SignInCredential = try {
            oneTapClient.getSignInCredentialFromIntent(result.data)
        } catch (e: ApiException) {
            if (e.statusCode == CommonStatusCodes.CANCELED) throw CloudSyncUserCancelledException()
            throw e
        }
        return Account(oneTapCredential.id, BibleApplication.application.packageName)
    }

    private suspend fun ensureDriveAccess(activity: ActivityBase, account: Account): Boolean {
        try {
            tokenSource(account)
        } catch (e: UserRecoverableAuthException) {
            val result = activity.awaitIntent(e.intent!!)
            if (result.resultCode != Activity.RESULT_OK) throw CloudSyncUserCancelledException()
            return true
        } catch (e: IOException) {
            Log.e(TAG, "Network unavailable", e)
            return true // We do not want to log out because of this!
        } catch (e: Throwable) {
            Log.e(TAG, "ensureDriveAccess error", e)
            return false
        }
        return true
    }

    override suspend fun accessToken(): String {
        val current = account ?: lastAccount?.also { account = it }
            ?: throw GoogleAuthRequiredException("Not signed in to Google")
        return withContext(Dispatchers.IO) {
            try {
                tokenSource(current)
            } catch (e: UserRecoverableAuthException) {
                throw GoogleAuthRequiredException("Google needs interactive consent", e)
            }
        }
    }

    override suspend fun invalidate(token: String) {
        withContext(Dispatchers.IO) { clearToken(token) }
    }

    override suspend fun signOut() {
        try {
            oneTapClient.signOut().await()
        } finally {
            lastAccount = null
            account = null
        }
    }
}
