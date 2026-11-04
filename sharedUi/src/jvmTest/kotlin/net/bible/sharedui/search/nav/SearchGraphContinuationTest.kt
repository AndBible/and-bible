package net.bible.sharedui.search.nav

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.search.EpubSearchMode
import net.bible.sharedcore.search.ReadingSearchRequest
import net.bible.sharedcore.search.SearchBibleSection
import net.bible.sharedcore.search.SearchFormController
import net.bible.sharedcore.search.SearchRequest
import net.bible.sharedcore.search.SearchType
import net.bible.sharedui.components.AbNavHost
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Exercises actual graph callbacks and Navigation argument decoding, not only route builders. */
@OptIn(ExperimentalTestApi::class)
class SearchGraphContinuationTest {
    private val strings = iosStrings("en")

    private fun deps(
        submit: (SearchRequest) -> SearchSubmission = { error("unexpected form submission") },
        mode: EpubSearchMode = EpubSearchMode.FTS,
    ) = SearchNavDeps(
        exitHost = { error("unexpected host exit") }, setWindowTitle = {},
        setHistoryRoute = { _, _ -> }, clearHistoryRoute = {}, goBackInHistory = { false },
        searchForm = SearchFormDeps(
            prepare = { SearchFormSetup("Find", "John") },
            loadTranslations = { TranslationSelection(listOf("KJV" to "KJV", "ESV" to "ESV"), listOf("KJV", "ESV")) },
            controllerFor = { SearchFormController(it, persistTranslations = {}) }, submit = submit,
        ),
        searchIndex = SearchIndexPromptDeps(
            resolve = { IndexTarget("Other unindexed translation", "Index target", false) },
            createIndex = {}, title = "Index",
        ),
        searchIndexProgress = SearchIndexProgressDeps(
            requestNotificationPermission = {}, observeJobs = { _, finished -> finished(); {} },
            awaitIndexed = { IndexOutcome.INDEXED }, title = "Index",
        ),
        epubSearch = EpubSearchFormDeps(
            prepare = { EpubSearchSetup("EPUB", "Book / 1") }, loadMode = { mode }, saveMode = {},
            askBeforeOpeningLink = { false }, onOpenExternal = {},
        ),
    )

    @Test
    fun formSubmitNavigatesToReadingWithExactDecoratedScope() = runComposeUiTest {
        val query = "100% + love & peace"
        val decorated = "+[John] \"$query\""
        var submitted: SearchRequest? = null
        var received: ReadingSearchRequest? = null
        lateinit var nav: NavHostController
        val dependencies = deps(submit = {
            submitted = it
            SearchSubmission.Results(it.toReadingSearchRequest(decorated, "KJV"))
        })
        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                AbTheme(darkTheme = false, disableAnimations = true) {
                    nav = rememberNavController()
                    AbNavHost(nav, startDestination = NavRoutes.searchForm(query, SearchType.PHRASE.name, SearchBibleSection.CURRENT_BOOK.name, "John")) {
                        searchNavGraph(nav, dependencies)
                        composable(NavRoutes.READING_SEARCH_PATTERN, arguments = listOf(navArgument("request") { type = NavType.StringType })) { entry ->
                            LaunchedEffect(Unit) {
                                received = NavRoutes.decodeReadingSearchArgument(entry.arguments!!.read { getString("request") })
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
        onNode(hasSetTextAction()).performImeAction()
        waitForIdle()
        assertEquals(SearchRequest(query, SearchType.PHRASE, SearchBibleSection.CURRENT_BOOK, listOf("KJV", "ESV"), "John"), submitted)
        assertEquals(ReadingSearchRequest(decorated, "KJV", listOf("KJV", "ESV"), preDecorated = true,
            section = SearchBibleSection.CURRENT_BOOK, type = SearchType.PHRASE), received)
        runOnIdle {
            assertFalse(nav.popBackStack(), "the submitted form must not remain under the handoff")
        }
    }

    @Test
    fun epubSubmitNavigatesToReadingWithItsSelectedMode() = runComposeUiTest {
        val query = "100% + love & peace"
        var received: ReadingSearchRequest? = null
        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                AbTheme(darkTheme = false, disableAnimations = true) {
                    val nav = rememberNavController()
                    AbNavHost(nav, startDestination = NavRoutes.EPUB_SEARCH) {
                        searchNavGraph(nav, deps(mode = EpubSearchMode.PHRASE))
                        composable(NavRoutes.READING_SEARCH_PATTERN, arguments = listOf(navArgument("request") { type = NavType.StringType })) { entry ->
                            LaunchedEffect(Unit) {
                                received = NavRoutes.decodeReadingSearchArgument(entry.arguments!!.read { getString("request") })
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
        onNode(hasSetTextAction()).performTextInput(query)
        onNode(hasSetTextAction()).performImeAction()
        waitForIdle()
        assertEquals(ReadingSearchRequest(query, "Book / 1", epubMode = EpubSearchMode.PHRASE), received)
    }

    @Test
    fun indexPromptAndProgressForwardTypedRequestWithoutReplacingSearchDocument() = runComposeUiTest {
        val request = ReadingSearchRequest(
            query = "100% + \"love & peace\"", documentId = "Original book", translationIds = listOf("KJV", "ESV"),
            preDecorated = true, section = SearchBibleSection.NEW_TESTAMENT, type = SearchType.PHRASE,
            epubMode = EpubSearchMode.ANY_WORD,
        )
        var received: ReadingSearchRequest? = null
        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                AbTheme(darkTheme = false, disableAnimations = true) {
                    val nav = rememberNavController()
                    AbNavHost(nav, startDestination = NavRoutes.searchIndex(searchDocument = "ESV", readingRequest = request)) {
                        searchNavGraph(nav, deps())
                        composable(NavRoutes.READING_SEARCH_PATTERN, arguments = listOf(navArgument("request") { type = NavType.StringType })) { entry ->
                            LaunchedEffect(Unit) {
                                received = NavRoutes.decodeReadingSearchArgument(entry.arguments!!.read { getString("request") })
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithText(strings.create).performClick()
        waitForIdle()
        assertEquals(request, received)
    }
}
