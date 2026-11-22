/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.sharedui.bookmark

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import net.bible.sharedcore.bookmark.BookmarkDisplayStyle
import net.bible.sharedcore.bookmark.LabelItem
import net.bible.sharedcore.bookmark.ManageLabelsController
import net.bible.sharedcore.bookmark.ManageLabelsMode
import net.bible.sharedcore.bookmark.ManageLabelsRow
import net.bible.sharedcore.bookmark.ManageLabelsService
import net.bible.sharedcore.bookmark.SearchMode
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Issue #4201: the assign sheet drives the full screen's own [ManageLabelsController] (ASSIGN), so its
 * search, filters and ticks must reach that controller and come back as the controller's rows.
 */
@OptIn(ExperimentalTestApi::class)
class LabelAssignSheetTest {
    private val strings = iosStrings("en")
    private val scope = CoroutineScope(Job() + Dispatchers.Unconfined)

    @AfterTest fun tearDown() = scope.cancel()

    private fun label(id: String, name: String, favourite: Boolean = false) =
        LabelItem(id, name, 0xFF3366CC.toInt(), favourite = favourite, isUnlabeled = false, isSpecial = false, customIcon = null)

    private val service = object : ManageLabelsService {
        override suspend fun assignableLabels() = listOf(
            label("1", "Sermon notes", favourite = true),
            label("2", "Prayer"),
            label("3", "Promises", favourite = true),
            label("4", "Sermon ideas"),
        )
        override suspend fun unlabeledLabel() =
            LabelItem("u", "Unlabeled", 0, favourite = false, isUnlabeled = true, isSpecial = true, customIcon = null)
        override fun recentLabelIds(): List<String> = emptyList()
        override suspend fun overriddenLabelStyles(): Map<String, BookmarkDisplayStyle> = emptyMap()
        override fun randomColorArgb(): Int = 0
        override suspend fun searchStudyPadsByContent(text: String): List<ManageLabelsRow.SearchResult> = emptyList()
        override fun styleTagsVisible(): Boolean = false
        override fun setStyleTagsVisible(visible: Boolean) = Unit
    }

    private fun controller(selected: Set<String> = emptySet()) = ManageLabelsController(
        mode = ManageLabelsMode.ASSIGN,
        service = service,
        scope = scope,
        initialSelected = selected,
        initialAutoAssign = setOf("2"),
        initialAutoAssignPrimary = null,
        initialBookmarkPrimary = selected.firstOrNull(),
        highlightLabelId = null,
        onEditLabel = {},
        onSelectStudyPad = { _, _ -> },
        onSave = {},
        onReset = {},
    )

    private var editLabels = 0
    private var newLabel = 0
    private var closed = 0
    private val editedLabels = mutableListOf<String>()

    private fun ComposeUiTest.show(controller: ManageLabelsController) {
        setContent {
            val rows by controller.rows.collectAsState()
            val searchText by controller.searchText.collectAsState()
            val searchMode by controller.searchMode.collectAsState()
            val filters by controller.filters.collectAsState()
            CompositionLocalProvider(LocalStrings provides strings) {
                AbTheme(darkTheme = false) {
                    Box(Modifier.width(360.dp)) {
                        LabelAssignSheetFrame(
                            state = LabelAssignSheetState(
                                title = strings.assignLabelsLabel, rows = rows, styleTagsVisible = false,
                                searchText = searchText, searchMode = searchMode, filters = filters,
                            ),
                            actions = LabelAssignSheetActions(
                                onSearch = controller::setSearch,
                                onSetSearchMode = controller::setSearchMode,
                                onToggleFilter = controller::toggleFilter,
                                onToggleChecked = controller::toggleChecked,
                                onToggleFavourite = controller::toggleFavourite,
                                onSetPrimary = controller::setPrimary,
                                onToggleAutoAssign = controller::toggleAutoAssign,
                                onEditLabel = { editedLabels += it },
                                onNewLabel = { newLabel++ },
                                onEditLabels = { editLabels++ },
                                newLabelIcon = { onClick -> IconButton(onClick = onClick) { Text("+") } },
                                iconSlot = { _, _ -> },
                            ),
                            onClose = { closed++ },
                        )
                    }
                }
            }
        }
        waitForIdle()
    }

    private fun names(controller: ManageLabelsController) =
        controller.rows.value.filterIsInstance<ManageLabelsRow.Item>().map { it.label.name }.toSet()

