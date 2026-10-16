package net.bible.sharedcore.settings

import kotlinx.coroutines.flow.StateFlow

/** Portable mirror of `net.bible.service.cloudsync.documents.DocumentSyncSummary` (no Android types),
 *  so the document-enable pre-scan result can cross the seam into the controller. */
data class DocSyncSummaryData(
    val uploadInitials: List<String>,
    val downloadInitials: List<String>,
    val uploadBytes: Long,
    val downloadBytes: Long,
) {
    val uploadCount get() = uploadInitials.size
    val downloadCount get() = downloadInitials.size
    val isEmpty get() = uploadInitials.isEmpty() && downloadInitials.isEmpty()
}

/**
 * Immutable snapshot the sync-settings screen renders from. The impl gathers it from all FOUR
 * backing stores (realShared creds / DocumentSyncSettings / category.syncEnabled / PreferenceStore
 * adapter) plus derived flags. All visibility/enabled gates and pre-composed summaries live here;
 * the controller never computes them.
 */
data class SyncSettingsSnapshot(
    // -- general category --
    val adapter: String,                       // sync_adapter selected value (CloudAdapters name)
    val adapterChoices: List<Choice2>,         // allEnabled → (name, displayName)
    val adapterSummary: String,                // pre-composed intro + adapter summary
    val adapterEnabled: Boolean,               // !signedIn
    val cloudInfoSummary: String?,             // pre-composed bytes-used; null while loading
    val serverUrl: String,
    val username: String,
    val password: String,
    val folderPath: String,
    val credsVisible: Boolean,                 // !adapterIsGoogleDrive
    val credsEnabled: Boolean,                 // !signedIn
    val resetVisible: Boolean,                 // isCloudSyncEnabled && signedIn
    val cloudInfoVisible: Boolean,             // isCloudSyncEnabled && signedIn
    // -- synchronization categories (key = "sync_enable_<name>") --
    val categoryEnabled: Map<String, Boolean>, // 5 categories, no readingplans (always hidden)
    val categorySummary: Map<String, String>,  // pre-composed contentDescription + last-synced
    val documentsEnabled: Boolean,             // sync_enable_documents state
    // -- document sync sub-category --
    val documentCategoryVisible: Boolean,      // signedIn
    val autoDownload: Boolean,
    val autoUpload: Boolean,
    val autoDelete: Boolean,
    val wifiOnly: Boolean,
    val autoTogglesVisible: Boolean,           // documentsEnabled
    val wifiOnlyVisible: Boolean,              // documentsEnabled
    // -- WebDAV extras (defaults keep other providers unchanged) --
    val serverUrlHint: String? = null,         // shown as the URL row summary while the URL is blank
    val httpsOnly: Boolean = false,            // true → a rejected URL gets the "HTTPS required" message
    val certificateVisible: Boolean = false,   // a TOFU certificate pin exists
    val certificateEnabled: Boolean = true,    // !signedIn
    val certificateSummary: String = "",       // shortened pinned fingerprint
)

/** The 5 syncable-category keys shown, in classic `sync_settings.xml` order (readingplans omitted —
 *  classic sets it `isVisible=false` unconditionally). Documents is a separate row handled apart. */
object SyncCategoryKeys {
    val DISPLAY = listOf(
        "sync_enable_bookmarks",
        "sync_enable_workspaces",
        "sync_enable_mydocuments",
        "sync_enable_ai_settings",
        "sync_enable_progress",
    )
}

/**
 * Seam over the four backing stores + the interactive cloud flows. Interactive methods are `suspend`
 * so the Android impl can run the activity-bound `CloudSync.signIn`; `:sharedCore` never sees an
 * `Activity`. `refresh()` re-reads all stores + derived flags and re-emits the snapshot; the async
 * cloud bytes-used (`CloudSync.bytesUsed()`) is fetched inside `refresh()` on the impl's own scope
 * and lands in `snapshot.cloudInfoSummary` on a follow-up emission.
 */
interface SyncSettingsService {
    val snapshot: StateFlow<SyncSettingsSnapshot>
    fun refresh()

    // Interactive (suspend) — Android impl captures the Activity for the OAuth flow.
    suspend fun signIn(): Boolean
    suspend fun scanDocuments(): DocSyncSummaryData

