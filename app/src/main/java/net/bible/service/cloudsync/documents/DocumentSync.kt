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
import net.bible.android.database.SwordDocumentInfo
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
import java.util.Collections

object DocumentSync {
    private const val TAG = "DocumentSync"

    /**
     * Whether [cloudVersion] is strictly newer than [localVersion].
     *
     * Equal raw version strings short-circuit to `false` (not newer) *before* attempting a
     * numeric parse. This matters because JSword's [Version] only accepts `\d+(.\d+){0,3}` and
     * throws on anything else — and MyBible/MySword/eSword/EPUB documents (exactly the kinds this
     * feature targets) frequently carry non-numeric or date versions. Without the equality check,
     * a non-parseable version would always compare as "not newer", which is fine for the equal
     * case but means a non-numeric version that genuinely differs is treated conservatively as
     * not-newer (no auto-propagation) rather than guessing an order we cannot reliably determine.
     */
    fun versionIsNewer(cloudVersion: String, localVersion: String): Boolean {
        if (cloudVersion == localVersion) return false
        return try { Version(cloudVersion) > Version(localVersion) } catch (e: Exception) { false }
    }

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
     * Tears document sync down on sign-out: disables automatic sync and drops the
     * cloud-listing cache. The cache mirrors the listing of the cloud account the user
     * just disconnected from, so it must not survive into a later sign-in (possibly a
     * different account). Mirrors the DB-sync sign-out, which clears its own sync status.
     * The block list and Wi-Fi-only preference are per-device and intentionally kept.
     */
    suspend fun onSignOut() {
        DocumentSyncSettings.enabled = false
        DatabaseContainer.instance.cloudDocumentsCacheDb.cloudDocumentCacheDao().clear()
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
        val localVersion = DocumentArchiver.documentVersion(book)
        // Check the cloud BEFORE packaging — packaging zips the whole module (potentially tens of
        // MB), which is wasteful when the cloud already holds the same/newer version. This is the
        // common case for an auto-push that echoes a just-downloaded module (see the
        // installingFromSync guard in downloadAndInstall) or any redundant re-push.
        val existing = store.listDocuments().firstOrNull { it.initials == book.initials && !it.deleted }
        if (existing != null && !versionIsNewer(localVersion, existing.version)) {
            Log.i(TAG, "Cloud has same/newer ${book.initials}; skipping upload"); return
        }
        val archive = DocumentArchiver.packageDocument(book)
        try {
            val cipherKey = DatabaseContainer.instance.repoDb.swordDocumentInfoDao().getBook(book.initials)?.cipherKey
            val meta = DocumentSyncMeta(
                initials = book.initials,
                name = book.name,
                documentType = DocumentArchiver.documentTypeOf(book),
                version = localVersion,
                size = archive.length(),
                language = book.language.code,
                category = book.bookCategory.name,
                sourceDevice = CommonUtils.deviceIdentifier,
                timestamp = System.currentTimeMillis(),
                cipherKey = cipherKey,
            )
            store.uploadDocument(meta, archive)
            DocumentSyncSettings.setSyncTimestamp(book.initials, meta.timestamp)
        } finally { archive.delete() }
    }

    /**
     * Initials currently being installed from a sync download. The install fires `bookAdded`,
     * which would otherwise immediately enqueue a redundant auto-push of the very module we just
     * downloaded. [BookInstallWatcher] consults [isInstallingFromSync] to suppress that echo.
     */
    private val installingFromSync: MutableSet<String> = Collections.synchronizedSet(HashSet())

    fun isInstallingFromSync(initials: String): Boolean = installingFromSync.contains(initials)

    suspend fun downloadAndInstall(initials: String) {
        val store = store() ?: return
        val meta = store.listDocuments().firstOrNull { it.initials == initials && !it.deleted } ?: return
        val archive = store.downloadArchive(initials, meta.version) ?: return
        installingFromSync.add(initials)
        try {
            // Integrity guard: meta.size is the exact packaged (zip) size recorded at push time, so
            // a length mismatch means a truncated/partial download. installArchive only checks that
            // the book registers, not that its data is complete, and a "success" would set the sync
            // timestamp and never re-download — leaving a permanently broken module. Skip and retry.
            if (meta.size > 0 && archive.length() != meta.size) {
                Log.w(TAG, "Downloaded $initials size ${archive.length()} != expected ${meta.size}; skipping install")
                return
            }
            if (DocumentArchiver.installArchive(archive, initials)) {
                applyCipherKey(initials, meta.cipherKey)
                DocumentSyncSettings.setSyncTimestamp(initials, meta.timestamp)
            }
        } finally {
            archive.delete()
            installingFromSync.remove(initials)
        }
    }

