package net.bible.sharedui.poc

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import net.bible.sharedui.bookmark.BookmarksScreen
import net.bible.sharedcore.bookmark.BookmarkSortMode
import net.bible.sharedui.history.HistoryScreen
import net.bible.sharedui.reading.ReadingViewScreen
import net.bible.sharedui.settings.AppSettingsScreen
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.theme.AbTheme
import net.bible.sharedui.webview.LocalBibleWebView

/** Receives a window id each time a placeholder pane is created; lets tests count pane (re)creations. */
val LocalPocPaneCreated = staticCompositionLocalOf<(String) -> Unit> { {} }

/**
 * The I0 PoC app: reading view (1-3 split panes), history, bookmarks and settings, all driven by
 * fixtures. Pane controllers live in a map outside the nav graph so they outlive split changes
 * and route navigation.
 */
@Composable
fun IosPocApp(
    scenario: PocScenario,
    documentJson: String,
    darkTheme: Boolean,
    onPaneCreated: (String) -> Unit = {},
) {
    AbTheme(darkTheme = darkTheme) {
        Surface(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalPocPaneCreated provides onPaneCreated) {
                PocContent(scenario, documentJson, darkTheme)
            }
        }
    }
}

@Composable
private fun PocContent(scenario: PocScenario, documentJson: String, darkTheme: Boolean) {
    var windowCount by rememberSaveable { mutableStateOf(scenario.windowCount) }
    var active by rememberSaveable { mutableStateOf("w1") }
    val strings = LocalStrings.current
    // Keyed on documentJson only: a theme flip must not re-create controllers (and so panes).
    val controllers = remember(documentJson) { mutableMapOf<String, PocBibleViewController>() }
    // Composition-time getOrPut: a controller is created once per window id, on first use.
    fun controller(id: String) = controllers.getOrPut(id) { PocBibleViewController(id, documentJson, darkTheme) }
    controllers.values.forEach { it.darkTheme = darkTheme }

    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route ?: scenario.startRoute
    val activeVerse by controller(active).currentVerse.collectAsState()
    val title = controller(active).titleFor(activeVerse)

    val toggleSplit = {
        windowCount = if (windowCount == 3) 2 else windowCount + 1
        // The active window must still exist after the count shrinks.
        if (active.removePrefix("w").toIntOrNull()?.let { it > windowCount } != false) active = "w1"
    }
    val open = { r: String -> nav.navigate(r) { launchSingleTop = true } }

    Column(Modifier.fillMaxSize()) {
        NavHost(nav, startDestination = scenario.startRoute, modifier = Modifier.weight(1f)) {
            composable("reading") {
                ReadingViewScreen(
                    layout = pocLayout(windowCount, active),
                    toolbar = pocToolbar(title),
                    toolbarIcons = pocToolbarIcons(),
                    toolbarCallbacks = pocToolbarCallbacks(
                        onHome = { open("history") },
                        onSearch = { open("bookmarks") },
                        onWorkspace = toggleSplit,
                        onOverflow = { open("settings") },
                    ),
                    fullScreen = false,
                    onWindowActivated = { active = it },
                    onSeparatorCommitted = { _, _, _, _ -> },
                    pane = { id -> LocalBibleWebView.current(controller(id), Modifier.fillMaxSize()) },
                )
            }
            composable("history") {
                HistoryScreen(POC_LABEL_HISTORY, pocHistory, null, onSelect = {}, onDismissError = {})
            }
            composable("bookmarks") {
                var sort by remember { mutableStateOf(BookmarkSortMode.BIBLE_ORDER) }
                BookmarksScreen(
                    title = strings.bookmarks, rows = pocBookmarks, filterLabels = emptyList(), selectedFilterIndex = 0,
                    sortMode = sort, searchText = "", showNotes = false, selection = emptySet(),
                    expandedIds = emptySet(), loading = false, onSelectFilter = {},
                    onCycleSort = { sort = sort.next() }, onSearch = {}, searchModeActive = false,
                    onOpenSearch = {}, onCloseSearch = {}, onToggleShowNotes = {}, onRowClick = { _, _ -> },
                    onRowLongClick = {}, onToggleSelected = {}, onToggleExpand = {}, onAssignSelected = {},
                    onDeleteSelected = {}, onClearSelection = {}, onManageLabels = {}, onExportCsv = {},
                    onImportCsv = {}, onUp = { nav.popBackStack() },
                )
            }
            composable("settings") {
                AppSettingsScreen(
                    state = pocSettings, onUp = { nav.popBackStack() }, onSwitch = { _, _ -> },
                    onListChoice = { _, _ -> }, onTextInput = { _, _ -> }, onSliderChange = { _, _ -> },
                    onMultiSelectChange = { _, _ -> }, onNavigate = {}, onReset = {},
                    resetContentDescription = strings.resetToDefault,
                )
            }
        }
        // Stable automation targets for XCUITest and desktop tests (always visible, outside the nav graph).
        Row(Modifier.fillMaxWidth()) {
            Text(title, Modifier.testTag("reading-title").semantics { stateDescription = active })
            if (route != "reading") TextButton({ nav.popBackStack() }, Modifier.testTag("poc-back")) { Text(strings.menuBack) }
            TextButton(toggleSplit, Modifier.testTag("poc-split-toggle")) { Text(POC_LABEL_SPLIT) }
            TextButton({ open("bookmarks") }, Modifier.testTag("poc-open-bookmarks")) { Text(strings.bookmarks) }
            TextButton({ open("history") }, Modifier.testTag("poc-open-history")) { Text(POC_LABEL_HISTORY) }
            TextButton({ open("settings") }, Modifier.testTag("poc-open-settings")) { Text(POC_LABEL_SETTINGS) }
        }
    }
}
