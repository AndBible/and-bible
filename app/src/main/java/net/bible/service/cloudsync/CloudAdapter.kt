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

package net.bible.service.cloudsync

import net.bible.android.database.SyncConfiguration
import net.bible.android.view.activity.base.ActivityBase
import java.io.File
import java.io.OutputStream

data class CloudFile(
    val id: String,
    val name: String,
    val size: Long,
    val createdTime: Long,
    val parentId: String
)

/**
 * Reports cumulative bytes written so far during a [CloudAdapter.download]. Fired periodically as
 * the transfer progresses (e.g. per download chunk), giving callers visible liveness/progress for
 * large downloads. The callback may throw to abort the download cooperatively (the Google Drive
 * adapter uses this to honour coroutine cancellation at a chunk boundary).
 */
typealias DownloadProgressListener = (bytesDownloaded: Long) -> Unit

interface CloudAdapter {
    val signedIn: Boolean
    suspend fun signIn(activity: ActivityBase): Boolean
    suspend fun signOut()
    suspend fun get(id: String): CloudFile
    suspend fun listFiles(
        parentsIds: List<String>? = null,
        name: String? = null,
        mimeType: String? = null,
        createdTimeAtLeast: Long? = null
    ): List<CloudFile>
    suspend fun getFolders(parentId: String): List<CloudFile>
    suspend fun download(id: String, outputStream: OutputStream, onProgress: DownloadProgressListener? = null)
    suspend fun createNewFolder(name: String, parentId: String? = null): CloudFile
    suspend fun upload(name: String, file: File, parentId: String): CloudFile
    suspend fun delete(id: String)
    suspend fun isSyncFolderKnown(dbDef: SyncableDatabaseAccessor<*>, name: String, id: String): Boolean
    suspend fun makeSyncFolderKnown(dbDef: SyncableDatabaseAccessor<*>, name: String, id: String)
    fun getConfigs(dbDef: SyncableDatabaseAccessor<*>): List<SyncConfiguration>
}
