/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.sharedui.desktop.assembly

import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.DatabaseHost
import net.bible.sharedcore.platform.desktop.DesktopPaths
import net.bible.sharedlogic.fixtures.StartupFixture
import net.bible.sharedui.assembly.HostStartup
import net.bible.sharedui.assembly.IosStringTableHolder
import net.bible.sharedui.assembly.StartupPhase
import org.crosswire.common.util.CWProject
import org.crosswire.common.util.PropertyMap
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.koin.mp.KoinPlatform
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The real desktop startup (Koin, databases, Sword) against temporary XDG directories. Every test resets the startup in
 * [after]; the database host is global, so the one installed before the class is put back.
 */
class DesktopAppStartupTest {
    private class FakeWindow : DesktopWindowHandle {
        override val sizeDp: IntSize = IntSize(1200, 800)
        override val density: Float = 1f
        override fun toFront() = Unit
    }

    private val mainThread = Executors.newSingleThreadExecutor { Thread(it, "test-main").apply { isDaemon = true } }.asCoroutineDispatcher()
    private lateinit var root: Path
    private var previousHost: DatabaseHost? = null
    private var restoreCwProject: () -> Unit = {}

    private fun paths(): DesktopPaths = DesktopPaths(
        { name ->
            when (name) {
                "XDG_DATA_HOME" -> root.resolve("data").toString()
                "XDG_CONFIG_HOME" -> root.resolve("config").toString()
                "XDG_CACHE_HOME" -> root.resolve("cache").toString()
                else -> null
            }
        },
        "Linux", root.resolve("home"),
    )

    private fun start(paths: DesktopPaths = paths()): StartupPhase =
        DesktopAppStartup.ensureStarted(paths, FakeWindow(), "en", repositories = { PropertyMap() })

    @BeforeTest fun before() {
        assertNull(KoinPlatform.getKoinOrNull(), "a Koin context leaked into this test")
        // The shared startup reaches Dispatchers.Main (MyDocumentBookManager's scope); the app has the AWT thread
        // (kotlinx-coroutines-swing in :desktopApp). Not on this classpath: it would move every Compose test's
        // navigation onto the AWT thread. A real thread stands in, for this class only.
        Dispatchers.setMain(mainThread)
        previousHost = runCatching { DatabaseContainer.host }.getOrNull()
        restoreCwProject = snapshotCwProject()
        root = Files.createTempDirectory("desktop-startup")
    }

    @AfterTest fun after() {
        try {
            DesktopAppStartup.resetForTest()
        } finally {
            previousHost?.let { DatabaseContainer.installHost(it) }
            // A start here may have established JSword's project home inside [root]; later test classes in this JVM
            // must not keep a project home in a deleted directory.
            restoreCwProject()
            root.toFile().deleteRecursively()
            Dispatchers.resetMain()
            mainThread.close()
        }
    }

    @Test fun startsWithEmptyDataDir() {
        val paths = paths()
        assertEquals(StartupPhase.Ready, start(paths))
        val koin = assertNotNull(KoinPlatform.getKoinOrNull())
        assertTrue(HostStartup.isStarted)
        assertTrue(HostStartup.databasesOpen)
        assertTrue(DatabaseContainer.ready)
        assertEquals("en", HostStartup.stringTable.tag)
        assertTrue(HostStartup.stringTable.text("okay").isNotBlank())
        assertEquals(koin.get<IosStringTableHolder>().current.tag, HostStartup.stringTable.tag)
        // Databases and Sword folders live below the data directory.
        assertTrue(Files.isDirectory(paths.data.resolve("databases")))
        assertTrue(Files.list(paths.data.resolve("databases")).use { s -> s.count() } > 0, "no database file was created")
        assertTrue(Files.isDirectory(paths.sword.resolve("mods.d")))
        // The log file is configured into the data directory.
        assertTrue(Files.isDirectory(paths.data.resolve("logs")))
        // A second call joins the finished start: same graph, still ready.
        assertEquals(StartupPhase.Ready, start(paths))
        assertSame(koin, KoinPlatform.getKoin())
    }

    @Test fun startsWithStartupFixtureModule() {
        val paths = paths()
        StartupFixture.files.forEach { (name, bytes) ->
            val target = paths.sword.resolve(name)
            Files.createDirectories(target.parent)
            Files.write(target, bytes)
        }
        assertEquals(StartupPhase.Ready, start(paths))
        val bibles = Books.installed().getBooks().filter { it.bookCategory == BookCategory.BIBLE }.map { it.initials }
        assertTrue("K4RawText" in bibles, "installed Bibles: $bibles")
    }

    @Test fun modulesOfOtherSwordAppsInHomeAreNotRead() {
        // A module in ~/.sword (another SWORD application's) must not become an installed book.
        val home = root.resolve("home")
        StartupFixture.files.forEach { (name, bytes) ->
            val target = home.resolve(".sword").resolve(name)
            Files.createDirectories(target.parent)
            Files.write(target, bytes)
        }
        val previousHome = System.getProperty("user.home")
        try {
            System.setProperty("user.home", home.toString())
            assertEquals(StartupPhase.Ready, start())
            val books = Books.installed().getBooks().map { it.initials }
            assertFalse("K4RawText" in books, "a ~/.sword module was installed: $books")
            assertTrue(Files.isRegularFile(home.resolve(".sword/mods.d/K4RawText.conf")), "~/.sword must be left alone")
        } finally {
            System.setProperty("user.home", previousHome)
        }
    }

