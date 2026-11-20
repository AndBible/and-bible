package net.bible.sharedui.help.nav

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import net.bible.sharedcore.docs.DocsLink
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.components.AbNavHost
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test

/** #4159: the Help route carries the help topic; the destination opens on the User guide tab with the topic's page highlighted. */
@OptIn(ExperimentalTestApi::class)
class HelpNavGraphTopicTest {
    private val strings = iosStrings("en")
    private val highlighted = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, strings.helpMatchingSection)
    private val deps = HelpNavDeps({}, {}, { emptyList() }, { "Version" }, {})

    private fun androidx.compose.ui.test.ComposeUiTest.host(start: String, onNav: (NavHostController) -> Unit = {}) = setContent {
        CompositionLocalProvider(LocalStrings provides strings) {
            AbTheme(darkTheme = false) {
                val nav = rememberNavController()
                onNav(nav)
                AbNavHost(nav, startDestination = start) {
                    composable("start") { androidx.compose.material3.Text("start screen") }
                    helpNavGraph(nav, deps)
                }
            }
        }
    }

    @Test fun startingOnATopicRouteHighlightsItsPage() = runComposeUiTest {
        host(NavRoutes.help(DocsLink.BACKUP_RESTORE))
        onNodeWithText("Backup and Restore").assertIsDisplayed().assert(highlighted)
    }

    @Test fun navigatingToATopicRouteHighlightsItsPage() = runComposeUiTest {
        lateinit var nav: NavHostController
        host("start") { nav = it }
        runOnIdle { nav.navigate(NavRoutes.help(DocsLink.MEMORIZE)) }
        onNodeWithText("Memorize").assertIsDisplayed().assert(highlighted)
    }

    @Test fun thePlainHelpRouteStillOpensWithoutAHighlight() = runComposeUiTest {
        host(NavRoutes.HELP)
        onNodeWithText(strings.helpUserGuideIntro).assertIsDisplayed()
        onAllNodes(highlighted).assertCountEquals(0)
    }
}
