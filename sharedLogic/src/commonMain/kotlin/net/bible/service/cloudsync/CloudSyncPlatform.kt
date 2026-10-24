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

/** Optional isolated host/test namespace; ordinary installations remain package-derived. */
data class CloudSyncNamespace(val value: String)

/** Deferred engine references break the reciprocal database/document-sync construction cycle. */
fun interface CloudSyncProvider { fun get(): CloudSync }
fun interface DocumentSyncProvider { fun get(): DocumentSync }
