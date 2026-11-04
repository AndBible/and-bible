package net.bible.sharedui.bookmark

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import net.bible.sharedcore.bookmark.LabelItem
import net.bible.sharedcore.bookmark.ManageLabelsMode
import net.bible.sharedcore.bookmark.ManageLabelsRow
import net.bible.sharedcore.bookmark.SearchMode
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ManageLabelsIconsTest {
    private fun item(id: String, name: String, icon: String?) = ManageLabelsRow.Item(
        LabelItem(id, name, 0xFF3366CC.toInt(), favourite = false, isUnlabeled = false, isSpecial = false, customIcon = icon),
        checked = false, isAutoAssign = false, isPrimary = false, highlighted = false,
    )

    /**
     * Pins what iOS FeatureUITests.testManageLabelsShowsIcons asserted: every label row is drawn with its name and the
     * host's icon slot receives that row's own customIcon (a custom key, or null for the default glyph).
     */
    @Test fun rowsShowNamesAndHandEachLabelsIconKeyToTheIconSlot() = runComposeUiTest {
        val slotCalls = mutableListOf<String?>()
        setContent {
            CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                AbTheme(darkTheme = false) {
                    ManageLabelsScreen(
                        title = "Manage labels",
                        rows = listOf(item("1", "Feature star", "star"), item("2", "Feature default", null)),
                        mode = ManageLabelsMode.STUDYPAD,
                        styleTagsVisible = false,
                        searchText = "", searchMode = SearchMode.NAME_START, onSearch = {}, onSetSearchMode = {},
                        filters = emptySet(), onToggleFilter = {}, searchModeActive = false, onCloseSearch = {},
                        onRowClick = {}, onRowLongClick = {}, onToggleChecked = {}, onToggleFavourite = {},
                        onSetPrimary = {}, onToggleAutoAssign = {}, onUp = {},
                        iconSlot = { customIcon, _ -> slotCalls += customIcon },
                        actions = {}, searchActions = {},
                    )
                }
            }
        }
        onNodeWithText("Feature star").assertExists()
        onNodeWithText("Feature default").assertExists()
        runOnIdle { assertEquals(setOf<String?>("star", null), slotCalls.toSet()) }
    }
}
