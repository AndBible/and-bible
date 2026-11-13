package net.bible.sharedui.app.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.components.AbNavHost
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.nav.popOrExitGuarded
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Issue #4073: a second tap on an up arrow while the first pop's (slow) exit transition still shows the
 * screen must not pop the reading start destination and so exit the app.
 */
@OptIn(ExperimentalTestApi::class)
class DoubleTapUpArrowTest {
    private val strings = iosStrings("en")

    @Test
    fun secondTapDuringPopTransitionPopsNothingMore() = runComposeUiTest {
        var exits = 0
        lateinit var nav: NavHostController
        mainClock.autoAdvance = false
        setContent {
            nav = rememberNavController()
            CompositionLocalProvider(LocalStrings provides strings) {
                AbTheme(darkTheme = false, disableAnimations = false) {
                    AbNavHost(nav, startDestination = NavRoutes.READING) {
                        composable(NavRoutes.READING) { Text("reading") }
                        composable(NavRoutes.SETTINGS) {
                            AbScaffold(title = "settings", onNavigateUp = { nav.popOrExitGuarded { exits++ } }) { }
                        }
                    }
                }
            }
        }
        mainClock.advanceTimeBy(1_000)
        runOnIdle { nav.navigate(NavRoutes.SETTINGS) }
        mainClock.advanceTimeBy(1_000)
        assertEquals(NavRoutes.SETTINGS, nav.currentBackStackEntry?.destination?.route)

        val up = onAllNodesWithContentDescription(strings.settingsEditorBack).onFirst()
        up.performClick()
        mainClock.advanceTimeBy(100) // mid-transition: settings is still drawn
        up.performClick()
        mainClock.advanceTimeBy(2_000)

        assertEquals(0, exits)
        assertNotNull(nav.currentBackStackEntry, "reading must still be on the stack")
        assertEquals(NavRoutes.READING, nav.currentBackStackEntry?.destination?.route)
    }

    @Test
    fun secondResultDeliveryDuringPopTransitionDeliversNothingMore() = runComposeUiTest {
        val delivered = mutableListOf<String>()
        val channel = NavResultChannel<String> { delivered += "exit:$it" }
        lateinit var nav: NavHostController
        mainClock.autoAdvance = false
        setContent {
            nav = rememberNavController()
            CompositionLocalProvider(LocalStrings provides strings) {
                AbTheme(darkTheme = false, disableAnimations = false) {
                    AbNavHost(nav, startDestination = NavRoutes.READING) {
                        composable(NavRoutes.READING) { Text("reading") }
                        composable(NavRoutes.SETTINGS) {
                            AbScaffold(title = "settings", onNavigateUp = { channel.deliver(nav, "r") }) { }
                        }
                    }
                }
            }
        }
        mainClock.advanceTimeBy(1_000)
        runOnIdle { nav.navigate(NavRoutes.SETTINGS) }
        mainClock.advanceTimeBy(1_000)
        val up = onAllNodesWithContentDescription(strings.settingsEditorBack).onFirst()
        up.performClick()
        mainClock.advanceTimeBy(100)
        up.performClick()
        mainClock.advanceTimeBy(2_000)

        assertEquals(emptyList(), delivered, "the result is published to the parent, never exits the host")
        assertEquals(NavRoutes.READING, nav.currentBackStackEntry?.destination?.route)
    }
}
