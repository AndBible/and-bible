package net.bible.sharedcore.settings

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeSyncSettingsService(initial: SyncSettingsSnapshot) : SyncSettingsService {
    val _snap = MutableStateFlow(initial)
    override val snapshot: StateFlow<SyncSettingsSnapshot> get() = _snap

    var signInResult = true
    var scanResult = DocSyncSummaryData(emptyList(), emptyList(), 0, 0)
    val calls = mutableListOf<String>()
    var refreshes = 0

    override fun refresh() { refreshes++ }
    override suspend fun signIn(): Boolean { calls += "signIn"; return signInResult }
    override suspend fun scanDocuments(): DocSyncSummaryData { calls += "scan"; return scanResult }
    override fun setDocumentsEnabled(summary: DocSyncSummaryData) { calls += "docsEnable" }
    override fun disableDocuments() { calls += "docsDisable" }
    override fun setDocumentSyncToggle(key: String, value: Boolean) { calls += "docToggle:$key=$value" }
    override fun setText(key: String, value: String): Boolean { calls += "text:$key=$value"; return textResult }
    var textResult = true
    var refusedPlainHttp = false
    override fun plainHttpRefused(url: String): Boolean = refusedPlainHttp
    override fun forgetCertificate() { calls += "forgetCert" }
    override fun setAdapter(value: String) { calls += "adapter=$value" }
    var resetFailure: Throwable? = null
    override suspend fun resetSync() { calls += "reset"; resetFailure?.let { throw it } }
    override fun formatEnableDocumentsMessage(summary: DocSyncSummaryData) = "docs-msg"

    var applyResult = ApplyResult.Applied
    var applyCancelled = false
    /** Held until completed: the apply is then "checking the cloud". */
    var beforeProbe: CompletableDeferred<Unit>? = null
    /** Held until completed: the apply is then past the probe (the cloud layer's dialog). */
    var afterProbe: CompletableDeferred<Unit>? = null
    val applied = mutableListOf<Map<String, Boolean>>()
    override suspend fun applyCategoryChanges(changes: Map<String, Boolean>, onProbed: () -> Unit): ApplyResult {
        calls += "apply"
        applied += changes
        try {
            if (changes.any { it.value }) {
                beforeProbe?.await()
                onProbed()
                afterProbe?.await()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            applyCancelled = true
            throw e
        }
        return applyResult
    }
}

fun syncSnap(
    credsVisible: Boolean = true,
    credsEnabled: Boolean = true,
    resetVisible: Boolean = false,
    cloudInfoVisible: Boolean = false,
    documentsEnabled: Boolean = false,
    documentCategoryVisible: Boolean = false,
    autoTogglesVisible: Boolean = false,
    wifiOnlyVisible: Boolean = false,
    adapterEnabled: Boolean = true,
    folderPath: String = "f",
    serverUrl: String = "https://s",
    serverUrlHint: String? = null,
    httpsOnly: Boolean = false,
    certificateVisible: Boolean = false,
    certificateSummary: String = "",
    adapter: String = "NEXT_CLOUD",
    enabledCategories: Set<String> = emptySet(),
) = SyncSettingsSnapshot(
    adapter = adapter,
    adapterChoices = listOf(Choice2("GOOGLE_DRIVE", "Google Drive"), Choice2("NEXT_CLOUD", "Nextcloud")),
    adapterSummary = "adapter summary",
    adapterEnabled = adapterEnabled,
    cloudInfoSummary = null,
    serverUrl = serverUrl, username = "u", password = "p", folderPath = folderPath,
    credsVisible = credsVisible, credsEnabled = credsEnabled,
    resetVisible = resetVisible, cloudInfoVisible = cloudInfoVisible,
    categoryEnabled = SyncCategoryKeys.DISPLAY.associateWith { it in enabledCategories },
    categorySummary = SyncCategoryKeys.DISPLAY.associateWith { "sum" },
    documentsEnabled = documentsEnabled,
    documentCategoryVisible = documentCategoryVisible,
    autoDownload = true, autoUpload = true, autoDelete = true, wifiOnly = true,
    autoTogglesVisible = autoTogglesVisible, wifiOnlyVisible = wifiOnlyVisible,
    serverUrlHint = serverUrlHint, httpsOnly = httpsOnly,
    certificateVisible = certificateVisible, certificateSummary = certificateSummary,
)

class SyncSettingsControllerTest {
    private fun controller(svc: SyncSettingsService, onOpen: () -> Unit = {}) =
        SyncSettingsController(svc, CoroutineScope(Dispatchers.Unconfined), SyncSettingsLabels.forTest(), onOpen)

    @Test fun documentsSummarySeparatesEnabledStateFromTransferStatus() {
        fun summary(enabled: Boolean): String? {
            val row = controller(FakeSyncSettingsService(syncSnap(documentsEnabled = enabled)))
                .state.value.screen.items.single { it.key == "sync_enable_documents" }
            return (row as SettingsItem.SwitchRow).summary
        }

        assertEquals("Enabled; no document transfer running", summary(true))
        assertEquals("Disabled; no document transfer running", summary(false))
    }

    @Test fun disabledDocumentsStillShowManualTransferAndTerminalStatus() {
        val service = FakeSyncSettingsService(syncSnap(documentsEnabled = false))
        val controller = controller(service)
        fun summary() = (controller.state.value.screen.items.single { it.key == "sync_enable_documents" } as SettingsItem.SwitchRow).summary
        service._snap.value = service._snap.value.copy(documentTransferSummary = "Download KJV: 1 / 2 operations, 42 bytes")
        assertEquals("Disabled; Download KJV: 1 / 2 operations, 42 bytes", summary())
        service._snap.value = service._snap.value.copy(documentTransferSummary = "Document sync queue finished: 2 / 2 operations")
        assertEquals("Disabled; Document sync queue finished: 2 / 2 operations", summary())
    }

    @Test fun retainedResetDialogRelocalizesWithoutClosing() {
        val c = controller(FakeSyncSettingsService(syncSnap()))
        c.onNavigate("cloud_sync_reset")
        c.updateLabels(SyncSettingsLabels.forTest().copy(screenTitle = "Synkronointi", resetConfirmMessage = "Palautetaanko?"))
        assertEquals("Synkronointi", c.state.value.screen.title)
        assertEquals(SyncDialog.ResetConfirm("Palautetaanko?"), c.state.value.dialog)
    }

    @Test fun buildsGeneralRowsInOrder_credsVisible() {
        val keys = controller(FakeSyncSettingsService(syncSnap())).state.value.screen.visibleItems.map { it.key }
        // general category + adapter, then creds (reset/info hidden), then sync category + 5 + documents.
        assertEquals("sync_general", keys[0])
        assertEquals("sync_adapter", keys[1])
        assertTrue(keys.containsAll(listOf(
            "cloud_sync_server_url", "cloud_sync_username", "cloud_sync_password", "cloud_sync_folder_path",
            "sync_category", "sync_enable_bookmarks", "sync_enable_documents",
        )))
        assertFalse("cloud_sync_reset" in keys)       // resetVisible=false
        assertFalse("document_sync_category" in keys)  // documentCategoryVisible=false
    }

    // #3902: the docs promise a Reading Plans toggle; it sits between workspaces and my documents (classic order).
    @Test fun readingPlansRowShownWithTitleAndSwitchable() {
        val svc = FakeSyncSettingsService(syncSnap())
        val ctrl = controller(svc)
        val keys = ctrl.state.value.screen.visibleItems.map { it.key }
        assertEquals(keys.indexOf("sync_enable_workspaces") + 1, keys.indexOf("sync_enable_readingplans"))
        val row = ctrl.state.value.screen.items.single { it.key == "sync_enable_readingplans" }
        assertEquals("Reading plans", (row as SettingsItem.SwitchRow).title)
        ctrl.onSwitch("sync_enable_readingplans", true)
        assertEquals(mapOf("sync_enable_readingplans" to true), ctrl.state.value.pending)
    }

    @Test fun certificateRow_hiddenByDefault_visibleWithPin() {
        val hidden = controller(FakeSyncSettingsService(syncSnap()))
        assertFalse(hidden.state.value.screen.items.first { it.key == "webdav_sync_cert" }.visible)
        val shown = controller(FakeSyncSettingsService(syncSnap(certificateVisible = true, certificateSummary = "AB:CD…")))
        val row = shown.state.value.screen.items.first { it.key == "webdav_sync_cert" }
        assertTrue(row.visible)
        assertEquals("AB:CD…", (row as SettingsItem.NavigationRow).summary)
    }

    @Test fun certificateRow_orderedAfterFolderPath() {
        val keys = controller(FakeSyncSettingsService(syncSnap(certificateVisible = true))).state.value.screen.visibleItems.map { it.key }
        assertEquals(keys.indexOf("cloud_sync_folder_path") + 1, keys.indexOf("webdav_sync_cert"))
    }

    @Test fun certificateRow_forgetFlow() {
        val svc = FakeSyncSettingsService(syncSnap(certificateVisible = true))
        val c = controller(svc)
        c.onNavigate("webdav_sync_cert")
        assertTrue(c.state.value.dialog is SyncDialog.ForgetCertificate)
        c.confirmForgetCertificate()
        assertTrue("forgetCert" in svc.calls)
        assertEquals(SyncDialog.None, c.state.value.dialog)
    }

    @Test fun invalidUrl_webDav_showsHttpsMessage() {
        val svc = FakeSyncSettingsService(syncSnap(httpsOnly = true)).apply { textResult = false }
        val c = controller(svc)
        c.onTextInput("cloud_sync_server_url", "http://nas")
        assertEquals(SyncSettingsLabels.forTest().httpsRequiredMessage, (c.state.value.dialog as SyncDialog.UrlError).message)
    }

    @Test fun invalidUrl_notWebDav_showsGenericMessage() {
        val svc = FakeSyncSettingsService(syncSnap()).apply { textResult = false }
        val c = controller(svc)
        c.onTextInput("cloud_sync_server_url", "nope")
        assertEquals(SyncSettingsLabels.forTest().invalidUrlMessage, (c.state.value.dialog as SyncDialog.UrlError).message)
    }

    @Test fun refusedPlainHttpShowsHttpsRequiredMessage() {
        val svc = FakeSyncSettingsService(syncSnap()).apply { textResult = false; refusedPlainHttp = true }
        val c = controller(svc)
        c.onTextInput("cloud_sync_server_url", "  HTTP://example.org/nc  ")
        assertEquals(SyncSettingsLabels.forTest().httpsRequiredMessage, (c.state.value.dialog as SyncDialog.UrlError).message)
        val translated = SyncSettingsLabels.forTest().copy(httpsRequiredMessage = "Translated HTTPS requirement")
        c.updateLabels(translated)
        assertEquals(translated.httpsRequiredMessage, (c.state.value.dialog as SyncDialog.UrlError).message)
    }

    @Test fun malformedHttpWithoutPlatformRefusalKeepsInvalidUrlMessage() {
        val svc = FakeSyncSettingsService(syncSnap()).apply { textResult = false }
        val c = controller(svc)
        c.onTextInput("cloud_sync_server_url", "http://bad host")
        assertEquals(SyncSettingsLabels.forTest().invalidUrlMessage, (c.state.value.dialog as SyncDialog.UrlError).message)
        val translated = SyncSettingsLabels.forTest().copy(invalidUrlMessage = "Translated invalid URL")
        c.updateLabels(translated)
        assertEquals(translated.invalidUrlMessage, (c.state.value.dialog as SyncDialog.UrlError).message)
    }

    @Test fun serverUrlHint_shownOnlyWhenUrlBlank() {
        val blank = controller(FakeSyncSettingsService(syncSnap(serverUrl = "", serverUrlHint = "hint")))
        assertEquals("hint", (blank.state.value.screen.items.first { it.key == "cloud_sync_server_url" } as SettingsItem.TextInputRow).summary)
        val filled = controller(FakeSyncSettingsService(syncSnap(serverUrl = "https://x", serverUrlHint = "hint")))
        assertNull((filled.state.value.screen.items.first { it.key == "cloud_sync_server_url" } as SettingsItem.TextInputRow).summary)
    }

    @Test fun credsHiddenForGoogleDrive() {
        val keys = controller(FakeSyncSettingsService(syncSnap(credsVisible = false))).state.value.screen.visibleItems.map { it.key }
        assertFalse("cloud_sync_username" in keys)
        assertFalse("cloud_sync_server_url" in keys)
    }

    @Test fun documentSubCategoryVisibleWhenSignedIn() {
        val keys = controller(FakeSyncSettingsService(syncSnap(
            documentCategoryVisible = true, autoTogglesVisible = true, wifiOnlyVisible = true,
        ))).state.value.screen.visibleItems.map { it.key }
        assertTrue(keys.containsAll(listOf(
            "document_sync_category", "sync_documents_auto_download", "sync_documents_wifi_only", "document_sync_manage",
        )))
    }

    @Test fun passwordRowIsMasked() {
        val pw = controller(FakeSyncSettingsService(syncSnap())).state.value.screen.items
            .filterIsInstance<SettingsItem.TextInputRow>().single { it.key == "cloud_sync_password" }
        assertTrue(pw.masked)
    }

    @Test fun adapterRowDisabledWhenSignedIn() {
        val adapter = controller(FakeSyncSettingsService(syncSnap(adapterEnabled = false))).state.value.screen.items
            .filterIsInstance<SettingsItem.ListChoiceRow>().single { it.key == "sync_adapter" }
        assertFalse(adapter.enabled)
    }

    private fun row(c: SyncSettingsController, key: String) =
        c.state.value.screen.items.single { it.key == key } as SettingsItem.SwitchRow

    @Test fun switchOnlyMarksPending() {
        val svc = FakeSyncSettingsService(syncSnap(enabledCategories = setOf("sync_enable_progress")))
        val c = controller(svc)
        c.onSwitch("sync_enable_bookmarks", true)
        c.onSwitch("sync_enable_progress", false)
        assertEquals(emptyList(), svc.calls)  // nothing reached the service: no sign-in, no write
        assertEquals(mapOf("sync_enable_bookmarks" to true, "sync_enable_progress" to false), c.state.value.pending)
        val labels = SyncSettingsLabels.forTest()
        assertTrue(row(c, "sync_enable_bookmarks").checked)
        assertEquals(labels.pendingOn, row(c, "sync_enable_bookmarks").summary)
        assertFalse(row(c, "sync_enable_progress").checked)
        assertEquals(labels.pendingOff, row(c, "sync_enable_progress").summary)
        assertEquals("sum", row(c, "sync_enable_workspaces").summary)
        // flipping back to the persisted value is no change at all
        c.onSwitch("sync_enable_bookmarks", false)
        assertEquals(mapOf("sync_enable_progress" to false), c.state.value.pending)
        assertEquals("sum", row(c, "sync_enable_bookmarks").summary)
    }

    @Test fun pendingEntryDropsWhenPersistedMatches() {
        val svc = FakeSyncSettingsService(syncSnap())
        val c = controller(svc)
        c.onSwitch("sync_enable_bookmarks", true)
        c.onSwitch("sync_enable_progress", true)
        svc._snap.value = svc._snap.value.copy(categoryEnabled = svc._snap.value.categoryEnabled + ("sync_enable_bookmarks" to true))
        assertEquals(mapOf("sync_enable_progress" to true), c.state.value.pending)
    }

    @Test fun signOutClearsPending() {
        val svc = FakeSyncSettingsService(syncSnap(adapterEnabled = false))  // signed in
        val c = controller(svc)
        c.onSwitch("sync_enable_bookmarks", true)
        assertEquals(1, c.state.value.pending.size)
        svc._snap.value = svc._snap.value.copy(adapterEnabled = true)         // signed out
        assertEquals(emptyMap(), c.state.value.pending)
        assertFalse(row(c, "sync_enable_bookmarks").checked)

        c.onSwitch("sync_enable_bookmarks", true)  // signed out: switches may be marked (sign-in happens on Apply)
        assertEquals(1, c.state.value.pending.size)
        svc._snap.value = svc._snap.value.copy(adapter = "GOOGLE_DRIVE")        // backend change
        assertEquals(emptyMap(), c.state.value.pending)
    }

    @Test fun applyShowsCheckingUntilProbed() {
        val svc = FakeSyncSettingsService(syncSnap()).apply {
            beforeProbe = CompletableDeferred(); afterProbe = CompletableDeferred()
        }
        val c = controller(svc)
        c.onSwitch("sync_enable_bookmarks", true)
        c.applyPending()
        assertEquals(SyncDialog.CheckingCloud, c.state.value.dialog)
        assertEquals(listOf(mapOf("sync_enable_bookmarks" to true)), svc.applied)
        svc.beforeProbe!!.complete(Unit)
        assertEquals(SyncDialog.None, c.state.value.dialog)           // probed: the plan dialog is the cloud layer's
        assertEquals(1, c.state.value.pending.size)                    // not finished yet
        svc.afterProbe!!.complete(Unit)
        assertEquals(emptyMap(), c.state.value.pending)
    }

    @Test fun failedApplyKeepsPendingAndClosesTheDialog() {
        val svc = FakeSyncSettingsService(syncSnap()).apply { applyResult = ApplyResult.SignInFailed }
        val c = controller(svc)
        c.onSwitch("sync_enable_bookmarks", true)
        c.applyPending()
        assertEquals(SyncDialog.None, c.state.value.dialog)
        assertEquals(1, c.state.value.pending.size)
    }

    @Test fun applyWithOnlyOffsShowsNoDialog() {
        val svc = FakeSyncSettingsService(syncSnap(enabledCategories = setOf("sync_enable_progress"))).apply {
            beforeProbe = CompletableDeferred()  // would block an "on" apply; an off-only apply never waits
        }
        val c = controller(svc)
        c.onSwitch("sync_enable_progress", false)
        c.applyPending()
        assertEquals(SyncDialog.None, c.state.value.dialog)
        assertEquals(listOf(mapOf("sync_enable_progress" to false)), svc.applied)
        assertEquals(emptyMap(), c.state.value.pending)
        assertFalse(c.state.value.loading)
    }

    @Test fun cancelCheckingCancelsApply() {
        val svc = FakeSyncSettingsService(syncSnap()).apply { beforeProbe = CompletableDeferred() }
        val c = controller(svc)
        c.onSwitch("sync_enable_bookmarks", true)
        c.applyPending()
        assertEquals(SyncDialog.CheckingCloud, c.state.value.dialog)
        c.cancelChecking()
        assertTrue(svc.applyCancelled)
        assertEquals(SyncDialog.None, c.state.value.dialog)
        assertFalse(c.state.value.loading)
        assertTrue(svc.refreshes >= 1)
        assertEquals(emptyMap(), c.state.value.pending, "progress Cancel reverts the ons: nothing stays pending")
        // the screen is usable again: a second apply starts a fresh job
        svc.beforeProbe = null
        c.onSwitch("sync_enable_bookmarks", true)
        c.applyPending()
        assertEquals(2, svc.applied.size)
    }

    @Test fun progressCancelKeepsOffsThatWereNotPersistedYet() {
        val svc = FakeSyncSettingsService(syncSnap(enabledCategories = setOf("sync_enable_progress")))
            .apply { beforeProbe = CompletableDeferred() }
        val c = controller(svc)
        c.onSwitch("sync_enable_bookmarks", true)
        c.onSwitch("sync_enable_progress", false)
        c.applyPending()
        c.cancelChecking()
        assertEquals(mapOf("sync_enable_progress" to false), c.state.value.pending)
    }

    @Test fun cancelledJoinPlanClearsPending() {
        val svc = FakeSyncSettingsService(syncSnap()).apply { applyResult = ApplyResult.Cancelled }
        val c = controller(svc)
        c.onSwitch("sync_enable_bookmarks", true)
        c.applyPending()
        assertEquals(emptyMap(), c.state.value.pending, "the cloud layer turned the ons off again; nothing is left to retry")
        assertEquals(SyncDialog.None, c.state.value.dialog)
        assertFalse(c.state.value.loading)
    }

    @Test fun confirmLeaveApplyWithPendingPrunedToEmptyStillLeaves() {
        var exits = 0
        val svc = FakeSyncSettingsService(syncSnap())
        val c = SyncSettingsController(svc, CoroutineScope(Dispatchers.Unconfined), SyncSettingsLabels.forTest(), {}, onExit = { exits++ })
        c.onSwitch("sync_enable_bookmarks", true)
        assertFalse(c.onLeave())
        // another path enables the category while the leave dialog is open: the pending entry is dropped
        svc._snap.value = svc._snap.value.copy(categoryEnabled = svc._snap.value.categoryEnabled + ("sync_enable_bookmarks" to true))
        c.confirmLeaveApply()
        assertEquals(1, exits)
        assertEquals(emptyList(), svc.applied)
        assertEquals(SyncDialog.None, c.state.value.dialog)
    }

    @Test fun leavingWhileAnApplyRunsStaysWithoutAskingTwice() {
        val svc = FakeSyncSettingsService(syncSnap()).apply { beforeProbe = CompletableDeferred(); afterProbe = CompletableDeferred() }
        val c = controller(svc)
        c.onSwitch("sync_enable_bookmarks", true)
        c.applyPending()
        svc.beforeProbe!!.complete(Unit)
        assertFalse(c.onLeave())
        assertEquals(SyncDialog.None, c.state.value.dialog, "the apply already runs: no second Apply/Discard question")
        svc.afterProbe!!.complete(Unit)
        assertTrue(c.onLeave())
    }

    @Test fun leaveWithPendingAsks() {
        var exits = 0
        val svc = FakeSyncSettingsService(syncSnap())
        val c = SyncSettingsController(svc, CoroutineScope(Dispatchers.Unconfined), SyncSettingsLabels.forTest(), {}, onExit = { exits++ })
        assertTrue(c.onLeave())                                         // nothing pending: leave
        c.onSwitch("sync_enable_bookmarks", true)
        assertFalse(c.onLeave())
        assertEquals(SyncDialog.LeaveWithPending, c.state.value.dialog)
        c.confirmLeaveDiscard()
        assertEquals(emptyMap(), c.state.value.pending)
        assertEquals(SyncDialog.None, c.state.value.dialog)
        assertEquals(1, exits)
        assertEquals(emptyList(), svc.calls)                            // discarded: nothing applied

        c.onSwitch("sync_enable_progress", true)
        assertFalse(c.onLeave())
        c.confirmLeaveApply()
        assertEquals(listOf(mapOf("sync_enable_progress" to true)), svc.applied)
        assertEquals(2, exits)                                          // applied, then left
    }

    @Test fun hopWithoutPendingRunsAtOnce() {
        var opened = 0
        val c = controller(FakeSyncSettingsService(syncSnap()), onOpen = { opened++ })
        c.onNavigate("document_sync_manage")
        assertEquals(1, opened)
        assertEquals(SyncDialog.None, c.state.value.dialog)
    }

    @Test fun hopWithPendingAsksAndRunsAfterDiscardInsteadOfExit() {
        var opened = 0
        var exits = 0
        val svc = FakeSyncSettingsService(syncSnap())
        val c = SyncSettingsController(svc, CoroutineScope(Dispatchers.Unconfined), SyncSettingsLabels.forTest(), { opened++ }, onExit = { exits++ })
        c.onSwitch("sync_enable_bookmarks", true)
        c.onNavigate("document_sync_manage")
        assertEquals(SyncDialog.LeaveWithPending, c.state.value.dialog)
        assertEquals(0, opened)
        c.confirmLeaveDiscard()
        assertEquals(1, opened)
        assertEquals(0, exits, "the hop replaces the exit")
        assertEquals(emptyMap(), c.state.value.pending)
        assertEquals(emptyList(), svc.applied)
    }

    @Test fun applyClearsOnlyTheKeysItApplied() {
        val svc = FakeSyncSettingsService(syncSnap()).apply { beforeProbe = CompletableDeferred(); afterProbe = CompletableDeferred() }
        val c = controller(svc)
        c.onSwitch("sync_enable_bookmarks", true)
        c.applyPending()
        c.onSwitch("sync_enable_workspaces", true) // flipped while the Apply runs
        svc.beforeProbe!!.complete(Unit)
        svc.afterProbe!!.complete(Unit)
        assertEquals(mapOf("sync_enable_workspaces" to true), c.state.value.pending, "the later switch is still pending")
    }

    @Test fun hopRefusedByARunningApplyDoesNotLeakIntoALaterLeave() {
        var opened = 0
        var exits = 0
        val svc = FakeSyncSettingsService(syncSnap()).apply { beforeProbe = CompletableDeferred(); afterProbe = CompletableDeferred() }
        val c = SyncSettingsController(svc, CoroutineScope(Dispatchers.Unconfined), SyncSettingsLabels.forTest(), { opened++ }, onExit = { exits++ })
        c.onSwitch("sync_enable_bookmarks", true)
        c.applyPending()
        svc.beforeProbe!!.complete(Unit)
        c.onNavigate("document_sync_manage") // refused: an Apply is running
        assertEquals(0, opened)
        svc.afterProbe!!.complete(Unit)
        svc.beforeProbe = null; svc.afterProbe = null
        c.onSwitch("sync_enable_workspaces", true)
        assertFalse(c.onLeave())
        c.confirmLeaveApply()
        assertEquals(0, opened, "the refused hop did not come back")
        assertEquals(1, exits)
    }

    @Test fun hopRefusedWhileApplyThenLeaveRunsKeepsTheConfirmedHop() {
        var opened = 0
        var exits = 0
        val svc = FakeSyncSettingsService(syncSnap()).apply { beforeProbe = CompletableDeferred(); afterProbe = CompletableDeferred() }
        val c = SyncSettingsController(svc, CoroutineScope(Dispatchers.Unconfined), SyncSettingsLabels.forTest(), { opened++ }, onExit = { exits++ })
        c.onSwitch("sync_enable_bookmarks", true)
        c.onNavigate("document_sync_manage") // the leave question for hop 1
        c.confirmLeaveApply()
        c.onNavigate("document_sync_manage") // hop 2 refused: the Apply runs
        svc.beforeProbe!!.complete(Unit)
        svc.afterProbe!!.complete(Unit)
        assertEquals(1, opened, "the confirmed hop still runs after the Apply")
        assertEquals(0, exits, "the screen does not just exit")
    }

    @Test fun hopWithPendingRunsAfterApplyCompletes() {
        var opened = 0
        val svc = FakeSyncSettingsService(syncSnap()).apply { afterProbe = CompletableDeferred(); beforeProbe = CompletableDeferred() }
        val c = controller(svc, onOpen = { opened++ })
        c.onSwitch("sync_enable_bookmarks", true)
        c.onNavigate("document_sync_manage")
        c.confirmLeaveApply()
        svc.beforeProbe!!.complete(Unit)
        assertEquals(0, opened, "not before the apply finished")
        svc.afterProbe!!.complete(Unit)
        assertEquals(1, opened)
    }

    @Test fun cancelledHopQuestionDoesNotLeakIntoALaterExit() {
        var opened = 0
        var exits = 0
        val c = SyncSettingsController(FakeSyncSettingsService(syncSnap()), CoroutineScope(Dispatchers.Unconfined), SyncSettingsLabels.forTest(), { opened++ }, onExit = { exits++ })
        c.onSwitch("sync_enable_bookmarks", true)
        c.onNavigate("document_sync_manage")
        c.dismissDialog()
        assertFalse(c.onLeave())
        c.confirmLeaveDiscard()
        assertEquals(0, opened)
        assertEquals(1, exits)
    }

    @Test fun abandonedLeaveDoesNotExitWhenTheApplyFinishesLater() {
        var exits = 0
        val svc = FakeSyncSettingsService(syncSnap()).apply { afterProbe = CompletableDeferred(); beforeProbe = CompletableDeferred() }
        val c = SyncSettingsController(svc, CoroutineScope(Dispatchers.Unconfined), SyncSettingsLabels.forTest(), {}, onExit = { exits++ })
        c.onSwitch("sync_enable_bookmarks", true)
        assertFalse(c.onLeave())
        c.confirmLeaveApply()
        c.abandonLeave()  // the screen is gone (e.g. re-entered meanwhile): the late finish must not pop anything
        svc.beforeProbe!!.complete(Unit); svc.afterProbe!!.complete(Unit)
        assertEquals(0, exits)
        assertEquals(emptyMap(), c.state.value.pending)
    }

    @Test fun applyingFlagCoversTheWholeApplyIncludingAfterTheProbe() {
        val svc = FakeSyncSettingsService(syncSnap()).apply { afterProbe = CompletableDeferred(); beforeProbe = CompletableDeferred() }
        val c = controller(svc)
        assertFalse(c.state.value.applying)
        c.onSwitch("sync_enable_bookmarks", true)
        c.applyPending()
        assertTrue(c.state.value.applying)
        svc.beforeProbe!!.complete(Unit)
        assertEquals(SyncDialog.None, c.state.value.dialog)
        assertTrue(c.state.value.applying, "the plan dialog is up: the bar must stay hidden")
        svc.afterProbe!!.complete(Unit)
        assertFalse(c.state.value.applying)
    }

    @Test fun documentsSwitchIsPendingButTransferOptionsStillApplyImmediately() {
        val svc = FakeSyncSettingsService(syncSnap(documentsEnabled = true))
        val c = controller(svc)
        c.onSwitch("sync_enable_documents", false)
        c.onSwitch("sync_documents_wifi_only", false)
        assertEquals(listOf("docToggle:sync_documents_wifi_only=false"), svc.calls)
        assertEquals(mapOf("sync_enable_documents" to false), c.state.value.pending)
        c.applyPending()
        assertTrue("docsDisable" in svc.calls)
        assertEquals(emptyMap(), c.state.value.pending)
    }

    @Test fun enableDocuments_showsDialogWithScannedSummary() {
        val svc = FakeSyncSettingsService(syncSnap()).apply {
            signInResult = true; scanResult = DocSyncSummaryData(listOf("KJV"), emptyList(), 100, 0)
        }
        val c = controller(svc)
        c.onSwitch("sync_enable_documents", true)
        assertEquals(emptyList(), svc.calls, "toggling alone must not sign in, scan or enable sync")
        assertTrue(row(c, "sync_enable_documents").checked)
        assertEquals("Will be turned on", row(c, "sync_enable_documents").summary)
        c.applyPending()
        assertEquals(listOf("signIn", "scan"), svc.calls)
        val d = c.state.value.dialog
        assertTrue(d is SyncDialog.EnableDocuments)
        assertEquals("docs-msg", (d as SyncDialog.EnableDocuments).message)
        assertEquals(SyncSettingsLabels.forTest().documentsEnableDialogTitle, d.title)
        assertFalse(c.state.value.loading)
    }

    @Test fun confirmEnableDocuments_persistsSummaryAndDismisses() {
        val svc = FakeSyncSettingsService(syncSnap()).apply { signInResult = true }
        val c = controller(svc)
        c.onSwitch("sync_enable_documents", true)
        c.applyPending()
        assertFalse("docsEnable" in svc.calls)
        c.confirmEnableDocuments()
        assertTrue("docsEnable" in svc.calls)
        assertEquals(emptyMap(), c.state.value.pending)
        assertFalse(c.state.value.applying)
        assertEquals(SyncDialog.None, c.state.value.dialog)
    }

    @Test fun discardDocumentsDoesNotWriteOrStartTheEnableFlow() {
        val svc = FakeSyncSettingsService(syncSnap())
        val c = controller(svc)
        c.onSwitch("sync_enable_documents", true)
        c.discardPending()
        assertFalse(row(c, "sync_enable_documents").checked)
        assertEquals(emptyMap(), c.state.value.pending)
        assertEquals(emptyList(), svc.calls)
    }

    @Test fun cancellingDocumentConfirmationDiscardsAllPendingOns() {
        val svc = FakeSyncSettingsService(syncSnap())
        val c = controller(svc)
        c.onSwitch("sync_enable_documents", true)
        c.onSwitch("sync_enable_bookmarks", true)
        c.applyPending()
        assertTrue(c.state.value.dialog is SyncDialog.EnableDocuments)
        c.dismissDialog()
        assertEquals(emptyList(), svc.applied)
        assertFalse("docsEnable" in svc.calls)
        assertEquals(emptyMap(), c.state.value.pending)
        assertFalse(c.state.value.applying)
    }

    @Test fun documentAndDatabaseChangesApplyTogetherAfterDocumentConfirmation() {
        val svc = FakeSyncSettingsService(syncSnap())
        val c = controller(svc)
        c.onSwitch("sync_enable_documents", true)
        c.onSwitch("sync_enable_bookmarks", true)
        c.applyPending()
        assertTrue(svc.applied.isEmpty())
        c.confirmEnableDocuments()
        assertEquals(listOf(mapOf("sync_enable_bookmarks" to true)), svc.applied)
        assertTrue("docsEnable" in svc.calls)
        assertEquals(emptyMap(), c.state.value.pending)
    }

    @Test fun failedDocumentSignInKeepsPendingForRetryWithoutScanning() {
        val svc = FakeSyncSettingsService(syncSnap()).apply { signInResult = false }
        val c = controller(svc)
        c.onSwitch("sync_enable_documents", true)
        c.applyPending()
        assertEquals(listOf("signIn"), svc.calls)
        assertEquals(mapOf("sync_enable_documents" to true), c.state.value.pending)
        assertEquals(SyncDialog.None, c.state.value.dialog)
        assertFalse(c.state.value.applying)
    }

    @Test fun cancelledDocumentPreviewStillAppliesPendingOffs() {
        val svc = FakeSyncSettingsService(syncSnap(enabledCategories = setOf("sync_enable_progress")))
        val c = controller(svc)
        c.onSwitch("sync_enable_documents", true)
        c.onSwitch("sync_enable_progress", false)
        c.applyPending()
        c.dismissDialog()
        assertEquals(listOf(mapOf("sync_enable_progress" to false)), svc.applied)
        assertFalse("docsEnable" in svc.calls)
        assertEquals(emptyMap(), c.state.value.pending)
    }

    @Test fun failedMixedApplyDoesNotDisableDocuments() {
        val svc = FakeSyncSettingsService(syncSnap(documentsEnabled = true)).apply { applyResult = ApplyResult.SignInFailed }
        val c = controller(svc)
        c.onSwitch("sync_enable_documents", false)
        c.onSwitch("sync_enable_bookmarks", true)
        c.applyPending()
        assertFalse("docsDisable" in svc.calls)
        assertEquals(mapOf("sync_enable_documents" to false, "sync_enable_bookmarks" to true), c.state.value.pending)
    }

    @Test fun failedDatabaseJoinDoesNotEnableDocuments() {
        val svc = FakeSyncSettingsService(syncSnap()).apply { applyResult = ApplyResult.Cancelled }
        val c = controller(svc)
        c.onSwitch("sync_enable_documents", true)
        c.onSwitch("sync_enable_bookmarks", true)
        c.applyPending()
        c.confirmEnableDocuments()
        assertFalse("docsEnable" in svc.calls)
        assertEquals(emptyMap(), c.state.value.pending)
    }

    @Test fun leaveAppliesDocumentsOnlyAfterTransferConfirmation() {
        var exits = 0
        val svc = FakeSyncSettingsService(syncSnap())
        val c = SyncSettingsController(svc, CoroutineScope(Dispatchers.Unconfined), SyncSettingsLabels.forTest(), {}, onExit = { exits++ })
        c.onSwitch("sync_enable_documents", true)
        assertFalse(c.onLeave())
        c.confirmLeaveApply()
        assertEquals(0, exits)
        assertTrue(c.state.value.applying)
        c.confirmEnableDocuments()
        assertEquals(1, exits)
        assertTrue("docsEnable" in svc.calls)
    }

    @Test fun leaveDiscardDropsDocumentsWithoutSigningIn() {
        var exits = 0
        val svc = FakeSyncSettingsService(syncSnap())
        val c = SyncSettingsController(svc, CoroutineScope(Dispatchers.Unconfined), SyncSettingsLabels.forTest(), {}, onExit = { exits++ })
        c.onSwitch("sync_enable_documents", true)
        assertFalse(c.onLeave())
        c.confirmLeaveDiscard()
        assertEquals(1, exits)
        assertEquals(emptyList(), svc.calls)
        assertFalse(row(c, "sync_enable_documents").checked)
    }

    @Test fun documentPendingEntryPrunesOnMatchingSnapshotAndToggleBack() {
        val svc = FakeSyncSettingsService(syncSnap())
        val c = controller(svc)
        c.onSwitch("sync_enable_documents", true)
        c.onSwitch("sync_enable_documents", false)
        assertEquals(emptyMap(), c.state.value.pending)
        c.onSwitch("sync_enable_documents", true)
        svc._snap.value = svc._snap.value.copy(documentsEnabled = true)
        assertEquals(emptyMap(), c.state.value.pending)
        assertTrue(row(c, "sync_enable_documents").checked)
    }

    @Test fun documentToggle_passthrough() {
        val svc = FakeSyncSettingsService(syncSnap())
        controller(svc).onSwitch("sync_documents_wifi_only", false)
        assertEquals(listOf("docToggle:sync_documents_wifi_only=false"), svc.calls)
    }

    @Test fun adapterChange_passthrough() {
        val svc = FakeSyncSettingsService(syncSnap())
        controller(svc).onListChoice("sync_adapter", "GOOGLE_DRIVE")
        assertTrue("adapter=GOOGLE_DRIVE" in svc.calls)
    }

    @Test fun invalidUrl_showsErrorDialog() {
        val svc = FakeSyncSettingsService(syncSnap()).apply { textResult = false }
        val c = controller(svc)
        c.onTextInput("cloud_sync_server_url", "bad")
        assertTrue(c.state.value.dialog is SyncDialog.UrlError)
    }

    @Test fun validText_noDialog() {
        val svc = FakeSyncSettingsService(syncSnap()).apply { textResult = true }
        val c = controller(svc)
        c.onTextInput("cloud_sync_username", "me")
        assertEquals(SyncDialog.None, c.state.value.dialog)
    }

    @Test fun resetNav_showsConfirm_thenResets() {
        val svc = FakeSyncSettingsService(syncSnap())
        val c = controller(svc)
        c.onNavigate("cloud_sync_reset")
        assertTrue(c.state.value.dialog is SyncDialog.ResetConfirm)
        c.confirmReset()
        assertTrue("reset" in svc.calls)
        assertEquals(SyncDialog.None, c.state.value.dialog)
    }

    /**
     * osTicket 3515: the reset runs in the host's lifecycle scope (no exception handler on Android), so a failing
     * reset crashed the app and left the screen loading. It is contained: the screen refreshes and stops loading.
     */
    @Test fun failingReset_doesNotEscapeTheScope_andStopsLoading() {
        val svc = FakeSyncSettingsService(syncSnap()).apply { resetFailure = IllegalStateException("provider sign-out failed") }
        val escaped = mutableListOf<Throwable>()
        val scope = CoroutineScope(Dispatchers.Unconfined + CoroutineExceptionHandler { _, e -> escaped += e })
        val c = SyncSettingsController(svc, scope, SyncSettingsLabels.forTest(), {})
        c.onNavigate("cloud_sync_reset")
        val refreshesBefore = svc.refreshes
        c.confirmReset()
        assertTrue("reset" in svc.calls)
        assertTrue(escaped.isEmpty(), "Reset failure escaped to the host scope: $escaped")
        assertFalse(c.state.value.loading)
        assertTrue(svc.refreshes > refreshesBefore, "The screen must show the state the failed reset left")
    }

    @Test fun manageNav_opensCloudDocuments() {
        var opened = false
        val svc = FakeSyncSettingsService(syncSnap())
        controller(svc) { opened = true }.onNavigate("document_sync_manage")
        assertTrue(opened)
    }

    /**
     * Round 14b: every sync row takes its own preference key as its icon key; `:app`'s
     * `settingsDrawableRes` maps that key to classic's drawable (pinned by `SettingsIconParityTest`).
     * Category headers stay iconless, exactly as in `res/xml/sync_settings.xml`.
     */
    @Test fun everyRowCarriesItsOwnKeyAsIconKey() {
        val items = controller(FakeSyncSettingsService(syncSnap())).state.value.screen.items
        val rows = items.filter { it !is SettingsItem.Category }
        assertEquals(emptyList(), rows.filter { it.iconKeyOrNull() != it.key }.map { it.key })
        assertEquals(emptyList(), items.filterIsInstance<SettingsItem.Category>().filter { it.iconKeyOrNull() != null }.map { it.key })
    }

    @Test fun theTwentySyncRowsAreAllPresent() {
        val rows = controller(FakeSyncSettingsService(syncSnap())).state.value.screen.items
            .filter { it !is SettingsItem.Category }.map { it.key }
        assertEquals(
            listOf(
                "sync_adapter", "cloud_sync_reset", "cloud_sync_info", "cloud_sync_server_url",
                "cloud_sync_username", "cloud_sync_password", "cloud_sync_folder_path", "webdav_sync_cert",
                "sync_enable_bookmarks", "sync_enable_workspaces", "sync_enable_readingplans",
                "sync_enable_mydocuments", "sync_enable_ai_settings", "sync_enable_progress", "sync_enable_documents",
                "sync_documents_auto_download", "sync_documents_auto_upload",
                "sync_documents_auto_delete", "sync_documents_wifi_only", "document_sync_manage",
            ),
            rows,
        )
    }

    @Test fun folder_path_row_shows_the_configured_path_and_falls_back_to_its_explanation() {
        val labels = SyncSettingsLabels.forTest()
        val configured = controller(FakeSyncSettingsService(syncSnap(folderPath = "/AndBible/documents")))
        val row = configured.state.value.screen.items
            .filterIsInstance<SettingsItem.TextInputRow>().first { it.key == "cloud_sync_folder_path" }
        assertEquals("/AndBible/documents", row.summary)

        val empty = controller(FakeSyncSettingsService(syncSnap(folderPath = "")))
        val emptyRow = empty.state.value.screen.items
            .filterIsInstance<SettingsItem.TextInputRow>().first { it.key == "cloud_sync_folder_path" }
        assertEquals(labels.folderPathSummary, emptyRow.summary)
    }
}
