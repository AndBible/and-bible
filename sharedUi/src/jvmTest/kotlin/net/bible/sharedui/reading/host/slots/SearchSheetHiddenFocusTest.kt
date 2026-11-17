package net.bible.sharedui.reading.host.slots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.cancel
import net.bible.sharedcore.search.MultiSearchResults
import net.bible.sharedcore.search.SearchResultsCache
import net.bible.sharedcore.search.StyledText
import net.bible.sharedcore.search.SwordResultRow
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalTestApi::class)
class SearchSheetHiddenFocusTest {
    @Test fun hiddenResultsCannotRetainOrRegainKeyboardFocus() = runComposeUiTest {
        val fixture = SearchFixture()
        val cache = SearchResultsCache()
        val state = ReadingSearchState(
            fixture.settings, fixture.scope, fixture.platform, fixture.jobs,
            ReadingSearchServices(fixture.bible, FakeEpubSearch(), cache, fixture.index),
            onLeaveFullScreen = {},
        )
        val session = state.session
        val rows = listOf("Gen.1.1", "Gen.1.2").map {
            SwordResultRow(it, emptyList(), StyledText.plain("light"))
        }
        try {
            fixture.platform.documents["KJV"] = fixture.platform.current!!
            state.openSearch("light")
            val request = assertNotNull(session.lastSearchRequestForTest)
            cache.put(request, MultiSearchResults(rows, emptyList(), 2))
            session.runSearch("KJV", "light")
            val strings = iosStrings("en")
            lateinit var focusManager: FocusManager
            setContent {
                focusManager = LocalFocusManager.current
                CompositionLocalProvider(LocalStrings provides strings) {
                    Column {
                        BasicTextField("query", {}, Modifier.testTag("query"))
                        SearchSheetSlotContent(session, strings)
                        BasicTextField("reader", {}, Modifier.testTag("reader"))
                    }
                }
            }
            onNodeWithText("Gen.1.1").assertExists()
            onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            runOnIdle { assertEquals(1, session.resultKeyboardSelection.value) }
            runOnIdle { session.controller.closeSheet() }
            onRoot().performKeyInput { pressKey(Key.DirectionUp); pressKey(Key.Enter); pressKey(Key.Tab) }
            runOnIdle { assertEquals(1, session.resultKeyboardSelection.value) }
            onNodeWithText("Gen.1.1").assertDoesNotExist()
            onNodeWithTag("query").performClick()
            // The headless test host does not perform the native window's Tab traversal.
            runOnIdle { focusManager.moveFocus(FocusDirection.Next) }
            onNodeWithTag("reader").assertIsFocused()
            // The click above was a pointer press; typing the query (a hardware key in the real Find
            // field) makes reopened results keyboard-focused again. Touch users keep their focus.
            runOnIdle { net.bible.sharedui.search.FindKeyboardInput.lastWasHardwareKey = true }

            runOnIdle { session.controller.showResults() }
            onNodeWithText("Gen.1.2").assertExists()
            runOnIdle { assertEquals(1, session.resultKeyboardSelection.value) }
            onRoot().performKeyInput { pressKey(Key.DirectionUp); pressKey(Key.Enter) }
            runOnIdle {
                assertEquals(0, session.resultKeyboardSelection.value)
                assertEquals("Gen.1.1", fixture.platform.shown.single().second.getName())
            }
        } finally {
            net.bible.sharedui.search.FindKeyboardInput.lastWasHardwareKey = false
            state.dispose()
            fixture.state.dispose()
            fixture.scope.cancel()
        }
    }
}
