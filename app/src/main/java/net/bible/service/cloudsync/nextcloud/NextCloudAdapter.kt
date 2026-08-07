/*
 * Copyright (c) 2025-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.cloudsync.nextcloud

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.owncloud.android.lib.common.OwnCloudClient
import com.owncloud.android.lib.common.OwnCloudClientFactory
import com.owncloud.android.lib.common.OwnCloudCredentialsFactory
import com.owncloud.android.lib.common.operations.OnRemoteOperationListener
import com.owncloud.android.lib.common.operations.RemoteOperation
import com.owncloud.android.lib.common.operations.RemoteOperationResult
import com.owncloud.android.lib.resources.files.*
import com.owncloud.android.lib.resources.files.model.RemoteFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.database.SyncConfiguration
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.cloudsync.CloudAdapter
import net.bible.service.cloudsync.CloudFile
import net.bible.service.cloudsync.DownloadProgressListener
import net.bible.service.cloudsync.GZIP_MIMETYPE
import net.bible.service.cloudsync.SyncableDatabaseAccessor
import net.bible.service.cloudsync.TAG
import net.bible.service.common.CommonUtils
import net.bible.service.common.asyncMap
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import kotlin.collections.List
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

const val FOLDER_MIMETYPE = "DIR"
const val NEXTCLOUD_SECRET_FILE_NAME_KEY = "nextCloudSecretFile"

private const val HTTP_NOT_FOUND = 404

class NextCloudAdapter(
    private val serverUrl: String?,
    private val username: String?,
    private val password: String?,
    folderPath: String? = null
) : CloudAdapter {
    private var _client: OwnCloudClient? = null
    private val client get() = _client!!

    /** Normalized base folder path (e.g., "/AndBible"), or null if using root */
    private val baseFolderPath: String? = folderPath?.trim()
        ?.removePrefix("/")
        ?.removeSuffix("/")
        ?.replace(Regex("/+"), "/")
        ?.takeIf { it.isNotBlank() }
        ?.let { "/$it" }

    /** Cached base folder ID, initialized lazily */
    private var _baseFolderId: String? = null
    private suspend fun getBaseFolderId(): String? {
        if (baseFolderPath == null) return null
        if (_baseFolderId == null) {
            CreateFolderRemoteOperation(baseFolderPath, true).execute()
            _baseFolderId = baseFolderPath
            Log.i(TAG, "Initialized base folder: $baseFolderPath")
        }
        return _baseFolderId
    }

    override val signedIn: Boolean get() = _client != null

    override suspend fun signIn(activity: ActivityBase): Boolean = withContext(Dispatchers.IO) {
        try {
            val serverUri = Uri.parse(serverUrl)
            _client = OwnCloudClientFactory.createOwnCloudClient(serverUri, activity, true).apply {
                credentials = OwnCloudCredentialsFactory.newBasicCredentials(username, password)
                userId = username
            }
            if (!verifyConnection()) {
                _client = null
                return@withContext false
            }
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Login to NextCloud failed", e)
            _client = null
            return@withContext false
        }
    }

    suspend fun verifyConnection(): Boolean {
        try {
            getFolders("/")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Server connectivity test failed", e)
            return false
        }
    }

    override suspend fun signOut() {
        _client = null
    }

    /**
     * Runs the operation, throwing only on a transport-level failure (the library populated
     * `result.exception`). A result that merely carries an unsuccessful *status* is returned as-is.
     *
     * Several callers rely on that leniency: [getBaseFolderId] and [createNewFolder] issue a
     * `CreateFolderRemoteOperation` for a folder that usually already exists, and [delete] may
     * target something already gone. Those "failures" are the normal steady state and have always
     * been ignored here — turning them into exceptions would break folder setup outright.
     *
     * Callers that go on to read `result.data` must use [executeForData] instead.
     */
    suspend fun <T >RemoteOperation<T>.execute(): RemoteOperationResult<T> = suspendCoroutine {
        execute(this@NextCloudAdapter.client, OnRemoteOperationListener { operation, result ->
            if (!result.isSuccess && result.exception != null) {
                it.resumeWithException(result.exception)
                return@OnRemoteOperationListener
            }
            it.resume(result as RemoteOperationResult<T>)
        }, Handler(Looper.getMainLooper()))
    }

    /**
     * Runs the operation and guarantees the returned result is safe to read data from.
     *
     * Reading `result.data` on an unsuccessful result throws
     * `RuntimeException("Accessing result data after operation failed!")`, which the sync layer
     * cannot recognise as a network problem and therefore surfaces to the user as "An error has
     * occurred" (OSTicket 3376 — repeated NextCloud HTTP 503). Failing with a well-typed
     * [IOException] instead lets the op be retried quietly on the next sync.
     */
    private suspend fun <T> RemoteOperation<T>.executeForData(): RemoteOperationResult<T> {
        val result = execute()
        if (!result.isSuccess) throw result.asIOException()
        return result
    }

    override suspend fun get(id: String): CloudFile {
        // FileNotFoundException when the resource is genuinely absent (isSyncFolderKnown acts on
        // that), IOException when the server answered with an error — previously *every* failure
        // became FileNotFoundException, so a 503 silently looked like deleted data.
        val result = ReadFileRemoteOperation(id).executeForData()
        val remoteFile = result.singleData as RemoteFile
        return remoteFile.toSyncFile()
    }

    override suspend fun listFiles(
        parentsIds: List<String>?,
        name: String?,
        mimeType: String?,
        createdTimeAtLeast: Long?
    ): List<CloudFile> {
        // Use base folder as default search location if none specified
        val defaultParent = getBaseFolderId() ?: "/"
        val results = (parentsIds ?: listOf(defaultParent)).asyncMap { parentFolder ->
            // NextCloudSearchMethod method is used for searching recently modified
            // patch files (more efficient than listing them all).
            // Method search scope, however, is infinitely deep (NextCloud server, as
            // of writing this, does not seem to care about d:depth definition at all,
            // so for listing folders (when createdTimeAtLeast is null) we use
            // ReadFolderRemoteOperation instead.
            val operation = if (createdTimeAtLeast != null) {
                val method = NextCloudSearchMethod(
                    client,
                    parentFolder,
                    createdTimeAtLeast
                )
                GenericRemoteOperation(method)
            } else {
                ReadFolderRemoteOperation(parentFolder)
            }

            val result = operation.executeForData()
            val filtered = (result.data as List<RemoteFile>)
                .filterNot { it.remotePath?.trimEnd('/') == parentFolder }
            return@asyncMap filtered
        }.flatten()

        var filtered: List<RemoteFile> = results
        if (name != null) {
            filtered = filtered.filter { it.name == name }
        }
        if (mimeType != null) {
            filtered = filtered.filter { it.mimeType == mimeType }
        }
        return filtered.map { it.toSyncFile() }
    }

    override suspend fun getFolders(parentId: String): List<CloudFile> =
        listFiles(parentsIds = listOf(parentId), mimeType = FOLDER_MIMETYPE)

    override suspend fun download(id: String, outputStream: OutputStream, onProgress: DownloadProgressListener?) {
        val tmpFile = File(CommonUtils.tmpDir, id)
        val operation = DownloadFileRemoteOperation(id, CommonUtils.tmpDir.absolutePath)
        // A failed download previously fell through to readBytes() below, surfacing as a bare
        // FileNotFoundException on the temp file rather than the actual server error.
        operation.executeForData()
        val bytes = tmpFile.readBytes()
        outputStream.write(bytes)
        // This adapter downloads in one shot (no chunked progress), so just report the final size.
        onProgress?.invoke(bytes.size.toLong())
        tmpFile.delete()
    }

    override suspend fun createNewFolder(name: String, parentId: String?): CloudFile {
        // Use base folder as default parent if none specified
        val parentPath = parentId ?: getBaseFolderId() ?: ""
        val folderPath = "$parentPath/$name"
        CreateFolderRemoteOperation(folderPath, true).execute()
        return CloudFile(
            id = folderPath,
            name = name,
            size = 0,
            createdTime = System.currentTimeMillis(),
            parentId = parentPath
        )
    }

    override suspend fun upload(name: String, file: File, parentId: String): CloudFile {
        val remotePath = "$parentId/$name"
        val createdTimeSeconds = System.currentTimeMillis() / 1000
        UploadFileRemoteOperation(
            file.absolutePath,
            remotePath,
            GZIP_MIMETYPE,
            createdTimeSeconds,
        ).execute()
        return CloudFile(
            id = remotePath,
            name = name,
            size = file.length(),
            createdTime = createdTimeSeconds*1000,
            parentId = parentId
        )
    }

    override suspend fun delete(id: String) {
        RemoveFileRemoteOperation(id).execute()
    }

    override suspend fun isSyncFolderKnown(dbDef: SyncableDatabaseAccessor<*>, name: String, id: String): Boolean {
        var secretFileName = dbDef.dao.getString(NEXTCLOUD_SECRET_FILE_NAME_KEY)?: return false
        try {
            get("$id/$secretFileName")
        } catch (e: FileNotFoundException) {
            dbDef.dao.removeConfig(NEXTCLOUD_SECRET_FILE_NAME_KEY)
            return false
        }
        return true
    }

    override suspend fun makeSyncFolderKnown(
        dbDef: SyncableDatabaseAccessor<*>,
        name: String,
        id: String
    ) {
        Log.i(TAG, "Making NextCloud sync folder known")
        val secretFileName = "device-known-${CommonUtils.deviceIdentifier}-${UUID.randomUUID()}"
        val tmpFile = File.createTempFile("ng-secret", null)
        val result = upload(secretFileName, tmpFile, id)
        Log.i(TAG, "Result: $result")
        tmpFile.delete()
        dbDef.dao.setConfig(NEXTCLOUD_SECRET_FILE_NAME_KEY, secretFileName)
    }

    override fun getConfigs(dbDef: SyncableDatabaseAccessor<*>): List<SyncConfiguration> {
        return listOf(
           dbDef.dao.getConfig(NEXTCLOUD_SECRET_FILE_NAME_KEY)
        ).filterNotNull()
    }
}

