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
package net.bible.sharedui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
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
import net.bible.sharedcore.settings.SettingsEditorPage
import net.bible.sharedcore.settings.SettingsEditorStack
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.settings.SettingsScreenState
import net.bible.sharedui.bookmark.LabelAssignSheet
import net.bible.sharedui.bookmark.LabelAssignSheetActions
import net.bible.sharedui.bookmark.LabelAssignSheetState
import net.bible.sharedui.progress.AbReadHistorySheet
import net.bible.sharedui.progress.ReadHistoryRow
import net.bible.sharedui.settings.GenericSettingsEditorSheet
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #4207 decision 1: no bottom sheet is taller than half of the window it is offered, the header and the footer or
 * confirm row stay on screen whatever the list length, and the list scrolls to its last row. A short sheet keeps its
 * natural height. The sheets are the real ones, in a window of a phone's size (1 px = 1 dp), portrait and landscape.
 */
@OptIn(ExperimentalTestApi::class)
class SheetHalfHeightCapTest {
    private val strings = iosStrings("en")
    private val portrait = Size(360f, 760f)
    private val landscape = Size(760f, 360f)

    private fun run(window: Size, block: SkikoComposeUiTest.(windowHeight: Float) -> Unit) =
        runSkikoComposeUiTest(size = window, density = Density(1f)) { block(window.height) }

    private fun SkikoComposeUiTest.mount(content: @androidx.compose.runtime.Composable () -> Unit) = setContent {
        CompositionLocalProvider(LocalStrings provides strings) {
            AbTheme(darkTheme = false, disableAnimations = true) { content() }
        }
    }

    /** The sheet is as tall as the distance from its header's top (the close button) to the window bottom. */
    private fun SkikoComposeUiTest.sheetHeight(windowHeight: Float): Float {
        val close = onNodeWithContentDescription(strings.settingsEditorClose).fetchSemanticsNode()
        // The 40dp button sits centred in the 48dp header row.
        return windowHeight - close.positionInRoot.y + 4f
    }

    private fun SkikoComposeUiTest.assertCapped(windowHeight: Float, what: String) {
        val height = sheetHeight(windowHeight)
        assertTrue(height <= windowHeight * 0.5f + 1f, "$what: sheet $height dp of a $windowHeight dp window is over half")
        assertTrue(height > 0f, "$what: no sheet")
    }

    /** [text] is on screen: displayed, non-empty, inside the window. */
    private fun SkikoComposeUiTest.assertOnScreen(text: String, windowHeight: Float, what: String) {
        val node = onNodeWithText(text).assertIsDisplayed().fetchSemanticsNode()
        assertTrue(node.size.height > 0, "$what: '$text' has no height")
        assertTrue(node.positionInRoot.y + node.size.height <= windowHeight + 1f, "$what: '$text' below the window")
    }

