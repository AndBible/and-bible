package net.bible.sharedcore.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Builds the declarative [SettingsScreenState] for the cloud-sync settings screen and orchestrates
 * its interactive flows (OAuth sign-in on category enable, document-enable scan+dialog, reset).
 * Visibility/enabled gates and pre-composed summaries come straight from the [SyncSettingsSnapshot]
 * (the controller never computes them). Modelled on [ReadingProgressSettingsController] +
 * [AppSettingsController]; flow methods (Task 3) live below [build].
 */
class SyncSettingsController(
    private val service: SyncSettingsService,
    private val scope: CoroutineScope,
    private val labels: SyncSettingsLabels,
    private val onOpenCloudDocuments: () -> Unit,
) {
    private val _state = MutableStateFlow(SyncSettingsUiState(screen = build(service.snapshot.value)))
    val state: StateFlow<SyncSettingsUiState> = _state.asStateFlow()

    init {
        scope.launch { service.snapshot.collect { s -> _state.value = _state.value.copy(screen = build(s)) } }
    }

    private fun categoryTitle(key: String): String = when (key) {
        "sync_enable_bookmarks" -> labels.bookmarksTitle
        "sync_enable_workspaces" -> labels.workspacesTitle
        "sync_enable_mydocuments" -> labels.myDocumentsTitle
        "sync_enable_ai_settings" -> labels.aiSettingsTitle
        "sync_enable_progress" -> labels.progressTitle
        else -> key
    }

    private fun build(s: SyncSettingsSnapshot): SettingsScreenState {
        val items = buildList {
            // ---- General ----
            add(SettingsItem.Category(key = "sync_general", title = labels.generalCat))
            add(SettingsItem.ListChoiceRow(
                key = "sync_adapter", title = labels.adapterTitle, summary = s.adapterSummary,
                entries = s.adapterChoices.map { SettingsItem.Choice(it.value, it.label) },
                selectedValue = s.adapter, enabled = s.adapterEnabled,
            ))
            add(SettingsItem.NavigationRow(
                key = "cloud_sync_reset", title = labels.resetTitle, summary = labels.resetSummary,
                visible = s.resetVisible,
            ))
            add(SettingsItem.InfoRow(
                key = "cloud_sync_info", title = labels.cloudInfoTitle, summary = s.cloudInfoSummary,
                visible = s.cloudInfoVisible,
            ))
            add(SettingsItem.TextInputRow(
                key = "cloud_sync_server_url", title = labels.serverUrlTitle, value = s.serverUrl,
                visible = s.credsVisible, enabled = s.credsEnabled,
            ))
            add(SettingsItem.TextInputRow(
                key = "cloud_sync_username", title = labels.usernameTitle, value = s.username,
                visible = s.credsVisible, enabled = s.credsEnabled,
            ))
            add(SettingsItem.TextInputRow(
                key = "cloud_sync_password", title = labels.passwordTitle, value = s.password, masked = true,
                visible = s.credsVisible, enabled = s.credsEnabled,
            ))
            add(SettingsItem.TextInputRow(
                key = "cloud_sync_folder_path", title = labels.folderPathTitle, summary = labels.folderPathSummary,
                value = s.folderPath, visible = s.credsVisible, enabled = s.credsEnabled,
            ))
            // ---- Synchronization categories ----
            add(SettingsItem.Category(key = "sync_category", title = labels.syncCat))
            SyncCategoryKeys.DISPLAY.forEach { key ->
                add(SettingsItem.SwitchRow(
                    key = key, title = categoryTitle(key), summary = s.categorySummary[key],
                    checked = s.categoryEnabled[key] ?: false,
                ))
            }
            add(SettingsItem.SwitchRow(
                key = "sync_enable_documents", title = labels.documentsTitle, summary = labels.documentsSummary,
                checked = s.documentsEnabled,
            ))
            // ---- Document sync ----
            add(SettingsItem.Category(
                key = "document_sync_category", title = labels.documentSyncCat, visible = s.documentCategoryVisible,
            ))
            add(SettingsItem.SwitchRow(
                key = "sync_documents_auto_download", title = labels.autoDownloadTitle, summary = labels.autoDownloadSummary,
                checked = s.autoDownload, visible = s.autoTogglesVisible,
            ))
            add(SettingsItem.SwitchRow(
                key = "sync_documents_auto_upload", title = labels.autoUploadTitle, summary = labels.autoUploadSummary,
                checked = s.autoUpload, visible = s.autoTogglesVisible,
            ))
            add(SettingsItem.SwitchRow(
                key = "sync_documents_auto_delete", title = labels.autoDeleteTitle, summary = labels.autoDeleteSummary,
                checked = s.autoDelete, visible = s.autoTogglesVisible,
            ))
            add(SettingsItem.SwitchRow(
                key = "sync_documents_wifi_only", title = labels.wifiOnlyTitle, summary = labels.wifiOnlySummary,
                checked = s.wifiOnly, visible = s.wifiOnlyVisible,
            ))
            add(SettingsItem.NavigationRow(
                key = "document_sync_manage", title = labels.manageTitle, summary = labels.manageSummary,
                visible = s.documentCategoryVisible,
            ))
        }
        return SettingsScreenState(title = labels.screenTitle, items = items)
    }

    private var documentEnableInProgress = false

    fun onSwitch(key: String, checked: Boolean) {
        when {
            key == "sync_enable_documents" ->
                if (checked) enableDocumentsFlow() else { service.disableDocuments(); service.refresh() }
            key.startsWith("sync_enable_") ->
                if (checked) enableCategoryFlow(key) else { service.setCategoryEnabled(key, false); service.refresh() }
            else -> { service.setDocumentSyncToggle(key, checked); service.refresh() }  // sync_documents_*
        }
    }

    private fun enableCategoryFlow(key: String) {
        scope.launch {
            setLoading(true)
            val ok = service.signIn()
            if (ok) service.setCategoryEnabled(key, true)
            service.refresh()
            setLoading(false)
        }
    }

    private fun enableDocumentsFlow() {
        if (documentEnableInProgress) return
        documentEnableInProgress = true
        scope.launch {
            try {
                setLoading(true)
                val ok = service.signIn()
                if (ok) {
                    val summary = service.scanDocuments()
                    val message = service.formatEnableDocumentsMessage(summary)
                    setLoading(false)
                    setDialog(SyncDialog.EnableDocuments(summary, message))
                } else {
                    setLoading(false)
                }
            } finally {
                documentEnableInProgress = false
            }
        }
    }

    fun confirmEnableDocuments() {
        val d = _state.value.dialog
        if (d is SyncDialog.EnableDocuments) {
            service.setDocumentsEnabled(d.summary)
            service.refresh()
        }
        dismissDialog()
    }

    fun onListChoice(key: String, value: String) {
        if (key == "sync_adapter") { service.setAdapter(value); service.refresh() }
    }

    fun onTextInput(key: String, value: String) {
        val ok = service.setText(key, value)
        if (!ok) setDialog(SyncDialog.UrlError(labels.invalidUrlMessage)) else service.refresh()
    }

    fun onNavigate(key: String) {
        when (key) {
            "cloud_sync_reset" -> setDialog(SyncDialog.ResetConfirm(labels.resetConfirmMessage))
            "document_sync_manage" -> onOpenCloudDocuments()
        }
    }

    fun confirmReset() {
        dismissDialog()
        scope.launch { setLoading(true); service.resetSync(); service.refresh(); setLoading(false) }
    }

    fun dismissDialog() { _state.value = _state.value.copy(dialog = SyncDialog.None) }
    private fun setLoading(v: Boolean) { _state.value = _state.value.copy(loading = v) }
    private fun setDialog(d: SyncDialog) { _state.value = _state.value.copy(dialog = d) }
}
