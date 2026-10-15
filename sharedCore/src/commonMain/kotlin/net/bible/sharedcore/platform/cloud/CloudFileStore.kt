package net.bible.sharedcore.platform.cloud

import okio.Path
import okio.Sink

data class CloudFile(
    val id: String,
    val name: String,
    val size: Long,
    val createdTime: Long,
    val parentId: String
)

/**
 * Cumulative bytes written so far during [CloudFileStore.download], reported periodically as the
 * transfer progresses. The callback may throw to abort the download cooperatively.
 */
typealias DownloadProgressListener = (bytesDownloaded: Long) -> Unit

/**
 * File operations of a sync backend. A missing [CloudFile.id] throws `okio.FileNotFoundException`
 * (`java.io.FileNotFoundException` on the JVM), which the sync engine treats as "gone".
 */
interface CloudFileStore {
    val signedIn: Boolean
    suspend fun signIn(): Boolean
    suspend fun signOut()
    suspend fun get(id: String): CloudFile
    suspend fun listFiles(
        parentsIds: List<String>? = null,
        name: String? = null,
        mimeType: String? = null,
        createdTimeAtLeast: Long? = null
    ): List<CloudFile>
    suspend fun getFolders(parentId: String): List<CloudFile>
    /** Downloads to [sink], closing it on completion or failure. */
    suspend fun download(id: String, sink: Sink, onProgress: DownloadProgressListener? = null)
    suspend fun createNewFolder(name: String, parentId: String? = null): CloudFile
    suspend fun upload(name: String, file: Path, parentId: String): CloudFile
    suspend fun delete(id: String)
}
