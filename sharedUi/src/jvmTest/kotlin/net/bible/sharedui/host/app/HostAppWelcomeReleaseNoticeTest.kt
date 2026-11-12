package net.bible.sharedui.host.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.IntSize
import androidx.navigation.NavHostController
import java.awt.EventQueue
import java.nio.file.Files
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.desktop.DesktopPaths
import net.bible.sharedcore.platform.desktop.dbus.DesktopDbus
import net.bible.sharedcore.platform.desktop.dbus.NoOpNativeNotifier
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedlogic.fixtures.StartupFixture
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.app.notice.ReleaseNotice
import net.bible.sharedui.app.notice.ReleaseNoticePlatform
import net.bible.sharedui.app.ports.HostPorts
import net.bible.sharedui.assembly.HostDebugArgs
import net.bible.sharedui.assembly.StartupPhase
import net.bible.sharedui.desktop.assembly.DesktopAppHostPlatform
import net.bible.sharedui.desktop.assembly.DesktopAppStartup
import net.bible.sharedui.desktop.assembly.DesktopHostLifecycle
import net.bible.sharedui.desktop.assembly.DesktopNotifierSource
import net.bible.sharedui.desktop.assembly.DesktopWindowHandle
import net.bible.sharedui.desktop.webengine.FakeWebEngine
import net.bible.sharedui.desktop.webview.DesktopReadingPaneEngine
import net.bible.sharedui.host.ports.HostWelcomeHostPlatform
import net.bible.sharedui.strings.Strings
import net.bible.sharedui.strings.keyed.StringTable
import org.crosswire.common.util.CWProject
import org.crosswire.common.util.PropertyMap
import org.koin.core.Koin
import org.koin.mp.KoinPlatform

