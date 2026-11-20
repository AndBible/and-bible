package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedcore.search.SearchBibleSection
import net.bible.sharedcore.search.SearchType
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The legacy search form (`SearchScreen`) and its goldens were removed in #4164; this class keeps the settings
 * sheet golden. There is deliberately NO golden for the recent-terms menu open. It existed until the search settings
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

    @Test fun search_settings_open() {
        captureGolden("Search", "settings_open", EDGE_MODE) {
            net.bible.sharedui.components.AbSettingsSummarySheetContent(
                onClose = {}, title = net.bible.sharedui.strings.LocalStrings.current.searchOptions,
            ) {
                net.bible.sharedui.search.BibleSearchSettings(
                    searchType = SearchType.PHRASE,
                    bibleSection = SearchBibleSection.OLD_TESTAMENT,
                    availableTranslations = translations,
                    selectedTranslationIds = listOf("esv"),
                    currentBookName = "Genesis",
                    onSearchType = {}, onBibleSection = {}, onTranslations = {},
                )
            }
        }
    }
}
