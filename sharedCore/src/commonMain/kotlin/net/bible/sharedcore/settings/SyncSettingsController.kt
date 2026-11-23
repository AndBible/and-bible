package net.bible.sharedcore.settings

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
    private var labels: SyncSettingsLabels,
    private val onOpenCloudDocuments: () -> Unit,
    /** Called when the screen may be left after the leave dialog's Apply (once applied) or Discard. */
    private val onExit: () -> Unit = {},
) {
    /** The category switches not applied yet (`sync_enable_*` key to wanted value); read by [build]. */
    private var pending: Map<String, Boolean> = emptyMap()
    private var lastAdapter = service.snapshot.value.adapter
    private var lastSignedOut = service.snapshot.value.adapterEnabled
    private var applyJob: Job? = null
    private var documentConfirmation: CompletableDeferred<Boolean>? = null
    /** What the leave dialog's Apply/Discard does once done: an in-graph hop; null = the plain [onExit]. */
    private var afterLeave: (() -> Unit)? = null
    /** False once [abandonLeave] ran: an Apply that finishes later must not leave a screen that is gone. */
    private var leaveArmed = false

    private val _state = MutableStateFlow(SyncSettingsUiState(screen = build(service.snapshot.value)))
    val state: StateFlow<SyncSettingsUiState> = _state.asStateFlow()

    init {
        scope.launch {
            service.snapshot.collect { s ->
                // Sign-out (adapterEnabled flips to true) or a backend change invalidates every pending switch; an
                // entry the persisted state now matches (another path changed it) is done.
                if (pending.isNotEmpty()) {
                    val reset = s.adapter != lastAdapter || (s.adapterEnabled && !lastSignedOut)
                    pending = if (reset) emptyMap() else pending.filter { (key, wanted) -> s.persisted(key) != wanted }
                }
                lastAdapter = s.adapter
                lastSignedOut = s.adapterEnabled
                _state.value = _state.value.copy(screen = build(s), pending = pending)
            }
        }
    }

    /** Rebuild translated state while retaining the pending action and scan result. */
    fun updateLabels(labels: SyncSettingsLabels) {
        this.labels = labels
        service.refresh()
        val dialog = when (val d = _state.value.dialog) {
            SyncDialog.None -> d
            is SyncDialog.ResetConfirm -> d.copy(message = labels.resetConfirmMessage)
            is SyncDialog.ForgetCertificate -> d.copy(message = labels.forgetCertificateMessage)
            is SyncDialog.UrlError -> d.copy(message = if (d.httpsRequired) labels.httpsRequiredMessage else labels.invalidUrlMessage)
            SyncDialog.CheckingCloud, SyncDialog.LeaveWithPending -> d
            is SyncDialog.EnableDocuments -> d.copy(title = labels.documentsEnableDialogTitle, message = service.formatEnableDocumentsMessage(d.summary))
        }
        _state.value = _state.value.copy(screen = build(service.snapshot.value), dialog = dialog)
    }

    private fun SyncSettingsSnapshot.persisted(key: String): Boolean =
        if (key == "sync_enable_documents") documentsEnabled else categoryEnabled[key] ?: false

    private fun categoryTitle(key: String): String = when (key) {
        "sync_enable_bookmarks" -> labels.bookmarksTitle
        "sync_enable_workspaces" -> labels.workspacesTitle
        "sync_enable_readingplans" -> labels.readingPlansTitle
        "sync_enable_mydocuments" -> labels.myDocumentsTitle
        "sync_enable_ai_settings" -> labels.aiSettingsTitle
        "sync_enable_progress" -> labels.progressTitle
        else -> key
    }

    private fun build(s: SyncSettingsSnapshot): SettingsScreenState {
        val items = buildList {
            // ---- General ----
            // Round 14b: iconKey is the row's own classic preference key; :app's settingsDrawableRes
            // maps it to the matching drawable (pinned by SettingsIconParityTest). :sharedCore stays
            // Android-free — it only ever carries the string key, never the art.
            add(SettingsItem.Category(key = "sync_general", title = labels.generalCat))
            add(SettingsItem.ListChoiceRow(
                key = "sync_adapter", title = labels.adapterTitle, summary = s.adapterSummary,
                entries = s.adapterChoices.map { SettingsItem.Choice(it.value, it.label) },
                selectedValue = s.adapter, enabled = s.adapterEnabled, iconKey = "sync_adapter",
            ))
            add(SettingsItem.NavigationRow(
                key = "cloud_sync_reset", title = labels.resetTitle, summary = labels.resetSummary,
                visible = s.resetVisible, iconKey = "cloud_sync_reset",
            ))
            add(SettingsItem.InfoRow(
                key = "cloud_sync_info", title = labels.cloudInfoTitle, summary = s.cloudInfoSummary,
                visible = s.cloudInfoVisible, iconKey = "cloud_sync_info",
            ))
            add(SettingsItem.TextInputRow(
                key = "cloud_sync_server_url", title = labels.serverUrlTitle, value = s.serverUrl,
                summary = s.serverUrlHint?.takeIf { s.serverUrl.isBlank() },
                visible = s.credsVisible, enabled = s.credsEnabled, iconKey = "cloud_sync_server_url",
            ))
            add(SettingsItem.TextInputRow(
                key = "cloud_sync_username", title = labels.usernameTitle, value = s.username,
                visible = s.credsVisible, enabled = s.credsEnabled, iconKey = "cloud_sync_username",
            ))
            add(SettingsItem.TextInputRow(
                key = "cloud_sync_password", title = labels.passwordTitle, value = s.password, masked = true,
                visible = s.credsVisible, enabled = s.credsEnabled, iconKey = "cloud_sync_password",
            ))
            add(SettingsItem.TextInputRow(
                key = "cloud_sync_folder_path", title = labels.folderPathTitle,
                // A configured path IS the row's summary; the static explanation is only what to
                // say when there is nothing to show. Deliberately fixed HERE rather than by
                // changing AbSettingsRow's `summary ?: value` rule, which would alter every other
                // settings screen under a request about one row.
                summary = s.folderPath.ifBlank { labels.folderPathSummary },
                value = s.folderPath, visible = s.credsVisible, enabled = s.credsEnabled,
                iconKey = "cloud_sync_folder_path",
            ))
            // Hidden unless a WebDAV TOFU pin exists; tapping offers to forget it.
            add(SettingsItem.NavigationRow(
                key = "webdav_sync_cert", title = labels.certificateTitle, summary = s.certificateSummary,
                visible = s.certificateVisible, enabled = s.certificateEnabled, iconKey = "webdav_sync_cert",
            ))
            // ---- Synchronization categories ----
            add(SettingsItem.Category(key = "sync_category", title = labels.syncCat))
            SyncCategoryKeys.DISPLAY.forEach { key ->
                add(SettingsItem.SwitchRow(
                    key = key, title = categoryTitle(key),
                    summary = pending[key]?.let { if (it) labels.pendingOn else labels.pendingOff } ?: s.categorySummary[key],
                    checked = pending[key] ?: s.categoryEnabled[key] ?: false, iconKey = key,
                ))
            }
            add(SettingsItem.SwitchRow(
                key = "sync_enable_documents", title = labels.documentsTitle,
                summary = pending["sync_enable_documents"]?.let { if (it) labels.pendingOn else labels.pendingOff }
                    ?: labels.formatDocumentsSummary(if (s.documentsEnabled) labels.documentsEnabled else labels.documentsDisabled, s.documentTransferSummary ?: labels.documentsIdle),
                checked = pending["sync_enable_documents"] ?: s.documentsEnabled, iconKey = "sync_enable_documents",
            ))
            // ---- Document sync ----
            add(SettingsItem.Category(
                key = "document_sync_category", title = labels.documentSyncCat, visible = s.documentCategoryVisible,
            ))
            add(SettingsItem.SwitchRow(
                key = "sync_documents_auto_download", title = labels.autoDownloadTitle, summary = labels.autoDownloadSummary,
                checked = s.autoDownload, visible = s.autoTogglesVisible, iconKey = "sync_documents_auto_download",
            ))
            add(SettingsItem.SwitchRow(
                key = "sync_documents_auto_upload", title = labels.autoUploadTitle, summary = labels.autoUploadSummary,
                checked = s.autoUpload, visible = s.autoTogglesVisible, iconKey = "sync_documents_auto_upload",
            ))
            add(SettingsItem.SwitchRow(
                key = "sync_documents_auto_delete", title = labels.autoDeleteTitle, summary = labels.autoDeleteSummary,
                checked = s.autoDelete, visible = s.autoTogglesVisible, iconKey = "sync_documents_auto_delete",
            ))
            add(SettingsItem.SwitchRow(
                key = "sync_documents_wifi_only", title = labels.wifiOnlyTitle, summary = labels.wifiOnlySummary,
                checked = s.wifiOnly, visible = s.wifiOnlyVisible, iconKey = "sync_documents_wifi_only",
            ))
            add(SettingsItem.NavigationRow(
                key = "document_sync_manage", title = labels.manageTitle, summary = labels.manageSummary,
                visible = s.documentCategoryVisible, iconKey = "document_sync_manage",
            ))
        }
        return SettingsScreenState(title = labels.screenTitle, items = items)
    }

    fun onSwitch(key: String, checked: Boolean) {
        when {
            key == "sync_enable_documents" || key in SyncCategoryKeys.DISPLAY -> markPending(key, checked)
            else -> { service.setDocumentSyncToggle(key, checked); service.refresh() }  // sync_documents_*
        }
    }

    /** Records the wanted value of a category switch; a value equal to the persisted one is no change at all. */
    private fun markPending(key: String, wanted: Boolean) {
        val persisted = service.snapshot.value.persisted(key)
        setPending(if (wanted == persisted) pending - key else pending + (key to wanted))
    }

    private fun setPending(value: Map<String, Boolean>) {
        pending = value
        _state.value = _state.value.copy(screen = build(service.snapshot.value), pending = value)
    }

    /**
     * Applies all pending switches. Only offs: no dialog. With an on: the blocking "Checking the cloud" dialog until
     * the cloud has been probed (the join plan dialog is the cloud layer's after that). A failed sign-in keeps the
     * pending map; [cancelChecking] aborts and reverts what this Apply turned on.
     */
    fun applyPending() = startApply(leaveAfter = false)

    private fun startApply(leaveAfter: Boolean) {
        if (applyJob?.isActive == true) return
        val changes = pending
        if (changes.isEmpty()) {  // everything was pruned meanwhile: nothing to apply, the leave still goes on
            if (leaveAfter) finishLeave()
            return
        }
        leaveArmed = leaveAfter
        val hasOn = changes.values.any { it }
        if (hasOn) setDialog(SyncDialog.CheckingCloud) else setLoading(true)
        _state.value = _state.value.copy(applying = true)
        applyJob = scope.launch {
            var completed = false
            try {
                val ok = applyChanges(changes)
                // Only a failed sign-in keeps the map for a retry; a cancelled plan left nothing to retry.
                // Only the applied keys: a switch flipped while the Apply ran stays pending.
                if (ok != ApplyResult.SignInFailed) setPending(pending - changes.keys)
                completed = ok != ApplyResult.SignInFailed
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Progress Cancel: the cloud layer turned the ons off again. Offs not persisted yet stay pending.
                setPending(pending.filterValues { !it })
                throw e
            } catch (e: Exception) {
                // The service reports failures to the user itself; this is the last-resort log.
                net.bible.sharedcore.log.Log.e("SyncSettingsController", "Applying sync changes failed", e)
            } finally {
                if (_state.value.dialog == SyncDialog.CheckingCloud) dismissDialog()
                _state.value = _state.value.copy(loading = false, applying = false)
                service.refresh()
            }
            if (completed && leaveAfter && leaveArmed) finishLeave()
        }
    }

    /** Drops all pending switches. */
    fun discardPending() = setPending(emptyMap())

    /** Cancel in the "Checking the cloud" dialog: aborts the apply, the cloud layer turns its categories off again. */
    fun cancelChecking() {
        if (_state.value.dialog == SyncDialog.CheckingCloud) applyJob?.cancel()
    }

    /** Back with pending switches asks first; true = nothing pending, leave now. */
    fun onLeave(): Boolean {
        afterLeave = null
        return askBeforeLeaving()
    }

    private fun askBeforeLeaving(): Boolean {
        if (applyJob?.isActive == true) return false  // an Apply is already running: stay, no second question
        if (pending.isEmpty()) return true
        setDialog(SyncDialog.LeaveWithPending)
        return false
    }

    /** Runs [hop] (an in-graph navigation out of this screen) at once, or after the leave question's Apply/Discard. */
    private fun leaveThen(hop: () -> Unit) {
        val previous = afterLeave
        afterLeave = hop
        if (askBeforeLeaving()) { afterLeave = null; hop() }
        else if (_state.value.dialog != SyncDialog.LeaveWithPending) {
            // No question open: nothing will run this hop. A refused hop while an Apply-then-leave runs must not lose the
            // hop that Apply already carries.
            afterLeave = if (applyJob?.isActive == true) previous else null
        }
    }

    private fun finishLeave() {
        val hop = afterLeave
        afterLeave = null
        if (hop != null) hop() else onExit()
    }

    /** The screen is gone: a leave still waiting for its Apply must do nothing when that finishes. */
    fun abandonLeave() {
        leaveArmed = false
        afterLeave = null
    }

    fun confirmLeaveApply() {
        dismissDialog()
        startApply(leaveAfter = true)
    }

    fun confirmLeaveDiscard() {
        dismissDialog()
        discardPending()
        finishLeave()
    }

    /** Document transfers keep their own preview, but only inside the shared Apply operation. */
    private suspend fun applyChanges(changes: Map<String, Boolean>): ApplyResult {
        val documents = changes["sync_enable_documents"]
        val categories = changes - "sync_enable_documents"
        val summary = if (documents == true) {
            if (!service.signIn()) return ApplyResult.SignInFailed
            val scanned = service.scanDocuments()
            val confirmation = CompletableDeferred<Boolean>()
            documentConfirmation = confirmation
            setDialog(SyncDialog.EnableDocuments(scanned, service.formatEnableDocumentsMessage(scanned), labels.documentsEnableDialogTitle))
            val confirmed = try {
                confirmation.await()
            } finally {
                documentConfirmation = null
                if (_state.value.dialog is SyncDialog.EnableDocuments) setDialog(SyncDialog.None)
            }
            if (!confirmed) {
                val offs = categories.filterValues { !it }
                if (offs.isNotEmpty()) service.applyCategoryChanges(offs)
                return ApplyResult.Cancelled
            }
            scanned
        } else null

        val result = if (categories.isEmpty()) ApplyResult.Applied else {
            if (categories.values.any { it }) setDialog(SyncDialog.CheckingCloud)
            service.applyCategoryChanges(categories) {
                if (_state.value.dialog == SyncDialog.CheckingCloud) dismissDialog()
            }
        }
        if (documents == false && result != ApplyResult.SignInFailed) service.disableDocuments()
        if (summary != null && result == ApplyResult.Applied) service.setDocumentsEnabled(summary)
        return result
    }

    fun confirmEnableDocuments() {
        if (_state.value.dialog is SyncDialog.EnableDocuments) documentConfirmation?.complete(true)
    }

    fun onListChoice(key: String, value: String) {
        if (key == "sync_adapter") { service.setAdapter(value); service.refresh() }
    }

    fun onTextInput(key: String, value: String) {
        val ok = service.setText(key, value)
        if (!ok) {
            val httpsRequired = key == "cloud_sync_server_url" &&
                (service.snapshot.value.httpsOnly ||
                    value.trim().startsWith("http://", ignoreCase = true) && service.plainHttpRefused(value))
            setDialog(SyncDialog.UrlError(if (httpsRequired) labels.httpsRequiredMessage else labels.invalidUrlMessage, httpsRequired))
        } else service.refresh()
    }

    fun onNavigate(key: String) {
        when (key) {
            "cloud_sync_reset" -> setDialog(SyncDialog.ResetConfirm(labels.resetConfirmMessage))
            "document_sync_manage" -> leaveThen(onOpenCloudDocuments)
            "webdav_sync_cert" -> setDialog(SyncDialog.ForgetCertificate(labels.forgetCertificateMessage))
        }
    }

    fun confirmForgetCertificate() { service.forgetCertificate(); service.refresh(); dismissDialog() }

    fun confirmReset() {
        dismissDialog()
        // The host scope is an Android lifecycleScope without an exception handler: a failing reset must not crash the
        // app (osTicket 3515). The screen then shows whatever state the reset left.
        scope.launch {
            setLoading(true)
            try {
                service.resetSync()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                net.bible.sharedcore.log.Log.e("SyncSettingsController", "Sync reset failed", e)
            } finally {
                service.refresh()
                setLoading(false)
            }
        }
    }

    fun dismissDialog() {
        val cancellingDocuments = _state.value.dialog is SyncDialog.EnableDocuments
        _state.value = _state.value.copy(dialog = SyncDialog.None)
        if (cancellingDocuments) documentConfirmation?.complete(false)
    }
    private fun setLoading(v: Boolean) { _state.value = _state.value.copy(loading = v) }
    private fun setDialog(d: SyncDialog) {
        // A dialog replacing the document transfer preview must not leave Apply waiting for it forever.
        if (_state.value.dialog is SyncDialog.EnableDocuments && d !is SyncDialog.EnableDocuments) {
            documentConfirmation?.complete(false)
        }
        _state.value = _state.value.copy(dialog = d)
    }
}
