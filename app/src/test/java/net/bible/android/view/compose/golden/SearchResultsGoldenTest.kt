package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedcore.search.BibleOption
import net.bible.sharedcore.search.StyledRun
import net.bible.sharedcore.search.StyledText
import net.bible.sharedcore.search.SwordResultRow
import net.bible.sharedcore.search.TranslationMatchVd
import net.bible.sharedui.search.SearchResultsScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class SearchResultsGoldenTest {

    /** A preview with a highlighted query term in the middle, so highlight styling shows. */
    private fun preview(before: String, hit: String, after: String) = StyledText(
        listOf(
            StyledRun(before),
            StyledRun(hit, highlight = true),
            StyledRun(after),
        ),
    )

    private val rows = listOf(
        SwordResultRow(
            referenceName = "John 3:16",
            matches = listOf(
                TranslationMatchVd("esv", "ESV", preview("For God so ", "loved", " the world…")),
                TranslationMatchVd("kjv", "KJV", preview("For God so ", "loved", " the world, that…")),
            ),
            primaryPreview = preview("For God so ", "loved", " the world…"),
        ),
        SwordResultRow(
            referenceName = "1 Corinthians 13:4",
            matches = listOf(
                TranslationMatchVd("esv", "ESV", preview("Love is patient and ", "kind", "…")),
                TranslationMatchVd("kjv", "KJV", preview("Charity suffereth long, and is ", "kind", "…")),
                TranslationMatchVd("niv", "NIV", preview("Love is patient, love is ", "kind", "…")),
            ),
            primaryPreview = preview("Love is patient and ", "kind", "…"),
        ),
        SwordResultRow(
            referenceName = "Psalm 23:1",
            matches = listOf(
                TranslationMatchVd("esv", "ESV", preview("The LORD is my ", "shepherd", "; I shall not want.")),
            ),
            primaryPreview = preview("The LORD is my ", "shepherd", "; I shall not want."),
        ),
    )

    private val candidates = listOf(
        BibleOption("KJV", "KJV", hasStrongs = true),
        BibleOption("ESV", "ESV", hasStrongs = false),
        BibleOption("NIV", "NIV", hasStrongs = false),
        BibleOption("BSB", "BSB", hasStrongs = true),
    )

    private fun screen(
        loading: Boolean = false,
        rows: List<SwordResultRow> = this.rows,
        scriptureToggleVisible: Boolean = false,
        scriptureShown: Boolean = false,
        initiallyExpanded: Set<String> = emptySet(),
        initiallyChooserOpen: Boolean = false,
    ) = @androidx.compose.runtime.Composable {
        SearchResultsScreen(
            title = "Search results",
            loading = loading,
            rows = rows,
            scriptureToggleVisible = scriptureToggleVisible,
            scriptureShown = scriptureShown,
            onToggleScripture = {},
            onOpenInWindow = {},
            onSelect = { _, _ -> },
            onNavigateUp = {},
            selectedAbbreviations = "KJV, ESV",
            candidates = candidates,
            selectedIds = listOf("KJV", "ESV"),
            onSelectTranslations = {},
            initiallyExpanded = initiallyExpanded,
            initiallyChooserOpen = initiallyChooserOpen,
        )
    }

    @Test fun results() {
        captureMatrix("SearchResults", "results", screen())
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun results_rtl() {
        captureRtl("SearchResults", "results", screen())
    }

    @Test fun expanded() {
        captureGolden("SearchResults", "expanded", EDGE_MODE, content = screen(initiallyExpanded = setOf("1 Corinthians 13:4")))
    }

    @Test fun scripture_toggle() {
        captureGolden("SearchResults", "scripture_toggle", EDGE_MODE, content = screen(scriptureToggleVisible = true, scriptureShown = true))
    }

    @Test fun translation_chooser() {
        captureGolden("SearchResults", "translation_chooser", EDGE_MODE, content = screen(initiallyChooserOpen = true))
    }

    @Test fun empty() {
        captureGolden("SearchResults", "empty", EDGE_MODE, content = screen(rows = emptyList()))
    }

    @Test fun loading() {
        captureGolden("SearchResults", "loading", EDGE_MODE, content = screen(loading = true))
    }
}
