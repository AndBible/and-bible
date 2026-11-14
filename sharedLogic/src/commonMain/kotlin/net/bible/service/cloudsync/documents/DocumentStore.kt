/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AndBible. If not, see <http://www.gnu.org/licenses/>.
 */

package net.bible.service.cloudsync.documents

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import net.bible.sharedcore.log.Log
import net.bible.service.cloudsync.CloudAdapter
import net.bible.sharedcore.platform.cloud.CloudFile
import net.bible.sharedcore.platform.cloud.DownloadProgressListener
import net.bible.service.common.asyncMap
import net.bible.service.common.createTempFile
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

private const val TAG = "DocumentStore"

/**
 * Bounded concurrency for the independent, read-only meta.json fetches in [DocumentStore.listChangedDocuments].
 * Each fetch is a self-contained download with no shared state, so fanning them out is safe; the bound of 6
 * matches the DB-sync fan-out and keeps the device from opening an unbounded number of connections at once.
 */
private const val LIST_CONCURRENCY = 6

/**
 * Trailing overlap (ms) re-queried below the watermark each incremental listing — absorbs
 * NextCloud's one-second createdTime resolution and minor clock skew. Re-reading a meta already in
 * the cache is idempotent; this is a safety overlap, not a skew guarantee (the reset action is).
 */
private const val LISTING_MARGIN_MS = 5_000L

/** Result of an incremental cloud listing — only the metas whose meta.json changed since a watermark. */
data class ChangedListing(
    val changedMetas: List<DocumentSyncMeta>,
    val currentInitials: Set<String>,
    val matchedCreatedTimes: List<Long>,
    val failedCreatedTimes: List<Long>,
)

/**
 * The meta.json reads of earlier listings, kept by their owner ([DocumentSync]) across the short-lived
 * [DocumentStore]s it builds per operation (#4103).
 *
 * The listing overlap ([LISTING_MARGIN_MS] here, plus the adapter's own clock margin) re-matches every meta
 * uploaded in the same burst as the newest one, on every refresh, until a newer meta arrives. Without this,
 * each refresh (the "Sync now" preview included) downloaded all of them again in a second sequential round
 * trip. Reusing a read changes nothing downstream: the listing still reports the meta as matched, so the merge
 * and the watermark see exactly what a fresh download would have given.
 *
 * Correctness over speed; a matched meta file is reused only when all of these hold:
 * - **Same cloud.** The reads belong to one adapter instance and one documents root folder ([begin] clears them
 *   when either changes). File ids alone do not identify a cloud: WebDAV ids are paths below the server base,
 *   equal on every server and account. An adapter instance talks to one server and account for its whole life
 *   (its configuration is fixed at construction; switching backend or changing the WebDAV server, URL or user
 *   signs in a new instance), so the instance plus the root folder id is the cloud's identity.
 * - **Same version.** The provider reports a version token ([CloudFile.etag]) and a raw server modification time
 *   ([CloudFile.serverModifiedTime]), and the file's id, token, size and server time equal the ones it was read
 *   at. The server time is compared as listed, not [CloudFile.createdTime]: that one carries a per-response
 *   clock offset that jitters.
 * - **Settled.** The listing that read it started at least [SETTLE_MS] after the file's modification time. Some
 *   servers derive the etag from mtime in whole seconds + size (+ an inode a delete-and-recreate may reuse):
 *   a rewrite in the same second with the same length repeats every listed property, so a read taken in that
 *   second may be stale while looking current.
 *
 * Otherwise the meta is downloaded, as before #4103. [clear] (sign-out, listing reset) also invalidates any
 * listing still running: a read it finishes afterwards is not remembered.
 */
class DocumentMetaReads(private val nowMs: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() }) {
    private data class Read(val etag: String, val size: Long, val serverModifiedTime: Long, val meta: DocumentSyncMeta)

    /** One listing's view of the reads: valid while no [clear] or owner change happened since it began. */
    internal class Session(val generation: Long, val startedAt: Long)

    private val reads = HashMap<String, Read>()
    private val lock = SynchronizedObject()
    private var ownerAdapter: Any? = null
    private var ownerRootFolderId: String? = null
    private var generation = 0L

    /**
     * Starts a listing of [rootFolderId] through [adapter], before its first request: forgets every read of
     * another cloud, and returns the session [reuse] and [remember] check.
     */
    internal fun begin(adapter: Any, rootFolderId: String): Session = synchronized(lock) { // L1-common-ok: atomicfu lock
        if (ownerAdapter !== adapter || ownerRootFolderId != rootFolderId) {
            clearLocked()
            ownerAdapter = adapter
            ownerRootFolderId = rootFolderId
        }
        Session(generation, nowMs())
    }

