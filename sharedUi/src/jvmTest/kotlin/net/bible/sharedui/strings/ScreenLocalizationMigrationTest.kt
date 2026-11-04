package net.bible.sharedui.strings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import net.bible.sharedcore.backup.BackupState
import net.bible.sharedcore.bookmark.BookmarkSortMode
import net.bible.sharedcore.navigation.*
import net.bible.sharedcore.readingplan.PlanEntry
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.settings.SettingsScreenState
import net.bible.sharedcore.settings.SyncSettingsUiState
import net.bible.sharedui.backup.BackupRestoreScreen
import net.bible.sharedui.bookmark.BookmarksScreen
import net.bible.sharedui.navigation.DocumentSelectionScreen
import net.bible.sharedui.readingplan.ReadingPlanSelectorScreen
import net.bible.sharedui.settings.AppSettingsScreen
import net.bible.sharedui.settings.SyncSettingsScreen
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/** Pins screen-local mirroring and translated chrome; native host language persistence is a separate boundary. */
@OptIn(ExperimentalTestApi::class)
class ScreenLocalizationMigrationTest {
    private enum class Screen { SETTINGS, DOWNLOAD, BOOKMARKS, PLANS, BACKUP, SYNC, LABELS }

    @Composable
    private fun ScreenContent(screen: Screen, s: Strings) {
        when (screen) {
            Screen.SETTINGS -> AppSettingsScreen(
                SettingsScreenState(s.settings, listOf(SettingsItem.SwitchRow("screen_keep_on_pref", "Fixture setting", checked = true))),
                onUp = {}, onSwitch = { _, _ -> }, onListChoice = { _, _ -> }, onTextInput = { _, _ -> },
                onSliderChange = { _, _ -> }, onMultiSelectChange = { _, _ -> }, onNavigate = {}, onReset = {},
                resetContentDescription = s.resetSettings,
            )
            Screen.DOWNLOAD -> DocumentSelectionScreen(
                title = s.downloadTitle, downloadMode = true, loading = false, isRefreshing = false, onRefresh = {},
                grouped = emptyList(), languages = emptyList(), selectedLanguage = null,
                typeFilters = emptyList(), selectedTypeFilter = DocTypeFilter.ALL, query = "", resultCount = "Fixture",
                selectionMode = false, selectedIds = emptySet(), error = null, dialog = DocumentSelectionDialog.None,
                topBarActions = {}, onQueryChange = {}, searchModeActive = false, onOpenSearch = {}, onCloseSearch = {},
                onLanguageChange = {}, onTypeFilterChange = {}, arrangement = DocArrangement(emptyList()),
                groupKeys = emptyList(), repositories = emptyList(), rememberArrangement = false, arrangementIsDefault = true,
                onMoveSort = { _, _ -> }, onToggleSortDirection = {}, onGroupByChange = {}, onRepositoryChange = {},
                onRememberChange = {}, onResetArrangement = {}, onRowClick = {}, onRowLongClick = {}, onDownload = {},
                onCancel = {}, onSelectionAbout = {}, onSelectionDelete = {}, onSelectionDeleteIndex = {},
                onSelectionUnlock = {}, unlockVisible = false, deleteVisible = false, onDismissError = {},
                onConfirmDialog = {}, onDismissDialog = {}, onConfirmProceed = {}, onDismissProceed = {},
                onNavigateUp = {}, onExitSelection = {},
            )
            Screen.BOOKMARKS -> BookmarksScreen(
                title = s.bookmarksAndMynotesTitle, rows = emptyList(), filterLabels = emptyList(), selectedFilterIndex = 0,
                sortMode = BookmarkSortMode.BIBLE_ORDER, searchText = "", showNotes = false, selection = emptySet(),
                expandedIds = emptySet(), loading = false, onSelectFilter = {}, onCycleSort = {}, onSearch = {},
                searchModeActive = false, onOpenSearch = {}, onCloseSearch = {}, onToggleShowNotes = {},
                onRowClick = { _, _ -> }, onRowLongClick = {}, onToggleSelected = {}, onToggleExpand = {},
                onAssignSelected = {}, onDeleteSelected = {}, onClearSelection = {}, onManageLabels = {},
                onExportCsv = {}, onImportCsv = {}, onUp = {},
            )
            Screen.PLANS -> ReadingPlanSelectorScreen(
                title = s.readingPlanSelectorTitle, plans = listOf(PlanEntry("fixture", "Fixture", "Fixture description")),
                duplicateWarning = false, error = null, onSelect = {}, onReset = {}, onDismissError = {},
                onDismissDuplicate = {}, onNavigateUp = {},
            )
            Screen.LABELS -> net.bible.sharedui.bookmark.ManageLabelsScreen(
                title = s.manageLabelsLabel, rows = emptyList(), mode = net.bible.sharedcore.bookmark.ManageLabelsMode.STUDYPAD,
                styleTagsVisible = false, searchText = "", searchMode = net.bible.sharedcore.bookmark.SearchMode.NAME_START,
                onSearch = {}, onSetSearchMode = {}, filters = emptySet(), onToggleFilter = {}, searchModeActive = false,
                onCloseSearch = {}, onRowClick = {}, onRowLongClick = {}, onToggleChecked = {}, onToggleFavourite = {},
                onSetPrimary = {}, onToggleAutoAssign = {}, onUp = {}, iconSlot = { _, _ -> }, actions = {}, searchActions = {},
            )
            Screen.BACKUP -> BackupRestoreScreen(BackupState(), { _, _ -> }, {}, {}, {}, {}, {}, {})
            Screen.SYNC -> SyncSettingsScreen(
                SyncSettingsUiState(screen = SettingsScreenState(s.cloudSyncTitle, emptyList())), onUp = {},
                onSwitch = { _, _ -> }, onListChoice = { _, _ -> }, onTextInput = { _, _ -> }, onNavigate = {},
                onConfirmReset = {}, onConfirmEnableDocuments = {}, onConfirmForgetCertificate = {}, onDismissDialog = {},
            )
        }
    }

