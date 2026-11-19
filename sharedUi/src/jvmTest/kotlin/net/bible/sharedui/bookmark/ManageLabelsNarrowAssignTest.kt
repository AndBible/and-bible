package net.bible.sharedui.bookmark

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.bookmark.LabelFilter
import net.bible.sharedcore.bookmark.LabelItem
import net.bible.sharedcore.bookmark.ManageLabelsMode
import net.bible.sharedcore.bookmark.ManageLabelsRow
import net.bible.sharedcore.bookmark.SearchMode
import net.bible.sharedcore.docs.TutorialVideos
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Issue #4153: in ASSIGN mode (picking labels for a bookmark) on a phone-width screen the three
 * trailing toggles (auto-assign, favourite, primary) left the label name about a third of the row.
 * Narrow ASSIGN drops them (they stay reachable in the label's editor), WORKSPACE keeps them at any
 * width, and a long name may take two lines instead of being cut after one.
 */
@OptIn(ExperimentalTestApi::class)
class ManageLabelsNarrowAssignTest {
    private val strings = iosStrings("en")

    private fun item(id: String, name: String, checked: Boolean) = ManageLabelsRow.Item(
        LabelItem(id, name, 0xFF3366CC.toInt(), favourite = false, isUnlabeled = false, isSpecial = false, customIcon = null),
        checked = checked, isAutoAssign = false, isPrimary = checked, highlighted = false,
    )

    private val longName = "A very long label name that certainly does not fit on a single line of a phone screen"

    private fun ComposeUiTest.show(
        mode: ManageLabelsMode, width: Dp, search: Boolean = false, help: Boolean = false,
        widthSource: () -> Dp = { width }, initialFilters: Set<LabelFilter> = emptySet(),
    ) {
        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                AbTheme(darkTheme = false) {
                    Box(Modifier.width(widthSource())) {
                        var activeFilters by remember { mutableStateOf(initialFilters) }
                        var rowTogglesShown by remember { mutableStateOf(true) }
                        ManageLabelsScreen(
                            title = "Labels",
                            rows = listOf(item("1", longName, checked = true), item("2", "Short", checked = false)),
                            mode = mode,
                            styleTagsVisible = false,
                            searchText = "", searchMode = SearchMode.NAME_START, onSearch = {}, onSetSearchMode = {},
                            filters = activeFilters,
                            onToggleFilter = { activeFilters = if (it in activeFilters) activeFilters - it else activeFilters + it },
                            searchModeActive = search, onCloseSearch = {},
                            onRowClick = {}, onRowLongClick = {}, onToggleChecked = {}, onToggleFavourite = {},
                            onSetPrimary = {}, onToggleAutoAssign = {}, onUp = {},
                            iconSlot = { _, _ -> },
                            actions = {
                                var helpOpen by remember { mutableStateOf(false) }
                                if (help) TextButton(onClick = { helpOpen = true }) { Text("Help") }
                                if (helpOpen) ManageLabelsHelpDialog(
                                    mode = mode, rowTogglesShown = rowTogglesShown,
                                    title = "Help", scopeSentence = null,
                                    videoEpisode = TutorialVideos.Episode.TUTORIAL_09_LABELS_AND_HIGHLIGHTS,
                                    onDismiss = { helpOpen = false },
                                )
                            }, searchActions = {},
                            onRowTogglesShownChange = { rowTogglesShown = it },
                        )
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.toggleCount(description: String) =
        onAllNodesWithContentDescription(description, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun SemanticsNodeInteraction.lineCount(): Int {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first().lineCount
    }

    @Test fun narrowAssignDropsTheTrailingToggles() = runComposeUiTest {
        show(ManageLabelsMode.ASSIGN, 360.dp)
        assertEquals(0, toggleCount(strings.autoAssignLabelSwitchLabel))
        assertEquals(0, toggleCount(strings.favouriteLabelSwitchLabel))
        assertEquals(0, toggleCount(strings.primaryLabelSwitchLabel))
    }

    @Test fun wideAssignKeepsTheTrailingToggles() = runComposeUiTest {
        show(ManageLabelsMode.ASSIGN, 600.dp)
        assertEquals(2, toggleCount(strings.autoAssignLabelSwitchLabel))
        assertEquals(2, toggleCount(strings.favouriteLabelSwitchLabel))
        assertEquals(2, toggleCount(strings.primaryLabelSwitchLabel))
    }

    @Test fun narrowWorkspaceKeepsTheTrailingToggles() = runComposeUiTest {
        show(ManageLabelsMode.WORKSPACE, 320.dp)
        assertEquals(2, toggleCount(strings.autoAssignLabelSwitchLabel))
        assertEquals(2, toggleCount(strings.favouriteLabelSwitchLabel))
        assertEquals(2, toggleCount(strings.primaryLabelSwitchLabel))
    }

    @Test fun longNameWrapsToTwoLines() = runComposeUiTest {
        show(ManageLabelsMode.ASSIGN, 360.dp)
        assertEquals(2, onNodeWithText(longName, useUnmergedTree = true).lineCount())
    }

    @Test fun narrowAssignSearchDoesNotOfferHiddenRowStates() = runComposeUiTest {
        show(ManageLabelsMode.ASSIGN, 360.dp, search = true)
        onNodeWithContentDescription(strings.searchOptions).performClick()
        onNodeWithText(strings.searchModeNameStart).assertExists()
        onNodeWithText(strings.labelsFilterAutoAssign).assertDoesNotExist()
        onNodeWithText(strings.labelsFilterFavourite).assertDoesNotExist()
    }

    @Test fun wideAssignSearchOffersVisibleRowStates() = runComposeUiTest {
        show(ManageLabelsMode.ASSIGN, 600.dp, search = true)
        onNodeWithContentDescription(strings.searchOptions).performClick()
        onNodeWithText(strings.labelsFilterAutoAssign).assertExists()
        onNodeWithText(strings.labelsFilterFavourite).assertExists()
    }

    @Test fun narrowWorkspaceSearchKeepsItsFilters() = runComposeUiTest {
        show(ManageLabelsMode.WORKSPACE, 320.dp, search = true)
        onNodeWithContentDescription(strings.searchOptions).performClick()
        onNodeWithText(strings.labelsFilterAutoAssign).assertExists()
        onNodeWithText(strings.labelsFilterFavourite).assertExists()
    }

    @Test fun resizingToNarrowClearsFiltersWhoseControlsDisappear() = runComposeUiTest {
        var width by mutableStateOf(600.dp)
        show(ManageLabelsMode.ASSIGN, 600.dp, search = true, widthSource = { width },
            initialFilters = setOf(LabelFilter.AUTO_ADD, LabelFilter.FAVOURITE))
        onNodeWithContentDescription(strings.searchOptions).performClick()
        onNodeWithText(strings.labelsFilterAutoAssign).assertIsOn()
        onNodeWithText(strings.labelsFilterFavourite).assertIsOn()
        runOnIdle { width = 360.dp }
        onNodeWithText(strings.labelsFilterAutoAssign).assertDoesNotExist()
        runOnIdle { width = 600.dp }
        onNodeWithText(strings.labelsFilterAutoAssign).assertIsOff()
        onNodeWithText(strings.labelsFilterFavourite).assertIsOff()
    }

    @Test fun narrowPaneHelpMatchesListRatherThanWindowWidth() = runComposeUiTest {
        // The desktop test window is wider than this constrained label-manager pane.
        show(ManageLabelsMode.ASSIGN, 360.dp, help = true)
        assertEquals(0, toggleCount(strings.autoAssignLabelSwitchLabel))
        onNodeWithText("Help").performClick()
        onNodeWithText(strings.manageLabelsHelpNarrowToggles).assertExists()
        onNodeWithText(strings.manageLabelsHelpAutoAssign).assertDoesNotExist()
        onNodeWithText(strings.manageLabelsHelpFavourite).assertDoesNotExist()
    }

    @Test fun rowTogglesShownPredicate() {
        assertTrue(!manageLabelsRowTogglesShown(ManageLabelsMode.ASSIGN, 479.dp))
        assertTrue(manageLabelsRowTogglesShown(ManageLabelsMode.ASSIGN, 480.dp))
        assertTrue(manageLabelsRowTogglesShown(ManageLabelsMode.WORKSPACE, 300.dp))
    }
}