    /** The meta read earlier from this exact version of [file], or null when it must be downloaded. */
    internal fun reuse(file: CloudFile, session: Session): DocumentSyncMeta? {
        val etag = file.etag ?: return null
        val mtime = file.serverModifiedTime ?: return null
        val read = synchronized(lock) { // L1-common-ok: atomicfu lock
            reads[file.id].takeIf { session.generation == generation }
        } ?: return null
        return read.meta.takeIf { read.etag == etag && read.size == file.size && read.serverModifiedTime == mtime }
    }

    /**
     * Keeps [meta], read from [file] in [session], for later listings: only when the file is versioned, the read
     * is settled (see the class comment) and nothing cleared the reads since the session began.
     */
    internal fun remember(file: CloudFile, meta: DocumentSyncMeta, session: Session) {
        val etag = file.etag ?: return
        val mtime = file.serverModifiedTime ?: return
        // createdTime is the device-clock time of the write, comparable with the device-clock session start.
        if (session.startedAt - file.createdTime < SETTLE_MS) return
        synchronized(lock) { // L1-common-ok: atomicfu lock
            if (session.generation == generation) reads[file.id] = Read(etag, file.size, mtime, meta)
        }
    }

    /** Forgets every read and invalidates running listings, so the next listing downloads every matched meta again. */
    fun clear() = synchronized(lock) { clearLocked() } // L1-common-ok: atomicfu lock

    private fun clearLocked() {
        reads.clear()
        generation++
    }

    companion object {
        /**
         * How long after its server modification time a meta read may be kept. Covers a whole-second server
         * mtime resolution plus the error of the device-clock conversion (the WebDAV clock offset comes from
         * whole-second `Date` headers).
         */
        const val SETTLE_MS = 5_000L
    }
}

