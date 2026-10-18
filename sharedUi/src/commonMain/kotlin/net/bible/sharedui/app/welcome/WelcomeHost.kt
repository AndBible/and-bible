package net.bible.sharedui.app.welcome

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.navigation.NavHostController
import net.bible.sharedcore.startup.StartupWelcomeController
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.startup.nav.WelcomeNavDeps

/**
 * The platform half of the first-run welcome: everything that needs the platform host (resources, intents,
 * app initialisation, document pickers). Members are named after the Android `WelcomeFlow` methods that
 * implement them. Reading [windowTitle] and [appName] must not touch the database.
 */
interface WelcomeHostPlatform {
    /** Window title shown while WELCOME is current. */
    val windowTitle: String

    /** Discrete-aware app name for the header. */
    val appName: String

    /** Discrete-aware logo; a slot because painters are resources. */
    val logo: @Composable () -> Painter?

    /** The welcome controller; built on first call (its first state reads the database). */
    val controller: StartupWelcomeController

    fun onDownload()
    fun onImport()
    fun onRestore()
    fun onRedownload()
    fun onEasyStart()
    fun onOpenHomepage()
    fun onOpenGithub()

    /** Host-level dialogs of the welcome destination (the redownload picker). */
    @Composable
    fun Dialogs()
}

/**
 * Builds the welcome destination's deps. Construction is cheap and must not touch the database:
 * Welcome starts uninitialised.
 */
class WelcomeHost(
    private val shell: HostShell,
    private val platform: WelcomeHostPlatform,
) {
    /** The destination's deps. Built once per host (`remember` in `setContent`). */
    @Suppress("UNUSED_PARAMETER")
    fun navDeps(navController: NavHostController): WelcomeNavDeps = WelcomeNavDeps(
        setWindowTitle = { title -> shell.setWindowTitle(title) },
        windowTitle = platform.windowTitle,
        controller = { platform.controller },
        appName = platform.appName,
        logo = platform.logo,
        onDownload = { platform.onDownload() },
        onImport = { platform.onImport() },
        onRestore = { platform.onRestore() },
        onRedownload = { platform.onRedownload() },
        onEasyStart = { platform.onEasyStart() },
        onOpenHomepage = { platform.onOpenHomepage() },
        onOpenGithub = { platform.onOpenGithub() },
        dialogs = { platform.Dialogs() },
    )
}
