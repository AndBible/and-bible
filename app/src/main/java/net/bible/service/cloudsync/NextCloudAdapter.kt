/*
 * Copyright (c) 2025 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.cloudsync

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
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import net.bible.service.common.asyncMap
import java.io.File
import java.io.FileNotFoundException
import java.io.OutputStream
import kotlin.collections.List
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

const val FOLDER_MIMETYPE = "DIR"

class NextCloudAdapter(
    private val serverUrl: String?,
    private val username: String?,
    private val password: String?
) : CloudAdapter {
    companion object {
        const val TAG: String = "NextCloud"
    }

    private var _client: OwnCloudClient? = null
    private val client get() = _client!!

    override val signedIn: Boolean get() = _client != null

    override suspend fun signIn(activity: ActivityBase): Boolean = withContext(Dispatchers.IO) {
        try {
            val serverUri = Uri.parse(serverUrl)
            _client = OwnCloudClientFactory.createOwnCloudClient(serverUri, activity, true).apply {
                credentials = OwnCloudCredentialsFactory.newBasicCredentials(username, password)
                userId = username
            }
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Login to NextCloud failed", e)
            _client = null
            return@withContext false
        }
    }

    override suspend fun signOut() {
        _client = null
    }

    suspend fun <T >RemoteOperation<T>.execute(): RemoteOperationResult<T> = suspendCoroutine {
        execute(this@NextCloudAdapter.client, OnRemoteOperationListener { operation, result ->
            it.resume(result as RemoteOperationResult<T>)
        }, Handler(Looper.getMainLooper()))
    }

    override suspend fun get(id: String): CloudFile {
        val result = ReadFileRemoteOperation(id).execute()
        if (!result.isSuccess) throw FileNotFoundException()
        val remoteFile = result.singleData as RemoteFile
        return remoteFile.toSyncFile()
    }

    override suspend fun listFiles(
        parentsIds: List<String>?,
        name: String?,
        mimeType: String?,
        createdTimeAtLeast: Long?
    ): List<CloudFile> {
        val results = (parentsIds?: listOf("/")).asyncMap { parentId ->
            val operation = ReadFolderRemoteOperation("/" + parentId.trimStart('/').trimEnd('/') + "/")
            val result = operation.execute()
            val filtered = (result.data as List<RemoteFile>)
                .filterNot { it.remotePath == parentId }
            return@asyncMap filtered
        }.flatten()

        var filtered: List<RemoteFile> = results
        if(createdTimeAtLeast != null) {
            filtered = filtered.filter { it.modifiedTimestamp > createdTimeAtLeast }
        }
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

    override suspend fun download(id: String, outputStream: OutputStream) {
        val tmpFile = File(CommonUtils.tmpDir, id)
        val operation = DownloadFileRemoteOperation(id, CommonUtils.tmpDir.absolutePath)
        operation.execute()
        outputStream.write(tmpFile.readBytes())
        tmpFile.delete()
    }

    override suspend fun createNewFolder(name: String, parentId: String?): CloudFile {
        val folderPath = if(parentId == null) "/${name.trimStart('/')}" else "${parentId.trimEnd('/')}/$name"
        CreateFolderRemoteOperation(folderPath, true).execute()
        return get(folderPath)
    }

    override suspend fun upload(name: String, file: File, parentId: String): CloudFile {
        val remotePath = "${parentId.trimEnd('/')}/$name"
        UploadFileRemoteOperation(
            file.absolutePath,
            remotePath,
            GZIP_MIMETYPE,
            System.currentTimeMillis() / 1000
        ).execute()
        return get(remotePath)
    }

    override suspend fun delete(id: String) {
        RemoveFileRemoteOperation(id).execute()
    }
}

private fun RemoteFile.toSyncFile(): CloudFile {
    return CloudFile(
        id = remotePath!!,
        name = remotePath!!,
        size = length,
        createdTime = creationTimestamp,
        parentId = parent
    )
}

private val RemoteFile.name get() = remotePath!!.trimEnd('/').substringAfterLast('/')
private val RemoteFile.parent get() = remotePath!!.trimEnd('/').substringBeforeLast('/') + "/"