class DocumentStore(
    private val adapter: CloudAdapter,
    private val rootFolderId: String,
    private val env: net.bible.sharedcore.platform.AppEnvironment,
    private val metaReads: DocumentMetaReads? = null,
) {
    private suspend fun folderFor(initials: String): CloudFile? =
        adapter.getFolders(rootFolderId).firstOrNull { it.name == initials }

    private suspend fun ensureFolder(initials: String): String =
        folderFor(initials)?.id ?: adapter.createNewFolder(initials, rootFolderId).id

    private suspend fun downloadMeta(metaFile: CloudFile): DocumentSyncMeta? {
        val tmp = createTempFile(env.tmpDir.toPath(), "andbible-", ".tmp")
        return try {
            adapter.download(metaFile.id, FileSystem.SYSTEM.sink(tmp))
            DocumentSyncMeta.fromJson(FileSystem.SYSTEM.read(tmp) { readUtf8() })
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading meta ${metaFile.id}", e); null
        } finally { runCatching { FileSystem.SYSTEM.delete(tmp) } }
    }

    /** Reads the current meta.json for one document by initials (single folder), or null if absent. */
    suspend fun readMeta(initials: String): DocumentSyncMeta? {
        val folderId = folderFor(initials)?.id ?: return null
        val metaFile = adapter.listFiles(parentsIds = listOf(folderId), name = DOCUMENT_META_FILENAME)
            .firstOrNull() ?: return null
        return downloadMeta(metaFile)
    }

    /**
     * Lists only the documents whose meta.json changed since [watermark] (server-side
     * createdTimeAtLeast filter, minus a small overlap margin), in one batched call across all
     * folders. Also returns the current folder set (for new-document discovery + purge detection)
     * and the matched/failed createdTimes so the merge can advance the watermark safely.
     * Pass watermark = 0 for a full (cold-start) listing.
     */
    suspend fun listChangedDocuments(watermark: Long): ChangedListing {
        // Begun before the first request: the settle check compares the file times with the listing's start.
        val session = metaReads?.begin(adapter, rootFolderId)
        val folders = adapter.getFolders(rootFolderId)
        val since = maxOf(0L, watermark - LISTING_MARGIN_MS)
        val metaFiles = adapter.listChangedFilesInFolders(
            rootFolderId = rootFolderId,
            folderIds = folders.map { it.id },
            name = DOCUMENT_META_FILENAME,
            createdTimeAtLeast = since,
        )
        // Each meta read is an independent read-only fetch, so fan them out with bounded concurrency.
        // The merge is order-independent, so the concurrent map + null-partition is correct.
        val matched = metaFiles.map { it.createdTime }
        val results = metaFiles.asyncMap(LIST_CONCURRENCY) { file ->
            file to (session?.let { metaReads?.reuse(file, it) } ?: downloadMeta(file)?.also { meta ->
                session?.let { metaReads?.remember(file, meta, it) }
            })
        }
        val changed = results.mapNotNull { it.second }
        val failed = results.filter { it.second == null }.map { it.first.createdTime }
        return ChangedListing(changed, folders.map { it.name }.toSet(), matched, failed)
    }

    /** Full listing of all live + tombstoned cloud metas (cold-start path: watermark 0). */
    suspend fun listDocuments(): List<DocumentSyncMeta> = listChangedDocuments(0).changedMetas

    private suspend fun writeMeta(folderId: String, meta: DocumentSyncMeta) {
        // delete existing meta.json then upload fresh (acts as the atomic commit point)
        adapter.listFiles(parentsIds = listOf(folderId), name = DOCUMENT_META_FILENAME)
            .forEach { adapter.delete(it.id) }
        val tmp = createTempFile(env.tmpDir.toPath(), "andbible-", ".tmp")
        try {
            FileSystem.SYSTEM.write(tmp) { writeUtf8(meta.toJson()) }
            adapter.upload(DOCUMENT_META_FILENAME, tmp, folderId)
        } finally { runCatching { FileSystem.SYSTEM.delete(tmp) } }
    }

    suspend fun uploadDocument(meta: DocumentSyncMeta, archive: Path) {
        val folderId = ensureFolder(meta.initials)
        val archiveName = "${meta.version}.abmd.zip"
        // upload archive first; only then commit meta.json pointing at it
        adapter.upload(archiveName, archive, folderId)
        // remove older archives (keep only newest version)
        adapter.listFiles(parentsIds = listOf(folderId))
            .filter { it.name.endsWith(".abmd.zip") && it.name != archiveName }
            .forEach { adapter.delete(it.id) }
        writeMeta(folderId, meta)
    }

    /**
     * Downloads the archive for [initials] at [version], or returns null if the folder or the
     * named archive is absent — e.g. a concurrent push/tombstone on another device removed it
     * between the caller's listing and now. Returning null lets the caller skip gracefully and
     * retry on the next pull rather than crashing the whole batch.
     */
    suspend fun downloadArchive(initials: String, version: String, onProgress: DownloadProgressListener? = null): Path? {
        val folderId = folderFor(initials)?.id ?: return null
        val file = adapter.listFiles(parentsIds = listOf(folderId), name = "$version.abmd.zip").firstOrNull()
            ?: return null
        val tmp = createTempFile(env.tmpDir.toPath(), "andbible-", ".tmp")
        try {
            adapter.download(file.id, FileSystem.SYSTEM.sink(tmp), onProgress)
        } catch (e: Exception) {
            // The temp file was created before the download; delete it so a failed (e.g. network
            // drop) download doesn't leak a potentially large orphan in the tmp dir.
            runCatching { FileSystem.SYSTEM.delete(tmp) }
            throw e
        }
        return tmp
    }

    suspend fun writeTombstone(meta: DocumentSyncMeta) {
        val folderId = ensureFolder(meta.initials)
        // Commit the tombstone meta FIRST, then drop the archives. A leftover archive under a
        // deleted=true meta is harmless (readers ignore archives for a tombstone); the dangerous
        // state is a deleted archive under a still-live meta, which would make the document
        // un-downloadable on other devices if we crashed between the two steps.
        writeMeta(folderId, meta.copy(deleted = true))
        adapter.listFiles(parentsIds = listOf(folderId))
            .filter { it.name.endsWith(".abmd.zip") }
            .forEach { adapter.delete(it.id) }
    }

    /**
     * Permanently deletes a document's entire cloud folder (its meta + any residual archives),
     * keyed by initials. Used to purge a tombstone from the cloud history. A no-op when the
     * folder is absent. Children are deleted first, then the folder, so the removal is explicit
     * regardless of whether the adapter cascades folder deletes.
     */
    suspend fun deleteDocument(initials: String) {
        val folder = folderFor(initials) ?: return
        adapter.listFiles(parentsIds = listOf(folder.id)).forEach { adapter.delete(it.id) }
        adapter.delete(folder.id)
    }
}