    @Test fun searchNarrowsTheListByNameStartAndTheContainsChipWidensIt() = runComposeUiTest {
        val controller = controller()
        show(controller)
        onNodeWithTag(LABEL_ASSIGN_SEARCH_CHIP_TAG).performClick()
        onNodeWithTag(LABEL_ASSIGN_SEARCH_TAG).performTextInput("ser")
        waitForIdle()
        assertEquals(setOf("Sermon notes", "Sermon ideas"), names(controller))
        onNodeWithText("Prayer").assertDoesNotExist()

        controller.setSearch("ide")
        waitForIdle()
        assertEquals(emptySet(), names(controller), "name-start does not match inside a name")
        onNodeWithTag(LABEL_ASSIGN_SEARCH_CONTAINS_TAG).performClick()
        waitForIdle()
        assertEquals(SearchMode.NAME_CONTAINS, controller.searchMode.value)
        assertEquals(setOf("Sermon ideas"), names(controller))
        onNodeWithText("Sermon ideas").assertExists()

        onNodeWithTag(LABEL_ASSIGN_SEARCH_CONTAINS_TAG).performClick()
        waitForIdle()
        assertEquals(SearchMode.NAME_START, controller.searchMode.value, "the chip toggles back")
    }

    @Test fun theFavouriteChipShowsOnlyFavouritesAndTheAutoAssignChipOnlyAutoAssigned() = runComposeUiTest {
        val controller = controller()
        show(controller)
        onNodeWithTag(LABEL_ASSIGN_FILTER_FAVOURITE_TAG).performClick()
        waitForIdle()
        assertEquals(setOf("Sermon notes", "Promises"), names(controller))
        onNodeWithText("Prayer").assertDoesNotExist()

        onNodeWithTag(LABEL_ASSIGN_FILTER_FAVOURITE_TAG).performClick()
        onNodeWithTag(LABEL_ASSIGN_FILTER_AUTO_ASSIGN_TAG).performClick()
        waitForIdle()
        assertEquals(setOf("Prayer"), names(controller))
    }

    @Test fun aTickedLabelStaysVisibleUnderASearchThatExcludesIt() = runComposeUiTest {
        val controller = controller(selected = setOf("2"))
        show(controller)
        controller.setSearch("ser")
        waitForIdle()
        assertTrue("Prayer" in names(controller), "a ticked label never disappears from under the user")
    }

    @Test fun aRowTapTicksTheLabelInsteadOfOpeningTheEditor() = runComposeUiTest {
        val controller = controller()
        show(controller)
        onNode(hasContentDescription("Promises") and isToggleable(), useUnmergedTree = true).assertIsOff()
        onNodeWithText("Promises").performClick()
        waitForIdle()
        assertTrue("3" in controller.resultSelected())
        assertEquals("3", controller.resultBookmarkPrimary(), "the first ticked label becomes primary, as in the full screen")
        onNode(hasContentDescription("Promises") and isToggleable(), useUnmergedTree = true).assertIsOn()

        onNodeWithText("Promises").performClick()
        waitForIdle()
        assertTrue("3" !in controller.resultSelected())
    }

    @Test fun theEditLabelsIconAndTheNewActionReachTheHost() = runComposeUiTest {
        show(controller())
        onNodeWithContentDescription(strings.labelAssignEditLabels).assertExists()
        onNodeWithTag(LABEL_ASSIGN_EDIT_LABELS_TAG).performClick()
        onNodeWithText("+").performClick()
        assertEquals(1, editLabels)
        assertEquals(1, newLabel)
    }

    @Test fun editLabelsIsAHeaderIconAndThereIsNoFooterRow() = runComposeUiTest {
        show(controller())
        onNodeWithText(strings.labelAssignEditLabels).assertDoesNotExist()
        onNodeWithTag(LABEL_ASSIGN_EDIT_LABELS_TAG).assertExists()
    }

    /** Below 480dp the rows have no toggles; the long press opens the label's editor, where they are. */
    @Test fun aLongPressOpensTheLabelsEditorWithoutTickingIt() = runComposeUiTest {
        val controller = controller()
        show(controller)
        onNodeWithText("Promises").performTouchInput { longClick() }
        waitForIdle()
        assertEquals(listOf("3"), editedLabels)
        assertTrue("3" !in controller.resultSelected(), "a long press does not tick")
    }

