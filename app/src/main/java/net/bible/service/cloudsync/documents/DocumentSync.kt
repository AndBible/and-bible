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
import net.bible.android.BibleApplication
import net.bible.android.control.document.canDelete
import net.bible.android.database.toMeta
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
import org.crosswire.jsword.book.sword.SwordBookMetaData

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
        /** Local copy is newer than the cloud copy (a push would update the cloud). */
        val localNewer: Boolean,
        val blocked: Boolean,
        val sizeBytes: Long,
        val category: BookCategory?,
        /** Whether the locally installed copy may be deleted (false e.g. for the last Bible). */
        val canDeleteLocal: Boolean,
    )

    suspend fun scan(): List<DocumentStatusItem> {
        val cacheDao = DatabaseContainer.instance.cloudDocumentsCacheDb.cloudDocumentCacheDao()
        val store = store()
        val local = installedSyncableBooks().associateBy { it.initials }
        val cloudMetas: List<DocumentSyncMeta> = if (store != null) {
            val live = store.listDocuments()
            cacheDao.replaceAll(live.map { it.toCacheEntity() })   // refresh cache from network
            live
        } else {
            cacheDao.all().map { it.toMeta() }                     // offline / not signed in: use cache
        }
        return buildStatusItems(cloudMetas, local)
    }

    /**
     * Refreshes the local cloud-listing cache from the network. Call after any sync operation
     * (push/download/remove) so the cached status is current even when the management view isn't
     * open to trigger its own scan.
     */
    suspend fun refreshCache() {
        val store = store() ?: return
        val cacheDao = DatabaseContainer.instance.cloudDocumentsCacheDb.cloudDocumentCacheDao()
        cacheDao.replaceAll(store.listDocuments().map { it.toCacheEntity() })
    }

    /**
     * Builds the status list from the local cloud-listing cache only, without any network
     * access. Lets the management view render instantly on open; [scan] then refreshes from
     * the network in the background.
     */
    suspend fun scanCached(): List<DocumentStatusItem> {
        val cacheDao = DatabaseContainer.instance.cloudDocumentsCacheDb.cloudDocumentCacheDao()
        val local = installedSyncableBooks().associateBy { it.initials }
        return buildStatusItems(cacheDao.all().map { it.toMeta() }, local)
    }

    /** Installed module size in bytes from the SWORD conf, or null if not declared. */
    private fun localInstallSizeBytes(book: Book): Long? =
        book.bookMetaData.getProperty(SwordBookMetaData.KEY_INSTALL_SIZE)?.toLongOrNull()

    private fun buildStatusItems(
        cloudMetas: List<DocumentSyncMeta>,
        local: Map<String, Book>,
    ): List<DocumentStatusItem> {
        val cloud = cloudMetas.filter { !it.deleted }.associateBy { it.initials }
        val blocked = DocumentSyncSettings.blockList.all()
        val allInitials = (cloud.keys + local.keys).toSortedSet()
        return allInitials.map { initials ->
            val c = cloud[initials]; val b = local[initials]
            val localVersion = b?.let { DocumentArchiver.documentVersion(it) }
            val update = c != null && localVersion != null && versionIsNewer(c.version, localVersion)
            val localNewer = c != null && localVersion != null && versionIsNewer(localVersion, c.version)
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
                localNewer = localNewer,
                blocked = initials in blocked,
                // Cloud size is the exact packaged size; for a local-only document not yet in
                // the cloud, fall back to the installed module size so the upload size isn't 0.
                sizeBytes = c?.size ?: b?.let { localInstallSizeBytes(it) } ?: 0L,
                category = category,
                // No local copy → nothing to delete locally; otherwise honour the last-Bible guard.
                canDeleteLocal = b?.canDelete ?: true,
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
        // The scheduled (automatic) pull requires automatic sync to be enabled and transfers to
        // be allowed on the current network. Manual "Sync now" (automaticOnly = false) bypasses
        // both — the user explicitly asked for it, even with automatic sync off.
        if (automaticOnly && (!DocumentSyncSettings.enabled || !DocumentSyncSettings.isAutoTransferAllowed)) return
        val store = store() ?: return
        val cloudMetas = store.listDocuments()
        // Keep the cache fresh on every pull (not only when something is transferred), so with
        // automatic sync on the management view can trust the cache without hitting the network.
        DatabaseContainer.instance.cloudDocumentsCacheDb.cloudDocumentCacheDao()
            .replaceAll(cloudMetas.map { it.toCacheEntity() })
        val local = installedSyncableBooks().associateBy { it.initials }
        val cloudDocs = cloudMetas.map {
            CloudDocument(it.initials, it.name, it.documentType, it.version, it.size, it.timestamp, it.deleted)
        }
        val localDocs = local.mapValues { (i, b) -> LocalDocument(i, DocumentArchiver.documentVersion(b)) }
        val syncTimestamps = local.keys.mapNotNull { i -> DocumentSyncSettings.syncTimestamp(i)?.let { i to it } }.toMap()
        val actions = resolveDocumentSyncActions(
            cloudDocs, localDocs, syncTimestamps,
            DocumentSyncSettings.blockList.all(), ::versionIsNewer)
        val toDownload = mutableListOf<String>()
        for (action in actions) when (action.type) {
            DocumentSyncActionType.DOWNLOAD, DocumentSyncActionType.UPGRADE -> toDownload.add(action.initials)
            DocumentSyncActionType.UNINSTALL -> uninstallLocal(action.initials, local)
            DocumentSyncActionType.SKIP_BLOCKED, DocumentSyncActionType.NONE -> {}
        }
        if (toDownload.isNotEmpty()) {
            DocumentSyncService.start(BibleApplication.application, pushInitials = emptyList(), downloadInitials = toDownload)
        }
    }

    private fun uninstallLocal(initials: String, local: Map<String, Book>) {
        val book = local[initials] ?: return
        try {
            SwordDocumentFacade.deleteDocument(book)
            DocumentSyncSettings.setSyncTimestamp(initials, System.currentTimeMillis())
        } catch (e: Exception) { Log.e(TAG, "Failed uninstalling $initials", e) }
    }

    suspend fun removeFromCloud(initials: String) {
        val store = store() ?: return
        val now = System.currentTimeMillis()
        val existing = store.listDocuments().firstOrNull { it.initials == initials }
        // Remove is only offered when a cloud copy exists, so `existing` is normally present;
        // the fallback just guards against a race where it disappeared.
        val meta = (existing ?: DocumentSyncMeta(
            initials = initials, name = initials, documentType = DocumentType.SWORD, version = "0.0",
            size = 0, language = "", sourceDevice = CommonUtils.deviceIdentifier,
            timestamp = 0,
        )).copy(timestamp = now)
        store.writeTombstone(meta)
        // Other devices (older sync timestamp) see the tombstone as strictly newer and uninstall
        // locally. With sync ENABLED here, "Remove from all devices" also deletes the local copy
        // on this device — otherwise it would be orphaned (gone from the cloud, no longer synced).
        // With sync OFF (manual "Remove from cloud"), the local copy is kept; it won't be
        // re-downloaded because automatic pull is off.
        if (DocumentSyncSettings.enabled) {
            Books.installed().getBook(initials)?.let { SwordDocumentFacade.deleteDocument(it) }
        }
        // Record this device as already knowing the tombstone so our own next pull is a no-op.
        DocumentSyncSettings.setSyncTimestamp(initials, now)
    }
}

/** Parses a stored BookCategory enum name; null for null/blank/unknown names. */
fun parseCategoryName(name: String?): BookCategory? =
    name?.takeIf { it.isNotBlank() }?.let { runCatching { BookCategory.valueOf(it) }.getOrNull() }
