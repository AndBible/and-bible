package net.bible.service.cloudsync

import net.bible.android.database.SyncDao
import net.bible.service.cloudsync.webdav.WebDavStateStore
import net.bible.sharedcore.platform.AppSettings

/** Platform-only credential storage, URL recognition and display formatting. */
interface SyncSettingsPlatform {
    fun credential(key: String): String
    fun setCredential(key: String, value: String)
    fun isValidUrl(value: String): Boolean
    fun formatFileSize(bytes: Long): String
    fun formatSyncTime(timestamp: Long): String
    /** The WebDAV adapter's persisted calibration and certificate pin. */
    val webDavState: WebDavStateStore
    val webDavServerUrlHint: String
}

/** Reads category metadata through the suspend DAO contract, including on a UI dispatcher. */
class SyncSettingsCategories(
    private val settings: AppSettings,
    private val dao: (SyncableDatabaseDefinition) -> SyncDao = { it.accessor.dao },
) {
    val syncEnabled: Boolean get() = SyncableDatabaseDefinition.ALL.any { it.isSyncEnabled(settings) }
    fun enabled(category: SyncableDatabaseDefinition): Boolean = category.isSyncEnabled(settings)
    fun setEnabled(category: SyncableDatabaseDefinition, value: Boolean) = category.setSyncEnabled(settings, value)
    suspend fun lastSynchronized(category: SyncableDatabaseDefinition): Long? =
        if (enabled(category)) dao(category).getLong(LAST_SYNCHRONIZED_KEY) else null
}
