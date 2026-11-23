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
import androidx.compose.ui.test.performSemanticsAction
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
import net.bible.sharedui.bookmark.LABEL_ASSIGN_SEARCH_CHIP_TAG
import net.bible.sharedui.bookmark.LABEL_ASSIGN_SEARCH_TAG
import net.bible.sharedui.bookmark.LabelAssignSheet
import net.bible.sharedui.bookmark.LabelAssignSheetActions
import net.bible.sharedui.bookmark.LabelAssignSheetState
import net.bible.sharedui.progress.AbReadHistorySheet
import net.bible.sharedui.progress.ReadHistoryRow
import net.bible.sharedui.settings.GenericSettingsEditorSheet
import net.bible.sharedui.settings.SettingsEditorSheet
import net.bible.sharedui.settings.TextInputRowEditorContent
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
@OptIn(ExperimentalTestApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
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

    /** The cap covers the whole sheet, the drag handle included (default handle, no description, no header close). */
    @Test fun sheetWithDefaultDragHandleIsCappedIncludingTheHandle() {
        for (window in listOf(portrait, landscape)) run(window) { h ->
            mount {
                AbModalBottomSheet(onDismissRequest = {}, modifier = Modifier.testTag("handle-sheet")) {
                    androidx.compose.foundation.layout.Column(Modifier.sheetVerticalScroll()) {
                        repeat(40) { Box(Modifier.fillMaxWidth().height(48.dp)) { Text("Row $it") } }
                    }
                }
            }
            waitForIdle()
            val height = onNodeWithTag("handle-sheet").fetchSemanticsNode().size.height.toFloat()
            assertTrue(height <= h * 0.5f + 1f, "sheet with a handle: $height dp of a $h dp window is over half")
            assertTrue(height > h * 0.4f, "sheet with a handle: $height dp did not use the room it has")
        }
    }

    /** A form page with a pinned confirm row keeps the row on screen at the cap, also in landscape. */
    @Test fun textInputPageKeepsItsConfirmRowAtTheCap() {
        for (window in listOf(portrait, landscape)) run(window) { h ->
            val row = SettingsItem.TextInputRow("k", "Server address", value = "https://example.org")
            mount {
                SettingsEditorSheet(SettingsEditorPage.Row("k"), "Server address", false, {}, {}) {
                    TextInputRowEditorContent(row, onApply = {}, onCancel = {})
                }
            }
            waitForIdle()
            assertCapped(h, "text input page $window")
            assertOnScreen(strings.settingsEditorApply, h, "text input page $window")
            assertOnScreen(strings.cancel, h, "text input page $window")
        }
    }

    private val textLabels = net.bible.sharedui.settings.TextDisplaySettingsScreenLabels(
        resetContentDescription = "Reset all", resetConfirmMessage = "Reset?", revertMessage = "Revert?",
        revertLabel = "Revert", fontSizeDialogTitle = "Font size", topMarginDialogTitle = "Top margin",
        lineSpacingDialogTitle = "Line spacing", marginSizeDialogTitle = "Margins",
        marginLeftLabelFormat = "Left %d", marginRightLabelFormat = "Right %d", marginMaxWidthLabelFormat = "Max width %d",
        resetToInheritedLabel = "Inherit", badgeWorkspace = "W", badgeGlobal = "G", okLabel = "OK",
        resetLabel = "Reset", cancelLabel = "Cancel",
    )

    /**
     * #4207 review finding 1: the slider pages of the text settings editor keep their Inherit/Cancel/OK row on
     * screen under the half-window cap (the margins page even in portrait, font size and line spacing in landscape).
     */
    private fun textSettingPage(window: Size, type: net.bible.sharedcore.settings.TextSettingType) = run(window) { h ->
        val value = if (type == net.bible.sharedcore.settings.TextSettingType.MARGINSIZE) {
            net.bible.sharedcore.settings.TextSettingRowValue.Margins(2, 2, 170, 30, 30, 500, "2 / 2 / 170")
        } else net.bible.sharedcore.settings.TextSettingRowValue.Numeric(16, 6, 60, "16")
        val rows = mapOf(type to net.bible.sharedcore.settings.TextSettingRow(
            type, value, net.bible.sharedcore.settings.InheritedFrom.NONE, enabled = true, visible = true,
        ))
        mount {
            net.bible.sharedui.settings.TextSettingRowEditorSheet(
                pages = listOf(SettingsEditorPage.Row(type.name)), rows = rows, dialogLabels = textLabels,
                onNumericChange = { _, _ -> }, onMarginsChange = { _, _, _, _ -> }, onRevert = {}, onPop = {}, onClose = {},
            )
        }
        waitForIdle()
        val what = "$type page $window"
        assertCapped(h, what)
        for (button in listOf("Inherit", "Cancel", "OK")) assertOnScreen(button, h, what)
    }

    @Test fun marginsPagePortrait() = textSettingPage(portrait, net.bible.sharedcore.settings.TextSettingType.MARGINSIZE)
    @Test fun marginsPageLandscape() = textSettingPage(landscape, net.bible.sharedcore.settings.TextSettingType.MARGINSIZE)
    @Test fun fontSizePagePortrait() = textSettingPage(portrait, net.bible.sharedcore.settings.TextSettingType.FONTSIZE)
    @Test fun fontSizePageLandscape() = textSettingPage(landscape, net.bible.sharedcore.settings.TextSettingType.FONTSIZE)
    @Test fun lineSpacingPageLandscape() = textSettingPage(landscape, net.bible.sharedcore.settings.TextSettingType.LINE_SPACING)

    /** Re-audit after review finding 1: the Speak sheet's repeat-range page keeps its Cancel/OK row at the cap. */
    private fun speakRangePage(window: Size) = run(window) { h ->
        mount {
            net.bible.sharedui.speak.SpeakSettingsSheet(
                page = net.bible.sharedcore.speak.SpeakSheetPage.RepeatRange, depth = 2, onDismiss = {}, onClose = {},
            ) { _, _ ->
                net.bible.sharedui.speak.SpeakRangeContent(
                    start = net.bible.sharedcore.speak.PickedVerse("Gen.1.1", "Genesis 1:1", 0),
                    end = net.bible.sharedcore.speak.PickedVerse("Gen.1.2", "Genesis 1:2", 1),
                    showOrderError = true, canCommit = true,
                    onPickStart = {}, onPickEnd = {}, onClear = {}, onConfirm = {}, onCancel = {},
                )
            }
        }
        waitForIdle()
        assertCapped(h, "speak range $window")
        assertOnScreen(strings.cancel, h, "speak range $window")
        assertOnScreen(strings.okay, h, "speak range $window")
    }

    @Test fun speakRangePagePortrait() = speakRangePage(portrait)
    @Test fun speakRangePageLandscape() = speakRangePage(landscape)

    /** Re-audit: the sleep-timer page has no pinned row, but its custom slider must stay reachable by scrolling. */
    @Test fun sleepTimerPageScrollsToItsSliderInLandscape() = run(landscape) { h ->
        mount {
            net.bible.sharedui.speak.SpeakSettingsSheet(
                page = net.bible.sharedcore.speak.SpeakSheetPage.SleepTimer, depth = 2, onDismiss = {}, onClose = {},
            ) { _, _ ->
                net.bible.sharedui.speak.SleepTimerContent(
                    selection = net.bible.sharedcore.speak.SleepTimerSelection.Custom(17), customMinutes = 17,
                    onPick = { _, _ -> },
                )
            }
        }
        waitForIdle()
        assertCapped(h, "sleep timer $landscape")
        val value = strings.speakSleepTimerMinutes(17)
        onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasText(value))
        assertOnScreen(value, h, "sleep timer slider")
    }

    /** #4207 review minor 6: a dialog opened from a sheet is not under the sheet's cap. */
    @Test fun aDialogOpenedFromASheetIsNotCapped() = run(portrait) { _ ->
        var capped: Boolean? = null
        mount {
            CompositionLocalProvider(LocalAbSheetHeightCapped provides true) {
                net.bible.sharedui.components.dialog.AbDialogShell(onDismissRequest = {}, buttons = {}, content = {
                    capped = LocalAbSheetHeightCapped.current
                    Text("Dialog body")
                })
            }
        }
        waitForIdle()
        assertEquals(false, capped)
    }

    /** #4207 review minor 7: the default drag handle (no description provided) still has a name and a dismiss action. */
    @Test fun theDefaultDragHandleHasANameAndADismissAction() = run(portrait) { _ ->
        var dismissed = false
        mount {
            AbModalBottomSheet(onDismissRequest = { dismissed = true }) { Text("Body") }
        }
        waitForIdle()
        val handle = onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.Dismiss))
        val description = handle.fetchSemanticsNode().config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
        assertTrue(description.any { it.isNotBlank() }, "drag handle has no name: $description")
        handle.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.Dismiss)
        waitForIdle()
        assertTrue(dismissed)
    }

    private val scope = CoroutineScope(Job() + Dispatchers.Unconfined)

    private fun labelAssign(window: Size, search: String? = null) = run(window) { h ->
        try {
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
        if (search != null) controller.setSearch(search)
        mount {
            val rows by controller.rows.collectAsState()
            val searchText by controller.searchText.collectAsState()
            val filters by controller.filters.collectAsState()
            LabelAssignSheet(
                state = LabelAssignSheetState(
                    title = strings.assignLabelsLabel, rows = rows, styleTagsVisible = false,
                    searchText = searchText, filters = filters,
                ),
                actions = LabelAssignSheetActions(
                    onSearch = controller::setNameSearch,
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
        if (window.height < 200f) {
            // #4207 floor: a 120dp-high window (landscape with the keyboard up) keeps usable chrome. The cap
            // is the floor (offered height here), never beyond the window, and the title and the search field
            // the user types into are fully visible.
            val height = sheetHeight(h)
            assertTrue(height <= h + 1f, "label assign $window: sheet $height dp over the offered $h dp")
            assertOnScreen(strings.assignLabelsLabel, h, "label assign $window")
            val field = onNodeWithTag(LABEL_ASSIGN_SEARCH_TAG).assertIsDisplayed().fetchSemanticsNode()
            assertTrue(field.size.height >= 16, "label search field clipped to ${field.size.height} dp")
            assertTrue(field.positionInRoot.y >= 0f && field.positionInRoot.y + field.size.height <= h + 1f,
                "label search field outside the window")
        } else if (window.height < window.width) {
            // #4215: in landscape the 50 % cap gives way to the sheet's floor: the header, the chip row and at least
            // two label rows are visible at rest, without scrolling; the sheet is no taller than that floor needs.
            val height = sheetHeight(h)
            assertTrue(height <= maxOf(h * 0.5f, AbLabelAssignSheetMinCapHeight.value) + 1f,
                "label assign $window: sheet $height dp is over its floor")
            assertOnScreen(strings.assignLabelsLabel, h, "label assign $window")
            onNodeWithTag(LABEL_ASSIGN_SEARCH_CHIP_TAG).assertIsDisplayed()
            // Fully inside the list's viewport (a lazy row cut off at its edge is composed too).
            val list = onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
                .fetchSemanticsNode().boundsInRoot
            val visibleRows = onAllNodes(hasText("Label ", substring = true)).fetchSemanticsNodes().count { node ->
                val bounds = node.boundsInRoot
                bounds.height >= 40f && bounds.top >= list.top - 1f && bounds.bottom <= minOf(list.bottom, h) + 1f
            }
            assertTrue(visibleRows >= 2, "label assign $window: only $visibleRows label rows visible in a $height dp sheet")
            assertListReachesLastRow("Label 40", "label assign $window")
        } else {
            assertCapped(h, "label assign $window")
            assertOnScreen(strings.assignLabelsLabel, h, "label assign $window")
            // The compact header (one chip row, #4207) leaves the list room even in a 360dp-high landscape window.
            assertListReachesLastRow("Label 40", "label assign $window")
        }
        } finally {
            scope.cancel()
        }
    }

    @Test fun labelAssignPortrait() = labelAssign(portrait)
    @Test fun labelAssignLandscape() = labelAssign(landscape)
    /** #4215: a phone landscape window without the keyboard (status bar taken off): still two label rows. */
    @Test fun labelAssignShortLandscapeShowsTwoLabelRows() = labelAssign(Size(800f, 330f))
    @Test fun labelAssignWithKeyboardUpKeepsTheSearchFieldVisible() = labelAssign(Size(760f, 120f), search = "Label")
}

@androidx.compose.runtime.Composable
private fun Column2(content: @androidx.compose.runtime.Composable () -> Unit) =
    androidx.compose.foundation.layout.Column { content() }