    @Test fun jswordProjectHomeIsTheModuleFolderAndTheHomeStaysEmpty() {
        val paths = paths()
        val home = Files.createDirectories(root.resolve("home"))
        val previousHome = System.getProperty("user.home")
        try {
            System.setProperty("user.home", home.toString())
            // CWProject keeps the first project home of the process: start from a fresh one, as the app's first start does.
            resetCwProject()
            assertEquals(StartupPhase.Ready, start(paths))
            val projectDir = assertNotNull(CWProject.instance().writableProjectDirPath).toNioPath()
            assertTrue(projectDir.startsWith(paths.sword), "JSword project dir $projectDir is not inside ${paths.sword}")
            val written = Files.walk(home).use { s -> s.filter { it != home }.map { home.relativize(it).toString() }.toList() }
            assertEquals(emptyList(), written, "files were written under user.home")
        } finally {
            System.setProperty("user.home", previousHome)
        }
    }

    /**
     * CWProject's process-wide state: the returned function puts the home settings that `setHome` stores back and
     * restores the singleton. A singleton that had established its home before is put back as it was; one that had not
     * (it may have established it inside this test's temp dir since) is replaced by a fresh instance, which establishes
     * its home again on first use.
     */
    private fun snapshotCwProject(): () -> Unit {
        fun field(name: String) = (runCatching { CWProject::class.java.getDeclaredField(name) }.getOrNull()
            ?: CWProject.Companion::class.java.getDeclaredField(name)).apply { isAccessible = true }
        fun owner(f: java.lang.reflect.Field): Any? = if (java.lang.reflect.Modifier.isStatic(f.modifiers)) null else CWProject.Companion
        val statics = listOf("homeProperty", "homeDirectory", "homeAltDirectory").map(::field)
        val staticValues = statics.map { it.get(owner(it)) }
        val instanceField = field("instance")
        val instance = instanceField.get(owner(instanceField))
        val established = CWProject::class.java.getDeclaredField("writeHome").apply { isAccessible = true }.get(instance) != null
        return {
            statics.zip(staticValues).forEach { (f, v) -> f.set(owner(f), v) }
            if (established) instanceField.set(owner(instanceField), instance) else resetCwProject()
        }
    }

    /** CWProject's own test reset (a private static): a new instance that establishes its home again on first use. */
    private fun resetCwProject() {
        val method = runCatching { CWProject::class.java.getDeclaredMethod("reset") }
            .getOrElse { CWProject.Companion::class.java.getDeclaredMethod("reset") }
        method.isAccessible = true
        method.invoke(if (java.lang.reflect.Modifier.isStatic(method.modifiers)) null else CWProject.Companion)
    }

    @Test fun unwritableDataDirFails() {
        val paths = paths()
        // The data directory's place is taken by a plain file, so it can be neither created nor written.
        Files.createDirectories(paths.data.parent)
        Files.write(paths.data, byteArrayOf(1))
        val phase = assertIs<StartupPhase.Failed>(start(paths))
        val message = assertNotNull(phase.error.message)
        assertTrue(paths.data.toString() in message, message)
        assertFalse(HostStartup.isStarted)
        assertFalse(HostStartup.databasesOpen)
        assertNull(KoinPlatform.getKoinOrNull())
    }

    @Test fun koinOnlyStartLeavesTheDatabasesClosedUntilAskedToOpen() {
        val paths = paths()
        assertEquals(StartupPhase.Ready, DesktopAppStartup.ensureStarted(paths, FakeWindow(), "en", openDatabases = false, repositories = { PropertyMap() }))
        assertNotNull(KoinPlatform.getKoinOrNull())
        assertTrue(HostStartup.isStarted)
        assertFalse(HostStartup.databasesOpen)
        assertFalse(DatabaseContainer.ready)
        assertEquals(StartupPhase.Ready, start(paths))
        assertTrue(HostStartup.databasesOpen)
    }

    @Test fun resetForTestForgetsEveryGlobal() {
        assertEquals(StartupPhase.Ready, start())
        DesktopAppStartup.resetForTest()
        assertNull(KoinPlatform.getKoinOrNull())
        assertFalse(HostStartup.isStarted)
        assertFalse(HostStartup.databasesOpen)
        assertFalse(DatabaseContainer.ready)
        // And a new start in a fresh directory works again.
        root.toFile().deleteRecursively()
        root = Files.createTempDirectory("desktop-startup")
        assertEquals(StartupPhase.Ready, start())
    }

    @Test fun exitRunsTheRegisteredTasksAfterTheWorkspaceSaveAndBounded() {
        assertEquals(StartupPhase.Ready, start())
        val koin = KoinPlatform.getKoin()
        val order = java.util.Collections.synchronizedList(mutableListOf<String>())
        val tasks = koin.get<net.bible.sharedcore.platform.desktop.DesktopExitTasks>()
        tasks.register("first") { order += "task" }
        tasks.register("hang") { kotlinx.coroutines.awaitCancellation() }
        val started = System.nanoTime()
        DesktopAppStartup.persistBeforeExit(exitTasksTimeoutMs = 300, saveWorkspace = { order += "save" })
        val tookMs = (System.nanoTime() - started) / 1_000_000
        assertEquals(listOf("save", "task"), order.toList())
        assertTrue(tookMs < 10_000, "persistBeforeExit took $tookMs ms")
    }

    @Test fun launchArgumentsAndLifecycleEventsReachTheGraph() {
        val events = net.bible.sharedcore.platform.desktop.DesktopLifecycleEvents()
        assertEquals(
            StartupPhase.Ready,
            DesktopAppStartup.ensureStarted(paths(), FakeWindow(), "en", repositories = { PropertyMap() }, launchArgs = listOf("a.zip"), lifecycleEvents = events),
        )
        val koin = KoinPlatform.getKoin()
        assertEquals(listOf("a.zip"), koin.get<DesktopLaunchArgs>().args)
        assertSame(events, koin.get<net.bible.sharedcore.platform.desktop.DesktopLifecycleEvents>())
    }
}
