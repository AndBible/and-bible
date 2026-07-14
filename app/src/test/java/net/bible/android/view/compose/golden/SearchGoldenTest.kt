package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedcore.search.SearchBibleSection
import net.bible.sharedcore.search.SearchType
import net.bible.sharedui.search.SearchScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class SearchGoldenTest {
    private val translations = listOf("esv" to "ESV", "kjv" to "KJV")

    @Test fun search_default() {
        captureMatrix("Search", "default") {
            SearchScreen(
                title = "Search",
                query = "",
                searchType = SearchType.ALL_WORDS,
                bibleSection = SearchBibleSection.ALL,
                availableTranslations = translations,
                selectedTranslationIds = emptyList(),
                onQueryChange = {}, onSearchType = {}, onBibleSection = {},
                onTranslations = {}, onSubmit = {}, onNavigateUp = {},
            )
        }
    }

    @Test fun search_phrase_ot_selected() {
        captureGolden("Search", "phrase_ot_selected", EDGE_MODE) {
            SearchScreen(
                title = "Search",
                query = "in the beginning",
                searchType = SearchType.PHRASE,
                bibleSection = SearchBibleSection.OLD_TESTAMENT,
                availableTranslations = translations,
                selectedTranslationIds = listOf("esv"),
                onQueryChange = {}, onSearchType = {}, onBibleSection = {},
                onTranslations = {}, onSubmit = {}, onNavigateUp = {},
            )
        }
    }

    @Test fun search_empty_translations() {
        captureGolden("Search", "empty_translations", EDGE_MODE) {
            SearchScreen(
                title = "Search",
                query = "grace",
                searchType = SearchType.ANY_WORDS,
                bibleSection = SearchBibleSection.NEW_TESTAMENT,
                availableTranslations = emptyList(),
                selectedTranslationIds = emptyList(),
                onQueryChange = {}, onSearchType = {}, onBibleSection = {},
                onTranslations = {}, onSubmit = {}, onNavigateUp = {},
            )
        }
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun search_default_rtl() {
        captureRtl("Search", "default") {
            SearchScreen(
                title = "Search",
                query = "",
                searchType = SearchType.ALL_WORDS,
                bibleSection = SearchBibleSection.ALL,
                availableTranslations = translations,
                selectedTranslationIds = emptyList(),
                onQueryChange = {}, onSearchType = {}, onBibleSection = {},
                onTranslations = {}, onSubmit = {}, onNavigateUp = {},
            )
        }
    }
}
