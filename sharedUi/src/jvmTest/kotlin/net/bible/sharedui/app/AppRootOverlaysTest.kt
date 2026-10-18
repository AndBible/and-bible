package net.bible.sharedui.app

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test

/** The overlays slot composes beside the NavHost, so it survives navigation inside the graph. */
@OptIn(ExperimentalTestApi::class)
class AppRootOverlaysTest {
    @Test
    fun overlaysComposeOutsideNavHost() = runComposeUiTest {
        lateinit var nav: NavHostController
        setContent {
            AbTheme(darkTheme = false) {
                nav = rememberNavController()
                AppRoot(
                    navController = nav,
                    startRoute = "t",
                    graphs = TestAppGraphs.minimal(),
                    readingGraph = {
                        composable("t") { Text("first") }
                        composable("t2") { Text("second") }
                    },
                    overlays = { Text("overlay") },
                )
            }
        }
        onNodeWithText("first").assertExists()
        onNodeWithText("overlay").assertExists()
        runOnIdle { nav.navigate("t2") }
        waitForIdle()
        onNodeWithText("second").assertExists()
        onNodeWithText("overlay").assertExists()
    }
}
