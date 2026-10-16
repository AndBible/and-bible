package net.bible.android.view.compose.golden

import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedcore.cloud.CloudDocFilter
import net.bible.sharedcore.cloud.CloudDocItem
import net.bible.sharedcore.cloud.CloudDocumentsDialog
import net.bible.sharedcore.navigation.DocArrangement
import net.bible.sharedcore.navigation.DocCategory
import net.bible.sharedcore.navigation.DocGroup
import net.bible.sharedcore.navigation.DocGroupBy
import net.bible.sharedcore.navigation.DocGroupKey
import net.bible.sharedcore.navigation.DocSortCriterion
import net.bible.sharedcore.navigation.DocSortKey
import net.bible.sharedui.components.AbArrangementLabels
import net.bible.sharedui.components.AbArrangementSheetContent
import net.bible.sharedui.cloud.CloudDocumentsScreen
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

    // The cloud controller's applicable sort keys (STATUS, TYPE, NAME, SIZE) — no LANGUAGE,
    // REPOSITORY or RECOMMENDED: a cloud listing has neither, and nothing marks a synced document
    // as recommended. See CloudDocumentsController.applicableSortKeys.
    private val cloudArrangement = DocArrangement(
        sort = listOf(
            DocSortCriterion(DocSortKey.STATUS),
            DocSortCriterion(DocSortKey.TYPE),
            DocSortCriterion(DocSortKey.NAME),
            DocSortCriterion(DocSortKey.SIZE),
        ),
        groupBy = DocGroupBy.NONE,
    )

    @Composable
    private fun screen(
        displayed: List<CloudDocItem> = rows,
        loading: Boolean = false,
        selectionMode: Boolean = false,
        selectedIds: Set<String> = emptySet(),
        query: String = "",
        searchModeActive: Boolean = false,
    ) = CloudDocumentsScreen(
        title = "Manage cloud documents",
        loading = loading, isRefreshing = false, onRefresh = {},
        grouped = listOf(DocGroup(DocGroupKey.None, displayed)),
        statusFilters = statusFilters, selectedStatusFilter = CloudDocFilter.ALL,
        categoryFilters = categoryFilters, selectedCategoryFilter = null,
        query = query, selectionMode = selectionMode, selectedIds = selectedIds, syncEnabled = false,
        // Deliberately always None: no golden here may open a real ModalBottomSheet (final review
        // I1) -- that hangs Roborazzi and the whole :app suite with it. A SyncNow value used to be
        // reachable through this helper's own syncNowDialog parameter with nothing calling it since
        // the sync-now golden was deleted (T6); Task 17 folded that parameter into this one
        // (CloudDocumentsDialog), so this capture path structurally still cannot open the sheet --
        // ConfirmRemove/ConfirmPurge render a plain AlertDialog (not a sheet), so they would be safe
        // here too, but None keeps every existing golden capture unchanged.
        dialog = CloudDocumentsDialog.None,
        topBarActions = {}, onQueryChange = {},
        searchModeActive = searchModeActive, onOpenSearch = {}, onCloseSearch = {},
        onStatusFilterChange = {}, onCategoryFilterChange = {},
        // Task 10: arrangement + show-removed plumbing. The filter bar's own arrangement sheet is
        // a ModalBottomSheet, so — same rule as the dialog above — no golden here may open it;
        // CloudDocFilterBar's chips/count render regardless, and the dedicated arrangement-sheet
        // golden below captures AbArrangementSheetContent directly instead.
        arrangement = cloudArrangement,
        groupKeys = listOf(DocGroupBy.NONE, DocGroupBy.TYPE, DocGroupBy.STATUS),
        rememberArrangement = true, arrangementIsDefault = true,
        onMoveSort = { _, _ -> }, onToggleSortDirection = {}, onGroupByChange = {},
        onRememberChange = {}, onResetArrangement = {},
        showRemoved = false, onShowRemovedChange = {},
        onRowClick = {}, onRowLongClick = {}, onRowAction = { _, _ -> }, onBulkAction = {},
        onSyncNowConfirm = {}, onSyncNowDismiss = {},
        onConfirmDialog = {}, onDismissDialog = {},
        onNavigateUp = {}, onExitSelection = {},
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
    @Test fun cloud_status_monochrome() {
        auditMono("CloudDocuments", "allStatuses", 900, emptyList()) {
            screen(displayed = statusRows)
        }
        MONO_MODES.forEach { mode ->
            val image = javax.imageio.ImageIO.read(java.io.File("build/mono-audit/CloudDocuments_allStatuses_${mode.tag}.png"))
            val ink = if (mode.dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            statusRows.indices.forEach { index ->
                val expected = if (index < 4) ink else 0xFF808080.toInt()
                // Two-line rows keep all status icons 72dp apart; inspect only the trailing status icon,
                // excluding both text and overflow. Require solid pixels, not an AA edge match.
                val colors = (178 + index * 72 until 198 + index * 72).flatMap { y ->
                    (236 until 256).map { x -> image.getRGB(x, y) }
                }
                org.junit.Assert.assertTrue("Status ${statusRows[index].name} in ${mode.tag}",
                    colors.count { it == expected } >= 2)
            }
        }
    }

    @Test fun cloud_status_bw() = captureGolden("CloudDocuments", "status", GoldenMode.BW, heightDp = 900) { screen(displayed = statusRows) }

    // Captured via AbArrangementSheetContent directly, never inside AbArrangementSheet: an open
    // ModalBottomSheet is a popup and hangs the Roborazzi capture (and the whole :app suite with
    // it) -- same rule ArrangementSheetGoldenTest follows for the download screen's own sheet.
    // Proves: exactly the cloud screen's four sort criteria, NO repository section (the
    // `repositories = emptyList()` guard in CloudDocFilterBar), and the "show removed documents"
    // switch rendered via `extraContent`, above the "remember" switch.
    @Test fun cloud_arrangementSheet() = captureGolden("CloudDocuments", "arrangementSheet", EDGE_MODE, heightDp = 900) {
        AbArrangementSheetContent(
            labels = AbArrangementLabels(
                title = "Filter and sort", repositoryLabel = "Repository",
                allRepositories = "All repositories", sortLabel = "Sort order", groupLabel = "Group by",
                rememberLabel = "Remember these settings", resetLabel = "Reset to defaults",
                reorderLabel = "Reorder", ascending = "Ascending", descending = "Descending",
                sortKeyLabel = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                groupKeyLabel = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
            ),
            sort = cloudArrangement.sort,
            groupBy = cloudArrangement.groupBy,
            groupKeys = listOf(DocGroupBy.NONE, DocGroupBy.TYPE, DocGroupBy.STATUS),
            repositories = emptyList(),
            selectedRepository = null,
            rememberSettings = true,
            resultCount = "6 documents",
            onMoveSort = { _, _ -> }, onToggleDirection = {}, onGroupByChange = {},
            onRepositoryChange = {}, onRememberChange = {}, onReset = {},
            extraContent = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Show removed documents", modifier = Modifier.weight(1f))
                    Switch(checked = false, onCheckedChange = {})
                }
            },
        )
    }
}
