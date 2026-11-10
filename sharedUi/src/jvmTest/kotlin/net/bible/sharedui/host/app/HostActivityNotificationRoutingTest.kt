package net.bible.sharedui.host.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.Text
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import net.bible.sharedui.components.AbNavHost
import net.bible.sharedui.theme.AbTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.IntSize
import androidx.navigation.NavHostController
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.bible.android.control.activity.ActivityListRequests
import net.bible.android.control.activity.ActivityMenuPreference
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.DatabaseHost
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.platform.desktop.DesktopPaths
import net.bible.sharedcore.platform.desktop.dbus.DesktopDbus
import net.bible.sharedcore.platform.desktop.dbus.NoOpNativeNotifier
import net.bible.sharedlogic.fixtures.StartupFixture
import net.bible.sharedui.assembly.HostDebugArgs
import net.bible.sharedui.assembly.StartupPhase
import net.bible.sharedui.desktop.assembly.DesktopAppHostPlatform
import net.bible.sharedui.desktop.assembly.DesktopAppStartup
import net.bible.sharedui.desktop.assembly.DesktopHostLifecycle
import net.bible.sharedui.desktop.assembly.DesktopNotifierSource
import net.bible.sharedui.desktop.assembly.DesktopWindowHandle
import net.bible.sharedui.desktop.webengine.FakeWebEngine
import net.bible.sharedui.desktop.webview.DesktopReadingPaneEngine
import net.bible.sharedui.reading.activity.ACTIVITY_LIST_POPOVER_TAG
import net.bible.sharedui.strings.Strings
import org.crosswire.common.util.CWProject
import org.crosswire.common.util.PropertyMap
import org.koin.mp.KoinPlatform
import java.awt.EventQueue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Exercises notification ingress through the production host root, not an isolated ActivityHost. */
@OptIn(ExperimentalTestApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HostActivityNotificationRoutingTest {
    private object Window : DesktopWindowHandle {
        override val sizeDp = IntSize(1200, 800)
        override val density = 1f
        override fun toFront() = Unit
    }

    // Navigation's LifecycleRegistry and Compose rendering must share the desktop UI thread.
    private val mainThread = object : CoroutineDispatcher() {
        override fun isDispatchNeeded(context: CoroutineContext) = !EventQueue.isDispatchThread()
        override fun dispatch(context: CoroutineContext, block: Runnable) = EventQueue.invokeLater(block)
    }
    private lateinit var root: Path
    private var previousHost: DatabaseHost? = null
    private var restoreProject: () -> Unit = {}

    @BeforeTest fun before() {
        assertNull(KoinPlatform.getKoinOrNull(), "a Koin context leaked into this test")
        Dispatchers.setMain(mainThread)
        previousHost = runCatching { DatabaseContainer.host }.getOrNull()
        restoreProject = snapshotProject()
        root = Files.createTempDirectory("host-notification-routing")
    }

    @AfterTest fun after() {
        try {
            DesktopAppStartup.resetForTest()
        } finally {
            HostRootLinks.reset()
            previousHost?.let { DatabaseContainer.installHost(it) }
            restoreProject()
            root.toFile().deleteRecursively()
            Dispatchers.resetMain()
        }
    }

    private fun onSettingsRoot(
        buffered: Boolean = false,
        enabled: Boolean = true,
        assertions: ComposeUiTest.(ActivityListRequests, ActivityMenuPreference, NavHostController) -> Unit,
    ) {
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
        val preference = koin.get<ActivityMenuPreference>().also { it.set(enabled) }
        val requests = koin.get<ActivityListRequests>()
        if (buffered) requests.request()
        val desktop = DesktopAppHostPlatform(
            DesktopHostLifecycle(), DesktopReadingPaneEngine(FakeWebEngine(), {}), Window,
        )
        var nav: NavHostController? = null
        val platform = object : AppHostPlatform by desktop {
            override val debugArgs = object : HostDebugArgs {
                override fun hasLaunchFlag(name: String) = name == "-task5Settings"
            }

            @Composable override fun DebugHarnessOverlays(
                strings: Strings, navController: NavHostController, navigate: (String) -> Unit,
            ) {
                nav = navController
            }
        }
        runComposeUiTest {
            setContent { HostApp(platform) }
            waitUntil(timeoutMillis = 10_000) { nav?.currentDestination?.route == NavRoutes.SETTINGS }
            if (!buffered || !enabled) {
                onNodeWithText("Application preferences", useUnmergedTree = true).assertExists()
            }
            assertions(requests, preference, nav!!)
        }
    }

    private fun ComposeUiTest.assertLists(count: Int) {
        onAllNodesWithTag(ACTIVITY_LIST_POPOVER_TAG, useUnmergedTree = true).assertCountEquals(count)
    }

    @Test fun notificationTapOpensActivityImmediatelyOnNonReadingRoot() = onSettingsRoot { requests, _, nav ->
        assertLists(0)
        runOnIdle { requests.request() }
        waitForIdle()
        assertLists(1)
        runOnIdle { assertEquals(NavRoutes.SETTINGS, nav.currentDestination!!.route) }
        onNodeWithText("Close").performClick()
        waitForIdle()
        assertLists(0)
        runOnIdle { nav.navigate(NavRoutes.SYNC_SETTINGS) }
        waitForIdle()
        assertLists(0)
        runOnIdle { requests.request() }
        waitForIdle()
        assertLists(1)
        onNodeWithText("Close").performClick()
        runOnIdle { nav.popBackStack() }
        waitForIdle()
        assertLists(0)
    }

    @Test fun bufferedStartupTapOpensOnceWithoutReadingInitialization() = onSettingsRoot(buffered = true) { _, _, _ ->
        waitForIdle()
        assertLists(1)
        kotlin.test.assertFalse(HostReadingReady.isReady)
        onNodeWithText("Close").performClick()
        waitForIdle()
        assertLists(0)
    }

    @Test fun disabledRootDropsTapAndDisablingClosesList() = onSettingsRoot(enabled = false) { requests, preference, _ ->
        runOnIdle { requests.request() }
        waitForIdle()
        assertLists(0)
        runOnIdle { preference.set(true) }
        waitForIdle()
        assertLists(0)
        runOnIdle { requests.request() }
        waitForIdle()
        assertLists(1)
        runOnIdle { preference.set(false) }
        waitForIdle()
        assertLists(0)
    }

    @Test fun repeatedOpenReadingForAgentRowsDoesNotDuplicateCurrentReadingEntry() = runComposeUiTest {
        lateinit var nav: NavHostController
        lateinit var shell: HostNavShell
        setContent {
            AbTheme(disableAnimations = true) {
                nav = rememberNavController()
                val scope = rememberCoroutineScope()
                shell = androidx.compose.runtime.remember { HostNavShell(scope, NavControllerNavigator { nav }) }
                DisposableEffect(nav) {
                    val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, destination, _ ->
                        shell.routeChanged(destination.route)
                    }
                    nav.addOnDestinationChangedListener(listener)
                    onDispose { nav.removeOnDestinationChangedListener(listener) }
                }
                AbNavHost(navController = nav, startDestination = NavRoutes.READING) {
                    composable(NavRoutes.READING) { Text("reading") }
                    composable(NavRoutes.SETTINGS) { Text("preferences") }
                }
            }
        }
        waitForIdle()
        runOnIdle {
            assertEquals(1, nav.currentBackStack.value.count { it.destination.route == NavRoutes.READING })
            // The persistent root's agent-row path calls this before delegating to the reading host.
            repeat(3) { shell.openReading() }
            assertEquals(1, nav.currentBackStack.value.count { it.destination.route == NavRoutes.READING })
        }
        runOnIdle { nav.navigate(NavRoutes.SETTINGS) }
        waitForIdle()
        runOnIdle { shell.openReading() }
        waitForIdle()
        runOnIdle {
            assertEquals(NavRoutes.READING, nav.currentDestination!!.route)
            assertEquals(1, nav.currentBackStack.value.count { it.destination.route == NavRoutes.READING })
        }
    }

    /** Preserve JSword's process-wide home, as the other real desktop startup fixtures do. */
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
