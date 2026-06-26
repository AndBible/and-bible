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
import net.bible.service.common.CommonUtils
import java.io.File

private const val TAG = "DocumentStore"

class DocumentStore(
    private val adapter: CloudAdapter,
    private val rootFolderId: String,
) {
    private suspend fun folderFor(initials: String): CloudFile? =
        adapter.getFolders(rootFolderId).firstOrNull { it.name == initials }

    private suspend fun ensureFolder(initials: String): String =
        folderFor(initials)?.id ?: adapter.createNewFolder(initials, rootFolderId).id

    private suspend fun readMeta(folderId: String): DocumentSyncMeta? {
        val metaFile = adapter.listFiles(parentsIds = listOf(folderId), name = DOCUMENT_META_FILENAME)
            .firstOrNull() ?: return null
        val tmp = CommonUtils.tmpFile
        return try {
            tmp.outputStream().use { adapter.download(metaFile.id, it) }
            DocumentSyncMeta.fromJson(tmp.readText())
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading meta for folder $folderId", e); null
        } finally { tmp.delete() }
    }

    suspend fun listDocuments(): List<DocumentSyncMeta> =
        adapter.getFolders(rootFolderId).mapNotNull { readMeta(it.id) }

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
    suspend fun downloadArchive(initials: String, version: String): File? {
        val folderId = folderFor(initials)?.id ?: return null
        val file = adapter.listFiles(parentsIds = listOf(folderId), name = "$version.abmd.zip").firstOrNull()
            ?: return null
        val tmp = CommonUtils.tmpFile
        try {
            tmp.outputStream().use { adapter.download(file.id, it) }
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
}
