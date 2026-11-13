package net.bible.sharedui.nav

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.components.AbNavHost
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Issue #4073: [AbNavHost] fades a popped screen out for 700 ms, and that screen stays clickable. A second tap
 * (up arrow, save button, dialog confirm) while it fades must not pop the reading start destination and so exit
 * the app; a tap during the ENTER fade must still work, and so must a programmatic cascade (a parent that pops
 * itself as soon as a child's result reaches it).
 */
@OptIn(ExperimentalTestApi::class)
class DoubleTapUpArrowTest {
    private val strings = iosStrings("en")

    private fun ComposeUiTest.host(graph: NavGraphBuilder.(NavHostController) -> Unit): () -> NavHostController {
        lateinit var nav: NavHostController
        mainClock.autoAdvance = false
        setContent {
            nav = rememberNavController()
            CompositionLocalProvider(LocalStrings provides strings) {
                AbTheme(darkTheme = false, disableAnimations = false) {
                    AbNavHost(nav, startDestination = NavRoutes.READING) {
                        composable(NavRoutes.READING) { Text("reading") }
                        graph(nav)
                    }
                }
            }
        }
        mainClock.advanceTimeBy(1_000)
        return { nav }
    }

    private fun ComposeUiTest.openSettings(nav: NavHostController, settleMs: Long = 1_000) {
        runOnIdle { nav.navigate(NavRoutes.SETTINGS) }
        mainClock.advanceTimeBy(settleMs)
        assertEquals(NavRoutes.SETTINGS, nav.currentBackStackEntry?.destination?.route)
    }

    private fun ComposeUiTest.upArrow() = onAllNodesWithContentDescription(strings.settingsEditorBack).onFirst()

    private fun routes(nav: NavHostController) = nav.currentBackStack.value.mapNotNull { it.destination.route }

    @Test
    fun secondTapDuringPopTransitionPopsNothingMore() = runComposeUiTest {
        var exits = 0
        val nav = host { nav ->
            composable(NavRoutes.SETTINGS) { entry ->
                AbScaffold(title = "settings", onNavigateUp = { nav.popOrExitFrom(entry) { exits++ } }) { }
            }
        }()
        openSettings(nav)

        upArrow().performClick()
        mainClock.advanceTimeBy(100) // mid-transition: settings is still drawn
        upArrow().performClick()
        mainClock.advanceTimeBy(2_000)

        assertEquals(0, exits)
        assertEquals(listOf(NavRoutes.READING), routes(nav))
    }

    @Test
    fun tapDuringEnterTransitionPopsExactlyOnce() = runComposeUiTest {
        var exits = 0
        val nav = host { nav ->
            composable(NavRoutes.SETTINGS) { entry ->
                AbScaffold(title = "settings", onNavigateUp = { nav.popOrExitFrom(entry) { exits++ } }) { }
            }
        }()
        openSettings(nav, settleMs = 100) // settings is still fading in, its entry not RESUMED yet

        upArrow().performClick()
        mainClock.advanceTimeBy(2_000)

        assertEquals(0, exits)
        assertEquals(listOf(NavRoutes.READING), routes(nav), "the first tap must pop, even mid enter fade")
    }

    @Test
    fun secondSaveDuringPopTransitionPopsNothingMore() = runComposeUiTest {
        var exits = 0
        var saves = 0
        val nav = host { nav ->
            composable(NavRoutes.SETTINGS) { entry ->
                // The AI filter / MyDocuments save shape: the save itself also runs only for the current entry.
                Button(onClick = { if (nav.isCurrentEntry(entry)) { saves++; nav.popOrExitFrom(entry) { exits++ } } }) {
                    Text("save")
                }
            }
        }()
        openSettings(nav)

        val save = onAllNodesWithText("save").onFirst()
        save.performClick()
        mainClock.advanceTimeBy(100)
        save.performClick()
        mainClock.advanceTimeBy(2_000)

        assertEquals(0, exits)
        assertEquals(1, saves, "the second tap must not save again")
        assertEquals(listOf(NavRoutes.READING), routes(nav))
    }

    @Test
    fun secondResultDeliveryDuringPopTransitionDeliversNothingMore() = runComposeUiTest {
        val delivered = mutableListOf<String>()
        val channel = NavResultChannel<String> { delivered += "exit:$it" }
        val nav = host { nav ->
            composable(NavRoutes.SETTINGS) { entry ->
                AbScaffold(title = "settings", onNavigateUp = { channel.deliver(nav, entry, "r") }) { }
            }
        }()
        openSettings(nav)

        upArrow().performClick()
        mainClock.advanceTimeBy(100)
        upArrow().performClick()
        mainClock.advanceTimeBy(2_000)

        assertEquals(emptyList(), delivered, "the result is published to the parent, never exits the host")
        assertEquals(listOf(NavRoutes.READING), routes(nav))
    }

    /** A dropped pop or delivery is logged (#4073 follow-up), so a lost result after async work can be traced. */
    @Test
    fun droppedPopsAndDeliveriesAreLogged() = runComposeUiTest {
        val warnings = mutableListOf<String>()
        net.bible.sharedcore.log.Log.sinkOverride = { level, _, msg, _ ->
            if (level == net.bible.sharedcore.log.LogLevel.WARN) synchronized(warnings) { warnings += msg }
        }
        try {
            val channel = NavResultChannel<String> { }
            var exits = 0
            val nav = host { nav ->
                composable(NavRoutes.SETTINGS) { entry ->
                    AbScaffold(title = "settings", onNavigateUp = { nav.popOrExitFrom(entry) { exits++ } }) { }
                    Button(onClick = { channel.deliver(nav, entry, "r") }) { Text("deliver") }
                }
            }()
            openSettings(nav)

            upArrow().performClick()
            mainClock.advanceTimeBy(100)
            upArrow().performClick()
            onAllNodesWithText("deliver").onFirst().performClick()
            mainClock.advanceTimeBy(2_000)

            val dropped = synchronized(warnings) { warnings.toList() }
            assertEquals(2, dropped.size, "one warning per dropped call: $dropped")
            dropped.forEach { assert(it.contains(NavRoutes.SETTINGS)) { "the warning names the requesting route: $it" } }
        } finally {
            net.bible.sharedcore.log.Log.sinkOverride = null
        }
    }

    /**
     * MyDocumentPages -> MyDocuments -> exit host: the parent consumes the child's result while it is still fading
     * in and delivers its own at once. Its entry is current, so that delivery must go through.
     */
    @Test
    fun programmaticCascadeDuringEnterTransitionStillPops() = runComposeUiTest {
        val exited = mutableListOf<String>()
        val childResults = NavResultChannel<String> { exited += "child:$it" }
        val parentResults = NavResultChannel<String> { exited += "parent:$it" }
        val nav = host { nav ->
            composable(PARENT) { entry ->
                val pending by childResults.pending.collectAsState()
                LaunchedEffect(pending) {
                    childResults.consume()?.let { parentResults.deliver(nav, entry, "after-$it") }
                }
                Text("parent")
            }
            composable(CHILD) { entry ->
                Button(onClick = { childResults.deliver(nav, entry, "saved") }) { Text("child-save") }
            }
        }()
        runOnIdle { nav.navigate(PARENT); nav.navigate(CHILD) }
        mainClock.advanceTimeBy(1_000)

        onAllNodesWithText("child-save").onFirst().performClick()
        mainClock.advanceTimeBy(2_000)

        assertEquals(emptyList(), exited, "both results go to parents, nothing exits the host")
        assertEquals(listOf(NavRoutes.READING), routes(nav), "the parent popped itself during its enter fade")
    }

    /**
     * A host-memoised controller (e.g. the bookmark list's, kept across visits) holds callbacks built for the FIRST
     * entry of its destination. On a later visit those callbacks name a destroyed entry; they must still pop the new
     * entry of the same destination once, and a second call during that pop's fade must still be dropped.
     */
    @Test
    fun memoisedCallbackFromAnEarlierVisitPopsOnceOnTheNextVisit() = runComposeUiTest {
        var exits = 0
        var memo: (() -> Unit)? = null
        val nav = host { nav ->
            composable(NavRoutes.SETTINGS) { entry ->
                val pop = memo ?: { nav.popOrExitFrom(entry) { exits++ } }.also { memo = it }
                Button(onClick = pop) { Text("save") }
            }
        }()
        openSettings(nav)
        onAllNodesWithText("save").onFirst().performClick()
        mainClock.advanceTimeBy(2_000)
        assertEquals(listOf(NavRoutes.READING), routes(nav))

        openSettings(nav) // a new entry; the memoised callback still names the first one
        val save = onAllNodesWithText("save").onFirst()
        save.performClick()
        mainClock.advanceTimeBy(100)
        save.performClick()
        mainClock.advanceTimeBy(2_000)

        assertEquals(0, exits)
        assertEquals(listOf(NavRoutes.READING), routes(nav))
    }

    private companion object {
        const val PARENT = "test_parent"
        const val CHILD = "test_child"
    }
}

