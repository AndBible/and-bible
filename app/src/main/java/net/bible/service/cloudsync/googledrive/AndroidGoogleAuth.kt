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
import android.app.PendingIntent
import android.content.Intent
import android.os.Parcel
import android.util.Base64
import androidx.activity.result.ActivityResult
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import net.bible.android.BibleApplication
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.service.cloudsync.CloudSyncUserCancelledException
import net.bible.service.cloudsync.TAG
import net.bible.service.cloudsync.drive.DRIVE_APPDATA_SCOPE
import net.bible.service.cloudsync.drive.GoogleAuth
import net.bible.service.cloudsync.drive.GoogleAuthRequiredException
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.log.Log
import java.io.IOException

private const val LAST_ACCOUNT_KEY = "cloud_sync_last_account"
private const val GOOGLE_ACCOUNT_TYPE = "com.google"
private const val EMAIL_SCOPE = "email"

/** Outcome of one authorization request for the Drive app-data scope. */
internal sealed interface DriveAuthorization {
    /** Access granted; [account] is the Google account it was granted for, when Play services reports it. */
    data class Granted(val token: String, val account: Account?) : DriveAuthorization

    /** Only the user can grant access: [pendingIntent] shows the account chooser and/or the consent sheet. */
    class NeedsResolution(val pendingIntent: PendingIntent) : DriveAuthorization
}

/**
 * Seam over Play services' [AuthorizationClient], so the sign-in and token logic of [AndroidGoogleAuth] is unit
 * testable. Failures are reported as Play services' [ApiException]s.
 */
internal interface DriveAuthorizer {
    /**
     * Asks for `drive.appdata` access for [account] (null: let Play services choose / ask). Without an account the
     * request also asks for the `email` scope, so the result names the account the user picked and it can be stored.
     */
    suspend fun authorize(account: Account?): DriveAuthorization

    /** Reads the outcome of a resolution launched from [DriveAuthorization.NeedsResolution]. */
    fun resultFromIntent(data: Intent?): DriveAuthorization

    /** Drops [token] from Play services' token cache. */
    suspend fun clearToken(token: String)
}

internal class PlayServicesDriveAuthorizer : DriveAuthorizer {
    private val client: AuthorizationClient by lazy { Identity.getAuthorizationClient(BibleApplication.application) }

    override suspend fun authorize(account: Account?): DriveAuthorization {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOfNotNull(Scope(DRIVE_APPDATA_SCOPE), if (account == null) Scope(EMAIL_SCOPE) else null))
            .apply { if (account != null) setAccount(account) }
            .build()
        return client.authorize(request).await().toDriveAuthorization()
    }

    override fun resultFromIntent(data: Intent?): DriveAuthorization =
        client.getAuthorizationResultFromIntent(data).toDriveAuthorization()

    override suspend fun clearToken(token: String) {
        client.clearToken(ClearTokenRequest.builder().setToken(token).build()).await()
    }

    private fun AuthorizationResult.toDriveAuthorization(): DriveAuthorization {
        val pendingIntent = pendingIntent
        val token = accessToken
        return when {
            hasResolution() && pendingIntent != null -> DriveAuthorization.NeedsResolution(pendingIntent)
            token != null -> DriveAuthorization.Granted(token, toGoogleSignInAccount()?.account)
            else -> throw ApiException(com.google.android.gms.common.api.Status(CommonStatusCodes.SIGN_IN_REQUIRED))
        }
    }
}

/** `GoogleSignInStatusCodes.SIGN_IN_FAILED`: authorization failed for good (not a transient error). */
private const val SIGN_IN_FAILED = 12500

/**
 * Status codes of an [ApiException] that a retry cannot fix: only a fresh interactive authorization can (or nothing
 * can, for a developer error). Every other code (network error, timeout, Play services missing / updating /
 * disconnected, internal error) is transient.
 */
private val SIGN_IN_STATUS_CODES = setOf(
    CommonStatusCodes.SIGN_IN_REQUIRED,
    CommonStatusCodes.INVALID_ACCOUNT,
    CommonStatusCodes.RESOLUTION_REQUIRED,
    CommonStatusCodes.DEVELOPER_ERROR,
    SIGN_IN_FAILED,
)

private val ApiException.needsSignIn get() = statusCode in SIGN_IN_STATUS_CODES

/** One Tap stored the account with the app's package name as type; authorization needs a Google account. */
private fun Account.asGoogleAccount() = if (type == GOOGLE_ACCOUNT_TYPE) this else Account(name, GOOGLE_ACCOUNT_TYPE)

/**
 * [GoogleAuth] over Play services' authorization API ([AuthorizationClient]): AndBible needs only Drive
 * authorization (`drive.appdata`), no identity. The account is remembered in [LAST_ACCOUNT_KEY] (same key and
 * format the old One Tap sign-in used), so an upgraded install asks Play services for that account and gets its
 * existing grant without any prompt. Lives in `googledrive/` so the F-Droid exclusion removes it with the rest.
 *
 * Interactive authorization ([signIn]) may launch Play services' chooser/consent sheet; background token requests
 * ([accessToken]) never do: a grant that needs the user is [GoogleAuthRequiredException], a transient Play services
 * or network failure is a plain [IOException] (retried, the sign-in is kept).
 */