/**
 * Builds the exception a failed NextCloud operation should throw when the library reported no
 * underlying exception of its own — i.e. the server answered, but with an error status.
 *
 * The distinction matters to the sync layer: [FileNotFoundException] means "this resource is not
 * there" and callers act on it (e.g. `isSyncFolderKnown` forgetting a stale sync-folder marker),
 * whereas a plain [IOException] is recognised as a transient network failure by
 * [net.bible.service.cloudsync.documents.isTransientNetworkError] and retried on the next sync
 * instead of being reported to the user. Collapsing a server error such as 503 into
 * FileNotFoundException would make a temporarily unavailable server look like deleted data.
 */
internal fun nextCloudFailureException(isNotFound: Boolean, httpCode: Int, description: String): IOException =
    if (isNotFound) FileNotFoundException("NextCloud: not found ($description, HTTP $httpCode)")
    else IOException("NextCloud operation failed: $description (HTTP $httpCode)")

/**
 * Either signal is enough to call it "not found": the raw HTTP status is the more dependable of
 * the two (a plain int straight off the response), while [RemoteOperationResult.ResultCode] depends
 * on how the library chose to classify the operation. Requiring only ResultCode risked reporting a
 * genuine 404 as a generic failure, which would break `isSyncFolderKnown`'s "forget the stale
 * marker" path.
 */
private fun RemoteOperationResult<*>.asIOException(): IOException = nextCloudFailureException(
    isNotFound = httpCode == HTTP_NOT_FOUND || code == RemoteOperationResult.ResultCode.FILE_NOT_FOUND,
    httpCode = httpCode,
    description = code?.name ?: "UNKNOWN",
)

private fun RemoteFile.toSyncFile(): CloudFile {
    return CloudFile(
        id = "$parent/$name",
        name = name,
        size = length,
        createdTime = creationTimestamp,
        parentId = parent
    )
}

private val RemoteFile.name get() = remotePath!!.trimEnd('/').substringAfterLast('/')
private val RemoteFile.parent get() = remotePath!!.trimEnd('/').substringBeforeLast('/')