    // Per-key writers routed to the correct backing store (impl encapsulates routing).
    fun setCategoryEnabled(key: String, enabled: Boolean)   // sync_enable_* (non-documents)
    fun setDocumentsEnabled(summary: DocSyncSummaryData)     // sync_enable_documents ON
    fun disableDocuments()                                   // sync_enable_documents OFF
    fun setDocumentSyncToggle(key: String, value: Boolean)  // sync_documents_auto_* / wifi_only
    fun setText(key: String, value: String): Boolean        // cloud_sync_* (false = URL invalid, not persisted)
    fun setAdapter(value: String)                           // sync_adapter
    fun forgetCertificate()                                  // webdav_sync_cert → clear the TOFU pin
    suspend fun resetSync()                                 // cloud_sync_reset → CloudSync.signOut()

    /** Localized message for the document-sync enable confirmation dialog (needs Android file-size /
     *  plural formatting), built host-side from [summary]. */
    fun formatEnableDocumentsMessage(summary: DocSyncSummaryData): String
}

/** Host-resolved strings (screen title, category titles, row titles/summaries, dialog messages),
 *  kept out of the controller so translated strings stay in `strings.xml`. Mirrors
 *  [ReadingProgressSettingsLabels]. */
data class SyncSettingsLabels(
    val screenTitle: String,
    // categories
    val generalCat: String,
    val syncCat: String,
    val documentSyncCat: String,
    // general rows
    val adapterTitle: String,
    val resetTitle: String,
    val resetSummary: String,
    val cloudInfoTitle: String,
    val serverUrlTitle: String,
    val usernameTitle: String,
    val passwordTitle: String,
    val folderPathTitle: String,
    val folderPathSummary: String,
    // category rows (titles only; summaries come from the snapshot)
    val bookmarksTitle: String,
    val workspacesTitle: String,
    val myDocumentsTitle: String,
    val aiSettingsTitle: String,
    val progressTitle: String,
    val documentsTitle: String,
    val documentsSummary: String,
    // document-sync sub-rows
    val autoDownloadTitle: String,
    val autoDownloadSummary: String,
    val autoUploadTitle: String,
    val autoUploadSummary: String,
    val autoDeleteTitle: String,
    val autoDeleteSummary: String,
    val wifiOnlyTitle: String,
    val wifiOnlySummary: String,
    val manageTitle: String,
    val manageSummary: String,
    // dialog messages
    val resetConfirmMessage: String,
    val invalidUrlMessage: String,
    val documentsEnableDialogTitle: String,
    // WebDAV certificate row / URL validation
    val certificateTitle: String,
    val forgetCertificateMessage: String,
    val httpsRequiredMessage: String,
) {
    companion object {
        fun forTest() = SyncSettingsLabels(
            screenTitle = "Device synchronization",
            generalCat = "General settings", syncCat = "Synchronize", documentSyncCat = "Document sync",
            adapterTitle = "Sync provider", resetTitle = "Reset sync", resetSummary = "Sign out and reset",
            cloudInfoTitle = "Cloud info", serverUrlTitle = "Server URL", usernameTitle = "Username",
            passwordTitle = "Password", folderPathTitle = "Folder path", folderPathSummary = "Remote folder",
            bookmarksTitle = "Bookmarks", workspacesTitle = "Workspaces", myDocumentsTitle = "My documents",
            aiSettingsTitle = "AI settings", progressTitle = "Progress", documentsTitle = "Documents",
            documentsSummary = "Sync installed documents",
            autoDownloadTitle = "Auto download", autoDownloadSummary = "Download automatically",
            autoUploadTitle = "Auto upload", autoUploadSummary = "Upload automatically",
            autoDeleteTitle = "Auto delete", autoDeleteSummary = "Delete automatically",
            wifiOnlyTitle = "Wi-Fi only", wifiOnlySummary = "Only over Wi-Fi",
            manageTitle = "Synced documents", manageSummary = "Manage synced documents",
            resetConfirmMessage = "Are you sure you want to reset synchronization?",
            invalidUrlMessage = "Invalid URL",
            documentsEnableDialogTitle = "Enable document sync",
            certificateTitle = "Server certificate", forgetCertificateMessage = "Forget certificate?",
            httpsRequiredMessage = "HTTPS required",
        )
    }
}

/** Which modal the sync screen is currently showing (screen-local, driven by the controller). */
sealed interface SyncDialog {
    data object None : SyncDialog
    data class EnableDocuments(val summary: DocSyncSummaryData, val message: String, val title: String) : SyncDialog
    data class ResetConfirm(val message: String) : SyncDialog
    data class UrlError(val message: String) : SyncDialog
    data class ForgetCertificate(val message: String) : SyncDialog
}

/** Full UI state: the declarative settings list plus transient blocking/dialog state. */
data class SyncSettingsUiState(
    val screen: SettingsScreenState,
    val loading: Boolean = false,
    val dialog: SyncDialog = SyncDialog.None,
)
