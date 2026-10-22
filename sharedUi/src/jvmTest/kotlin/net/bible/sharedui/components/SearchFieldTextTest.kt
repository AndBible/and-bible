package net.bible.sharedui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import net.bible.sharedcore.navigation.DictRow
import net.bible.sharedcore.search.EpubSearchMode
import net.bible.sharedcore.search.SearchBibleSection
import net.bible.sharedcore.search.SearchType
import net.bible.sharedui.navigation.ChooseDictionaryWordScreen
import net.bible.sharedui.search.EpubSearchScreen
import net.bible.sharedui.search.SearchScreen
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Non-reading [AbSearchField] callers (dictionary word chooser, EPUB search, SWORD search): the controller's query comes
 * back through `collectAsState` a dispatch after the keystroke. The echo is held in a queue at the real screen's
 * `query` / `onQueryChange` boundary; two keystrokes before the first echo must both survive. The reading toolbar's
 * search field is R3's and not covered here.
 */
@OptIn(ExperimentalTestApi::class)
class SearchFieldTextTest {
    private val strings = iosStrings("en")

    private class Harness {
        var shown by mutableStateOf("")
        val queue = ArrayDeque<String>()
        val received = mutableListOf<String>()
        fun onChange(v: String) { received += v; queue.addLast(v) }
        fun releaseOne() { if (queue.isNotEmpty()) shown = queue.removeFirst() }
        fun releaseAll() { while (queue.isNotEmpty()) shown = queue.removeFirst() }
    }

    private fun ComposeUiTest.mount(content: @androidx.compose.runtime.Composable () -> Unit) = setContent {
        CompositionLocalProvider(LocalStrings provides strings) { AbTheme(darkTheme = false) { content() } }
    }

    private fun ComposeUiTest.field() = onNode(hasSetTextAction())
    private fun SemanticsNodeInteraction.text() = fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun ComposeUiTest.showDictionary(h: Harness) = mount {
        ChooseDictionaryWordScreen(
            title = "Dictionary", hint = "Find word", loading = false, query = h.shown,
            rows = emptyList<DictRow>(), error = null, loadSnippet = { "" },
            onQueryChange = h::onChange, onSelect = {}, onDismissError = {}, onNavigateUp = {},
        )
    }

    private fun ComposeUiTest.showEpub(h: Harness) = mount {
        EpubSearchScreen(
            title = "EPUB", query = h.shown, mode = EpubSearchMode.ALL_WORDS,
            onQueryChange = h::onChange, onMode = {}, onSubmit = {}, onHelp = {}, onNavigateUp = {},
        )
    }

    private fun ComposeUiTest.showSword(h: Harness, recent: List<String> = emptyList(), onRecent: (String) -> Unit = {}) = mount {
        SearchScreen(
            title = "Search", query = h.shown, searchType = SearchType.ALL_WORDS, bibleSection = SearchBibleSection.ALL,
            availableTranslations = emptyList(), selectedTranslationIds = emptyList(),
            onQueryChange = h::onChange, onSearchType = {}, onBibleSection = {}, onTranslations = {},
            onSubmit = {}, onNavigateUp = {}, recentTerms = recent, onRecentTermSelected = onRecent,
        )
    }

    /** Types "a", lets the screen recompose with the echo still held, then types "b". */
    private fun ComposeUiTest.typeTwoBeforeEcho(h: Harness): String {
        val node = field()
        node.performClick()
        node.performTextInput("a")
        waitForIdle()
        node.performTextInput("b")
        waitForIdle()
        val whileHeld = node.text()
        runOnIdle { h.releaseAll() }
        waitForIdle()
        assertEquals(listOf("a", "ab"), h.received)
        assertEquals("ab", h.shown)
        return whileHeld
    }

    @Test fun dictionaryWordFieldKeepsBothKeystrokesWhileTheEchoIsDelayed() = runComposeUiTest {
        val h = Harness(); showDictionary(h)
        assertEquals("ab", typeTwoBeforeEcho(h))
        assertEquals("ab", field().text())
    }

    @Test fun epubSearchFieldKeepsBothKeystrokesWhileTheEchoIsDelayed() = runComposeUiTest {
        val h = Harness(); showEpub(h)
        assertEquals("ab", typeTwoBeforeEcho(h))
        assertEquals("ab", field().text())
    }

    @Test fun swordSearchFieldKeepsBothKeystrokesWhileTheEchoIsDelayed() = runComposeUiTest {
        val h = Harness(); showSword(h)
        assertEquals("ab", typeTwoBeforeEcho(h))
        assertEquals("ab", field().text())
    }

    @Test fun aStaleEchoDeliveredAloneNeverOverwritesNewerInput() = runComposeUiTest {
        val h = Harness(); showEpub(h)
        val node = field()
        node.performClick()
        node.performTextInput("a")
        waitForIdle()
        node.performTextInput("b")
        waitForIdle()
        runOnIdle { h.releaseOne() } // "a" echoed, "ab" still held
        waitForIdle()
        assertEquals("ab", node.text())
        runOnIdle { h.releaseOne() }
        waitForIdle()
        assertEquals("ab", node.text())
    }

    @Test fun swordRecentTermSelectionStillWritesTheField() = runComposeUiTest {
        val h = Harness()
        showSword(h, recent = listOf("grace")) { term -> h.shown = term }
        val node = field()
        node.performClick()
        node.performTextInput("a")
        waitForIdle()
        runOnIdle { h.releaseAll() }
        waitForIdle()
        // A genuine external write (recent-term pick) must reach the field even after own echoes were absorbed.
        runOnIdle { h.shown = "grace" }
        waitForIdle()
        assertEquals("grace", node.text())
    }
}
