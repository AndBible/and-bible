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
import android.database.sqlite.SQLiteDatabase
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
import com.google.api.client.http.InputStreamContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.SharedConstants
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.backup.GZIP_MIMETYPE
import net.bible.android.control.backup.JSON_MIMETYPE
import net.bible.android.control.backup.TEXT_MIMETYPE
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.ReadingPlanDatabase
import net.bible.android.database.RepoDatabase
import net.bible.android.database.SettingsDatabase
import net.bible.android.database.WorkspaceDatabase
import net.bible.android.database.json
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import net.bible.service.db.ALL_DB_FILENAMES
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.DatabasePatching
import java.io.Closeable
import java.io.File
import java.util.Collections
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
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

@Serializable
class DatabaseTimeStamps(
    val bookmark: Long,
    val readingPlans: Long,
    val workspaces: Long,
    val repo: Long,
    val settings: Long,
) {
    fun toJson(): String {
        return json.encodeToString(serializer(), this)
    }

    fun updatedFiles(compareStatus: DatabaseTimeStamps): List<String> {
        val updatedFiles = mutableListOf<String>()
        if (bookmark > compareStatus.bookmark) {
            updatedFiles.add(BookmarkDatabase.dbFileName)
        }
        if (readingPlans > compareStatus.readingPlans) {
            updatedFiles.add(ReadingPlanDatabase.dbFileName)
        }
        if (workspaces > compareStatus.workspaces) {
            updatedFiles.add(WorkspaceDatabase.dbFileName)
        }
        if (repo > compareStatus.repo) {
            updatedFiles.add(RepoDatabase.dbFileName)
        }
        if (settings > compareStatus.settings) {
            updatedFiles.add(SettingsDatabase.dbFileName)
        }
        return updatedFiles
    }

    companion object {
        fun fromFiles() = DatabaseTimeStamps(
            bookmark = application.getDatabasePath(BookmarkDatabase.dbFileName).lastModified(),
            readingPlans = application.getDatabasePath(ReadingPlanDatabase.dbFileName).lastModified(),
            workspaces = application.getDatabasePath(WorkspaceDatabase.dbFileName).lastModified(),
            repo = application.getDatabasePath(RepoDatabase.dbFileName).lastModified(),
            settings = application.getDatabasePath(SettingsDatabase.dbFileName).lastModified(),
        )

        fun fromJson(jsonString: String): DatabaseTimeStamps {
            return json.decodeFromString(serializer(), jsonString)
        }
    }
}

