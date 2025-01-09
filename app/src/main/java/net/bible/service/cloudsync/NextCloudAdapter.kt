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
import com.nextcloud.common.NextcloudClient
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
import java.io.File
import java.io.FileNotFoundException
import java.io.OutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

fun getWaiter(command: () -> Void): OnRemoteOperationListener {
    return OnRemoteOperationListener { operation, result ->
        command.invoke()
    }
}


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

    override suspend fun get(id: String): CloudFile {
        throw FileNotFoundException()
        val result = ReadFileRemoteOperation(id).execute()
        val remoteFile = result.resultData as RemoteFile
        return remoteFile.toSyncFile()
    }

    override suspend fun listFiles(
        parentsIds: List<String>?,
        name: String?,
        mimeType: String?,
        createdTimeAtLeast: Long?
    ): List<CloudFile> {
        return listOf()
        TODO()
        //val path = parentsIds?.firstOrNull() ?: FileUtils.PATH_SEPARATOR
        //val operation = ReadFolderRemoteOperation(name)
        //val result = operation.execute(client)

        //return result.resultData.filterIsInstance<RemoteFile>().map { it.toCloudFile() }
    }

    suspend fun <T >RemoteOperation<T>.execute(): RemoteOperationResult<T> = suspendCoroutine {
        execute(this@NextCloudAdapter.client, OnRemoteOperationListener { operation, result ->
            it.resume(result as RemoteOperationResult<T>)
        }, Handler(Looper.getMainLooper()))
    }

    override suspend fun getFolders(parentId: String): List<CloudFile> {
        val result = ReadFolderRemoteOperation(parentId).execute()
        result.resultData
        TODO()
        //return listFiles(parentsIds = listOf(parentId), mimeType = FileUtils.MIME_TYPE_FOLDER)
    }

    override suspend fun download(id: String, outputStream: OutputStream) {
        TODO()
        //val remotePath = FileUtils.PATH_SEPARATOR + id
        //val operation = DownloadFileRemoteOperation(remotePath, outputStream)
        //operation.execute(client)
    }

    override suspend fun createNewFolder(name: String, parentId: String?): CloudFile {
        val folderPath = if(parentId == null) "/${name}" else "$parentId/$name"
        val result = CreateFolderRemoteOperation(folderPath, true).execute()
        return get(folderPath)
    }

    override suspend fun upload(name: String, file: File, parentId: String?): CloudFile {
        TODO()
        //val parentPath = parentId ?: FileUtils.PATH_SEPARATOR
        //val remotePath = "$parentPath/$name"
        //UploadFileRemoteOperation(file.absolutePath, remotePath, FileUtils.getMimeType(file), file.lastModified() / 1000)
        //    .execute(client)
        //return get(remotePath)
    }

    override suspend fun delete(id: String) {
        val remotePath = FileUtils.PATH_SEPARATOR + id
        RemoveFileRemoteOperation(remotePath).execute()
    }
}

private fun RemoteFile.toSyncFile(): CloudFile {
    TODO()
    //return CloudFile(
    //    id = remotePath.trim(FileUtils.PATH_SEPARATOR),
    //    name = fileName,
    //    size = length,
    //    createdTime = creationTimestamp,
    //    parentId = parent.trimEnd('/').substringAfterLast('/')
    //)
}
