package net.bible.android.view.compose.golden

import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedcore.navigation.DocArrangement
import net.bible.sharedcore.navigation.DocCategory
import net.bible.sharedcore.navigation.DocGroup
import net.bible.sharedcore.navigation.DocGroupBy
import net.bible.sharedcore.navigation.DocInstallStatus
import net.bible.sharedcore.navigation.DocRow
import net.bible.sharedcore.navigation.DocSortKey
import net.bible.sharedcore.navigation.DocTypeFilter
import net.bible.sharedcore.navigation.LangOption
import net.bible.sharedcore.navigation.defaultArrangement
import net.bible.sharedcore.navigation.groupDocuments
import net.bible.sharedui.navigation.DocumentSelectionScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class DocumentSelectionGoldenTest {
    private val english = LangOption("en", "English", "en")
    private val greek = LangOption("grc", "Greek", "grc")

    private val rows = listOf(
        DocRow(
            docId = "KJV", osisId = "KJV", abbreviation = "KJV", name = "King James Version",
            language = english, repository = "CrossWire", category = DocCategory.BIBLE,
            installStatus = DocInstallStatus.INSTALLED, percentDone = 0, recommended = false,
            badWarn = false, locked = false, enciphered = false, canDelete = true, installSizeMb = 4.2,
        ),
        DocRow(
            docId = "MHC", osisId = "MHC", abbreviation = "MHC", name = "Matthew Henry Commentary",
            language = english, repository = "CrossWire", category = DocCategory.COMMENTARY,
            installStatus = DocInstallStatus.NOT_INSTALLED, percentDone = 0, recommended = false,
            badWarn = false, locked = false, enciphered = false, canDelete = false, installSizeMb = 12.5,
        ),
        DocRow(
            docId = "StrongsGreek", osisId = "StrongsGreek", abbreviation = "Strong", name = "Strong's Greek Dictionary",
            language = greek, repository = "CrossWire", category = DocCategory.DICTIONARY,
            installStatus = DocInstallStatus.UPGRADE_AVAILABLE, percentDone = 0, recommended = false,
            badWarn = false, locked = false, enciphered = false, canDelete = true, installSizeMb = 1.1,
        ),
        DocRow(
            docId = "ESV2011", osisId = "ESV2011", abbreviation = "ESV", name = "English Standard Version",
            language = english, repository = "CrossWire", category = DocCategory.BIBLE,
            installStatus = DocInstallStatus.NOT_INSTALLED, percentDone = 0, recommended = true,
            badWarn = false, locked = false, enciphered = false, canDelete = false, installSizeMb = 3.8,
        ),
        DocRow(
            docId = "NIV", osisId = "NIV", abbreviation = "NIV", name = "New International Version",
            language = english, repository = "Locked", category = DocCategory.BIBLE,
            installStatus = DocInstallStatus.INSTALLED, percentDone = 0, recommended = false,
            badWarn = false, locked = true, enciphered = true, canDelete = true, installSizeMb = 4.0,
        ),
    )

    /**
     * Round 17e-1 final-review fix (I1): a DocCategory.OTHER row, so [chooseDocument_grouped]
     * actually exercises the "uncategorized" header branch. Real-world equivalents are CrossWire's
     * DAILY_DEVOTIONS/GLOSSARY/QUESTIONABLE/ESSAYS/IMAGES documents, which DocCategoryMapping maps
     * to OTHER — grouping by TYPE on a real download list shows this header for that whole bucket.
     */
    private val otherCategoryRow = DocRow(
        docId = "DailyDevo", osisId = "DailyDevo", abbreviation = "Devo", name = "Daily Devotions",
        language = english, repository = "CrossWire", category = DocCategory.OTHER,
        installStatus = DocInstallStatus.NOT_INSTALLED, percentDone = 0, recommended = false,
        badWarn = false, locked = false, enciphered = false, canDelete = false, installSizeMb = 0.5,
    )

    private val languages = listOf(english, greek)

    private val typeFilters = listOf(
        DocTypeFilter.ALL to "All types",
        DocTypeFilter.BIBLE to "Bibles",
        DocTypeFilter.COMMENTARY to "Commentaries",
        DocTypeFilter.DICTIONARY to "Dictionaries",
        DocTypeFilter.GENERAL_BOOK to "General books",
        DocTypeFilter.MAPS to "Maps",
        DocTypeFilter.ADDON to "Add-ons",
    )

    private val resultCount = "5 documents"

    /**
     * Download-mode fixture: install sizes on every row, a variety of install states including
     * a NOT_INSTALLED row (renders the download affordance) and a BEING_INSTALLED row
     * (determinate progress bar + cancel at [downloadInstallingRows]).
     */
    private val downloadRows = listOf(
        DocRow(
            docId = "ESV2011", osisId = "ESV2011", abbreviation = "ESV", name = "English Standard Version",
            language = english, repository = "CrossWire", category = DocCategory.BIBLE,
            installStatus = DocInstallStatus.NOT_INSTALLED, percentDone = 0, recommended = true,
            badWarn = false, locked = false, enciphered = false, canDelete = false, installSizeMb = 3.8,
        ),
        DocRow(
            docId = "NET", osisId = "NET", abbreviation = "NET", name = "New English Translation",
            language = english, repository = "CrossWire", category = DocCategory.BIBLE,
            installStatus = DocInstallStatus.NOT_INSTALLED, percentDone = 0, recommended = false,
            badWarn = false, locked = false, enciphered = false, canDelete = false, installSizeMb = 5.6,
        ),
        DocRow(
            docId = "KJV", osisId = "KJV", abbreviation = "KJV", name = "King James Version",
            language = english, repository = "CrossWire", category = DocCategory.BIBLE,
            installStatus = DocInstallStatus.INSTALLED, percentDone = 0, recommended = false,
            badWarn = false, locked = false, enciphered = false, canDelete = true, installSizeMb = 4.2,
        ),
        DocRow(
            docId = "MHC", osisId = "MHC", abbreviation = "MHC", name = "Matthew Henry Commentary",
            language = english, repository = "CrossWire", category = DocCategory.COMMENTARY,
            installStatus = DocInstallStatus.NOT_INSTALLED, percentDone = 0, recommended = false,
            badWarn = false, locked = false, enciphered = false, canDelete = false, installSizeMb = 12.5,
        ),
        DocRow(
            docId = "StrongsGreek", osisId = "StrongsGreek", abbreviation = "Strong", name = "Strong's Greek Dictionary",
            language = greek, repository = "CrossWire", category = DocCategory.DICTIONARY,
            installStatus = DocInstallStatus.UPGRADE_AVAILABLE, percentDone = 0, recommended = false,
            badWarn = false, locked = false, enciphered = false, canDelete = true, installSizeMb = 1.1,
        ),
        DocRow(
            docId = "Josephus", osisId = "Josephus", abbreviation = "Jos", name = "Works of Josephus",
            language = english, repository = "CrossWire", category = DocCategory.GENERAL_BOOK,
            installStatus = DocInstallStatus.NOT_INSTALLED, percentDone = 0, recommended = false,
            badWarn = false, locked = false, enciphered = false, canDelete = false, installSizeMb = 8.9,
        ),
        DocRow(
            docId = "Maps", osisId = "Maps", abbreviation = "Maps", name = "Bible Maps",
            language = english, repository = "CrossWire", category = DocCategory.MAPS,
            installStatus = DocInstallStatus.NOT_INSTALLED, percentDone = 0, recommended = false,
            badWarn = false, locked = false, enciphered = false, canDelete = false, installSizeMb = 22.3,
        ),
    )

    /** Same as [downloadRows] but with the first NOT_INSTALLED row switched to a mid-download state. */
    private val downloadInstallingRows = downloadRows.mapIndexed { index, row ->
        if (index == 0) row.copy(installStatus = DocInstallStatus.BEING_INSTALLED, percentDone = 45) else row
    }

    private val downloadResultCount = "7 documents"

    /**
     * Round 8b: the marker slot beside the leading icon. Three rows -- recommended-only,
     * bad-only, and both markers on the same row -- all NOT_INSTALLED in download mode.
     */
    private val markerRows = listOf(
        DocRow(
            docId = "REC", osisId = "REC", abbreviation = "REC", name = "English Standard Version",
            language = english, repository = "CrossWire", category = DocCategory.BIBLE,
            installStatus = DocInstallStatus.NOT_INSTALLED, percentDone = 0, recommended = true,
            badWarn = false, locked = false, enciphered = false, canDelete = false, installSizeMb = 3.8,
        ),
        DocRow(
            docId = "BAD", osisId = "BAD", abbreviation = "BAD", name = "Suspect Bible Edition",
            language = english, repository = "CrossWire", category = DocCategory.BIBLE,
            installStatus = DocInstallStatus.NOT_INSTALLED, percentDone = 0, recommended = false,
            badWarn = true, locked = false, enciphered = false, canDelete = false, installSizeMb = 4.1,
        ),
        DocRow(
            docId = "BOTH", osisId = "BOTH", abbreviation = "BOTH", name = "Recommended But Flagged",
            language = english, repository = "CrossWire", category = DocCategory.BIBLE,
            installStatus = DocInstallStatus.NOT_INSTALLED, percentDone = 0, recommended = true,
            badWarn = true, locked = false, enciphered = false, canDelete = false, installSizeMb = 2.9,
        ),
    )

    @Composable
    private fun screen(
        loading: Boolean = false,
        displayed: List<DocRow> = rows,
        groupBy: DocGroupBy = DocGroupBy.NONE,
        count: String = resultCount,
        selectionMode: Boolean = false,
        selectedIds: Set<String> = emptySet(),
        deleteVisible: Boolean = false,
        unlockVisible: Boolean = false,
        downloadMode: Boolean = false,
        isRefreshing: Boolean = false,
        selectedLanguage: LangOption? = null,
        selectedTypeFilter: DocTypeFilter = DocTypeFilter.ALL,
        query: String = "",
        searchModeActive: Boolean = false,
    ) = DocumentSelectionScreen(
        title = if (downloadMode) "Download documents" else "Documents",
        downloadMode = downloadMode,
        loading = loading,
        isRefreshing = isRefreshing,
        onRefresh = if (downloadMode) ({}) else null,
        grouped = groupDocuments(displayed, groupBy),
        languages = languages,
        selectedLanguage = selectedLanguage,
        typeFilters = typeFilters,
        selectedTypeFilter = selectedTypeFilter,
        query = query,
        resultCount = count,
        selectionMode = selectionMode,
        selectedIds = selectedIds,
        error = null,
        topBarActions = {},
        onQueryChange = {},
        searchModeActive = searchModeActive,
        onOpenSearch = {},
        onCloseSearch = {},
        onLanguageChange = {},
        onTypeFilterChange = {},
        arrangement = defaultArrangement(DocSortKey.entries.toSet()).copy(groupBy = groupBy),
        groupKeys = DocGroupBy.entries.toList(),
        repositories = emptyList(),
        rememberArrangement = false,
        arrangementIsDefault = groupBy == DocGroupBy.NONE,
        onMoveSort = { _, _ -> },
        onToggleSortDirection = {},
        onGroupByChange = {},
        onRepositoryChange = {},
        onRememberChange = {},
        onResetArrangement = {},
        onRowClick = {},
        onRowLongClick = {},
        onDownload = {},
        onCancel = {},
        onSelectionAbout = {},
        onSelectionDelete = {},
        onSelectionDeleteIndex = {},
        onSelectionUnlock = {},
        unlockVisible = unlockVisible,
        deleteVisible = deleteVisible,
        onDismissError = {},
        onNavigateUp = {},
        onExitSelection = {},
    )

    @Test fun chooseDocument_populated() {
        captureMatrix("ChooseDocument", "populated") { screen() }
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun chooseDocument_populated_rtl() {
        captureRtl("ChooseDocument", "populated") { screen() }
    }

    @Test fun chooseDocument_selection() {
        captureGolden("ChooseDocument", "selection", EDGE_MODE) {
            screen(
                selectionMode = true,
                selectedIds = setOf(rows.first().docId),
                deleteVisible = true,
                unlockVisible = false,
            )
        }
    }

    // Search-mode top app bar: non-empty query (so the clear/close action icon is visible),
    // displayed pre-filtered to what that query would actually match.
    @Test fun chooseDocument_searchMode() {
        captureMatrix("ChooseDocument", "searchMode") {
            screen(
                displayed = rows.filter { it.name.contains("King") },
                count = "1 document",
                query = "King",
                searchModeActive = true,
            )
        }
    }

    @Test fun chooseDocument_empty() {
        captureGolden("ChooseDocument", "empty", EDGE_MODE) {
            screen(displayed = emptyList(), count = "0 documents")
        }
    }

    @Test fun chooseDocument_loading() {
        captureGolden("ChooseDocument", "loading", EDGE_MODE) {
            screen(loading = true, displayed = emptyList())
        }
    }

    /**
     * Grouped by TYPE over a fixture holding three categories (BIBLE, COMMENTARY, OTHER): guards
     * the sticky header rendering added in round 17e-1 — three distinct headers, rows filed under
     * the right one, an opaque header background rather than one that lets scrolled-under row text
     * show through, and (final-review fix I1) the OTHER category's header reading "Other" rather
     * than the misleading "All". `heightDp = 1024` keeps all three headers and their rows in frame.
     */
    @Test fun chooseDocument_grouped() {
        captureGolden("ChooseDocument", "grouped", EDGE_MODE, heightDp = 1024) {
            screen(
                displayed = rows.filter { it.category == DocCategory.BIBLE || it.category == DocCategory.COMMENTARY } +
                    otherCategoryRow,
                groupBy = DocGroupBy.TYPE,
                count = "5 documents",
            )
        }
    }

    @Test fun download_populated() {
        captureMatrix("Download", "populated") {
            screen(downloadMode = true, displayed = downloadRows, count = downloadResultCount)
        }
    }

    /**
     * Long language name + long type label: the chips must ellipsize and the result count must
     * stay fully visible. The inverse (a trailing weighted Spacer clipping the count) is the
     * layout trap this asserts against.
     */
    @Test fun download_filtersLongLanguageName() {
        captureGolden("Download", "filtersLongLanguageName", EDGE_MODE) {
            screen(
                downloadMode = true,
                displayed = downloadRows,
                count = downloadResultCount,
                selectedLanguage = LangOption("pt-BR", "Portuguese (Brazil)", "pt"),
            )
        }
    }

    /**
     * A non-ALL type selected: the type chip's leading icon is now a real category icon
     * (TypeFilterIcon's non-null branch), not the empty ALL slot. Guards against the icon being
     * sized for the sheet's ListItem (24dp) instead of the chip (AssistChipDefaults.IconSize,
     * 18dp) — nothing else in this suite ever selects a non-ALL type, so without this test the
     * chip-vs-sheet icon-size mismatch was invisible to every check that ran.
     */
    @Test fun download_selectedType() {
        captureGolden("Download", "selectedType", EDGE_MODE) {
            screen(
                downloadMode = true,
                displayed = downloadRows,
                count = downloadResultCount,
                selectedTypeFilter = DocTypeFilter.MAPS,
            )
        }
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun download_populated_rtl() {
        captureRtl("Download", "populated") {
            screen(downloadMode = true, displayed = downloadRows, count = downloadResultCount)
        }
    }

    @Test fun download_installing() {
        captureGolden("Download", "installing", EDGE_MODE) {
            screen(downloadMode = true, displayed = downloadInstallingRows, count = downloadResultCount)
        }
    }

    @Test fun download_refreshing() {
        captureGolden("Download", "refreshing", EDGE_MODE) {
            screen(downloadMode = true, displayed = downloadRows, count = downloadResultCount, isRefreshing = true)
        }
    }

    /** Round 8b: the marker slot beside the leading icon. `recommended` was only ever incidental
     *  inside `download_populated`, and `badWarn` had never been captured at all because nothing
     *  rendered it. */
    @Test fun download_markers() {
        captureMatrix("Download", "markers") {
            screen(downloadMode = true, displayed = markerRows, count = "3 documents")
        }
    }

    /** Pins the star's ABSENCE in selection mode -- the reported defect was the star landing on the
     *  checkbox, so this is the golden that would catch a regression of that fix. The bad-document
     *  marker must still be visible here. */
    @Test fun download_markersSelection() {
        captureGolden("Download", "markersSelection", EDGE_MODE) {
            screen(
                downloadMode = true,
                displayed = markerRows,
                count = "3 documents",
                selectionMode = true,
                selectedIds = setOf("REC", "BAD"),
                deleteVisible = false,
            )
        }
    }
}
