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
import net.bible.service.cloudsync.CloudSync
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.download.isPseudoBook
import net.bible.service.sword.SwordDocumentFacade
import net.bible.service.sword.mydocument.isMyDocument
import org.crosswire.common.util.Version
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books

/** Wall-clock budget for DOWNLOAD/UPGRADE actions in a single pull cycle (ms). */
private const val PULL_DOWNLOAD_BUDGET_MS = 150_000L

object DocumentSync {
    private const val TAG = "DocumentSync"

    fun versionIsNewer(cloudVersion: String, localVersion: String): Boolean =
        try { Version(cloudVersion) > Version(localVersion) } catch (e: Exception) { false }

    private suspend fun store(): DocumentStore? {
        val adapter = CloudSync.cloudAdapter ?: return null
        val folderId = CloudSync.documentsSyncFolderId() ?: return null
        return DocumentStore(adapter, folderId)
    }

    private fun installedSyncableBooks(): List<Book> =
        Books.installed().books.filter { !it.isPseudoBook && !it.isMyDocument }

    data class DocumentStatusItem(
        val initials: String,
        val name: String,
        val type: DocumentType,
        val cloudVersion: String?,
        val localVersion: String?,
        val cloudOnly: Boolean,
        val localOnly: Boolean,
        val updateAvailable: Boolean,
        val blocked: Boolean,
        val sizeBytes: Long,
        val category: BookCategory?,
    )

    suspend fun scan(): List<DocumentStatusItem> {
        val store = store() ?: return emptyList()
        val cloud = store.listDocuments().filter { !it.deleted }.associateBy { it.initials }
        val local = installedSyncableBooks().associateBy { it.initials }
        val blocked = DocumentSyncSettings.blockList.all()
        val allInitials = (cloud.keys + local.keys).toSortedSet()
        return allInitials.map { initials ->
            val c = cloud[initials]; val b = local[initials]
            val localVersion = b?.let { DocumentArchiver.documentVersion(it) }
            val update = c != null && localVersion != null && versionIsNewer(c.version, localVersion)
            val category = b?.bookCategory ?: parseCategoryName(c?.category)
            DocumentStatusItem(
                initials = initials,
                name = c?.name ?: b?.name ?: initials,
                type = b?.let { DocumentArchiver.documentTypeOf(it) } ?: c?.documentType ?: DocumentType.SWORD,
                cloudVersion = c?.version,
                localVersion = localVersion,
                cloudOnly = c != null && b == null,
                localOnly = c == null && b != null,
                updateAvailable = update,
                blocked = initials in blocked,
                sizeBytes = c?.size ?: 0L,
                category = category,
            )
        }
    }

    suspend fun pushDocument(book: Book) {
        val store = store() ?: return
        val archive = DocumentArchiver.packageDocument(book)
        try {
            val cipherKey = DatabaseContainer.instance.repoDb.swordDocumentInfoDao().getBook(book.initials)?.cipherKey
            val meta = DocumentSyncMeta(
                initials = book.initials,
                name = book.name,
                documentType = DocumentArchiver.documentTypeOf(book),
                version = DocumentArchiver.documentVersion(book),
                size = archive.length(),
                language = book.language.code,
                category = book.bookCategory.name,
                sourceDevice = CommonUtils.deviceIdentifier,
                timestamp = System.currentTimeMillis(),
                cipherKey = cipherKey,
            )
            // Skip if cloud already has same/newer version
            val existing = store.listDocuments().firstOrNull { it.initials == book.initials && !it.deleted }
            if (existing != null && !versionIsNewer(meta.version, existing.version)) {
                Log.i(TAG, "Cloud has same/newer ${book.initials}; skipping upload"); return
            }
            store.uploadDocument(meta, archive)
            DocumentSyncSettings.setSyncTimestamp(book.initials, meta.timestamp)
        } finally { archive.delete() }
    }

    suspend fun uploadDocument(book: Book) {
        if (!DocumentSyncSettings.enabled || !DocumentSyncSettings.automatic) return
        if (DocumentSyncSettings.blockList.isBlocked(book.initials)) return
        if (!DocumentSyncSettings.isAutoTransferAllowed) return
        pushDocument(book)
    }

    suspend fun downloadAndInstall(initials: String) {
        val store = store() ?: return
        val meta = store.listDocuments().firstOrNull { it.initials == initials && !it.deleted } ?: return
        val archive = store.downloadArchive(initials, meta.version)
        try {
            if (DocumentArchiver.installArchive(archive, initials)) {
                DocumentSyncSettings.setSyncTimestamp(initials, meta.timestamp)
            }
        } finally { archive.delete() }
    }

    suspend fun pullDocuments(automaticOnly: Boolean) {
        if (!DocumentSyncSettings.enabled) return
        if (automaticOnly && (!DocumentSyncSettings.automatic || !DocumentSyncSettings.isAutoTransferAllowed)) return
        val store = store() ?: return
        val cloudMetas = store.listDocuments()
        val local = installedSyncableBooks().associateBy { it.initials }
        val cloudDocs = cloudMetas.map {
            CloudDocument(it.initials, it.name, it.documentType, it.version, it.size, it.timestamp, it.deleted)
        }
        val localDocs = local.mapValues { (i, b) -> LocalDocument(i, DocumentArchiver.documentVersion(b)) }
        val syncTimestamps = local.keys.mapNotNull { i -> DocumentSyncSettings.syncTimestamp(i)?.let { i to it } }.toMap()
        val actions = resolveDocumentSyncActions(
            cloudDocs, localDocs, syncTimestamps,
            DocumentSyncSettings.blockList.all(), ::versionIsNewer)
        val start = System.currentTimeMillis()
        var deferred = 0
        for (action in actions) when (action.type) {
            DocumentSyncActionType.DOWNLOAD, DocumentSyncActionType.UPGRADE -> {
                if ((System.currentTimeMillis() - start) > PULL_DOWNLOAD_BUDGET_MS) {
                    deferred++
                } else {
                    downloadAndInstall(action.initials)
                }
            }
            DocumentSyncActionType.UNINSTALL -> uninstallLocal(action.initials, local)
            DocumentSyncActionType.SKIP_BLOCKED, DocumentSyncActionType.NONE -> {}
        }
        if (deferred > 0) {
            Log.i(TAG, "Document pull: deferred $deferred document download(s) to next sync cycle (time budget reached)")
        }
    }

    private fun uninstallLocal(initials: String, local: Map<String, Book>) {
        val book = local[initials] ?: return
        try {
            SwordDocumentFacade.deleteDocument(book)
            DocumentSyncSettings.setSyncTimestamp(initials, System.currentTimeMillis())
        } catch (e: Exception) { Log.e(TAG, "Failed uninstalling $initials", e) }
    }

    suspend fun removeFromCloud(initials: String, name: String, type: DocumentType) {
        val store = store() ?: return
        val existing = store.listDocuments().firstOrNull { it.initials == initials }
        val meta = (existing ?: DocumentSyncMeta(
            initials = initials, name = name, documentType = type, version = "0.0",
            size = 0, language = "", sourceDevice = CommonUtils.deviceIdentifier,
            timestamp = 0,
        )).copy(timestamp = System.currentTimeMillis())
        store.writeTombstone(meta)
    }
}

/** Parses a stored BookCategory enum name; null for null/blank/unknown names. */
fun parseCategoryName(name: String?): BookCategory? =
    name?.takeIf { it.isNotBlank() }?.let { runCatching { BookCategory.valueOf(it) }.getOrNull() }
