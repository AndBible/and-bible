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

/**
 * There is deliberately NO golden for the recent-terms menu open. It existed until the search settings
 * gained an `AbDropdownField` (an `ExposedDropdownMenuBox`): two popup hosts in one Robolectric
 * NATIVE-graphics capture hang Roborazzi indefinitely, and with that test present the whole unit suite
 * never finishes. One popup was survivable; two are not. `ReadingToolbarSearchGoldenTest` had already
 * reached the same conclusion for the reading toolbar's own recent-terms menu and says so in its kdoc —
 * this class simply predated that decision. What the capture covered is covered without pixels by
 * `ReadingSearchBarStateTest` and the search controller's tests.
 */
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

    @Test fun search_settings_open() {
        captureGolden("Search", "settings_open", EDGE_MODE) {
            SearchScreen(
                title = "Search",
                query = "in the beginning",
                searchType = SearchType.PHRASE,
                bibleSection = SearchBibleSection.OLD_TESTAMENT,
                availableTranslations = translations,
                selectedTranslationIds = listOf("esv"),
                onQueryChange = {}, onSearchType = {}, onBibleSection = {},
                onTranslations = {}, onSubmit = {}, onNavigateUp = {},
                initiallySettingsOpen = true,
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
