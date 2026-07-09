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

    @Composable
    private fun screen(
        loading: Boolean = false,
        displayed: List<DocRow> = rows,
        count: String = resultCount,
        selectionMode: Boolean = false,
        selectedIds: Set<String> = emptySet(),
        deleteVisible: Boolean = false,
        unlockVisible: Boolean = false,
    ) = DocumentSelectionScreen(
        title = "Documents",
        downloadMode = false,
        loading = loading,
        isRefreshing = false,
        onRefresh = null,
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
}