@OptIn(ExperimentalTestApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HostAppWelcomeReleaseNoticeTest {
    private object Window : DesktopWindowHandle {
        override val sizeDp = IntSize(1200, 800)
        override val density = 1f
        override fun toFront() = Unit
    }

    @Test
    fun restoreFromWelcomeMarksBeforeReadingOnBothHostPlatforms() {
        for (noticePlatform in listOf(ReleaseNoticePlatform.IOS, ReleaseNoticePlatform.DESKTOP)) {
            val previousVersion = System.getProperty("andbible.version")
            assertNull(KoinPlatform.getKoinOrNull())
            val dispatcher = object : CoroutineDispatcher() {
                override fun isDispatchNeeded(context: CoroutineContext) = !EventQueue.isDispatchThread()
                override fun dispatch(context: CoroutineContext, block: Runnable) = EventQueue.invokeLater(block)
            }
            Dispatchers.setMain(dispatcher)
            val previousHost = runCatching { DatabaseContainer.host }.getOrNull()
            val restoreProject = snapshotProject()
            val root = Files.createTempDirectory("host-welcome-notice")
            try {
                System.setProperty("andbible.version", "6.0.7")
                val paths = DesktopPaths({ name ->
                    when (name) {
                        "XDG_DATA_HOME" -> root.resolve("data").toString()
                        "XDG_CONFIG_HOME" -> root.resolve("config").toString()
                        "XDG_CACHE_HOME" -> root.resolve("cache").toString()
                        else -> null
                    }
                }, "Linux", root.resolve("home"))
                StartupFixture.files.forEach { (name, bytes) ->
                    val target = paths.sword.resolve(name)
                    Files.createDirectories(target.parent)
                    Files.write(target, bytes)
                }
                assertEquals(StartupPhase.Ready, DesktopAppStartup.ensureStarted(
                    paths, Window, "en", repositories = { PropertyMap() },
                    notifierSource = DesktopNotifierSource(DesktopDbus().supportedOs) { NoOpNativeNotifier },
                ))
                val koin = KoinPlatform.getKoin()
                val settings = koin.get<AppSettings>()
                val environment = koin.get<AppEnvironment>()
                kotlin.test.assertFalse(environment.isBeta, "fixture must exercise stable releases")
                kotlin.test.assertFalse(environment.isDiscrete, "fixture must exercise non-discrete releases")
                val desktop = DesktopAppHostPlatform(
                    DesktopHostLifecycle(), DesktopReadingPaneEngine(FakeWebEngine(), {}), Window,
                )
                var nav: NavHostController? = null
                var welcome: HostWelcomeHostPlatform? = null
                val platform = object : AppHostPlatform by desktop {
                    override val releaseNoticePlatform = noticePlatform
                    override val betaNoticePlatform = null
                    override val debugArgs = object : HostDebugArgs {
                        override fun hasLaunchFlag(name: String) = name == "-task5Settings"
                    }
                    override fun buildHostPorts(
                        shell: HostShell, koin: Koin, table: StringTable,
                        initialiseWelcome: suspend () -> Unit, isPortrait: () -> Boolean,
                    ): HostPorts = desktop.buildHostPorts(shell, koin, table, initialiseWelcome, isPortrait).also {
                        welcome = it.welcomePlatform() as HostWelcomeHostPlatform
                    }
                    @Composable override fun DebugHarnessOverlays(
                        strings: Strings, navController: NavHostController, navigate: (String) -> Unit,
                    ) { nav = navController }
                }
                val version = platform.readingHost.versionName().split('.').take(2).joinToString(".")
                runComposeUiTest {
                    setContent { HostApp(platform) }
                    waitUntil(timeoutMillis = 10_000) { nav?.currentDestination?.route == NavRoutes.SETTINGS && welcome != null }
                    runOnIdle { nav!!.navigate(NavRoutes.WELCOME) }
                    waitUntil(timeoutMillis = 10_000) { nav?.currentDestination?.route == NavRoutes.WELCOME }
                    onNodeWithTag("welcome-screen").assertExists()
                    runOnIdle {
                        assertEquals("", settings.getString(ReleaseNotice.DISPLAYED_KEY, ""))
                        welcome!!.afterRestore()
                    }
                    waitUntil(timeoutMillis = 30_000) { nav?.currentDestination?.route == NavRoutes.READING }
                    waitForIdle()
                    runOnIdle {
                        assertEquals(version, settings.getString(ReleaseNotice.DISPLAYED_KEY, ""), "$noticePlatform")
                        val pending = koin.get<AppDialogController>().pending.value
                        kotlin.test.assertFalse(pending?.request is AppDialogRequest.ReleaseNotice)
                    }
                }
            } finally {
                DesktopAppStartup.resetForTest()
                HostRootLinks.reset()
                previousHost?.let { DatabaseContainer.installHost(it) }
                restoreProject()
                root.toFile().deleteRecursively()
                Dispatchers.resetMain()
                if (previousVersion == null) System.clearProperty("andbible.version")
                else System.setProperty("andbible.version", previousVersion)
            }
        }
    }

    private fun snapshotProject(): () -> Unit {
        fun field(name: String) = (runCatching { CWProject::class.java.getDeclaredField(name) }.getOrNull()
            ?: CWProject.Companion::class.java.getDeclaredField(name)).apply { isAccessible = true }
        fun owner(f: java.lang.reflect.Field): Any? =
            if (java.lang.reflect.Modifier.isStatic(f.modifiers)) null else CWProject.Companion
        val statics = listOf("homeProperty", "homeDirectory", "homeAltDirectory").map(::field)
        val values = statics.map { it.get(owner(it)) }
        val instanceField = field("instance")
        val instance = instanceField.get(owner(instanceField))
        val established = CWProject::class.java.getDeclaredField("writeHome").apply { isAccessible = true }
            .get(instance) != null
        return {
            statics.zip(values).forEach { (f, value) -> f.set(owner(f), value) }
            if (established) instanceField.set(owner(instanceField), instance) else {
                val reset = runCatching { CWProject::class.java.getDeclaredMethod("reset") }
                    .getOrElse { CWProject.Companion::class.java.getDeclaredMethod("reset") }
                    .apply { isAccessible = true }
                reset.invoke(if (java.lang.reflect.Modifier.isStatic(reset.modifiers)) null else CWProject.Companion)
            }
        }
    }
}
