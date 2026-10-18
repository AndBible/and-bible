package net.bible.sharedui.app.welcome

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import net.bible.sharedcore.startup.StartupWelcomeController
import net.bible.sharedcore.startup.StartupWelcomeInfo
import net.bible.sharedui.app.HostShell
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class WelcomeHostTest {
    private val calls = mutableListOf<String>()

    private val shell = object : HostShell {
        override val hostScope: CoroutineScope = CoroutineScope(Job())
        override fun exitHost() = Unit
        override fun setWindowTitle(title: String) { calls += "title:$title" }
        override fun recreateHost() = Unit
        override fun openReading() = Unit
        override fun setHistoryRoute(owner: Any, route: String) = Unit
        override fun clearHistoryRoute(owner: Any) = Unit
        override fun goBackInHistory(): Boolean = false
        override fun navigate(route: String) = Unit
    }

    private val welcomeController = StartupWelcomeController {
        StartupWelcomeInfo("v", "formats", previousInstallDetected = false, easyStartAvailable = true)
    }

    private val platform = object : WelcomeHostPlatform {
        override val windowTitle = "Label"
        override val appName = "App"
        override val logo: @Composable () -> Painter? = { null }
        override val controller: StartupWelcomeController get() { calls += "controller"; return welcomeController }
        override fun onDownload() { calls += "download" }
        override fun onImport() { calls += "import" }
        override fun onRestore() { calls += "restore" }
        override fun onRedownload() { calls += "redownload" }
        override fun onEasyStart() { calls += "easyStart" }
        override fun onOpenHomepage() { calls += "homepage" }
        override fun onOpenGithub() { calls += "github" }
        @Composable override fun Dialogs() { calls += "dialogs" }
    }

    private fun navController(): NavHostController {
        lateinit var nav: NavHostController
        runComposeUiTest { setContent { nav = rememberNavController() } }
        return nav
    }

    @Test
    fun navDepsDelegatesToPlatform() {
        val deps = WelcomeHost(shell, platform).navDeps(navController())
        assertEquals("Label", deps.windowTitle)
        assertEquals("App", deps.appName)
        assertTrue(calls.isEmpty(), "construction must not touch the platform's lazy members: $calls")

        deps.setWindowTitle("T")
        assertSame(welcomeController, deps.controller())
        deps.onDownload()
        deps.onImport()
        deps.onRestore()
        deps.onRedownload()
        deps.onEasyStart()
        deps.onOpenHomepage()
        deps.onOpenGithub()
        assertEquals(
            listOf("title:T", "controller", "download", "import", "restore", "redownload", "easyStart", "homepage", "github"),
            calls,
        )
    }
}
