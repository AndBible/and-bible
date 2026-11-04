package net.bible.sharedui.reading

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedcore.reading.OptionsMenuItem
import net.bible.sharedcore.reading.ReadingSearchBarState
import net.bible.sharedcore.reading.ToolbarState
import net.bible.sharedcore.speak.SpeakTransportVd
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The visible toolbar labels and destination controls missed by callback-only tests. */
@OptIn(ExperimentalTestApi::class)
class ToolbarDestinationMigrationTest {
    @Test fun namedToolbarActionsReachSearchSpeakWorkspaceAndOverflowControls() = runComposeUiTest {
        var search by mutableStateOf(false)
        var speak by mutableStateOf(false)
        var workspaceOpens = 0
        var overflow by mutableStateOf(false)
        var next = 0
        val strings = iosStrings("en")
        val callbacks = ReadingToolbarCallbacks(
            onHome = {}, onTitleTap = {}, onTitleLongPress = {}, onTitleFlingVertical = {},
            onTitleFlingHorizontal = {}, onBible = {}, onBibleLong = {}, onCommentary = {}, onCommentaryLong = {},
            onStrongs = {}, onStrongsLong = {}, onSearch = { search = true }, onSpeak = { speak = true },
            onSpeakLong = {}, onWorkspace = { workspaceOpens++ }, onOverflow = { overflow = true },
        )
        val searchCallbacks = ReadingSearchBarCallbacks(
            onQueryChange = {}, onSubmit = {}, onRecentTermsOpen = {}, onRecentTermsDismiss = {},
            onRecentTermSelected = {}, onOpenSettings = {}, onClose = { search = false }, onImeRequestHandled = {},
            onFieldFocusChanged = {}, onRebuildIndex = {}, onShowResults = {},
        )
        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                AbTheme(darkTheme = false) {
                    val icon = ColorPainter(Color.Gray)
                    Column {
                        ReadingToolbar(
                            ToolbarState("John 3", "KJV", false, false, false, false, 0, true, true, true),
                            ReadingToolbarIcons(icon, icon, icon, icon, icon, icon, icon, icon, icon), callbacks,
                            searchBar = if (search) ReadingSearchBarState(query = "") else null,
                            searchBarCallbacks = searchCallbacks,
                            overflowItems = listOf(
                                OptionsMenuItem("night", "Night mode", true, false, true, false),
                                OptionsMenuItem("text", "All text options", false, false, true, true),
                            ), overflowExpanded = overflow, onOverflowDismiss = { overflow = false },
                        )
                        if (speak) SpeakTransportBar(
                            SpeakTransportVd(visible = true), {}, {}, {}, {}, {}, { next++ }, {}, {},
                        )
                    }
                }
            }
        }
        onNodeWithContentDescription("Find").performClick()
        onNodeWithTag(SEARCH_FIELD_TAG).assertExists()
        onNodeWithContentDescription(strings.searchClose).performClick()
        onNodeWithContentDescription("Speak").performClick()
        onNodeWithContentDescription("Next").performClick()
        assertEquals(1, next)
        onNodeWithContentDescription("Workspaces").performClick()
        assertEquals(1, workspaceOpens)
        onNodeWithContentDescription("More options").performClick()
        onNodeWithText("Night mode").assertExists()
        onNodeWithText("All text options …").assertExists()
    }
}
