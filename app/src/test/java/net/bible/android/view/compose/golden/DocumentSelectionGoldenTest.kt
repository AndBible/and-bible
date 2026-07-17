package net.bible.android.view.compose.golden

import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedcore.navigation.DocCategory
import net.bible.sharedcore.navigation.DocInstallStatus
import net.bible.sharedcore.navigation.DocRow
import net.bible.sharedcore.navigation.DocTypeFilter
import net.bible.sharedcore.navigation.LangOption
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

    @Composable
    private fun screen(
        loading: Boolean = false,
        displayed: List<DocRow> = rows,
        count: String = resultCount,
        selectionMode: Boolean = false,
        selectedIds: Set<String> = emptySet(),
        deleteVisible: Boolean = false,
        unlockVisible: Boolean = false,
        downloadMode: Boolean = false,
        isRefreshing: Boolean = false,
        filtersExpanded: Boolean = false,
    ) = DocumentSelectionScreen(
        title = if (downloadMode) "Download documents" else "Documents",
        downloadMode = downloadMode,
        loading = loading,
        isRefreshing = isRefreshing,
        onRefresh = if (downloadMode) ({}) else null,
        displayed = displayed,
        languages = languages,
        selectedLanguage = null,
        typeFilters = typeFilters,
        selectedTypeFilter = DocTypeFilter.ALL,
        query = "",
        resultCount = count,
        selectionMode = selectionMode,
        selectedIds = selectedIds,
        error = null,
        topBarActions = {},
        onQueryChange = {},
        onLanguageChange = {},
        onTypeFilterChange = {},
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
        initiallyFiltersExpanded = filtersExpanded,
    )

    @Test fun chooseDocument_populated() {
        captureMatrix("ChooseDocument", "populated") { screen() }
    }

    /** Filters expanded inline (F13): reveals the language picker + type dropdown below the summary row. */
    @Test fun chooseDocument_filtersExpanded() {
        captureGolden("ChooseDocument", "filtersExpanded", EDGE_MODE) {
            screen(filtersExpanded = true)
        }
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

    @Test fun download_populated() {
        captureMatrix("Download", "populated") {
            screen(downloadMode = true, displayed = downloadRows, count = downloadResultCount)
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
}
