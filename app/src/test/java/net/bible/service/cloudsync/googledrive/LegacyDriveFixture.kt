/*
 * Copyright (c) 2023-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.FileContent
import com.google.api.client.util.DateTime
import com.google.api.services.drive.model.File as DriveFile
import com.google.api.services.drive.Drive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import net.bible.android.database.SyncConfiguration
import net.bible.service.cloudsync.CloudAdapter
import net.bible.sharedcore.platform.cloud.CloudFile
import net.bible.sharedcore.platform.cloud.DownloadProgressListener
import net.bible.service.cloudsync.GZIP_MIMETYPE
import net.bible.service.cloudsync.SyncableDatabaseAccessor
import java.io.FileNotFoundException
import okio.Path
import okio.Sink
import okio.buffer

private const val FOLDER_MIMETYPE = "application/vnd.google-apps.folder"

// Download in moderately sized chunks (instead of the API client's 32MB default). Smaller chunks
// give finer progress/liveness feedback and a smaller unit of work to retry if one chunk stalls,
// at the cost of a few more requests — a good trade-off on the slow/flaky connections this targets.
private const val DOWNLOAD_CHUNK_SIZE = 8 * 1024 * 1024

private fun DriveFile.toSyncFile() = CloudFile(
    id = id,
    name = name,
    size = getSize()?: 0,
    createdTime = createdTime.value,
    parentId = parents.first()
)

private fun Drive.Files.List.collectAll(): List<DriveFile> {
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


/**
 * FROZEN FIXTURE: the Drive file-store operations of the old Android adapter (`GoogleDriveCloudAdapter` on the Java
 * Google API client), as shipped by every app version up to the last release before the shared Ktor adapter became
 * the only Android Drive adapter (phase 4, 2026-11-13). Those versions stay installed on many devices and keep
 * writing to the same Drive folder, so the compatibility tests use this as the "old app".
 *
 * Do not "fix" or modernise it: its requests and the data it writes must stay what the old app sends. Only sign-in
 * (One Tap + `GoogleAccountCredential`) was removed; the caller passes a ready [Drive] service.
 */
class LegacyDriveAdapter(private val service: Drive) : CloudAdapter {
    override val signedIn get() = true

    override suspend fun signIn(): Boolean = true

    override suspend fun get(id: String): CloudFile =
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

    override suspend fun listFiles(
        parentsIds: List<String>?,
        name: String?,
        mimeType: String?,
        createdTimeAtLeast: Long?
    ): List<CloudFile> {
        val createdTimeAtLeastDateTime = createdTimeAtLeast?.let { DateTime(it) }
        val q = mutableListOf<String>()
        if (parentsIds != null) {
            q.add(
               parentsIds.joinToString(
                   separator = " or ",
                   prefix = "(",
                   postfix = ")",
               ) { "'$it' in parents" }
            )
        }
        if(createdTimeAtLeastDateTime != null) {
            q.add(
               "createdTime > '${createdTimeAtLeastDateTime.toStringRfc3339()}'"
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

    override suspend fun getFolders(parentId: String): List<CloudFile> =
        listFiles(parentsIds = listOf(parentId), mimeType = FOLDER_MIMETYPE)

    override suspend fun delete(id: String) {
        service.files().delete(id).execute()
    }

    override suspend fun isSyncFolderKnown(dbDef: SyncableDatabaseAccessor<*>, name: String, id: String): Boolean {
        // For Google Drive implementation, we just need to
        // verify that id is found in Drive
        try {
            get(id)
        } catch (e: FileNotFoundException) {
            return false
        }
        return true
    }

    override suspend fun makeSyncFolderKnown(
        dbDef: SyncableDatabaseAccessor<*>,
        name: String,
        id: String
    )
    // For Google Drive implementation, we don't need to do anything
    {}

    override suspend fun getConfigs(dbDef: SyncableDatabaseAccessor<*>): List<SyncConfiguration> = emptyList()

    override suspend fun download(id: String, sink: Sink, onProgress: DownloadProgressListener?): Unit =
        withContext(Dispatchers.IO) {
            sink.buffer().outputStream().use { outputStream ->
                val get = service.files().get(id)
                if (onProgress != null) {
                    // Capture the context here (suspend scope); the progress callback below runs on the
                    // blocking download thread and can't itself suspend.
                    val ctx = coroutineContext
                    get.mediaHttpDownloader?.apply {
                        setChunkSize(DOWNLOAD_CHUNK_SIZE)
                        setProgressListener { dl ->
                            // Cooperatively abort at this chunk boundary if the sync coroutine was
                            // cancelled (sign-out / service stop), instead of blocking to the read timeout.
                            ctx.ensureActive()
                            onProgress(dl.numBytesDownloaded)
                        }
                    }
                }
                get.executeMediaAndDownloadTo(outputStream)
            }
        }

    override suspend fun createNewFolder(name: String, parentId: String?): CloudFile =
        service.files()
            .create(DriveFile().apply {
                this.name = name
                mimeType = FOLDER_MIMETYPE
                parents = listOf(parentId ?: "appDataFolder")
            })
            .setFields(FIELDS)
            .execute()
            .toSyncFile()

    override suspend fun upload(name: String, file: Path, parentId: String): CloudFile {
        val f = file.toFile()
        return service.files().create(
            DriveFile().apply {
                this.name = name
                parents = listOf(parentId)
            },
            FileContent(GZIP_MIMETYPE, f)
        )
            .setFields(FIELDS)
            .execute()
            .toSyncFile()
    }

    override suspend fun signOut() {}
}
