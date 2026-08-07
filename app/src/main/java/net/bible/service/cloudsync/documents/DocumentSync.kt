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
import net.bible.service.sword.SwordDocumentFacade
import org.crosswire.common.util.Version
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookMetaData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
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
        Books.installed().books.filter { it.isSyncableDocument }

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
        /** The cloud meta for this document is a tombstone (removed from the cloud). */
        val cloudDeleted: Boolean = false,
    )

    suspend fun scan(includeDeleted: Boolean = false): List<DocumentStatusItem> {
        // Signed in: incrementally refresh the cache from the network (a no-op when signed out).
        // A network failure — most commonly the device being offline — must NOT crash the caller:
        // the management view (CloudDocumentsActivity) and the "enable document sync" flow both call
        // scan(), so an uncaught IOException here would crash the app on opening the sync view while
        // offline (OSTicket 3362). The network reach is documentsSyncFolderId() -> listFiles(),
        // which throws IOException when offline; on failure we fall back to the cached listing.
        try {
            refreshCache()
        } catch (e: IOException) {
            Log.i(TAG, "Network unavailable during document scan; showing cached listing", e)
        }
        return scanCached(includeDeleted)     // offline / after refresh: build from the cache
    }

    /**
     * Incrementally refreshes the cloud-listing cache: fetches only metas changed since the stored
     * watermark, merges them into the cache (upsert + purge), and advances the watermark. With
     * nothing changed this is two round-trips and no downloads. Watermark 0 (cold start) lists all.
     *
     * The read-merge-write (cache + watermark) is not wrapped in a single transaction; it relies on
     * callers being serialized — the sync cycle and the single-threaded [DocumentSyncService] drain
     * never run it concurrently. (Even if they did, the watermark only ever advances via [mergeCloudListing]'s
     * maxOf guard, so the worst case is a redundant re-list, never a backward step or data loss.)
     */
    suspend fun refreshCache() {
        val store = store() ?: return
        val cacheDao = DatabaseContainer.instance.documentSyncDb.cloudDocumentCacheDao()
        val watermark = DocumentSyncSettings.watermark
        val listing = store.listChangedDocuments(watermark)
        val merged = mergeCloudListing(
            oldCache = cacheDao.all().map { it.toMeta() },
            changed = listing.changedMetas,
            currentInitials = listing.currentInitials,
            oldWatermark = watermark,
            matchedCreatedTimes = listing.matchedCreatedTimes,
            failedCreatedTimes = listing.failedCreatedTimes,
        )
        cacheDao.replaceAll(merged.cache.map { it.toCacheEntity() })
        DocumentSyncSettings.watermark = merged.watermark
    }

    /**
     * Clears the listing cache and watermark so the next [scan]/[refreshCache] cold-starts a full
     * authoritative listing. User preferences (block list, toggles) are intentionally kept — this is
     * "re-scan the cloud", not a sign-out. Recovery path for a rare clock-skew silent miss.
     */
    suspend fun resetListingCache() {
        DatabaseContainer.instance.documentSyncDb.apply {
            cloudDocumentCacheDao().clear()
            cloudListingStateDao().clear()
        }
    }

    /**
     * Tears document sync down on sign-out by wiping the entire [net.bible.android.database.DocumentSyncDatabase]:
     * settings, the cloud-listing cache, the listing watermark, and per-document sync timestamps.
     * All of it is device-local state tied to the account just disconnected — a later sign-in may
     * target a different cloud account and must start clean (sync off, empty block list, cold-start
     * listing). Mirrors the DB-sync sign-out, which clears its own per-database sync status.
     */
    suspend fun onSignOut() = withContext(Dispatchers.IO) {
        val db = DatabaseContainer.instance.documentSyncDb
        db.runInTransaction {
            db.cloudDocumentCacheDao().clear()
            db.documentSyncPreferencesDao().clear()
            db.cloudListingStateDao().clear()
            db.cloudDocumentSyncTimestampDao().clear()
        }
    }

    /**
     * Builds the status list from the local cloud-listing cache only, without any network
     * access. Lets the management view render instantly on open; [scan] then refreshes from
     * the network in the background.
     */
    suspend fun scanCached(includeDeleted: Boolean = false): List<DocumentStatusItem> {
        val cacheDao = DatabaseContainer.instance.documentSyncDb.cloudDocumentCacheDao()
        val local = installedSyncableBooks().associateBy { it.initials }
        return buildStatusItems(cacheDao.all().map { it.toMeta() }, local, includeDeleted)
    }

    /** Cloud storage used by synced document archives, read from the local listing cache (no network). */
    suspend fun cloudBytesUsed(): Long = withContext(Dispatchers.IO) {
        val cacheDao = DatabaseContainer.instance.documentSyncDb.cloudDocumentCacheDao()
        sumCloudBytes(cacheDao.all().map { it.toMeta() })
    }

    /** Installed module size in bytes from the SWORD conf, or null if not declared. */
    private fun localInstallSizeBytes(book: Book): Long? =
        book.bookMetaData.getProperty(SwordBookMetaData.KEY_INSTALL_SIZE)?.toLongOrNull()

    private fun buildStatusItems(
        cloudMetas: List<DocumentSyncMeta>,
        local: Map<String, Book>,
        includeDeleted: Boolean = false,
    ): List<DocumentStatusItem> {
        val localDocs = local.mapValues { (_, b) ->
            LocalDoc(
                name = b.name,
                version = DocumentArchiver.documentVersion(b),
                category = b.bookCategory,
                type = DocumentArchiver.documentTypeOf(b),
                canDelete = b.canDelete,
                installSizeBytes = localInstallSizeBytes(b),
            )
        }
        return assembleStatusItems(cloudMetas, localDocs, DocumentSyncSettings.blockList.all(), includeDeleted)
    }

    suspend fun pushDocument(book: Book) {
        // Last line of defence: a queued Push op carries only initials, so a book that cannot be
        // packaged as a module archive must be rejected here too — however the op was enqueued.
        if (!book.isSyncableDocument) {
            Log.w(TAG, "Not a syncable document; skipping upload of ${book.initials}"); return
        }
        val store = store() ?: return
        val localVersion = DocumentArchiver.documentVersion(book)
        // Check the cloud BEFORE packaging — packaging zips the whole module (potentially tens of
        // MB), which is wasteful when the cloud already holds the same/newer version. This is the
        // common case for an auto-push that echoes a just-downloaded module (see the
        // installingFromSync guard in downloadAndInstall) or any redundant re-push.
        val existing = store.readMeta(book.initials)?.takeIf { !it.deleted }
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

    /**
     * Downloads and installs [initials] from the cloud. [onProgress] (if given) reports
     * `(bytesDownloaded, totalBytes)` as the archive transfers, for a progress notification;
     * `totalBytes` is the recorded archive size (`<= 0` when unknown for older uploads).
     */
    suspend fun downloadAndInstall(initials: String, onProgress: ((bytesDownloaded: Long, totalBytes: Long) -> Unit)? = null) {
        val store = store() ?: return
        val meta = store.readMeta(initials)?.takeIf { !it.deleted } ?: return
        val archive = store.downloadArchive(initials, meta.version,
            onProgress?.let { cb -> { bytes -> cb(bytes, meta.size) } }
        ) ?: return
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
        val plan = computeSyncPlan(download, upload, delete)
        DocumentSyncService.start(
            BibleApplication.application,
            pushInitials = plan.toUpload,
            downloadInitials = plan.toDownload,
            uninstallInitials = plan.toUninstall,
        )
    }

    /**
     * Resolves what document sync would transfer for the selected operations, using the same
     * resolver path as [runSync] (full cloud cache incl. tombstones + per-document sync timestamps).
     * Refreshes the listing cache first. Returns an empty plan when not signed in. The manual
     * "Sync now" preview calls this with all directions true to show per-direction counts.
     */
    suspend fun computeSyncPlan(download: Boolean, upload: Boolean, delete: Boolean): SyncPlan {
        store() ?: return SyncPlan(emptyList(), emptyList(), emptyList(), 0L, 0L)
        // Incrementally refresh the cache (fast no-op when nothing changed), then resolve actions
        // from the freshly-merged full cloud picture held in the cache.
        refreshCache()
        val cloudMetas = DatabaseContainer.instance.documentSyncDb.cloudDocumentCacheDao().all().map { it.toMeta() }
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
        val toUninstall = mutableListOf<String>()
        for (action in actions) when (action.type) {
            DocumentSyncActionType.DOWNLOAD, DocumentSyncActionType.UPGRADE -> toDownload.add(action.initials)
            // Tombstone-driven local removals run on the service queue too (like downloads/uploads),
            // so a manual "Sync now" survives leaving the view and shows the progress notification.
            DocumentSyncActionType.UNINSTALL -> toUninstall.add(action.initials)
            DocumentSyncActionType.SKIP_BLOCKED, DocumentSyncActionType.NONE -> {}
        }
        val toUpload = if (upload) resolveUploads(localDocs, cloudDocs, blocked, ::versionIsNewer) else emptyList()
        val cloudSizeByInitials = cloudMetas.associate { it.initials to it.size }
        val localSizeByInitials = local.mapValues { (_, b) -> localInstallSizeBytes(b) ?: 0L }
        return SyncPlan(
            toDownload = toDownload,
            toUpload = toUpload,
            toUninstall = toUninstall,
            downloadBytes = sumPlanBytes(toDownload, cloudSizeByInitials),
            uploadBytes = sumPlanBytes(toUpload, localSizeByInitials),
        )
    }

    /**
     * Uninstalls the local copy of [initials] after it was removed from the cloud elsewhere
     * (tombstone-driven). Looks the book up by initials so it can run from the sync-service queue.
     * Honours the last-deletable guard (e.g. never removes the last remaining Bible).
     */
    fun uninstallLocal(initials: String) {
        val book = installedSyncableBooks().firstOrNull { it.initials == initials } ?: return
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
        val cacheDao = DatabaseContainer.instance.documentSyncDb.cloudDocumentCacheDao()
        // Mark the cache entry as a tombstone up front (before the slow network writes) so the
        // management view is correct even if it is closed and reopened mid-operation. Capture the
        // pre-state so a *failed* cloud write can revert it — otherwise the cache would show a
        // tombstone that doesn't exist in the cloud, and an incremental refresh would not re-fetch
        // it to self-correct (the live meta's createdTime is unchanged, so it stays below the
        // watermark). Only the cloud tombstone write is guarded: once it succeeds the mark is
        // correct, so a later local-delete failure must not revert it.
        val cachedBefore = cacheDao.all().firstOrNull { it.initials == initials }
        cacheDao.markDeleted(initials)
        val now = System.currentTimeMillis()
        try {
            val existing = store.readMeta(initials)
            // Remove is only offered when a cloud copy exists, so `existing` is normally present;
            // the fallback just guards against a race where it disappeared.
            val meta = (existing ?: DocumentSyncMeta(
                initials = initials, name = initials, documentType = DocumentType.SWORD, version = "0.0",
                size = 0, language = "", sourceDevice = CommonUtils.deviceIdentifier,
                timestamp = 0,
            )).copy(timestamp = now)
            store.writeTombstone(meta)
        } catch (e: Exception) {
            if (cachedBefore != null) cacheDao.insertAll(listOf(cachedBefore))
            else cacheDao.deleteByInitials(initials)
            throw e
        }
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

    /**
     * Permanently removes a tombstone (the removed-document marker) from the cloud, so the
     * document no longer appears in the "show removed documents" view. Note: if the document is
     * still installed on another device that has not yet applied the removal, that device may
     * re-upload it on its next sync — the tombstone is the signal that prevents that. Manual
     * action only; bypasses no guards because it touches only this account's cloud store.
     */
    suspend fun purgeTombstone(initials: String) {
        val store = store() ?: return
        // Drop the cache entry up front (before the slow network delete) so the management view is
        // correct even if it is closed and reopened mid-operation — scanCached reads the cache.
        DatabaseContainer.instance.documentSyncDb.cloudDocumentCacheDao().deleteByInitials(initials)
        store.deleteDocument(initials)
        refreshCache()
    }
}

/** Total cloud archive bytes for non-deleted documents (the ZIP sizes stored in the cloud). */
fun sumCloudBytes(metas: List<DocumentSyncMeta>): Long =
    metas.filterNot { it.deleted }.sumOf { it.size }

/** What a sync run would transfer, used by the manual "Sync now" preview and by [DocumentSync.runSync]. */
data class SyncPlan(
    val toDownload: List<String>,
    val toUpload: List<String>,
    val toUninstall: List<String>,
    /** Sum of cloud ZIP sizes for [toDownload]. */
    val downloadBytes: Long,
    /** Sum of local install sizes for [toUpload] (the same estimate the enable dialog shows). */
    val uploadBytes: Long,
)

/** Sum the byte sizes of [initials] looked up in [sizeByInitials]; unknown initials contribute 0. */
fun sumPlanBytes(initials: List<String>, sizeByInitials: Map<String, Long>): Long =
    initials.sumOf { sizeByInitials[it] ?: 0L }

/** Parses a stored BookCategory enum name; null for null/blank/unknown names. */
fun parseCategoryName(name: String?): BookCategory? =
    name?.takeIf { it.isNotBlank() }?.let { runCatching { BookCategory.valueOf(it) }.getOrNull() }

/**
 * Local document facts the status list needs, extracted from a [Book] so the assembly logic
 * below stays pure and unit-testable (no Android / JSword dependencies).
 */
data class LocalDoc(
    val name: String,
    val version: String,
    val category: BookCategory,
    val type: DocumentType,
    val canDelete: Boolean,
    val installSizeBytes: Long?,
)

/**
 * Pure builder of the management view's status rows from cloud metas + local documents.
 *
 * When [includeDeleted] is false (the default), cloud tombstones are dropped entirely — a
 * document removed from the cloud but still installed locally then shows as a normal local-only
 * row, exactly as before this feature. When true, tombstones become their own rows: because the
 * archive is gone, a tombstone is treated as "no downloadable cloud copy" for the
 * cloudOnly / update / localNewer computations, and flagged via [DocumentSync.DocumentStatusItem.cloudDeleted].
 */
fun assembleStatusItems(
    cloudMetas: List<DocumentSyncMeta>,
    localDocs: Map<String, LocalDoc>,
    blocked: Set<String>,
    includeDeleted: Boolean,
): List<DocumentSync.DocumentStatusItem> {
    val cloud = (if (includeDeleted) cloudMetas else cloudMetas.filter { !it.deleted })
        .associateBy { it.initials }
    val allInitials = (cloud.keys + localDocs.keys).toSortedSet()
    return allInitials.map { initials ->
        val c = cloud[initials]
        val b = localDocs[initials]
        // A tombstone has no downloadable archive: treat the live cloud copy as absent for the
        // cloudOnly / update / localNewer computations, but keep its meta for display + the flag.
        val liveCloud = c?.takeIf { !it.deleted }
        val localVersion = b?.version
        val update = liveCloud != null && localVersion != null &&
            DocumentSync.versionIsNewer(liveCloud.version, localVersion)
        val localNewer = liveCloud != null && localVersion != null &&
            DocumentSync.versionIsNewer(localVersion, liveCloud.version)
        DocumentSync.DocumentStatusItem(
            initials = initials,
            name = c?.name ?: b?.name ?: initials,
            type = b?.type ?: c?.documentType ?: DocumentType.SWORD,
            cloudVersion = c?.version,
            localVersion = localVersion,
            cloudOnly = liveCloud != null && b == null,
            localOnly = liveCloud == null && b != null,
            updateAvailable = update,
            localNewer = localNewer,
            blocked = initials in blocked,
            sizeBytes = liveCloud?.size ?: b?.installSizeBytes ?: 0L,
            category = b?.category ?: parseCategoryName(c?.category),
            canDeleteLocal = b?.canDelete ?: true,
            cloudDeleted = c?.deleted == true,
        )
    }
}
