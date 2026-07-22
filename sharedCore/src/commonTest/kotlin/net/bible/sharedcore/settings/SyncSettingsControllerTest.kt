package net.bible.sharedcore.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    override fun setCategoryEnabled(key: String, enabled: Boolean) { calls += "cat:$key=$enabled" }
    override fun setDocumentsEnabled(summary: DocSyncSummaryData) { calls += "docsEnable" }
    override fun disableDocuments() { calls += "docsDisable" }
    override fun setDocumentSyncToggle(key: String, value: Boolean) { calls += "docToggle:$key=$value" }
    override fun setText(key: String, value: String): Boolean { calls += "text:$key=$value"; return textResult }
    var textResult = true
    override fun setAdapter(value: String) { calls += "adapter=$value" }
    override suspend fun resetSync() { calls += "reset" }
    override fun formatEnableDocumentsMessage(summary: DocSyncSummaryData) = "docs-msg"
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
) = SyncSettingsSnapshot(
    adapter = "NEXT_CLOUD",
    adapterChoices = listOf(Choice2("GOOGLE_DRIVE", "Google Drive"), Choice2("NEXT_CLOUD", "Nextcloud")),
    adapterSummary = "adapter summary",
    adapterEnabled = adapterEnabled,
    cloudInfoSummary = null,
    serverUrl = "https://s", username = "u", password = "p", folderPath = "f",
    credsVisible = credsVisible, credsEnabled = credsEnabled,
    resetVisible = resetVisible, cloudInfoVisible = cloudInfoVisible,
    categoryEnabled = SyncCategoryKeys.DISPLAY.associateWith { false },
    categorySummary = SyncCategoryKeys.DISPLAY.associateWith { "sum" },
    documentsEnabled = documentsEnabled,
    documentCategoryVisible = documentCategoryVisible,
    autoDownload = true, autoUpload = true, autoDelete = true, wifiOnly = true,
    autoTogglesVisible = autoTogglesVisible, wifiOnlyVisible = wifiOnlyVisible,
)

class SyncSettingsControllerTest {
    private fun controller(svc: SyncSettingsService, onOpen: () -> Unit = {}) =
        SyncSettingsController(svc, CoroutineScope(Dispatchers.Unconfined), SyncSettingsLabels.forTest(), onOpen)

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
        assertFalse("sync_enable_readingplans" in keys) // always omitted
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

    @Test fun enableCategory_signInSuccess_persistsAndRefreshes() {
        val svc = FakeSyncSettingsService(syncSnap()).apply { signInResult = true }
        controller(svc).onSwitch("sync_enable_bookmarks", true)
        assertEquals(listOf("signIn", "cat:sync_enable_bookmarks=true"), svc.calls)
        assertTrue(svc.refreshes >= 1)
    }

    @Test fun enableCategory_signInFail_doesNotPersist() {
        val svc = FakeSyncSettingsService(syncSnap()).apply { signInResult = false }
        controller(svc).onSwitch("sync_enable_bookmarks", true)
        assertEquals(listOf("signIn"), svc.calls)  // no cat write
    }

    @Test fun disableCategory_immediate() {
        val svc = FakeSyncSettingsService(syncSnap())
        controller(svc).onSwitch("sync_enable_progress", false)
        assertEquals(listOf("cat:sync_enable_progress=false"), svc.calls)
    }

    @Test fun enableDocuments_showsDialogWithScannedSummary() {
        val svc = FakeSyncSettingsService(syncSnap()).apply {
            signInResult = true; scanResult = DocSyncSummaryData(listOf("KJV"), emptyList(), 100, 0)
        }
        val c = controller(svc)
        c.onSwitch("sync_enable_documents", true)
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
        c.confirmEnableDocuments()
        assertTrue("docsEnable" in svc.calls)
        assertEquals(SyncDialog.None, c.state.value.dialog)
    }

    @Test fun disableDocuments_immediate() {
        val svc = FakeSyncSettingsService(syncSnap(documentsEnabled = true))
        controller(svc).onSwitch("sync_enable_documents", false)
        assertTrue("docsDisable" in svc.calls)
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

    @Test fun manageNav_opensCloudDocuments() {
        var opened = false
        val svc = FakeSyncSettingsService(syncSnap())
        controller(svc) { opened = true }.onNavigate("document_sync_manage")
        assertTrue(opened)
    }
}
