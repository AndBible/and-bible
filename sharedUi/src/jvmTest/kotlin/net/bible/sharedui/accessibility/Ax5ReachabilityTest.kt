package net.bible.sharedui.accessibility

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue
import net.bible.sharedcore.bookmark.*
import net.bible.sharedcore.reading.ToolbarState
import net.bible.sharedcore.window.WindowPaneMenuItem
import net.bible.sharedui.bookmark.BookmarksScreen
import net.bible.sharedui.bookmark.ManageLabelsScreen
import net.bible.sharedui.components.AbQuickSheetContent
import net.bible.sharedui.components.LocalBarFontScaleCap
import net.bible.sharedui.reading.*
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme

/** Portable geometry only: UIKit trait propagation, native hit testing and window insets stay in Swift. */
@OptIn(ExperimentalTestApi::class)
class Ax5ReachabilityTest {
    private val strings = iosStrings("en")

    private fun ComposeUiTest.phoneContent(content: @Composable () -> Unit) {
        setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, 53f / 17f), LocalStrings provides strings,
                LocalBarFontScaleCap provides 1.35f,
            ) {
                AbTheme(darkTheme = false, disableAnimations = true) {
                    Box(Modifier.size(390.dp, 844.dp)) { content() }
                }
            }
        }
    }

    @Test fun bookmarksAndManageLabelsActionsRemainOnScreenAtAx5() = runSkikoComposeUiTest(size = androidx.compose.ui.geometry.Size(390f, 844f)) {
        var managingLabels by mutableStateOf(false)
        phoneContent {
            if (managingLabels) {
                ManageLabelsScreen(
                    title = strings.labelsTitle, rows = listOf(ManageLabelsRow.Item(
                        LabelItem("label", "Important", 0xFF336699.toInt(), false, false, false, null),
                        checked = false, isAutoAssign = false, isPrimary = false, highlighted = false,
                    )), mode = ManageLabelsMode.WORKSPACE, styleTagsVisible = false,
                    searchText = "", searchMode = SearchMode.NAME_START, onSearch = {}, onSetSearchMode = {},
                    filters = emptySet(), onToggleFilter = {}, searchModeActive = false, onCloseSearch = {},
                    onRowClick = {}, onRowLongClick = {}, onToggleChecked = {}, onToggleFavourite = {},
                    onSetPrimary = {}, onToggleAutoAssign = {}, onUp = { managingLabels = false }, iconSlot = { _, _ -> },
                    actions = {}, searchActions = {},
                )
            } else {
                BookmarksScreen(
                    title = strings.bookmarksAndMynotesTitle, rows = emptyList(), filterLabels = emptyList(), selectedFilterIndex = 0,
                    sortMode = BookmarkSortMode.BIBLE_ORDER, searchText = "", showNotes = false,
                    selection = emptySet(), expandedIds = emptySet(), loading = false,
                    onSelectFilter = {}, onCycleSort = {}, onSearch = {}, searchModeActive = false,
                    onOpenSearch = {}, onCloseSearch = {}, onToggleShowNotes = {}, onRowClick = { _, _ -> },
                    onRowLongClick = {}, onToggleSelected = {}, onToggleExpand = {}, onAssignSelected = {},
                    onDeleteSelected = {}, onClearSelection = {}, onManageLabels = { managingLabels = true },
                    onExportCsv = {}, onImportCsv = {}, onUp = {},
                )
            }
        }
        inside(onNodeWithText(strings.bookmarksAndMynotesTitle), "Bookmarks title")
        inside(onNodeWithContentDescription(strings.settingsEditorBack), "Bookmarks Back")
        inside(onNodeWithContentDescription(strings.sortByBibleBookLabel), "Sort")
        inside(onNodeWithText(strings.emptyList), "Empty list")
        val manage = onNodeWithContentDescription(strings.manageLabelsLabel)
        inside(manage, "Manage labels action")
        manage.performClick()
        inside(onNodeWithText("Important"), "Manage labels row")
        val back = onNodeWithContentDescription(strings.settingsEditorBack)
        inside(back, "Manage labels Back")
        back.performClick()
        onNodeWithText(strings.bookmarksAndMynotesTitle).assertIsDisplayed()
    }

    @Test fun readingToolbarTitleMenuAndMoreDoNotOverlapAtAx5() = runSkikoComposeUiTest(size = androidx.compose.ui.geometry.Size(390f, 844f)) {
        val painter = ColorPainter(Color.Gray)
        var home = false
        var overflow = false
        val callbacks = ReadingToolbarCallbacks(
            { home = true }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, { overflow = true },
        )
        phoneContent {
            ReadingToolbar(
                ToolbarState("John 3", "KJV", false, false, false, false, 0, false, false, true, null, false),
                ReadingToolbarIcons(painter, painter, painter, painter, painter, painter, painter, painter, painter), callbacks,
            )
        }
        val title = onNodeWithText("John 3")
        val menu = onNodeWithContentDescription(strings.menu)
        val more = onNodeWithContentDescription(strings.readingToolbarOptions)
        val controls = listOf(title, menu, more)
        controls.forEach { inside(it, "toolbar control") }
        val bounds = controls.map { it.fetchSemanticsNode().boundsInRoot }
        bounds.forEachIndexed { i, first -> bounds.drop(i + 1).forEach { second ->
            assertTrue(!first.overlaps(second), "toolbar controls overlap: $first and $second")
        } }
        menu.performClick()
        more.performClick()
        assertTrue(home && overflow, "Menu and More remain actionable")
    }

    @Test fun paneMenuActionFitsAndCanBeClickedAtAx5() = runSkikoComposeUiTest(size = androidx.compose.ui.geometry.Size(390f, 844f)) {
        var selected = false
        phoneContent {
            WindowPaneMenu(listOf(WindowPaneMenuItem("max", "Maximise")), true, { selected = it == "max" }, {})
        }
        val maximise = onNodeWithText("Maximise")
        inside(maximise, "Pane Maximise")
        maximise.performClick()
        assertTrue(selected)
    }

    @Test fun quickSheetCloseFitsAndCanBeClickedAtAx5() = runSkikoComposeUiTest(size = androidx.compose.ui.geometry.Size(390f, 844f)) {
        var closed = false
        phoneContent { AbQuickSheetContent(title = "Choose a passage from this deliberately long document name", onClose = { closed = true }, body = {}) }
        val close = onNodeWithContentDescription(strings.settingsEditorClose)
        inside(close, "Quick sheet Close")
        close.performClick()
        assertTrue(closed)
    }

    private fun inside(node: SemanticsNodeInteraction, name: String) {
        node.assertIsDisplayed()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.left >= 0f && bounds.right <= 390f && bounds.top >= 0f && bounds.bottom <= 844f,
            "$name must fit within 390x844 at AX5: $bounds")
    }
}
