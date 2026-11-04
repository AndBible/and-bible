/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 * AndBible is free software under the GNU General Public License, version 3 or later.
 */
package net.bible.sharedui.host.reading

import androidx.compose.ui.unit.IntSize
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.WorkspaceEntities
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.DatabaseHost
import net.bible.service.startup.ReadingAppInit
import net.bible.sharedcore.platform.desktop.DesktopPaths
import net.bible.sharedlogic.fixtures.StartupFixture
import net.bible.sharedlogic.platform.NightModeState
import net.bible.sharedui.assembly.HostStartup
import net.bible.sharedui.assembly.StartupPhase
import net.bible.sharedui.desktop.assembly.DesktopAppStartup
import net.bible.sharedui.desktop.assembly.DesktopReadingHostPlatform
import net.bible.sharedui.desktop.assembly.DesktopWindowHandle
import net.bible.sharedui.desktop.webengine.FakeWebEngine
import net.bible.sharedui.desktop.webview.DesktopReadingPaneEngine
import net.bible.sharedui.host.app.HostNavShell
import net.bible.sharedui.host.app.HostNavigator
import net.bible.sharedui.host.app.HostReadingReady
import org.crosswire.common.util.CWProject
import org.crosswire.common.util.PropertyMap
import org.koin.mp.KoinPlatform
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WorkspaceSwitchContentTest {
    private object WindowHandle : DesktopWindowHandle {
        override val sizeDp = IntSize(1200, 800)
        override val density = 1f
        override fun toFront() = Unit
    }

    private object Navigator : HostNavigator {
        override fun navigate(route: String) = Unit
        override fun popBackStack() = false
        override fun popBackStackTo(route: String, inclusive: Boolean) = false
    }

    private object Day : NightModeState {
        override val nightMode = false
    }

    private val main = Executors.newSingleThreadExecutor { Thread(it, "workspace-switch-main").apply { isDaemon = true } }.asCoroutineDispatcher()
    private lateinit var root: Path
    private var previousHost: DatabaseHost? = null
    private var restoreCwProject: () -> Unit = {}

    @BeforeTest fun setUp() {
        assertNull(KoinPlatform.getKoinOrNull(), "a Koin context leaked into this test")
        Dispatchers.setMain(main)
        previousHost = runCatching { DatabaseContainer.host }.getOrNull()
        restoreCwProject = snapshotCwProject()
        root = Files.createTempDirectory("workspace-switch-content")
    }

    @AfterTest fun tearDown() {
        try {
            runBlocking { withContext(Dispatchers.Main) { HostReadingWindows.resetForTest() } }
            HostReadingReady.resetForTest()
            ReadingAppInit.resetForTest()
            DesktopAppStartup.resetForTest()
        } finally {
            previousHost?.let { DatabaseContainer.installHost(it) }
            restoreCwProject()
            root.toFile().deleteRecursively()
            Dispatchers.resetMain()
            main.close()
        }
    }

    @Test fun workspaceSwitchReusesOnePaneAndDisposesTheSpares() = runBlocking {
        val paths = DesktopPaths(
            { name -> when (name) {
                "XDG_DATA_HOME" -> root.resolve("data").toString()
                "XDG_CONFIG_HOME" -> root.resolve("config").toString()
                "XDG_CACHE_HOME" -> root.resolve("cache").toString()
                else -> null
            } }, "Linux", root.resolve("home"),
        )
        StartupFixture.files.forEach { (name, bytes) ->
            val target = paths.sword.resolve(name)
            Files.createDirectories(target.parent)
            Files.write(target, bytes)
        }
        assertEquals(StartupPhase.Ready, DesktopAppStartup.ensureStarted(paths, WindowHandle, "en", repositories = { PropertyMap() }))
        HostReadingReady.ensure(HostStartup.stringTable)
        val koin = KoinPlatform.getKoin()
        val scope = HostReadingReady.scope
        val control = koin.get<WindowControl>()
        val repository = control.windowRepository
        val fake = FakeWebEngine()
        val engine = DesktopReadingPaneEngine(fake, {}, koin = { koin })
        val target = WorkspaceEntities.Workspace(name = "Target", contentsText = null, orderNumber = 1)
        val dao = DatabaseContainer.instance.workspaceDb.workspaceDao()

        withContext(Dispatchers.Main) {
            val host = HostReadingHost(scope, HostNavShell(scope, Navigator), koin, DesktopReadingHostPlatform(WindowHandle), Day)
            HostReadingWindows.bind(host, scope, koin, Day, engine)
            val first = repository.activeWindow
            val second = control.addNewWindow(first)
            val third = control.addNewWindow(second)
            val outgoing = listOf(first, second, third)
            outgoing.forEach { window ->
                val id = window.id.toString()
                val session = HostReadingWindows.sessionFor(id)
                engine.attach(id, false, HostReadingWindows.bridgeFor(id), {}, { session.jsChannel.flush() })
                assertNotNull(window.bibleView)
            }
            assertEquals(3, fake.panes.size)
            val originalPanes = fake.panes.toList()
            repository.saveIntoDb()
            repository.persistence.awaitPersisted()
            dao.insertWorkspace(target)

            // Use the host's real switch path, not a simulated repository notification.
            val actions = HostReadingQuickActions(
                windowControl = { control }, fakeBookFactory = { koin.get() }, settings = koin.get(),
                scope = scope, toast = {}, chooserTarget = { error("not used by workspace switch") },
            )
            actions.switchToWorkspace(target.id.toString(), saveOutgoing = false)
            val incoming = repository.activeWindow
            assertTrue(outgoing.none { it.id == incoming.id })
            val id = incoming.id.toString()
            val session = HostReadingWindows.sessionFor(id)
            engine.attach(id, false, HostReadingWindows.bridgeFor(id), {}, { session.jsChannel.flush() })
            val adopted = assertNotNull(engine.paneFor(id))
            assertTrue(originalPanes.any { it === adopted }, "the incoming window must adopt an outgoing native pane")
            assertTrue(outgoing.all { it.bibleView == null }, "outgoing owners must detach their views")
            assertSame(adopted, engine.paneFor(id))
            // Simulate the completed-composition cleanup seam.
            HostReadingWindows.releaseSpares()
            assertEquals(3, fake.panes.size, "switching must not create a new native pane")
            assertEquals(2, originalPanes.count { it.disposed }, "unused outgoing panes must be disposed")
            assertFalse(originalPanes.single { it === adopted }.disposed)

            val staleView = incoming.bibleView
            val previousGeneration = HostReadingWindows.switchGeneration
            session.jsChannel.markClientReady()
            actions.switchToWorkspace(target.id.toString(), saveOutgoing = false)
            val reconstructed = repository.activeWindow
            assertEquals(incoming.id, reconstructed.id)
            assertTrue(incoming !== reconstructed)
            assertNull(incoming.bibleView)
            assertFalse(checkNotNull(staleView).listenEvents)
            assertNotNull(reconstructed.bibleView, "same-ID owner must be adopted without pane recomposition")
            assertFalse(session.jsChannel.loading, "adoption preserves page readiness")
            val before = session.firstDocument
            staleView.loadDocument(net.bible.android.control.page.ErrorDocument("stale", net.bible.android.control.page.ErrorSeverity.NORMAL), false, null, null, null, null)
            assertSame(before, session.firstDocument, "late outgoing delivery must be ignored")
            HostReadingWindows.releaseSpares(previousGeneration)
            assertFalse(originalPanes.single { it === adopted }.disposed, "older cleanup must not dispose a newer switch's pane")
            HostReadingWindows.releaseSpares()
            assertFalse(originalPanes.single { it === adopted }.disposed, "cleanup must not dispose the displayed same-ID pane")
            assertSame(session, HostReadingWindows.sessionFor(id))
            assertSame(adopted, engine.paneFor(id))
            assertSame(reconstructed, session.window)
            HostReadingWindows.releaseSpares()
        }
    }

    private fun snapshotCwProject(): () -> Unit {
        fun field(name: String) = (runCatching { CWProject::class.java.getDeclaredField(name) }.getOrNull()
            ?: CWProject.Companion::class.java.getDeclaredField(name)).apply { isAccessible = true }
        fun owner(f: java.lang.reflect.Field): Any? = if (java.lang.reflect.Modifier.isStatic(f.modifiers)) null else CWProject.Companion
        val statics = listOf("homeProperty", "homeDirectory", "homeAltDirectory").map(::field)
        val values = statics.map { it.get(owner(it)) }
        val instanceField = field("instance")
        val instance = instanceField.get(owner(instanceField))
        val established = CWProject::class.java.getDeclaredField("writeHome").apply { isAccessible = true }.get(instance) != null
        return {
            statics.zip(values).forEach { (f, v) -> f.set(owner(f), v) }
            if (established) instanceField.set(owner(instanceField), instance) else {
                val reset = runCatching { CWProject::class.java.getDeclaredMethod("reset") }
                    .getOrElse { CWProject.Companion::class.java.getDeclaredMethod("reset") }
                reset.isAccessible = true
                reset.invoke(if (java.lang.reflect.Modifier.isStatic(reset.modifiers)) null else CWProject.Companion)
            }
        }
    }
}