class AndroidGoogleAuth internal constructor(
    private val authorizer: DriveAuthorizer,
    private val launchResolution: suspend (ActivityBase, PendingIntent) -> ActivityResult,
) : GoogleAuth {
    constructor() : this(PlayServicesDriveAuthorizer(), { activity, intent -> activity.awaitPendingIntent(intent) })

    @Volatile private var account: Account? = null
    @Volatile private var authorized = false
    /** Bumped by [forget]: a token request that started before a sign-out must not sign the user back in. */
    @Volatile private var generation = 0

    /** In memory only, like the old adapter: a stored pref alone must not skip [signIn]'s consent check. */
    override val signedIn get() = authorized

    private val prefs get() = CommonUtils.realSharedPreferences

    private var lastAccount: Account?
        get() {
            val s = prefs.getString(LAST_ACCOUNT_KEY, null) ?: return null
            return try {
                val bytes = Base64.decode(s, Base64.DEFAULT)
                val p = Parcel.obtain()
                try {
                    p.unmarshall(bytes, 0, bytes.size)
                    p.setDataPosition(0)
                    Account(p).asGoogleAccount()
                } finally {
                    p.recycle()
                }
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
                try {
                    value.writeToParcel(p, 0)
                    prefs.edit().putString(LAST_ACCOUNT_KEY, String(Base64.encode(p.marshall(), Base64.DEFAULT))).apply()
                } finally {
                    p.recycle()
                }
            }
        }

    private fun forget() {
        generation++
        authorized = false
        account = null
        lastAccount = null
    }

    private fun remember(granted: DriveAuthorization.Granted, requested: Account?) {
        val chosen = granted.account?.asGoogleAccount() ?: requested
        account = chosen
        if (chosen != null) lastAccount = chosen
        authorized = true
    }

    override suspend fun signIn(): Boolean = withContext(Dispatchers.IO) {
        val activity = CurrentActivityHolder.currentActivity ?: return@withContext false
        Log.i(TAG, "Authorizing Google Drive")
        var requested = lastAccount
        val first = try {
            authorizer.authorize(requested)
        } catch (e: ApiException) {
            when {
                // The stored account may be gone from the device: let Play services offer the chooser once.
                e.needsSignIn && requested != null -> null
                e.needsSignIn -> { Log.e(TAG, "Authorization failed", e); forget(); return@withContext false }
                // Transient: keep a known account (do not log out because of this), else the sign-in cannot start.
                requested != null -> { account = requested; authorized = true; return@withContext true }
                else -> throw IOException("Google Play services unavailable: ${e.message}", e)
            }
        }
        val outcome = first ?: run {
            requested = null
            lastAccount = null
            try {
                authorizer.authorize(null)
            } catch (e: ApiException) {
                Log.e(TAG, "Authorization failed", e)
                forget()
                if (e.needsSignIn) return@withContext false
                throw IOException("Google Play services unavailable: ${e.message}", e)
            }
        }
        val granted = when (outcome) {
            is DriveAuthorization.Granted -> outcome
            is DriveAuthorization.NeedsResolution -> resolve(activity, outcome.pendingIntent)
                ?: return@withContext false
        }
        remember(granted, requested)
        return@withContext true
    }

    /** Shows Play services' chooser / consent sheet; null when it ends without a grant. */
    private suspend fun resolve(activity: ActivityBase, intent: PendingIntent): DriveAuthorization.Granted? {
        val result = launchResolution(activity, intent)
        if (result.resultCode != Activity.RESULT_OK) {
            forget()
            throw CloudSyncUserCancelledException()
        }
        val outcome = try {
            authorizer.resultFromIntent(result.data)
        } catch (e: ApiException) {
            forget()
            if (e.statusCode == CommonStatusCodes.CANCELED) throw CloudSyncUserCancelledException()
            Log.e(TAG, "Authorization result error", e)
            return null
        }
        return outcome as? DriveAuthorization.Granted ?: run { forget(); null }
    }

    override suspend fun accessToken(): String {
        val startGeneration = generation
        val current = account ?: lastAccount
        if (current == null && !authorized) throw GoogleAuthRequiredException("Not signed in to Google")
        return withContext(Dispatchers.IO) {
            val outcome = try {
                authorizer.authorize(current)
            } catch (e: ApiException) {
                if (e.needsSignIn) throw GoogleAuthRequiredException("Google auth failed: ${e.message}", e)
                // Play services missing/updating/unavailable or no network: retryable I/O, not lost sign-in.
                throw IOException("Google Play services unavailable: ${e.message}", e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                // Anything else Play services throws (SecurityException, IllegalStateException, ...): retryable I/O.
                throw IOException("Google authorization failed: $e", e)
            }
            when (outcome) {
                is DriveAuthorization.Granted -> outcome.token.also {
                    // In memory only (the pref already holds this account); skipped if a sign-out happened meanwhile.
                    if (generation == startGeneration) {
                        if (account == null) account = current
                        authorized = true
                    }
                }
                is DriveAuthorization.NeedsResolution -> throw GoogleAuthRequiredException("Google needs interactive consent")
            }
        }
    }

    override suspend fun invalidate(token: String) {
        withContext(Dispatchers.IO) {
            try {
                authorizer.clearToken(token)
            } catch (e: ApiException) {
                throw IOException("Could not clear the Google token: ${e.message}", e)
            }
        }
    }

    /** Forgets the account locally. The Drive grant is not revoked: other devices of the same account keep it. */
    override suspend fun signOut() {
        forget()
    }
}
