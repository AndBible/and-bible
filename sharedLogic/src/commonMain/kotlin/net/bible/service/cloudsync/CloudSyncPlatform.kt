package net.bible.service.cloudsync

import net.bible.service.cloudsync.documents.DocumentSync

/** Cloud SDK construction and credentials belong to the host platform. */
interface CloudAdapterFactory {
    val available: List<String>
    /**
     * The backend an install used before Google Drive became the fresh-install default (#4102): what an existing install
     * with sync on and no stored `sync_adapter` was implicitly using. Hosts whose order changed override it.
     */
    val legacyDefaultId: String? get() = available.firstOrNull()
    fun displayName(id: String): String
    fun create(id: String): CloudAdapter
    /** Restores existing credentials only. Hosts with interactive sign-in override this path. */
    fun createForBackground(id: String): CloudAdapter = create(id)
}

private const val ADAPTER_KEY = "sync_adapter"

private fun net.bible.sharedcore.platform.AppSettings.storedAdapterId(): String? = getString(ADAPTER_KEY)?.takeIf { it.isNotEmpty() }

private fun net.bible.sharedcore.platform.AppSettings.anySyncEnabled() = SyncableDatabaseDefinition.ALL.any { it.isSyncEnabled(this) }

/**
 * The backend in use. A stored id wins. With none stored, an install that already syncs keeps the implicit old default
 * ([CloudAdapterFactory.legacyDefaultId]); a fresh install takes the first available backend (Google Drive where offered).
 */
fun CloudAdapterFactory.effectiveAdapterId(settings: net.bible.sharedcore.platform.AppSettings): String =
    settings.storedAdapterId()
        ?: (if (settings.anySyncEnabled()) legacyDefaultId?.takeIf { it in available } else null)
        ?: available.firstOrNull().orEmpty()

/** One-time upgrade step (idempotent): persists the old implicit backend for an install that already syncs. Fresh installs stay unpinned. */
fun CloudAdapterFactory.pinLegacyAdapterIfInUse(settings: net.bible.sharedcore.platform.AppSettings) {
    if (settings.storedAdapterId() == null && settings.anySyncEnabled()) {
        legacyDefaultId?.takeIf { it in available }?.let { settings.setString(ADAPTER_KEY, it) }
    }
}

/** Call before the first category is enabled: fixes the backend so a later change of the default never moves the user. */
fun CloudAdapterFactory.pinAdapterOnEnable(settings: net.bible.sharedcore.platform.AppSettings) {
    if (settings.storedAdapterId() == null) effectiveAdapterId(settings).takeIf { it.isNotEmpty() }?.let { settings.setString(ADAPTER_KEY, it) }
}

/**
 * Availability without constructing the sync engine (startup and shutdown policy checks use this).
 * A missing or empty stored id means "never chosen" and takes the platform default, like [CloudSync.adapterId].
 */
fun CloudAdapterFactory.isAvailable(settings: net.bible.sharedcore.platform.AppSettings): Boolean =
    effectiveAdapterId(settings) in available

/** Optional isolated host/test namespace; ordinary installations remain package-derived. */
data class CloudSyncNamespace(val value: String)

/** Deferred engine references break the reciprocal database/document-sync construction cycle. */
fun interface CloudSyncProvider { fun get(): CloudSync }
fun interface DocumentSyncProvider { fun get(): DocumentSync }
