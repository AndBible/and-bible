package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedcore.search.EpubSearchMode
import net.bible.sharedui.search.EpubSearchScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class EpubSearchGoldenTest {
    @Test fun epubsearch_default() {
        captureMatrix("EpubSearch", "default") {
            EpubSearchScreen(
                title = "Search",
                query = "",
                mode = EpubSearchMode.ALL_WORDS,
                onQueryChange = {}, onMode = {}, onSubmit = {}, onHelp = {}, onNavigateUp = {},
            )
        }
    }

    @Test fun epubsearch_fts_selected() {
        captureGolden("EpubSearch", "fts_selected", EDGE_MODE) {
            EpubSearchScreen(
                title = "Search",
                query = "grace NEAR faith",
                mode = EpubSearchMode.FTS,
                onQueryChange = {}, onMode = {}, onSubmit = {}, onHelp = {}, onNavigateUp = {},
            )
        }
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun epubsearch_default_rtl() {
        captureRtl("EpubSearch", "default") {
            EpubSearchScreen(
                title = "Search",
                query = "",
                mode = EpubSearchMode.ALL_WORDS,
                onQueryChange = {}, onMode = {}, onSubmit = {}, onHelp = {}, onNavigateUp = {},
            )
        }
    }
}
