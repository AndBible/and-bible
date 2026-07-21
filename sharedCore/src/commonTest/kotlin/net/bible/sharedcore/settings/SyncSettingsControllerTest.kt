package net.bible.sharedcore.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeSyncSettingsService(initial: SyncSettingsSnapshot) : SyncSettingsService {
    val _snap = MutableStateFlow(initial)
    override val snapshot: StateFlow<SyncSettingsSnapshot> get() = _snap
    val boolWrites = mutableListOf<Pair<String, Boolean>>()
    val stringWrites = mutableListOf<Pair<String, String>>()
    override fun setBool(key: String, value: Boolean) { boolWrites += key to value }
    override fun setString(key: String, value: String) { stringWrites += key to value }
    override fun refresh() {}
}

class SyncSettingsControllerTest {
    private fun snap(
        enableDocuments: Boolean = false,
    ) = SyncSettingsSnapshot(
        syncAdapter = "cloud",
        syncAdapterChoices = emptyList(),
        serverUrl = "https://example.com",
        username = "user",
        password = "secret",
        folderPath = "",
        enableBookmarks = true,
        enableWorkspaces = true,
        enableReadingPlans = true,
        enableMyDocuments = true,
        enableAiSettings = true,
        enableProgress = true,
        enableDocuments = enableDocuments,
        autoDownload = false,
        autoUpload = false,
        autoDelete = false,
        wifiOnly = false,
    )

    private fun controller(s: SyncSettingsService) =
        SyncSettingsController(s, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            SyncSettingsLabels.forTest(), onNavigate = {})

    @Test fun documentSyncCategoryGatedByEnableDocuments() {
        val hidden = controller(FakeSyncSettingsService(snap(enableDocuments = false))).state.value.visibleItems.map { it.key }
        assertFalse(hidden.contains("document_sync_category"))
        assertFalse(hidden.contains("sync_documents_auto_download"))
        val shown = controller(FakeSyncSettingsService(snap(enableDocuments = true))).state.value.visibleItems.map { it.key }
        assertTrue(shown.contains("sync_documents_auto_upload"))
        assertTrue(shown.contains("document_sync_category"))
        assertTrue(shown.contains("document_sync_manage"))
    }

    @Test fun passwordRowIsMasked() {
        val row = controller(FakeSyncSettingsService(snap())).state.value.items
            .filterIsInstance<SettingsItem.TextInputRow>().first { it.key == "cloud_sync_password" }
        assertTrue(row.masked)
    }

    @Test fun onSwitchWritesThroughByKey() {
        val svc = FakeSyncSettingsService(snap())
        controller(svc).onSwitch("sync_enable_documents", true)
        assertEquals("sync_enable_documents" to true, svc.boolWrites.single())
    }

    @Test fun onListChoiceWritesThroughByKey() {
        val svc = FakeSyncSettingsService(snap())
        controller(svc).onListChoice("sync_adapter", "webdav")
        assertEquals("sync_adapter" to "webdav", svc.stringWrites.single())
    }

    @Test fun onTextInputWritesThroughByKey() {
        val svc = FakeSyncSettingsService(snap())
        controller(svc).onTextInput("cloud_sync_username", "newuser")
        assertEquals("cloud_sync_username" to "newuser", svc.stringWrites.single())
    }

    @Test fun onNavigateForwardsKey() {
        var navigated: String? = null
        val c = SyncSettingsController(
            FakeSyncSettingsService(snap()),
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            SyncSettingsLabels.forTest(),
            onNavigate = { navigated = it },
        )
        c.onNavigate(SyncSettingsNav.RESET)
        assertEquals(SyncSettingsNav.RESET, navigated)
    }

    @Test fun itemOrderMatchesClassicTable() {
        val c = controller(FakeSyncSettingsService(snap(enableDocuments = true)))
        val keys = c.state.value.items.map { it.key }
        assertEquals(
            listOf(
                "sync_general",
                "sync_adapter",
                "cloud_sync_reset",
                "cloud_sync_info",
                "cloud_sync_server_url",
                "cloud_sync_username",
                "cloud_sync_password",
                "cloud_sync_folder_path",
                "sync_category",
                "sync_enable_bookmarks",
                "sync_enable_workspaces",
                "sync_enable_readingplans",
                "sync_enable_mydocuments",
                "sync_enable_ai_settings",
                "sync_enable_progress",
                "sync_enable_documents",
                "document_sync_category",
                "sync_documents_auto_download",
                "sync_documents_auto_upload",
                "sync_documents_auto_delete",
                "sync_documents_wifi_only",
                "document_sync_manage",
            ),
            keys,
        )
    }

    @Test fun navConstantsMatchClassicKeys() {
        assertEquals("cloud_sync_reset", SyncSettingsNav.RESET)
        assertEquals("cloud_sync_info", SyncSettingsNav.INFO)
        assertEquals("document_sync_manage", SyncSettingsNav.MANAGE_DOCS)
    }
}
