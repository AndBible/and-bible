package net.bible.sharedcore.settings

import kotlinx.coroutines.flow.StateFlow

data class SyncSettingsSnapshot(
    val syncAdapter: String,
    val syncAdapterChoices: List<Choice2>,      // Choice2 defined in AppSettingsService.kt (Batch 10a)
    val serverUrl: String,
    val username: String,
    val password: String,
    val folderPath: String,
    val enableBookmarks: Boolean,
    val enableWorkspaces: Boolean,
    val enableReadingPlans: Boolean,
    val enableMyDocuments: Boolean,
    val enableAiSettings: Boolean,
    val enableProgress: Boolean,
    val enableDocuments: Boolean,               // gates the document-sync category
    val autoDownload: Boolean,
    val autoUpload: Boolean,
    val autoDelete: Boolean,
    val wifiOnly: Boolean,
)

interface SyncSettingsService {
    val snapshot: StateFlow<SyncSettingsSnapshot>
    fun setBool(key: String, value: Boolean)
    fun setString(key: String, value: String)
    fun refresh()
}
