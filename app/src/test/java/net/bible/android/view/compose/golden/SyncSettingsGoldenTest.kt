package net.bible.android.view.compose.golden

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.bible.android.TEST_SDK
import net.bible.sharedcore.settings.Choice2
import net.bible.sharedcore.settings.DocSyncSummaryData
import net.bible.sharedcore.settings.SyncCategoryKeys
import net.bible.sharedcore.settings.SyncDialog
import net.bible.sharedcore.settings.SyncSettingsController
import net.bible.sharedcore.settings.SyncSettingsLabels
import net.bible.sharedcore.settings.SyncSettingsService
import net.bible.sharedcore.settings.SyncSettingsSnapshot
import net.bible.sharedcore.settings.SyncSettingsUiState
import net.bible.sharedui.settings.SyncSettingsScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens for [SyncSettingsScreen]. The settings list is produced by the REAL [SyncSettingsController]
 * (fed a hand-built snapshot via a fake service) so the golden can't drift from `build()`; the
 * transient states (loading overlay, enable-documents dialog) are applied by copying the resulting
 * [SyncSettingsUiState].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class SyncSettingsGoldenTest {

    private class FakeService(initial: SyncSettingsSnapshot) : SyncSettingsService {
        override val snapshot: StateFlow<SyncSettingsSnapshot> = MutableStateFlow(initial)
        override fun refresh() {}
        override suspend fun signIn() = true
        override suspend fun scanDocuments() = DocSyncSummaryData(emptyList(), emptyList(), 0, 0)
        override fun setCategoryEnabled(key: String, enabled: Boolean) {}
        override fun setDocumentsEnabled(summary: DocSyncSummaryData) {}
        override fun disableDocuments() {}
        override fun setDocumentSyncToggle(key: String, value: Boolean) {}
        override fun setText(key: String, value: String) = true
        override fun setAdapter(value: String) {}
        override suspend fun resetSync() {}
        override fun formatEnableDocumentsMessage(summary: DocSyncSummaryData) =
            "1 document to upload (2.3 MB)"
    }

    private fun snap(
        signedIn: Boolean = false,
        documentsEnabled: Boolean = false,
    ) = SyncSettingsSnapshot(
        adapter = "NEXT_CLOUD",
        adapterChoices = listOf(Choice2("GOOGLE_DRIVE", "Google Drive"), Choice2("NEXT_CLOUD", "Nextcloud")),
        adapterSummary = "Sync keeps your data across devices. Provider: Nextcloud",
        adapterEnabled = !signedIn,
        cloudInfoSummary = if (signedIn) "Using 12.34 MB in the cloud" else null,
        serverUrl = "https://cloud.example.com", username = "alice", password = "secret", folderPath = "AndBible",
        credsVisible = true, credsEnabled = !signedIn,
        resetVisible = signedIn, cloudInfoVisible = signedIn,
        categoryEnabled = SyncCategoryKeys.DISPLAY.associateWith { signedIn },
        categorySummary = SyncCategoryKeys.DISPLAY.associateWith { "Synced content" },
        documentsEnabled = documentsEnabled,
        documentCategoryVisible = signedIn,
        autoDownload = true, autoUpload = true, autoDelete = false, wifiOnly = true,
        autoTogglesVisible = documentsEnabled, wifiOnlyVisible = documentsEnabled,
    )

    private fun uiStateFor(snapshot: SyncSettingsSnapshot): SyncSettingsUiState =
        SyncSettingsController(
            service = FakeService(snapshot),
            scope = CoroutineScope(Job()),
            labels = SyncSettingsLabels.forTest(),
            onOpenCloudDocuments = {},
        ).state.value

    private fun screen(uiState: SyncSettingsUiState): @Composable () -> Unit = {
        SyncSettingsScreen(
            uiState = uiState,
            onUp = {},
            onSwitch = { _, _ -> },
            onListChoice = { _, _ -> },
            onTextInput = { _, _ -> },
            onNavigate = {},
            onConfirmReset = {},
            onConfirmEnableDocuments = {},
            onDismissDialog = {},
        )
    }

    // heightDp=2400: the full sync list is longer than the default viewport.
    @Test fun signedout_matrix() =
        captureMatrix("SyncSettings", "signedout", heightDp = 2400, content = screen(uiStateFor(snap(signedIn = false))))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun signedout_rtl() =
        captureRtl("SyncSettings", "signedout", heightDp = 2400, content = screen(uiStateFor(snap(signedIn = false))))

    @Test fun signedin_edge() =
        captureGolden("SyncSettings", "signedin", EDGE_MODE, heightDp = 2400, content = screen(uiStateFor(snap(signedIn = true))))

    @Test fun documentsenabled_edge() =
        captureGolden("SyncSettings", "documentsenabled", EDGE_MODE, heightDp = 2400,
            content = screen(uiStateFor(snap(signedIn = true, documentsEnabled = true))))

    @Test fun loading_edge() =
        captureGolden("SyncSettings", "loading", EDGE_MODE, heightDp = 2400,
            content = screen(uiStateFor(snap(signedIn = false)).copy(loading = true)))

    @Test fun enabledocsdialog_edge() =
        captureGolden("SyncSettings", "enabledocsdialog", EDGE_MODE, heightDp = 2400,
            content = screen(
                uiStateFor(snap(signedIn = true)).copy(
                    dialog = SyncDialog.EnableDocuments(
                        DocSyncSummaryData(listOf("KJV"), emptyList(), 2_400_000, 0),
                        "1 document to upload (2.3 MB)",
                        title = SyncSettingsLabels.forTest().documentsEnableDialogTitle,
                    ),
                ),
            ))
}
