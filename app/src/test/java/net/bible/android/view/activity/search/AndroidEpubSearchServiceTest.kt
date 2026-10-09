package net.bible.android.view.activity.search

import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.sharedcore.search.EpubSearchMode
import net.bible.sharedcore.search.StyledRun
import net.bible.sharedcore.search.parseHighlightHtml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AndroidEpubSearchServiceTest {
    // Live FTS5 search is covered by EpubSearchTest under the bundled SQLite driver. This test locks
    // the highlight->StyledText mapping the host relies on.
    @Test fun highlight_html_maps_to_bold_run() {
        val st = parseHighlightHtml("say <b>peace</b>")
        assertEquals(listOf(StyledRun("say ", false), StyledRun("peace", true)), st.runs)
    }

    // Driver-independent short-circuits: an unknown/missing docId resolves to no Book, so both
    // entry points return early WITHOUT ever touching the SQLite driver / epubBackend state.
    // (`runBlocking` here matches the app-module test convention — coroutines-test is not on this
    // module's classpath; no virtual time is needed for a short-circuit path.)
    @Test fun isIndexed_is_false_for_unknown_doc() {
        assertFalse(AndroidEpubSearchService().isIndexed("no-such-doc"))
    }

    @Test fun searchEpub_returns_empty_for_unknown_doc() = runBlocking {
        val rows = AndroidEpubSearchService().searchEpub("no-such-doc", "x", EpubSearchMode.ALL_WORDS)
        assertEquals(emptyList<Any>(), rows)
    }
}
