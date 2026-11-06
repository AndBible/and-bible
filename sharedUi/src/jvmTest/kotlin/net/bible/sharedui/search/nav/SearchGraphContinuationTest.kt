package net.bible.sharedui.search.nav

import kotlin.test.assertTrue
import net.bible.sharedui.HostBackDispatcher
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.Text
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
        goBackInHistory: () -> Boolean = { false },
        historyBackEnabled: Boolean = true,
    ) = SearchNavDeps(
        exitHost = { error("unexpected host exit") }, setWindowTitle = {},
        setHistoryRoute = { _, _ -> }, clearHistoryRoute = {}, goBackInHistory = goBackInHistory,
        historyBackEnabled = historyBackEnabled,
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

    /** Feeds a predictive back gesture into the scene, as UIKit's edge-swipe recognizer does on iOS. */
    private class GestureInput : NavigationEventInput() {
        fun start() = dispatchOnBackStarted(NavigationEvent(touchX = 0f, touchY = 300f, progress = 0f, swipeEdge = NavigationEvent.EDGE_LEFT))
        fun progress(p: Float) = dispatchOnBackProgressed(NavigationEvent(touchX = 200f, touchY = 300f, progress = p, swipeEdge = NavigationEvent.EDGE_LEFT))
        fun complete() = dispatchOnBackCompleted()
    }

    /**
     * #4003 review: with the host shell (iOS/desktop) the search destination leaves back to the NavHost, whose own handler
     * runs the predictive pop. A History-first pass-through handler on top would receive the gesture's start and progress
     * and only pop on completion, so the previous screen never shows during the iOS edge swipe.
     */
    @Test
    fun hostSearchScreenKeepsThePredictiveBackGestureForTheNavHost() = runComposeUiTest {
        val gesture = GestureInput()
        lateinit var nav: NavHostController
        val popped = mutableListOf<String>()
        setContent {
            val dispatcher = HostBackDispatcher.currentDispatcher()
            DisposableEffect(dispatcher) {
                dispatcher?.addInput(gesture)
                onDispose { dispatcher?.removeInput(gesture) }
            }
            CompositionLocalProvider(LocalStrings provides strings) {
                AbTheme(darkTheme = false, disableAnimations = false) {
                    nav = rememberNavController()
                    AbNavHost(nav, startDestination = "home") {
                        composable("home") { Text("Home screen") }
                        searchNavGraph(nav, deps(goBackInHistory = { popped += "history"; nav.popBackStack() }, historyBackEnabled = false))
                    }
                }
            }
        }
        waitForIdle()
        runOnIdle { nav.navigate(NavRoutes.EPUB_SEARCH) }
        waitForIdle()
        assertTrue(onAllNodesWithText("Home screen").fetchSemanticsNodes().isEmpty(), "home must be covered before the gesture")
        mainClock.autoAdvance = false
        runOnIdle { gesture.start(); gesture.progress(0.5f) }
        repeat(5) { mainClock.advanceTimeByFrame() }
        assertTrue(onAllNodesWithText("Home screen").fetchSemanticsNodes().isNotEmpty(),
            "mid-gesture the NavHost must show the previous screen (predictive pop)")
        runOnIdle { gesture.complete() }
        mainClock.autoAdvance = true
        waitForIdle()
        assertEquals("home", nav.currentDestination?.route)
        assertEquals(emptyList(), popped, "the NavHost, not a History pass-through, pops on the host")
    }
}
