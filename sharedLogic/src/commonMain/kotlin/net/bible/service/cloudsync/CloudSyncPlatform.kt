package net.bible.service.cloudsync

import net.bible.service.cloudsync.documents.DocumentSync

/** Cloud SDK construction and credentials belong to the host platform. */
interface CloudAdapterFactory {
    val available: List<String>
    fun displayName(id: String): String
    fun create(id: String): CloudAdapter
    /** Restores existing credentials only. Hosts with interactive sign-in override this path. */
    fun createForBackground(id: String): CloudAdapter = create(id)
}

/**
 * Availability without constructing the sync engine (startup and shutdown policy checks use this).
 * A missing or empty stored id means "never chosen" and takes the platform default, like [CloudSync.adapterId].
 */
fun CloudAdapterFactory.isAvailable(settings: net.bible.sharedcore.platform.AppSettings): Boolean =
    (settings.getString("sync_adapter")?.takeIf { it.isNotEmpty() } ?: available.firstOrNull()) in available

/** Optional isolated host/test namespace; ordinary installations remain package-derived. */
data class CloudSyncNamespace(val value: String)

/** Deferred engine references break the reciprocal database/document-sync construction cycle. */
fun interface CloudSyncProvider { fun get(): CloudSync }
fun interface DocumentSyncProvider { fun get(): DocumentSync }
