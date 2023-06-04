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
import android.os.Parcel
import android.util.Base64
import android.util.Log
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.auth.api.identity.SignInCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.drive.model.File as DriveFile
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.BibleApplication
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.util.Collections

fun DriveFile.toSyncFile() = SyncFile(
    id = id,
    name = name,
    size = getSize()?: 0,
    createdTime = createdTime,
    parentId = parents.first()
)

fun Drive.Files.List.collectAll(): List<DriveFile> {
    val result = mutableListOf<DriveFile>()
    var pageToken: String? = null
    do {
        val lst = setPageToken(pageToken).execute()
        result.addAll(lst.files)
        pageToken = lst.nextPageToken
    } while(pageToken != null)
    return result
}

private const val FIELDS = "id, name, size, createdTime, parents"


class GoogleDrive: CloudAdapter {
    private var oneTapClient: SignInClient = Identity.getSignInClient(BibleApplication.application)
    private var account: Account? = null

    override val signedIn get() = account != null
    private var _service: Drive? = null
    private val service: Drive
        get() {
        if (!signedIn) {
            throw IllegalStateException("Not signed in")
        }
        return _service?: Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            GoogleAccountCredential
                .usingOAuth2(BibleApplication.application, Collections.singleton(DriveScopes.DRIVE_APPDATA))
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

    override suspend fun signIn(activity: ActivityBase): Boolean = withContext(Dispatchers.IO) {
        Log.i(DeviceSynchronize.TAG, "Starting")
        try {
            account = lastAccount ?: oneTapSignIn(activity).also {lastAccount = it }
        } catch (e: Throwable) {
            Log.e(DeviceSynchronize.TAG, "Error signing in", e)
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

    private suspend fun oneTapSignIn(activity: ActivityBase): Account {
        Log.i(DeviceSynchronize.TAG, "Signing in (one tap)")
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
        return Account(oneTapCredential.id, BibleApplication.application.packageName)
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
            Log.e(DeviceSynchronize.TAG, "Network unavailable", e)
            return false
        } catch (e: Throwable) {
            Log.e(DeviceSynchronize.TAG, "ensureDriveAccess error", e)
            return false
        }
        return true
    }

    override fun get(id: String): SyncFile =
        try {
            service.files()
                .get(id)
                .setFields(FIELDS)
                .execute().toSyncFile()
        } catch (e: GoogleJsonResponseException) {
            if(e.statusCode == 404) {
                throw FileNotFoundException()
            } else {
                throw e;
            }
        }

    override fun listFiles(
        parents: List<String>?,
        name: String?,
        mimeType: String?,
        createdTimeAtLeast: DateTime?
    ): List<SyncFile> {
        val q = mutableListOf<String>()
        if (parents != null) {
            q.add(
               parents.joinToString(
                   separator = " or ",
                   prefix = "(",
                   postfix = ")",
               ) { "'$it' in parents" }
            )
        }
        if(createdTimeAtLeast != null) {
            q.add(
               "createdTime > '${createdTimeAtLeast.toStringRfc3339()}'"
            )
        }
        if(name != null) {
            q.add("name = '$name'")
        }
        if(mimeType != null) {
            q.add("mimeType = '$mimeType'")
        }
        if(q.isEmpty()) {
            throw RuntimeException("Illegal query")
        }
        return service.files().list()
            .setSpaces("appDataFolder")
            .setQ(q.joinToString(" and "))
            .setPageSize(1000) // maximum page size
            .setFields("nextPageToken, files($FIELDS)")
            .collectAll()
            .map { it.toSyncFile() }
    }

    override fun getFolders(parentId: String): List<SyncFile> =
        listFiles(parents = listOf(parentId), mimeType = FOLDER_MIMETYPE)

    override fun delete(id: String) {
        service.files().delete(id).execute()
    }

    override fun download(id: String, outputStream: OutputStream) {
       service.files().get(id).executeMediaAndDownloadTo(outputStream)
    }

    override fun createNewFolder(name: String, parent: String?): SyncFile =
        service.files()
            .create(DriveFile().apply {
                this.name = name
                mimeType = FOLDER_MIMETYPE
                parents = listOf(parent ?: "appDataFolder")
            })
            .setFields(FIELDS)
            .execute().toSyncFile()

    override fun createNewFile(name: String, file: File, parent: String?): SyncFile =
        service.files().create(
            DriveFile().apply {
                this.name = name
                parents = listOf(parent?: "appDataFolder")
            },
            FileContent(GZIP_MIMETYPE, file)
        )
            .setFields(FIELDS)
            .execute()
            .toSyncFile()


    override suspend fun signOut() {
        oneTapClient.signOut().await()
        lastAccount = null
        account = null
    }
}