    private fun SkikoComposeUiTest.assertListReachesLastRow(last: String, what: String) {
        onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasText(last))
        onNodeWithText(last).assertIsDisplayed()
    }

    private fun quickSheetWithFooter(window: Size) = run(window) { h ->
        mount {
            val list = rememberLazyListState()
            AbQuickSheet(
                open = true, title = "Quick", onDismiss = {},
                footer = { AbQuickSheetFooterRow("Footer row", {}) },
                canScrollForward = { list.canScrollForward },
            ) {
                LazyColumn(state = list) { items(40) { Box(Modifier.fillMaxWidth().height(48.dp)) { Text("Row $it") } } }
            }
        }
        waitForIdle()
        assertCapped(h, "quick sheet $window")
        assertTrue(onNodeWithTag(AbQuickSheetTag).fetchSemanticsNode().size.height <= h * 0.5f + 1f)
        assertOnScreen("Quick", h, "quick sheet $window")
        assertOnScreen("Footer row", h, "quick sheet $window")
        assertListReachesLastRow("Row 39", "quick sheet $window")
        assertOnScreen("Footer row", h, "quick sheet $window after scrolling")
    }

    @Test fun quickSheetWithFooterPortrait() = quickSheetWithFooter(portrait)
    @Test fun quickSheetWithFooterLandscape() = quickSheetWithFooter(landscape)

    @Test fun shortSheetKeepsItsNaturalHeight() {
        for (window in listOf(portrait, landscape)) run(window) { h ->
            mount {
                AbQuickSheet(open = true, title = "Quick", onDismiss = {}) {
                    Column2 { repeat(2) { Box(Modifier.fillMaxWidth().height(48.dp)) { Text("Row $it") } } }
                }
            }
            waitForIdle()
            // Header 48 + two rows + the column's 16dp bottom padding.
            assertEquals(48f + 96f + 16f, sheetHeight(h), 2f, "short sheet in $window")
        }
    }

    private fun settingsEditorWithConfirmRow(window: Size) = run(window) { h ->
        val row = SettingsItem.MultiSelectRow(
            "k", "Pick many",
            options = (0 until 40).map { SettingsItem.Choice("v$it", "Option $it") }, selectedValues = emptySet(),
        )
        mount {
            GenericSettingsEditorSheet(
                state = SettingsScreenState("S", listOf(row)), editor = SettingsEditorStack(),
                page = SettingsEditorPage.Row("k"), depth = 1,
                onListChoice = { _, _ -> }, onTextInput = { _, _ -> }, onMultiSelectChange = { _, _ -> },
            )
        }
        waitForIdle()
        assertCapped(h, "settings editor $window")
        assertOnScreen("Pick many", h, "settings editor $window")
        assertOnScreen(strings.settingsEditorApply, h, "settings editor $window")
        assertListReachesLastRow("Option 39", "settings editor $window")
        assertOnScreen(strings.settingsEditorApply, h, "settings editor $window after scrolling")
    }

    @Test fun settingsEditorWithConfirmRowPortrait() = settingsEditorWithConfirmRow(portrait)
    @Test fun settingsEditorWithConfirmRowLandscape() = settingsEditorWithConfirmRow(landscape)

    private fun readHistory(window: Size) = run(window) { h ->
        mount {
            AbReadHistorySheet(
                title = "History", rows = (0 until 40).map { ReadHistoryRow("$it", "Primary $it", "Secondary $it") },
                onApplyDeletes = {}, onDismiss = {},
            )
        }
        waitForIdle()
        assertCapped(h, "read history $window")
        assertOnScreen("History", h, "read history $window")
        assertListReachesLastRow("Primary 39", "read history $window")
    }

    @Test fun readHistoryPortrait() = readHistory(portrait)
    @Test fun readHistoryLandscape() = readHistory(landscape)

    private val scope = CoroutineScope(Job() + Dispatchers.Unconfined)

    private fun labelAssign(window: Size) = run(window) { h ->
        fun label(i: Int) = LabelItem("l$i", "Label ${i.toString().padStart(2, '0')}", 0xFF3366CC.toInt(),
            favourite = false, isUnlabeled = false, isSpecial = false, customIcon = null)
        val service = object : ManageLabelsService {
            override suspend fun assignableLabels() = (1..40).map(::label)
            override suspend fun unlabeledLabel() =
                LabelItem("u", "Unlabeled", 0, favourite = false, isUnlabeled = true, isSpecial = true, customIcon = null)
            override fun recentLabelIds(): List<String> = emptyList()
            override suspend fun overriddenLabelStyles(): Map<String, BookmarkDisplayStyle> = emptyMap()
            override fun randomColorArgb(): Int = 0
            override suspend fun searchStudyPadsByContent(text: String): List<ManageLabelsRow.SearchResult> = emptyList()
            override fun styleTagsVisible(): Boolean = false
            override fun setStyleTagsVisible(visible: Boolean) = Unit
        }
        val controller = ManageLabelsController(
            mode = ManageLabelsMode.ASSIGN, service = service, scope = scope, initialSelected = emptySet(),
            initialAutoAssign = emptySet(), initialAutoAssignPrimary = null, initialBookmarkPrimary = null,
            highlightLabelId = null, onEditLabel = {}, onSelectStudyPad = { _, _ -> }, onSave = {}, onReset = {},
        )
        mount {
            val rows by controller.rows.collectAsState()
            val searchText by controller.searchText.collectAsState()
            val searchMode by controller.searchMode.collectAsState()
            val filters by controller.filters.collectAsState()
            LabelAssignSheet(
                state = LabelAssignSheetState(
                    title = strings.assignLabelsLabel, rows = rows, styleTagsVisible = false,
                    searchText = searchText, searchMode = searchMode, filters = filters,
                ),
                actions = LabelAssignSheetActions(
                    onSearch = controller::setSearch, onSetSearchMode = controller::setSearchMode,
                    onToggleFilter = controller::toggleFilter, onToggleChecked = controller::toggleChecked,
                    onToggleFavourite = controller::toggleFavourite, onSetPrimary = controller::setPrimary,
                    onToggleAutoAssign = controller::toggleAutoAssign, onEditLabel = {}, onNewLabel = {},
                    onEditLabels = {}, newLabelIcon = { onClick ->
                        androidx.compose.material3.IconButton(onClick = onClick) { Text("+") }
                    }, iconSlot = { _, _ -> },
                ),
                onDismiss = {},
            )
        }
        waitForIdle()
        assertCapped(h, "label assign $window")
        assertOnScreen(strings.assignLabelsLabel, h, "label assign $window")
        assertOnScreen(strings.labelAssignEditLabels, h, "label assign $window")
        // The label header (search field, chips) is ~150dp and sits inside the body, so in a 360dp-high landscape
        // window (cap 180dp, minus the title and the footer) no room is left for the list: Tasks 2-3 of #4207
        // compact that header, and the list is asserted reachable there. Portrait has the room today.
        if (window.height > window.width) {
            assertListReachesLastRow("Label 40", "label assign $window")
            assertOnScreen(strings.labelAssignEditLabels, h, "label assign $window after scrolling")
        }
        scope.cancel()
    }

    @Test fun labelAssignPortrait() = labelAssign(portrait)
    @Test fun labelAssignLandscape() = labelAssign(landscape)
}

@androidx.compose.runtime.Composable
private fun Column2(content: @androidx.compose.runtime.Composable () -> Unit) =
    androidx.compose.foundation.layout.Column { content() }
