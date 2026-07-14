package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedcore.search.EpubResultRow
import net.bible.sharedcore.search.StyledRun
import net.bible.sharedcore.search.StyledText
import net.bible.sharedui.search.EpubSearchResultsScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class EpubSearchResultsGoldenTest {

    /** An FTS snippet with a bold-highlighted match term in the middle, so highlight styling shows. */
    private fun snippet(before: String, hit: String, after: String) = StyledText(
        listOf(
            StyledRun(before),
            StyledRun(hit, bold = true, highlight = true),
            StyledRun(after),
        ),
    )

    private val rows = listOf(
        EpubResultRow("k1", "Chapter 1 — Introduction", snippet("…the ", "grace", " of God abounds…")),
        EpubResultRow("k2", "Chapter 4 — On Faith", snippet("…justified by ", "grace", " through faith…")),
        EpubResultRow("k3", "Appendix — Notes", snippet("…a further note on ", "grace", " and works…")),
    )

    private fun screen(
        loading: Boolean = false,
        rows: List<EpubResultRow> = this.rows,
    ) = @androidx.compose.runtime.Composable {
        EpubSearchResultsScreen(
            title = "Search results",
            loading = loading,
            rows = rows,
            onSelect = {},
            onNavigateUp = {},
        )
    }

    @Test fun results() {
        captureMatrix("EpubSearchResults", "results", screen())
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun results_rtl() {
        captureRtl("EpubSearchResults", "results", screen())
    }

    @Test fun empty() {
        captureGolden("EpubSearchResults", "empty", EDGE_MODE, content = screen(rows = emptyList()))
    }

    @Test fun loading() {
        captureGolden("EpubSearchResults", "loading", EDGE_MODE, content = screen(loading = true))
    }
}