    /**
     * Persists and applies the encryption key for a freshly downloaded SWORD module. Without this
     * an encrypted module syncs but stays locked, rendering no readable content. The key travels
     * in the document's [DocumentSyncMeta] (the user's own private cloud account).
     */
    private fun applyCipherKey(initials: String, cipherKey: String?) {
        if (cipherKey == null) return
        val book = Books.installed().getBook(initials) ?: return
        book.unlock(cipherKey)
        val dao = DatabaseContainer.instance.repoDb.swordDocumentInfoDao()
        // bookAdded inserts a row (cipherKey = null) during install, so normally update it; insert
        // as a fallback in case the row is missing.
        val existing = dao.getBook(initials)
        if (existing != null) {
            existing.cipherKey = cipherKey
            dao.update(existing)
        } else {
            dao.insert(SwordDocumentInfo(initials, book.name, book.abbreviation, book.language.name, "", cipherKey))
        }
    }

    /**
     * Runs document sync for the selected operations. Used by both the automatic cycle and the
     * manual "Sync now". [download] enqueues DOWNLOAD/UPGRADE, [delete] applies tombstone-driven
     * UNINSTALLs, [upload] pushes local-only / local-newer documents. Block list and (for
     * automatic runs) the enabled + Wi-Fi-only guards always apply; a [manual] run bypasses the
     * enabled/Wi-Fi guards because the user asked for it explicitly.
     */
    suspend fun runSync(download: Boolean, upload: Boolean, delete: Boolean, manual: Boolean) {
        if (!manual && (!DocumentSyncSettings.enabled || !DocumentSyncSettings.isAutoTransferAllowed)) return
        val store = store() ?: return
        val cloudMetas = store.listDocuments()
        // Keep the cache fresh on every run (not only when something transfers), so with automatic
        // sync on the management view can trust the cache without hitting the network.
        DatabaseContainer.instance.cloudDocumentsCacheDb.cloudDocumentCacheDao()
            .replaceAll(cloudMetas.map { it.toCacheEntity() })
        val local = installedSyncableBooks().associateBy { it.initials }
        val cloudDocs = cloudMetas.map {
            CloudDocument(it.initials, it.name, it.documentType, it.version, it.size, it.timestamp, it.deleted)
        }
        val localDocs = local.mapValues { (i, b) -> LocalDocument(i, DocumentArchiver.documentVersion(b)) }
        val blocked = DocumentSyncSettings.blockList.all()
        val syncTimestamps = local.keys.mapNotNull { i -> DocumentSyncSettings.syncTimestamp(i)?.let { i to it } }.toMap()
        val actions = selectSyncActions(
            resolveDocumentSyncActions(cloudDocs, localDocs, syncTimestamps, blocked, ::versionIsNewer),
            allowDownload = download,
            allowDelete = delete,
        )
        val toDownload = mutableListOf<String>()
        for (action in actions) when (action.type) {
            DocumentSyncActionType.DOWNLOAD, DocumentSyncActionType.UPGRADE -> toDownload.add(action.initials)
            DocumentSyncActionType.UNINSTALL -> uninstallLocal(action.initials, local)
            DocumentSyncActionType.SKIP_BLOCKED, DocumentSyncActionType.NONE -> {}
        }
        val toUpload = if (upload) resolveUploads(localDocs, cloudDocs, blocked, ::versionIsNewer) else emptyList()
        if (toUpload.isNotEmpty() || toDownload.isNotEmpty()) {
            DocumentSyncService.start(BibleApplication.application, pushInitials = toUpload, downloadInitials = toDownload)
        }
    }

    private fun uninstallLocal(initials: String, local: Map<String, Book>) {
        val book = local[initials] ?: return
        try {
            val decision = decideUninstall(book.canDelete)
            if (decision.delete) {
                SwordDocumentFacade.deleteDocument(book)
            } else {
                // e.g. the last remaining Bible — keep it rather than leave the device with none.
                Log.i(TAG, "Tombstone for $initials not applied: book is not deletable")
            }
            if (decision.advanceTimestamp) {
                DocumentSyncSettings.setSyncTimestamp(initials, System.currentTimeMillis())
            }
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
            Books.installed().getBook(initials)?.let { book ->
                // Same last-Bible guard as the propagated uninstall: never delete an undeletable
                // local document even when the user removed it from the cloud here.
                if (book.canDelete) SwordDocumentFacade.deleteDocument(book)
                else Log.i(TAG, "Keeping local $initials after cloud removal: book is not deletable")
            }
        }
        // Record this device as already knowing the tombstone so our own next pull is a no-op.
        DocumentSyncSettings.setSyncTimestamp(initials, now)
    }
}

/** Parses a stored BookCategory enum name; null for null/blank/unknown names. */
fun parseCategoryName(name: String?): BookCategory? =
    name?.takeIf { it.isNotBlank() }?.let { runCatching { BookCategory.valueOf(it) }.getOrNull() }