    @Test fun atRestThereIsASearchChipAndNeitherTheFieldNorTheContainsToggle() = runComposeUiTest {
        show(controller())
        onNodeWithTag(LABEL_ASSIGN_SEARCH_CHIP_TAG).assertExists()
        onNodeWithText(strings.labelsSearchHint).assertExists()
        onNodeWithTag(LABEL_ASSIGN_SEARCH_TAG).assertDoesNotExist()
        onNodeWithTag(LABEL_ASSIGN_SEARCH_CONTAINS_TAG).assertDoesNotExist()
        onNodeWithTag(LABEL_ASSIGN_FILTER_FAVOURITE_TAG).assertExists()
        onNodeWithText(strings.labelAssignFilterFavourites).assertExists()
    }

    @Test fun tappingTheChipOpensAFocusedFieldAndTheFiltersBecomeIconOnly() = runComposeUiTest {
        val controller = controller()
        show(controller)
        onNodeWithTag(LABEL_ASSIGN_SEARCH_CHIP_TAG).performClick()
        waitForIdle()
        onNodeWithTag(LABEL_ASSIGN_SEARCH_TAG).assertIsFocused()
        onNodeWithTag(LABEL_ASSIGN_SEARCH_CHIP_TAG).assertDoesNotExist()
        onNodeWithTag(LABEL_ASSIGN_SEARCH_CONTAINS_TAG).assertExists()
        onNodeWithText(strings.labelAssignFilterFavourites).assertDoesNotExist()
        onNodeWithContentDescription(strings.labelAssignFilterFavourites).assertExists()
        onNodeWithContentDescription(strings.labelAssignFilterAutoAssign).assertExists()
        onNodeWithTag(LABEL_ASSIGN_FILTER_FAVOURITE_TAG).performClick()
        waitForIdle()
        assertEquals(setOf("Sermon notes", "Promises"), names(controller), "filters still work while the search is open")
    }

    @Test fun theClearIconEmptiesTheTextAndCollapsesBackToTheChip() = runComposeUiTest {
        val controller = controller()
        show(controller)
        onNodeWithTag(LABEL_ASSIGN_SEARCH_CHIP_TAG).performClick()
        onNodeWithTag(LABEL_ASSIGN_SEARCH_TAG).performTextInput("ser")
        waitForIdle()
        assertEquals(setOf("Sermon notes", "Sermon ideas"), names(controller))
        onNodeWithContentDescription(strings.searchClear).performClick()
        waitForIdle()
        assertEquals("", controller.searchText.value)
        onNodeWithTag(LABEL_ASSIGN_SEARCH_TAG).assertDoesNotExist()
        onNodeWithTag(LABEL_ASSIGN_SEARCH_CHIP_TAG).assertExists()
        assertEquals(4, names(controller).size, "the list is unfiltered again")
    }

    @Test fun escapeOnTheFieldClearsAndCollapsesWithoutLeavingTheSheet() = runComposeUiTest {
        val controller = controller()
        show(controller)
        onNodeWithTag(LABEL_ASSIGN_SEARCH_CHIP_TAG).performClick()
        onNodeWithTag(LABEL_ASSIGN_SEARCH_TAG).performTextInput("ser")
        waitForIdle()
        onNodeWithTag(LABEL_ASSIGN_SEARCH_TAG).performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertEquals("", controller.searchText.value)
        onNodeWithTag(LABEL_ASSIGN_SEARCH_TAG).assertDoesNotExist()
        onNodeWithTag(LABEL_ASSIGN_SEARCH_CHIP_TAG).assertExists()
        assertEquals(0, closed, "the first Escape only closes the search")
    }

    @Test fun aNonEmptySearchTextRendersTheFieldOpen() = runComposeUiTest {
        val controller = controller()
        controller.setSearch("ser")
        show(controller)
        onNodeWithTag(LABEL_ASSIGN_SEARCH_TAG).assertExists()
        onNodeWithTag(LABEL_ASSIGN_SEARCH_CHIP_TAG).assertDoesNotExist()
    }

    @Test fun theContainsToggleSitsInTheOpenFieldRow() = runComposeUiTest {
        val controller = controller()
        show(controller)
        onNodeWithTag(LABEL_ASSIGN_SEARCH_CHIP_TAG).performClick()
        onNodeWithTag(LABEL_ASSIGN_SEARCH_CONTAINS_TAG).performClick()
        waitForIdle()
        assertEquals(SearchMode.NAME_CONTAINS, controller.searchMode.value)
    }
}
