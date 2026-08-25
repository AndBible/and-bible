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

    // A dedicated sample that produces ALL EIGHT CloudDocStatus values (one row each) so the BW
    // status matrix golden pins every status's icon + text label in grayscale — including the two
    // the 6-row `rows` sample never reaches: WONT_SYNC (blocked && localOnly) and
    // REMOVED_STILL_INSTALLED (cloudDeleted && localOnly, label "Removed · still installed").
    // Precedence per cloudDocStatus(): cloudDeleted → blocked → updateAvailable → cloudOnly → localOnly.
    private val statusRows = listOf(
        item("SYN", "Synced Bible"),                                                              // SYNCED
        item("UPD", "Update Available", updateAvailable = true),                                  // UPDATE
        item("CLD", "Cloud Only Book", category = DocCategory.COMMENTARY, cloudOnly = true),      // CLOUD_ONLY
        item("LOC", "Local Only Book", localOnly = true),                                         // LOCAL_ONLY
        item("BLK", "Blocked Book", category = DocCategory.DICTIONARY, blocked = true),           // BLOCKED
        item("WNT", "Wont Sync Book", blocked = true, localOnly = true),                          // WONT_SYNC
        item("RMV", "Removed Book", cloudDeleted = true),                                         // REMOVED
        item("RSI", "Removed Still Installed", cloudDeleted = true, localOnly = true),            // REMOVED_STILL_INSTALLED
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
        query: String = "",
        searchModeActive: Boolean = false,
    ) = CloudDocumentsScreen(
        title = "Manage cloud documents",
        loading = loading, isRefreshing = false, onRefresh = {},
        displayed = displayed,
        statusFilters = statusFilters, selectedStatusFilter = CloudDocFilter.ALL,
        categoryFilters = categoryFilters, selectedCategoryFilter = null,
        query = query, selectionMode = selectionMode, selectedIds = selectedIds, syncEnabled = false,
        syncNowDialog = syncNowDialog,
        topBarActions = {}, onQueryChange = {},
        searchModeActive = searchModeActive, onOpenSearch = {}, onCloseSearch = {},
        onStatusFilterChange = {}, onCategoryFilterChange = {},
        onRowClick = {}, onRowLongClick = {}, onRowAction = { _, _ -> }, onBulkAction = {},
        onSyncNowConfirm = {}, onSyncNowDismiss = {}, onNavigateUp = {}, onExitSelection = {},
    )

    @Test fun cloud_populated() = captureMatrix("CloudDocuments", "populated") { screen() }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun cloud_populated_rtl() = captureRtl("CloudDocuments", "populated") { screen() }

    // Search-mode top app bar: non-empty query (so the clear/close action icon is visible),
    // displayed pre-filtered to what that query would actually match.
    @Test fun cloud_searchMode() = captureMatrix("CloudDocuments", "searchMode") {
        screen(displayed = rows.filter { it.name.contains("King") }, query = "King", searchModeActive = true)
    }

    @Test fun cloud_empty() = captureGolden("CloudDocuments", "empty", EDGE_MODE) { screen(displayed = emptyList()) }

    @Test fun cloud_loading() = captureGolden("CloudDocuments", "loading", EDGE_MODE) { screen(loading = true, displayed = emptyList()) }

    @Test fun cloud_selection() = captureGolden("CloudDocuments", "selection", EDGE_MODE) {
        screen(selectionMode = true, selectedIds = setOf("KJV", "NET"))
    }

    // e-ink status colours: the full status matrix captured in COLOR_EINK is already covered by
    // cloud_populated's EINK mode. This dedicated case pins ALL EIGHT statuses in BW — one row each
    // via statusRows — proving every status (incl. WONT_SYNC + REMOVED_STILL_INSTALLED) reads by
    // icon + text with colour degraded to grayscale.
    @Test fun cloud_status_bw() = captureGolden("CloudDocuments", "status", GoldenMode.BW, heightDp = 900) { screen(displayed = statusRows) }
}
