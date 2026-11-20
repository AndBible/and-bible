package net.bible.sharedui.nav

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventInput
import net.bible.sharedui.HostBackDispatcher
import net.bible.sharedui.PassThroughBackHandler
import net.bible.sharedui.components.AbNavHost
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #4170 removed the full-screen search routes (`search/epub`, `search/index`, `search/indexProgress`), whose graph held
 * these two fixtures (from #4164 and #4003); they now run against live destinations.
 */
@OptIn(ExperimentalTestApi::class)
class RetiredRouteNavigationTest {
    private val strings = iosStrings("en")

    /**
     * A stale route naming a removed destination (a restored back stack, an old deep link) must fail the way every
     * unknown route does: a catchable `IllegalArgumentException` from `navigate`, which
     * `NavHostComposeActivity.navigateToRoute` logs and ignores, leaving the current destination alone.
     */
    @Test
    fun aRemovedSearchRouteIsNotRoutableAndLeavesTheCurrentDestination() = runComposeUiTest {
        lateinit var nav: NavHostController
        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                AbTheme(darkTheme = false, disableAnimations = true) {
                    nav = rememberNavController()
                    AbNavHost(nav, startDestination = "home") {
                        composable("home") { Text("Home screen") }
                    }
                }
            }
        }
        waitForIdle()
        val stale = listOf(
            "search/form?searchText=love&searchType=PHRASE",
            "search/epub",
            "search/index?searchText=love&searchDocument=ESV&isStrongsSearch=false",
            "search/indexProgress?searchDocument=ESV&isStrongsSearch=false",
        )
        for (route in stale) {
            var failure: Throwable? = null
            runOnIdle { failure = runCatching { nav.navigate(route) }.exceptionOrNull() }
            waitForIdle()
            assertTrue(failure is IllegalArgumentException, "an unknown route must surface as IllegalArgumentException, was $failure ($route)")
            assertEquals("home", nav.currentDestination?.route, route)
        }
        assertTrue(onAllNodesWithText("Home screen").fetchSemanticsNodes().isNotEmpty())
    }

    /** Feeds a predictive back gesture into the scene, as UIKit's edge-swipe recognizer does on iOS. */
    private class GestureInput : NavigationEventInput() {
        fun start() = dispatchOnBackStarted(NavigationEvent(touchX = 0f, touchY = 300f, progress = 0f, swipeEdge = NavigationEvent.EDGE_LEFT))
        fun progress(p: Float) = dispatchOnBackProgressed(NavigationEvent(touchX = 200f, touchY = 300f, progress = p, swipeEdge = NavigationEvent.EDGE_LEFT))
        fun complete() = dispatchOnBackCompleted()
    }

    /**
     * #4003: with the host shell (iOS/desktop) a destination that publishes a history route leaves back to the NavHost,
     * whose own handler runs the predictive pop. A History-first pass-through handler on top would receive the
     * gesture's start and progress and only pop on completion, so the previous screen never shows during the iOS edge
     * swipe. That is why the graphs install `PassThroughBackHandler(enabled = historyBackEnabled)`, with it false here.
     */
    @Test
    fun aDestinationWithHistoryBackDisabledKeepsThePredictiveBackGestureForTheNavHost() = runComposeUiTest {
        val gesture = GestureInput()
        lateinit var nav: NavHostController
        val passedThrough = mutableListOf<String>()
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
                        composable("live") {
                            PassThroughBackHandler(enabled = false) { passThrough -> passedThrough += "history"; passThrough() }
                            Text("Live screen")
                        }
                    }
                }
            }
        }
        waitForIdle()
        runOnIdle { nav.navigate("live") }
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
        assertEquals(emptyList(), passedThrough, "the NavHost, not a History pass-through, pops on the host")
    }
}