    private inline fun <reified T> host(): T = java.lang.reflect.Proxy.newProxyInstance(
        T::class.java.classLoader, arrayOf(T::class.java),
    ) { _, method, _ ->
        when (method.returnType) {
            Boolean::class.javaPrimitiveType -> true
            String::class.java -> "Fixture"
            List::class.java -> emptyList<Any>()
            else -> null
        }
    } as T

    private fun labels(node: androidx.compose.ui.semantics.SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()

    @Test fun fourFeatureScreensMirrorTheirBackControlAndContentInRtl() {
        for (screen in listOf(Screen.SETTINGS, Screen.DOWNLOAD, Screen.BOOKMARKS, Screen.PLANS)) {
            fun positions(direction: LayoutDirection): Pair<Float, Float> {
                var result = 0f to 0f
                runComposeUiTest {
                    val holder = net.bible.sharedui.assembly.HostStringTableHolder(net.bible.sharedui.assembly.hostStringTable("en"))
                    holder.switchTo(if (direction == LayoutDirection.Rtl) "he" else "en")
                    setContent {
                        val language = holder.current.tag
                        ProvideLanguageLayoutDirection(language) {
                            CompositionLocalProvider(LocalStrings provides iosStrings(language)) {
                                AbTheme(darkTheme = false) { ScreenContent(screen, iosStrings(language)) }
                            }
                        }
                    }
                    val language = if (direction == LayoutDirection.Rtl) "he" else "en"
                    val back = onNodeWithContentDescription(iosStrings(language).settingsEditorBack).fetchSemanticsNode().boundsInRoot.center.x
                    val text = when (screen) {
                        Screen.SETTINGS -> "Fixture setting"
                        Screen.DOWNLOAD -> "Fixture"
                        Screen.PLANS -> "Fixture description"
                        else -> iosStrings(language).emptyList
                    }
                    val node = onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
                    val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                    node.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
                    val content = node.boundsInRoot.left + layouts.single().getBoundingBox(0).center.x
                    val width = onRoot().fetchSemanticsNode().boundsInRoot.width
                    if (direction == LayoutDirection.Ltr) assertTrue(back / width < 0.25f, "$screen: LTR Back must sit at left edge")
                    else assertTrue(back / width > 0.75f, "$screen: RTL Back must sit at right edge")
                    result = back to content
                }
                return result
            }
            val ltr = positions(LayoutDirection.Ltr)
            val rtl = positions(LayoutDirection.Rtl)
            assertTrue(rtl.first > ltr.first + 200f, "$screen: Back must move to the right leading edge: $ltr -> $rtl")
            if (screen != Screen.BOOKMARKS) {
                assertTrue(rtl.second > ltr.second + 52f, "$screen: content must move right by over 5% in RTL: $ltr -> $rtl")
            }
        }
    }

    @Test fun finnishReadingDrawerAndOverflowUseTheShippedTranslations() = runComposeUiTest {
        val keyed = net.bible.sharedui.strings.keyed.KeyedReadingMenuStrings(net.bible.sharedui.assembly.hostStringTable("fi"))
        val drawer = net.bible.sharedui.reading.toolbar.ReadingDrawerMenu(
            host<net.bible.sharedui.reading.toolbar.ReadingDrawerHost>(), { keyed },
        ).build()
        val overflow = net.bible.sharedui.reading.toolbar.ReadingOverflowController(
            host<net.bible.sharedui.reading.toolbar.ReadingOverflowHost>(), { keyed },
        ).build()
        var showDrawer by androidx.compose.runtime.mutableStateOf(true)
        setContent {
            CompositionLocalProvider(LocalStrings provides iosStrings("fi")) {
                AbTheme(darkTheme = false) {
                    if (showDrawer) net.bible.sharedui.reading.ReadingDrawerContent(drawer, { null }, {})
                    else net.bible.sharedui.reading.ReadingOverflowMenuRows(overflow, {}, { null })
                }
            }
        }
        for (label in listOf("Valitse teos", "Historia", "Opintoalustat", "Varmuuskopioi & palauta", "Laitteiden synkronointi")) {
            onNodeWithText(label).performScrollTo().assertIsDisplayed()
        }
        for (label in listOf("Choose Document", "History", "Study Pads", "Backup & Restore", "Device synchronization")) {
            onNodeWithText(label).assertDoesNotExist()
        }
        runOnIdle { showDrawer = false }
        onNodeWithText("Yötila").assertExists()
        onNodeWithText("Night mode").assertDoesNotExist()
    }

    @Test fun finnishFeatureScreensExposeTranslationsWithoutEnglishChrome() {
        val en = iosStrings("en")
        val fi = iosStrings("fi")
        // The same resource-derived exclusion as the Swift walk, without copying its generated word list.
        val translatedEnglish = Strings::class.java.methods.filter { it.parameterCount == 0 && it.returnType == String::class.java }
            .mapNotNull { method ->
                val english = method.invoke(en) as String
                val finnish = method.invoke(fi) as String
                english.takeIf { it.isNotBlank() && it != finnish }
            }.toSet()
        for (screen in Screen.entries.filter { it != Screen.PLANS }) {
            runComposeUiTest {
                setContent {
                    CompositionLocalProvider(LocalStrings provides fi) {
                        AbTheme(darkTheme = false) { ScreenContent(screen, fi) }
                    }
                }
                val title = when (screen) {
                    Screen.SETTINGS -> "Sovelluksen asetukset"
                    Screen.DOWNLOAD -> "Lataa teoksia"
                    Screen.BOOKMARKS -> "Kirjanmerkit & Omat Merkinnät"
                    Screen.BACKUP -> "Varmuuskopioi & palauta"
                    Screen.SYNC -> "Laitteiden synkronointi"
                    Screen.LABELS -> "Hallitse kirjanmerkkiryhmiä"
                    else -> error("not in Finnish walk")
                }
                assertTrue(onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty(), "$screen title must be Finnish")
                val exposed = onAllNodes(SemanticsMatcher("any") { true }, useUnmergedTree = true)
                    .fetchSemanticsNodes().flatMap(::labels)
                assertTrue((exposed.toSet() intersect translatedEnglish).isEmpty(), "$screen contains English chrome: ${exposed.toSet() intersect translatedEnglish}")
            }
        }
    }
}
