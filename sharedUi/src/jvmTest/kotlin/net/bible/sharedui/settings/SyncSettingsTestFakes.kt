package net.bible.sharedui.settings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.bible.sharedcore.settings.ApplyResult
import net.bible.sharedcore.settings.Choice2
import net.bible.sharedcore.settings.DocSyncSummaryData
import net.bible.sharedcore.settings.SyncCategoryKeys
import net.bible.sharedcore.settings.SyncSettingsService
import net.bible.sharedcore.settings.SyncSettingsSnapshot

/** A recording [SyncSettingsService] for screen- and nav-level tests. */
internal class FakeSyncService(initial: SyncSettingsSnapshot) : SyncSettingsService {
    override val snapshot: StateFlow<SyncSettingsSnapshot> = MutableStateFlow(initial)
    val applied = mutableListOf<Map<String, Boolean>>()
    var gate: CompletableDeferred<ApplyResult>? = null
    var cancelledApply = false
    override fun refresh() {}
    override suspend fun signIn() = true
    override suspend fun scanDocuments() = DocSyncSummaryData(emptyList(), emptyList(), 0, 0)
    override fun disableCategory(key: String) {}
    override suspend fun applyCategoryChanges(changes: Map<String, Boolean>, onProbed: () -> Unit): ApplyResult {
        applied += changes
        val g = gate ?: return ApplyResult.Applied
        try { return g.await() } catch (e: CancellationException) { cancelledApply = true; throw e }
    }
    override fun setDocumentsEnabled(summary: DocSyncSummaryData) {}
    override fun disableDocuments() {}
    override fun setDocumentSyncToggle(key: String, value: Boolean) {}
    override fun setText(key: String, value: String) = true
    override fun forgetCertificate() {}
    override fun setAdapter(value: String) {}
    override suspend fun resetSync() {}
    override fun formatEnableDocumentsMessage(summary: DocSyncSummaryData) = ""
}


internal fun syncSnapshot(on: Boolean) = SyncSettingsSnapshot(
    adapter = "NEXT_CLOUD",
    adapterChoices = listOf(Choice2("NEXT_CLOUD", "Nextcloud")),
    adapterSummary = "Provider", adapterEnabled = false, cloudInfoSummary = null,
    serverUrl = "https://cloud.example.com", username = "a", password = "b", folderPath = "X",
    credsVisible = true, credsEnabled = false, resetVisible = true, cloudInfoVisible = false,
    categoryEnabled = SyncCategoryKeys.DISPLAY.associateWith { on },
    categorySummary = SyncCategoryKeys.DISPLAY.associateWith { "Synced content" },
    documentsEnabled = false, documentCategoryVisible = false,
    autoDownload = true, autoUpload = true, autoDelete = false, wifiOnly = true,
    autoTogglesVisible = false, wifiOnlyVisible = false,
    serverUrlHint = null, httpsOnly = false, certificateVisible = false, certificateSummary = "",
)

