package net.bible.sharedui.reading

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.Modifier
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.reading.ReadingSearchBarState
import net.bible.sharedcore.reading.ToolbarState
import net.bible.sharedcore.search.ReadingSearchController
import net.bible.sharedcore.search.SearchDocumentCategory
import net.bible.sharedcore.search.SearchDocumentInfo
import net.bible.sharedcore.search.StyledText
import net.bible.sharedcore.search.SwordResultRow
import net.bible.sharedui.search.SearchSheetContent
import net.bible.sharedui.search.bibleResultRows
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)
class ReadingFindResultsKeyboardTest {
    private class Results {
        var opened: Int? = null
        var selected = -1
        val edits = mutableListOf<String?>()
        lateinit var listState: LazyListState
    }

    private fun ComposeUiTest.showResults(
        count: Int = 2,
        dark: Boolean = false,
        colorMode: DisplayColorMode = DisplayColorMode.NORMAL,
    ): Results {
        val result = Results()
        setContent {
            CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                AbTheme(darkTheme = dark, colorMode = colorMode, disableAnimations = true) {
                    val listState = rememberLazyListState()
                    result.listState = listState
                    Box(Modifier.height(200.dp)) {
                        SearchSheetContent(
                            countLabel = "$count results",
                            loading = false,
                            error = null,
                            empty = false,
                            listState = listState,
                            onDismissError = {},
                            onClose = {},
                            resultCount = count,
                            onOpenResult = { result.opened = it },
                            onEditQuery = { result.edits.add(it) },
                        ) { selected ->
                            result.selected = selected
                            items((0 until count).toList()) { index ->
                                Text("result $index", Modifier.clickable { result.opened = index }.padding(12.dp))
                            }
                        }
                    }
                }
            }
        }
        return result
    }

    @Test fun initialFocusSelectsFirstResultInEveryTheme() {
        for ((dark, mode) in listOf(
            false to DisplayColorMode.NORMAL,
            true to DisplayColorMode.NORMAL,
            false to DisplayColorMode.MONOCHROME,
            true to DisplayColorMode.MONOCHROME,
        )) runComposeUiTest {
            val result = showResults(dark = dark, colorMode = mode)
            runOnIdle { assertEquals(0, result.selected) }
            onRoot().performKeyInput { pressKey(Key.Enter) }
            runOnIdle { assertEquals(0, result.opened) }
        }
    }

    @Test fun downThenEnterOpensSecondResult() = runComposeUiTest {
        val result = showResults()
        onRoot().performKeyInput { pressKey(Key.DirectionDown); pressKey(Key.Enter) }
        runOnIdle { assertEquals(1, result.opened) }
    }

    @Test fun repeatedDownKeepsSelectedResultVisible() = runComposeUiTest {
        val result = showResults(count = 40)
        repeat(20) { onRoot().performKeyInput { pressKey(Key.DirectionDown) } }
        waitForIdle()
        runOnIdle {
            assertEquals(20, result.selected)
            assertTrue(result.listState.layoutInfo.visibleItemsInfo.any { it.index == 20 })
        }
        onRoot().performKeyInput { pressKey(Key.Enter) }
        runOnIdle { assertEquals(20, result.opened) }
    }

    @Test fun upAtFirstResultReturnsToQuery() = runComposeUiTest {
        val result = showResults()
        onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        runOnIdle { assertEquals(listOf<String?>(null), result.edits) }
    }

    @Test fun controlFindReturnsToQuery() = runComposeUiTest {
        val result = showResults()
        onRoot().performKeyInput { keyDown(Key.CtrlLeft); pressKey(Key.F); keyUp(Key.CtrlLeft) }
        runOnIdle { assertEquals(listOf<String?>(null), result.edits) }
    }

    @Test fun hiddenResultsLeaveFieldFocusedUntilShown() = runComposeUiTest {
        val visible = mutableStateOf(false)
        val requester = FocusRequester()
        var opened: Int? = null
        setContent {
            CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                Column {
                    BasicTextField("query", {}, Modifier.testTag("query").focusRequester(requester))
                    SearchSheetContent(
                        countLabel = "1 result", loading = false, error = null, empty = false,
                        listState = rememberLazyListState(), onDismissError = {}, onClose = {},
                        resultCount = 1, requestResultsFocus = visible.value,
                        onOpenResult = { opened = it },
                    ) { item { Text("result") } }
                }
            }
        }
        runOnIdle { requester.requestFocus() }
        onNodeWithTag("query").assertIsFocused()
        runOnIdle { visible.value = true }
        onRoot().performKeyInput { pressKey(Key.Enter) }
        runOnIdle { assertEquals(0, opened) }
    }

    @Test fun resultsWinAgainstToolbarReleaseAndUpFocusesRealField() = runComposeUiTest {
        val controller = ReadingSearchController(
            resolveDoc = { SearchDocumentInfo("KJV", SearchDocumentCategory.BIBLE, false, true) },
            onUnavailable = {}, onLeaveFullScreen = {}, onStartIndexing = {}, onRunSearch = { _, _, _ -> },
        )
        controller.open("light")
        controller.onResultOpened()
        var opened: String? = null
        setContent {
            val query by controller.queries.query.collectAsState()
            val request by controller.imeRequest.collectAsState()
            CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    Column {
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
                        val rows = remember { listOf(SwordResultRow("Gen.1.2", emptyList(), StyledText.plain("light"))) }
                        val expanded = remember { mutableStateMapOf<String, Boolean>() }
                        SearchSheetContent(
                            countLabel = "1 result", loading = false, error = null, empty = false,
                            listState = rememberLazyListState(), onDismissError = {}, onClose = {},
                            resultCount = rows.size, onOpenResult = { opened = rows[it].referenceName },
                            onEditQuery = controller::editQuery,
                        ) { selected ->
                            bibleResultRows(rows, expanded, false, { ref, _ -> opened = ref }, selected)
                        }
                    }
                }
            }
        }
        onRoot().performKeyInput { pressKey(Key.Enter) }
        runOnIdle { assertEquals("Gen.1.2", opened) }
        onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        onNodeWithTag(SEARCH_FIELD_TAG).assertIsFocused()
    }

    @Test fun desktopUsesTypedCharacterNotPhysicalKey() {
        val source = java.awt.Canvas()
        fun event(id: Int, char: Char, modifiers: Int = 0): androidx.compose.ui.input.key.KeyEvent {
            val native = java.awt.event.KeyEvent(source, id, 0L, modifiers,
                if (id == java.awt.event.KeyEvent.KEY_TYPED) java.awt.event.KeyEvent.VK_UNDEFINED else java.awt.event.KeyEvent.VK_A,
                char)
            return androidx.compose.ui.input.key.KeyEvent(
                Key.A,
                if (id == java.awt.event.KeyEvent.KEY_TYPED) androidx.compose.ui.input.key.KeyEventType.Unknown
                else androidx.compose.ui.input.key.KeyEventType.KeyDown,
                codePoint = char.code,
                isCtrlPressed = modifiers and java.awt.event.InputEvent.CTRL_DOWN_MASK != 0,
                isAltPressed = modifiers and java.awt.event.InputEvent.ALT_DOWN_MASK != 0,
                nativeEvent = native,
            )
        }
        assertEquals(null, searchResultKeyCharacter(event(java.awt.event.KeyEvent.KEY_PRESSED, 'a')))
        assertEquals("ä", searchResultKeyCharacter(event(java.awt.event.KeyEvent.KEY_TYPED, 'ä')))
        assertEquals("ł", searchResultKeyCharacter(event(java.awt.event.KeyEvent.KEY_TYPED, 'ł',
            java.awt.event.InputEvent.CTRL_DOWN_MASK or java.awt.event.InputEvent.ALT_DOWN_MASK)))
        assertEquals(null, searchResultKeyCharacter(androidx.compose.ui.input.key.KeyEvent(
            Key.A, androidx.compose.ui.input.key.KeyEventType.Unknown, codePoint = 0xffff,
        )))
    }
}
