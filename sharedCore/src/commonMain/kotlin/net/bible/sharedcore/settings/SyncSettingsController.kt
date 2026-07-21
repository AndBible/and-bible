package net.bible.sharedcore.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Navigation keys the host maps to concrete actions/screens from the sync settings screen. Rows
 * whose only job is to jump elsewhere carry one of these as their `key` and forward through the
 * constructor [SyncSettingsController.onNavigate] lambda.
 */
object SyncSettingsNav {
    const val RESET = "cloud_sync_reset"
    const val INFO = "cloud_sync_info"
    const val MANAGE_DOCS = "document_sync_manage"
}

/**
 * Builds the declarative [SettingsScreenState] for the cloud sync settings screen from a
 * [SyncSettingsService] snapshot, in the classic preference-screen order. The document-sync
 * category (and its 4 toggles + the manage-documents shortcut) is gated on
 * `SyncSettingsSnapshot.enableDocuments` — the controller never computes that flag, it comes
 * straight from the snapshot. All switch/list-choice/text-input keys equal their classic pref
 * keys, so every write is a direct pass-through. Modelled on `AppSettingsController` /
 * `net.bible.sharedcore.ai.AiConnectionSettingsController`.
 */
class SyncSettingsController(
    private val service: SyncSettingsService,
    private val scope: CoroutineScope,
    private val labels: SyncSettingsLabels,
    private val onNavigate: (String) -> Unit,
) {
    private val _state = MutableStateFlow(build(service.snapshot.value))
    val state: StateFlow<SettingsScreenState> = _state.asStateFlow()

    init {
        scope.launch { service.snapshot.collect { _state.value = build(it) } }
    }

    private fun build(s: SyncSettingsSnapshot): SettingsScreenState {
        val items = listOf(
            // ---- General ----
            SettingsItem.Category(
                key = "sync_general",
                title = labels.generalCat,
                visible = true,
            ),
            SettingsItem.ListChoiceRow(
                key = "sync_adapter",
                title = labels.syncAdapterTitle,
                entries = s.syncAdapterChoices.map { SettingsItem.Choice(it.value, it.label) },
                selectedValue = s.syncAdapter,
            ),
            SettingsItem.NavigationRow(
                key = SyncSettingsNav.RESET,
                title = labels.cloudSyncResetTitle,
                summary = labels.cloudSyncResetSummary,
            ),
            SettingsItem.NavigationRow(
                key = SyncSettingsNav.INFO,
                title = labels.cloudSyncInfoTitle,
            ),
            SettingsItem.TextInputRow(
                key = "cloud_sync_server_url",
                title = labels.cloudSyncServerUrlTitle,
                value = s.serverUrl,
            ),
            SettingsItem.TextInputRow(
                key = "cloud_sync_username",
                title = labels.cloudSyncUsernameTitle,
                value = s.username,
            ),
            SettingsItem.TextInputRow(
                key = "cloud_sync_password",
                title = labels.cloudSyncPasswordTitle,
                value = s.password,
                masked = true,
            ),
            SettingsItem.TextInputRow(
                key = "cloud_sync_folder_path",
                title = labels.cloudSyncFolderPathTitle,
                summary = labels.cloudSyncFolderPathSummary,
                value = s.folderPath,
            ),
            // ---- Categories (what to sync) ----
            SettingsItem.Category(
                key = "sync_category",
                title = labels.categoriesCat,
                visible = true,
            ),
            SettingsItem.SwitchRow(
                key = "sync_enable_bookmarks",
                title = labels.syncEnableBookmarksTitle,
                summary = labels.syncEnableBookmarksSummary,
                checked = s.enableBookmarks,
            ),
            SettingsItem.SwitchRow(
                key = "sync_enable_workspaces",
                title = labels.syncEnableWorkspacesTitle,
                summary = labels.syncEnableWorkspacesSummary,
                checked = s.enableWorkspaces,
            ),
            SettingsItem.SwitchRow(
                key = "sync_enable_readingplans",
                title = labels.syncEnableReadingplansTitle,
                summary = labels.syncEnableReadingplansSummary,
                checked = s.enableReadingPlans,
            ),
            SettingsItem.SwitchRow(
                key = "sync_enable_mydocuments",
                title = labels.syncEnableMydocumentsTitle,
                summary = labels.syncEnableMydocumentsSummary,
                checked = s.enableMyDocuments,
            ),
            SettingsItem.SwitchRow(
                key = "sync_enable_ai_settings",
                title = labels.syncEnableAiSettingsTitle,
                summary = labels.syncEnableAiSettingsSummary,
                checked = s.enableAiSettings,
            ),
            SettingsItem.SwitchRow(
                key = "sync_enable_progress",
                title = labels.syncEnableProgressTitle,
                summary = labels.syncEnableProgressSummary,
                checked = s.enableProgress,
            ),
            SettingsItem.SwitchRow(
                key = "sync_enable_documents",
                title = labels.syncEnableDocumentsTitle,
                summary = labels.syncEnableDocumentsSummary,
                checked = s.enableDocuments,
            ),
            // ---- Document sync ----
            SettingsItem.Category(
                key = "document_sync_category",
                title = labels.documentSyncCat,
                visible = s.enableDocuments,
            ),
            SettingsItem.SwitchRow(
                key = "sync_documents_auto_download",
                title = labels.syncDocumentsAutoDownloadTitle,
                summary = labels.syncDocumentsAutoDownloadSummary,
                checked = s.autoDownload,
                visible = s.enableDocuments,
            ),
            SettingsItem.SwitchRow(
                key = "sync_documents_auto_upload",
                title = labels.syncDocumentsAutoUploadTitle,
                summary = labels.syncDocumentsAutoUploadSummary,
                checked = s.autoUpload,
                visible = s.enableDocuments,
            ),
            SettingsItem.SwitchRow(
                key = "sync_documents_auto_delete",
                title = labels.syncDocumentsAutoDeleteTitle,
                summary = labels.syncDocumentsAutoDeleteSummary,
                checked = s.autoDelete,
                visible = s.enableDocuments,
            ),
            SettingsItem.SwitchRow(
                key = "sync_documents_wifi_only",
                title = labels.syncDocumentsWifiOnlyTitle,
                summary = labels.syncDocumentsWifiOnlySummary,
                checked = s.wifiOnly,
                visible = s.enableDocuments,
            ),
            SettingsItem.NavigationRow(
                key = SyncSettingsNav.MANAGE_DOCS,
                title = labels.documentSyncManageTitle,
                summary = labels.documentSyncManageSummary,
                visible = s.enableDocuments,
            ),
        )
        return SettingsScreenState(title = labels.screenTitle, items = items)
    }

    /** All switch/list-choice/text-input keys equal their classic pref key, so every write is a direct pass-through. */
    fun onSwitch(key: String, checked: Boolean) = service.setBool(key, checked)

    fun onListChoice(key: String, value: String) = service.setString(key, value)

    fun onTextInput(key: String, value: String) = service.setString(key, value)

    fun onNavigate(key: String) = onNavigate.invoke(key)
}
