package net.bible.android.view.compose.golden

import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedcore.cloud.CloudDocFilter
import net.bible.sharedcore.cloud.CloudDocItem
import net.bible.sharedcore.cloud.SyncNowDialogState
import net.bible.sharedcore.navigation.DocCategory
import net.bible.sharedui.cloud.CloudDocumentsScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class CloudDocumentsGoldenTest {
    private fun item(
        i: String, name: String, category: DocCategory? = DocCategory.BIBLE,
        cloudOnly: Boolean = false, localOnly: Boolean = false, updateAvailable: Boolean = false,
        blocked: Boolean = false, cloudDeleted: Boolean = false,
    ) = CloudDocItem(i, name, category, "2.1", "2.0", cloudOnly, localOnly, updateAvailable, false,
        blocked, true, cloudDeleted, "4.2 MB")

    // A row per status so the e-ink capture proves every status reads by icon + text (not colour).
    private val rows = listOf(
        item("KJV", "King James Version"),
        item("ESV", "English Standard Version", updateAvailable = true),
        item("MHC", "Matthew Henry Commentary", category = DocCategory.COMMENTARY, cloudOnly = true, localOnly = false),
        item("NET", "New English Translation", localOnly = true),
        item("STR", "Strong's Greek", category = DocCategory.DICTIONARY, blocked = true),
        item("OLD", "Removed Book", cloudDeleted = true),
    )

    private val statusFilters = listOf(
        CloudDocFilter.ALL to "All", CloudDocFilter.INSTALLED to "Installed", CloudDocFilter.CLOUD to "In cloud",
        CloudDocFilter.UPDATES to "Updates", CloudDocFilter.BLOCKED to "Blocked",
        CloudDocFilter.DEVICE_ONLY to "Device only", CloudDocFilter.CLOUD_ONLY to "Cloud only",
    )
    private val categoryFilters = listOf<Pair<DocCategory?, String>>(
        null to "All types", DocCategory.BIBLE to "Bibles", DocCategory.COMMENTARY to "Commentaries",
        DocCategory.DICTIONARY to "Dictionaries", DocCategory.GENERAL_BOOK to "General books",
        DocCategory.MAPS to "Maps", DocCategory.AND_BIBLE to "Add-ons",
    )

    @Composable
    private fun screen(
        displayed: List<CloudDocItem> = rows,
        loading: Boolean = false,
        selectionMode: Boolean = false,
        selectedIds: Set<String> = emptySet(),
        syncNowDialog: SyncNowDialogState? = null,
    ) = CloudDocumentsScreen(
        title = "Manage cloud documents",
        loading = loading, isRefreshing = false, onRefresh = {},
        displayed = displayed,
        statusFilters = statusFilters, selectedStatusFilter = CloudDocFilter.ALL,
        categoryFilters = categoryFilters, selectedCategoryFilter = null,
        query = "", selectionMode = selectionMode, selectedIds = selectedIds, syncEnabled = false,
        syncNowDialog = syncNowDialog,
        topBarActions = {}, onQueryChange = {}, onStatusFilterChange = {}, onCategoryFilterChange = {},
        onRowClick = {}, onRowLongClick = {}, onRowAction = { _, _ -> }, onBulkAction = {},
        onSyncNowConfirm = {}, onSyncNowDismiss = {}, onNavigateUp = {}, onExitSelection = {},
    )

    @Test fun cloud_populated() = captureMatrix("CloudDocuments", "populated") { screen() }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun cloud_populated_rtl() = captureRtl("CloudDocuments", "populated") { screen() }

    @Test fun cloud_empty() = captureGolden("CloudDocuments", "empty", EDGE_MODE) { screen(displayed = emptyList()) }

    @Test fun cloud_loading() = captureGolden("CloudDocuments", "loading", EDGE_MODE) { screen(loading = true, displayed = emptyList()) }

    @Test fun cloud_selection() = captureGolden("CloudDocuments", "selection", EDGE_MODE) {
        screen(selectionMode = true, selectedIds = setOf("KJV", "NET"))
    }

    @Test fun cloud_sync_now_dialog() = captureGolden("CloudDocuments", "syncnow", EDGE_MODE) {
        screen(syncNowDialog = SyncNowDialogState(
            labels = listOf("Download\n2 documents (8.0 MB)", "Upload\n1 document (4.2 MB)", "Delete\nnothing to transfer"),
            checked = listOf(true, true, false)))
    }

    // e-ink status colours: the full status matrix captured in COLOR_EINK is already covered by
    // cloud_populated's EINK mode. This dedicated case pins the tombstone/blocked rows in BW too.
    @Test fun cloud_status_bw() = captureGolden("CloudDocuments", "status", GoldenMode.BW) { screen() }
}