const val TIMESTAMPS_FILENAME = "timestamps.json"
const val LOCK_FILENAME = "lock.txt"

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

    private val timestampFileHandle get() = service.files().list()
            .setSpaces("appDataFolder")
            .setFields("nextPageToken, files(id, name)")
            .execute().files.firstOrNull { it.name == TIMESTAMPS_FILENAME }

    private val lockFileHandle get() = service.files().list()
            .setSpaces("appDataFolder")
            .setFields("nextPageToken, files(id, name)")
            .execute().files.firstOrNull { it.name == LOCK_FILENAME }

    private suspend fun readLastModifiedFromDrive(): DatabaseTimeStamps? = withContext(Dispatchers.IO) {
        Log.i(TAG, "Reading last modified from drive")
        val fileHandle = timestampFileHandle?: return@withContext null
        val json = service
            .files()
            .get(fileHandle.id)
            .executeMediaAsInputStream()
            .readBytes()

        return@withContext DatabaseTimeStamps.fromJson(String(json))
    }

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
    suspend fun synchronize() = withContext(Dispatchers.IO) {
        Log.i(TAG, "Synchronizing")
        val lock = fileLock
        if(fileLock == null) {
            Log.i(TAG, "Lock file present, can't synchronize")
            return@withContext
        }
        lock.use {
            val lastSynchronized = CommonUtils.settings.getLong("lastSynchronized", 0)
            cleanupPatchFolder()
            downloadNewPatches(lastSynchronized)
            DatabasePatching.dropTriggers(DatabaseContainer.instance)
            DatabasePatching.applyPatchFiles()
            val now = System.currentTimeMillis()
            DatabasePatching.createPatchFiles()
            uploadNewPatches(now)
            CommonUtils.settings.setLong("lastSynchronized", now)
            DatabasePatching.createTriggers(DatabaseContainer.instance)
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
            .setFields("nextPageToken, files(id, name)")
            .execute().files.filter {
                if (!it.name.endsWith(".sqlite3.gz"))
                    false
                else {
                    timeStampFromPatchFileName(it.name) > lastSynchronized
                }
            }
        for(file in newPatchFiles) {
            Log.i(TAG, "Downloading ${file.name}")
            File(patchInFilesDir, file.name).outputStream().use {
                service
                    .files()
                    .get(file.id)
                    .executeAndDownloadTo(it)
            }
        }
    }

    private suspend fun uploadNewPatches(now: Long)  = withContext(Dispatchers.IO) {
        Log.i(TAG, "Uploading new patches")
        val files = patchOutFilesDir.listFiles()?: emptyArray()
        for(file in files) {
            val content = FileContent(GZIP_MIMETYPE, file)
            val category = categoryFromPatchFileName(file.name)
            val driveFile = DriveFile().apply {
                name = "$category.${now}.sqlite3.gz"
                parents = listOf("appDataFolder")
            }
            Log.i(TAG, "Uploading ${file.name} as ${driveFile.name}")
            service.files().create(driveFile, content).execute()
        }
    }

    private suspend fun writeLastModifiedToDrive() = withContext(Dispatchers.IO) {
        Log.i(TAG, "Writing last modified to drive")
        val fileHandle = timestampFileHandle
        val content = ByteArrayContent(JSON_MIMETYPE, DatabaseTimeStamps.fromFiles().toJson().toByteArray())
        if(fileHandle != null) {
            service.files().delete(fileHandle.id).execute()
        }
        service.files().create(
            DriveFile().apply {
                name = TIMESTAMPS_FILENAME
                parents = listOf("appDataFolder")
            }, content).execute()
    }

    suspend fun uploadUpdatedDatabases() {
        Log.i(TAG, "uploadUpdatedDatabases")
        val first = DatabaseTimeStamps.fromFiles()
        //DatabaseContainer.vacuum() // TODO: THIS WRITES FILE ALWAYS
        DatabaseContainer.sync()
        val second = DatabaseTimeStamps.fromFiles()
        val modifiedInVacuum = second.updatedFiles(first)
        Log.i(TAG, "Modified in vacuum: ${modifiedInVacuum.joinToString(",")}")

        val localStatus = DatabaseTimeStamps.fromFiles()
        val driveStatus = readLastModifiedFromDrive()
        val filesToUpload = if(driveStatus != null) localStatus.updatedFiles(driveStatus) else ALL_DB_FILENAMES.toList()
        writeLastModifiedToDrive()
        for(fileName in filesToUpload) {
            val file = application.getDatabasePath(fileName)
            uploadFile(file)
        }
    }

    suspend fun downloadUpdatedDatabases() {
        Log.i(TAG, "downloadUpdatedDatabases")
        DatabaseContainer.sync()
        val localStatus = DatabaseTimeStamps.fromFiles()
        val driveStatus = readLastModifiedFromDrive()?: return
        val filesToDownload = driveStatus.updatedFiles(localStatus)
        for(fileName in filesToDownload) {
            downloadAndRestoreFile(fileName)
        }
        DatabaseContainer.reset()
    }

    private suspend fun downloadAndRestoreFile(fileName: String) = withContext(Dispatchers.IO) {
        val gzippedFileName = "${fileName}.gz"
        val file = service.files().list()
            .setSpaces("appDataFolder")
            .setFields("nextPageToken, files(id, name, size)")
            .execute().files.firstOrNull { it.name == gzippedFileName}?: return@withContext
        Log.i(TAG, "Downloading file ${file.name} (${file.id}) ${file.getSize()}")
        val inputStream = GZIPInputStream(service.files().get(file.id).executeMediaAsInputStream())
        val tmpFile = File(BackupControl.internalDbBackupDir, gzippedFileName)
        tmpFile.outputStream().use { inputStream.copyTo(it) }
        Closeable { tmpFile.delete() }.use {
            val version = SQLiteDatabase.openDatabase(tmpFile.path, null, SQLiteDatabase.OPEN_READONLY).use {
                it.version
            }
            val maxVersion = DatabaseContainer.maxDatabaseVersion(fileName)
            if (version <= maxVersion) {
                val dbFile = application.getDatabasePath(fileName)
                tmpFile.copyTo(dbFile, true)
            }
        }
    }

    private suspend fun uploadFile(file: File) = withContext(Dispatchers.IO) {
        val gzippedFileName = "${file.name}.gz"
        val fileHandle = service.files().list()
            .setSpaces("appDataFolder")
            .setFields("nextPageToken, files(id, name)")
            .execute().files.firstOrNull { it.name == gzippedFileName }

        if(fileHandle != null) {
            service.files().delete(fileHandle.id).execute()
        }

        val tmpFile = File(SharedConstants.internalFilesDir, gzippedFileName)
        GZIPOutputStream(tmpFile.outputStream()).use { file.inputStream().copyTo(it) }
        Closeable {
            tmpFile.delete()
        }.use {
            tmpFile.inputStream().use { inputStream ->
                val content = InputStreamContent(GZIP_MIMETYPE, inputStream)
                val f = service.files().create(
                    DriveFile().apply {
                        name = gzippedFileName
                        parents = Collections.singletonList("appDataFolder")
                    }
                    , content)
                    .setFields("id, name, size")
                    .execute()

                Log.d(TAG, "Upload success into File ID: ${f.id} ${f.name}, size ${f.getSize()}")
            }
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
                .setFields("nextPageToken, files(id, name)")
                .execute()
        } catch (e: UserRecoverableAuthIOException) {
            val result = activity.awaitIntent(e.intent)
            return result.resultCode == Activity.RESULT_OK
        }
        lst.files.forEach {
            Log.i(TAG, "Files in Drive: ${it.name} (${it.id})")
            //service.files().delete(it.id).execute()
        }

        return true
    }

    const val TAG = "GoogleDrive"
}
