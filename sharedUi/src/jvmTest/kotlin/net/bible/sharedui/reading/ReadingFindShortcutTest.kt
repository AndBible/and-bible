package net.bible.sharedui.reading

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextRange
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.reading.ReadingSearchBarState
import net.bible.sharedcore.reading.ToolbarState
import net.bible.sharedcore.search.ReadingSearchController
import net.bible.sharedcore.search.SearchDocumentCategory
import net.bible.sharedcore.search.SearchDocumentInfo
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ReadingFindShortcutTest {
    @Test fun shortcutAfterResultsFocusesAndSelectsTheWholeQuery() {
        for ((dark, colorMode, noAnimations) in listOf(
            Triple(false, DisplayColorMode.NORMAL, false),
            Triple(true, DisplayColorMode.NORMAL, false),
            Triple(false, DisplayColorMode.MONOCHROME, true),
            Triple(true, DisplayColorMode.MONOCHROME, true),
        )) runComposeUiTest {
            val controller = ReadingSearchController(
                resolveDoc = { SearchDocumentInfo("KJV", SearchDocumentCategory.BIBLE, false, true) },
                onUnavailable = {}, onLeaveFullScreen = {}, onStartIndexing = {}, onRunSearch = { _, _, _ -> },
            )
            controller.open("light")
            controller.onResultOpened()
            controller.imeRequestHandled()
            setContent {
                val query by controller.queries.query.collectAsState()
                val request by controller.imeRequest.collectAsState()
                CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                    AbTheme(darkTheme = dark, colorMode = colorMode, disableAnimations = noAnimations) {
                        val icon = ColorPainter(Color.Gray)
                        ReadingToolbar(
                            ToolbarState("John 3", "KJV", false, false, false, false, 0, true, true, true),
                            ReadingToolbarIcons(icon, icon, icon, icon, icon, icon, icon, icon, icon),
                            ReadingToolbarCallbacks(
                                onHome = {}, onTitleTap = {}, onTitleLongPress = {}, onTitleFlingVertical = {},
                                onTitleFlingHorizontal = {}, onBible = {}, onBibleLong = {}, onCommentary = {},
                                onCommentaryLong = {}, onStrongs = {}, onStrongsLong = {}, onSearch = {},
                                onSpeak = {}, onSpeakLong = {}, onWorkspace = {}, onOverflow = {},
                            ),
                            searchBar = ReadingSearchBarState(query = query, imeRequest = request),
                            searchBarCallbacks = ReadingSearchBarCallbacks(
                                onQueryChange = controller.queries::setQuery, onSubmit = controller::submit,
                                onRecentTermsOpen = {}, onRecentTermsDismiss = {}, onRecentTermSelected = {},
                                onOpenSettings = {}, onClose = {}, onImeRequestHandled = controller::imeRequestHandled,
                                onFieldFocusChanged = {}, onRebuildIndex = {}, onShowResults = {},
                            ),
                        )
                    }
                }
            }
            runOnIdle { controller.openFromShortcut() }
            val field = onNodeWithTag(SEARCH_FIELD_TAG)
            field.assertIsFocused()
            assertEquals(TextRange(0, 5), field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
            field.performTextInput("hope")
            runOnIdle { assertEquals("hope", controller.queries.query.value) }
            field.performKeyInput {
                keyDown(Key.CtrlLeft)
                keyDown(Key.F)
                keyUp(Key.F)
                keyUp(Key.CtrlLeft)
            }
            assertEquals(TextRange(0, 4), field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        }
    }
}
