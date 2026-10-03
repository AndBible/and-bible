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

import android.util.Log
import net.bible.service.cloudsync.CloudAdapter
import net.bible.service.cloudsync.CloudFile
import net.bible.service.cloudsync.DownloadProgressListener
import net.bible.service.common.CommonUtils
import net.bible.service.common.asyncMap
import java.io.File

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

class DocumentStore(
    private val adapter: CloudAdapter,
    private val rootFolderId: String,
) {
    private suspend fun folderFor(initials: String): CloudFile? =
        adapter.getFolders(rootFolderId).firstOrNull { it.name == initials }

    private suspend fun ensureFolder(initials: String): String =
        folderFor(initials)?.id ?: adapter.createNewFolder(initials, rootFolderId).id

    private suspend fun downloadMeta(metaFile: CloudFile): DocumentSyncMeta? {
        val tmp = CommonUtils.tmpFile
        return try {
            tmp.outputStream().use { adapter.download(metaFile.id, it) }
            DocumentSyncMeta.fromJson(tmp.readText())
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading meta ${metaFile.id}", e); null
        } finally { tmp.delete() }
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
        val folders = adapter.getFolders(rootFolderId)
        val since = maxOf(0L, watermark - LISTING_MARGIN_MS)
        val metaFiles = adapter.listFiles(
            parentsIds = folders.map { it.id },
            name = DOCUMENT_META_FILENAME,
            createdTimeAtLeast = since,
        )
        // Each meta read is an independent read-only fetch, so fan them out with bounded concurrency.
        // The merge is order-independent, so the concurrent map + null-partition is correct.
        val matched = metaFiles.map { it.createdTime }
        val results = metaFiles.asyncMap(LIST_CONCURRENCY) { it to downloadMeta(it) }
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
        val tmp = CommonUtils.tmpFile
        try {
            tmp.writeText(meta.toJson())
            adapter.upload(DOCUMENT_META_FILENAME, tmp, folderId)
        } finally { tmp.delete() }
    }

    suspend fun uploadDocument(meta: DocumentSyncMeta, archive: File) {
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
    suspend fun downloadArchive(initials: String, version: String, onProgress: DownloadProgressListener? = null): File? {
        val folderId = folderFor(initials)?.id ?: return null
        val file = adapter.listFiles(parentsIds = listOf(folderId), name = "$version.abmd.zip").firstOrNull()
            ?: return null
        val tmp = CommonUtils.tmpFile
        try {
            tmp.outputStream().use { adapter.download(file.id, it, onProgress) }
        } catch (e: Exception) {
            // The temp file was created before the download; delete it so a failed (e.g. network
            // drop) download doesn't leak a potentially large orphan in the tmp dir.
            tmp.delete()
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
