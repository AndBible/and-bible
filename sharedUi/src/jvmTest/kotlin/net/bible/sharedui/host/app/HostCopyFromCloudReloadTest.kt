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
package net.bible.sharedui.host.app

import androidx.compose.ui.unit.IntSize
import androidx.room3.useWriterConnection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.service.cloudsync.CloudSync
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.DatabaseHost
import net.bible.service.db.exec
import net.bible.service.startup.ReadingAppInit
import net.bible.sharedcore.platform.desktop.DesktopPaths
import net.bible.sharedui.assembly.HostStartup
import net.bible.sharedui.assembly.StartupPhase
import net.bible.sharedui.desktop.assembly.DesktopAppStartup
import net.bible.sharedui.desktop.assembly.DesktopWindowHandle
import okio.Path.Companion.toPath
import net.bible.sharedlogic.fixtures.StartupFixture
import org.crosswire.common.util.CWProject
import org.crosswire.common.util.PropertyMap
import org.koin.mp.KoinPlatform
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #3919: after "Copy from Cloud" (the initial-download join, [CloudSync.swapInInitialDb]) the host (desktop, iOS) must
 * reload its window repository from the downloaded database. The swap bumps the restore epoch, which freezes
 * `WindowRepository.saveIntoDb` until the repository reloads; the swap announces the reload on
 * [CloudSync.workspaceRefreshRequired], not on `DatabaseContainer.databaseRestored`.
 *
 * Real desktop startup (Koin, databases) against temporary XDG directories, as in `DesktopAppStartupTest`.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HostCopyFromCloudReloadTest {
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

    @BeforeTest fun before() {
        assertNull(KoinPlatform.getKoinOrNull(), "a Koin context leaked into this test")
        Dispatchers.setMain(mainThread)
        previousHost = runCatching { DatabaseContainer.host }.getOrNull()
        restoreCwProject = snapshotCwProject()
        root = Files.createTempDirectory("host-copy-from-cloud")
    }

    @AfterTest fun after() {
        try {
            HostReadingReady.resetForTest()
            ReadingAppInit.resetForTest()
            DesktopAppStartup.resetForTest()
        } finally {
            previousHost?.let { DatabaseContainer.installHost(it) }
            // The start established JSword's project home inside [root]; later classes must not keep a deleted one.
            restoreCwProject()
            root.toFile().deleteRecursively()
            Dispatchers.resetMain()
            mainThread.close()
        }
    }

    private val workspaces get() = DatabaseContainer.databaseAccessorsByCategory[SyncableDatabaseDefinition.WORKSPACES]!!
    private val dao get() = DatabaseContainer.instance.workspaceDb.workspaceDao()

    private fun saveOnMain(repository: WindowRepository, name: String) = runBlocking {
        withContext(Dispatchers.Main) {
            repository.name = name
            repository.saveIntoDb(false)
        }
        repository.persistence.awaitPersisted()
    }

    /** The "download": the current workspaces database with the workspace renamed [name], checkpointed and copied out. */
    private fun downloadNamed(repository: WindowRepository, name: String): okio.Path = runBlocking {
        val current = dao.workspace(repository.id)!!
        dao.updateWorkspace(current.copy(name = name))
        workspaces.localDb.useWriterConnection { it.exec("PRAGMA wal_checkpoint(FULL)") }
        val file = Files.createTempFile(root, "initial", ".sqlite3")
        Files.copy(Path.of(workspaces.localDbPath), file, StandardCopyOption.REPLACE_EXISTING)
        dao.updateWorkspace(current)
        file.toString().toPath()
    }

    /** Real desktop startup on a fresh profile, reading ready; returns the Koin context. */
    private fun startDesktop(): org.koin.core.Koin {
        val paths = paths()
        // Reading needs a Bible (the first start's example bookmarks use the default one).
        StartupFixture.files.forEach { (name, bytes) ->
            val target = paths.sword.resolve(name)
            Files.createDirectories(target.parent)
            Files.write(target, bytes)
        }
        assertEquals(
            StartupPhase.Ready,
            DesktopAppStartup.ensureStarted(paths, FakeWindow(), "en", repositories = { PropertyMap() }, notifierSource = net.bible.sharedui.desktop.assembly.DesktopNotifierSource(net.bible.sharedcore.platform.desktop.dbus.DesktopDbus().supportedOs) { net.bible.sharedcore.platform.desktop.dbus.NoOpNativeNotifier }),
        )
        val koin = KoinPlatform.getKoin()
        runBlocking { HostReadingReady.ensure(HostStartup.stringTable) }
        return koin
    }

    /** The subscription hops to Main and launches the reload on [hostScope]: drain both. */
    private fun drainReload(hostScope: CoroutineScope) = runBlocking {
        withTimeout(30_000) {
            repeat(3) {
                withContext(Dispatchers.Main) { }
                hostScope.coroutineContext[Job]!!.children.toList().forEach { it.join() }
            }
        }
    }

    /** The current database file of [accessor], checkpointed and copied out (a cloud initial file of the same data). */
    private fun copyOf(accessor: net.bible.service.cloudsync.SyncableDatabaseAccessor<*>): okio.Path = runBlocking {
        accessor.localDb.useWriterConnection { it.exec("PRAGMA wal_checkpoint(FULL)") }
        val file = Files.createTempFile(root, "initial", ".sqlite3")
        Files.copy(Path.of(accessor.localDbPath), file, StandardCopyOption.REPLACE_EXISTING)
        file.toString().toPath()
    }

    private fun activeVerse(repository: WindowRepository): String = runBlocking {
        withContext(Dispatchers.Main) { repository.activeWindow.pageManager.currentBible.singleKey.getOsisID() }
    }

    /**
     * #4101: the first-sync join of another category (here Bookmarks, swapped in like "Merge data" and "Fetch" do)
     * bumps the restore epoch and makes the host reload its workspace from the database. A reading position that
     * was only in memory (a fresh profile's first-use verse, or any navigation not yet saved) was lost: the reload
     * showed the stored default, Genesis 1:1.
     */
    @Test fun theReadingPositionSurvivesAnotherCategorysJoinSwap() {
        val koin = startDesktop()
        val repository = koin.get<WindowControl>().windowRepository
        // Navigate in memory only, as the reading view does; nothing saves the workspace before the sync settings.
        runBlocking {
            withContext(Dispatchers.Main) {
                val bible = repository.activeWindow.pageManager.currentBible
                bible.doSetKey(org.crosswire.jsword.passage.VerseFactory.fromString(bible.versification, "Rom.8.28"))
            }
        }
        assertEquals("Rom.8.28", activeVerse(repository))

        val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val subscription = installRestoreReload(koin, hostScope)
        try {
            val bookmarks = DatabaseContainer.databaseAccessorsByCategory[SyncableDatabaseDefinition.BOOKMARKS]!!
            runBlocking { koin.get<CloudSync>().swapInInitialDb(bookmarks, copyOf(bookmarks)) { } }
            drainReload(hostScope)
            assertFalse(repository.staleAfterRestore, "the host did not reload the repository after the join")
            assertEquals("Rom.8.28", activeVerse(repository), "the join lost the in-memory reading position")
        } finally {
            subscription.cancel()
            hostScope.cancel()
        }
    }

    /**
     * #4101 review: the pre-replace save hops to Main, so it must never wait on a Main that is itself blocked on the
     * replace. Here Main blocks (as `ReadingAppInit.hasStoredCipherKeyAtEdge` does with `runBlocking`) until the replace
     * holds the AI admission and then waits for that admission. Saving under the admission was a lock cycle; saving
     * before it with an unbounded Main hop waits forever on the blocked Main. The replace must finish regardless.
     */
    @Test fun aReplaceFinishesWhileMainIsBlockedOnItsAdmission() {
        val koin = startDesktop()
        assertTrue(koin.get<WindowControl>().holdsLoadedRepository, "the save must have something to save")
        val lifetime = DatabaseContainer.aiLifetime
        // An owned job is cancelled by the replace right after it takes the admission: the signal Main waits for.
        val admitted = CoroutineScope(Dispatchers.Default).launch(start = CoroutineStart.LAZY) { awaitCancellation() }
        lifetime.ownUntilReplace(admitted)
        admitted.start()
        val mainBlocked = CompletableDeferred<Unit>()
        val mainDone = CoroutineScope(Dispatchers.Main).launch {
            mainBlocked.complete(Unit)
            runBlocking {
                admitted.join()
                lifetime.use { }
            }
        }
        runBlocking { mainBlocked.await() }
        val bookmarks = DatabaseContainer.databaseAccessorsByCategory[SyncableDatabaseDefinition.BOOKMARKS]!!
        val initial = copyOf(bookmarks)
        // async + await, not withContext: a test whose replace hangs on Main must fail, not hang itself.
        val swap = CoroutineScope(Dispatchers.IO).async { koin.get<CloudSync>().swapInInitialDb(bookmarks, initial) { } }
        runBlocking {
            withTimeout(30_000) { swap.await() }
            withTimeout(30_000) { mainDone.join() }
        }
    }

    @Test fun aWorkspaceChangeAfterCopyFromCloudIsSaved() {
        val koin = startDesktop()
        val repository = koin.get<WindowControl>().windowRepository
        saveOnMain(repository, "A")

        val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val subscription = installRestoreReload(koin, hostScope)
        try {
            val downloaded = downloadNamed(repository, "C")
            runBlocking { koin.get<CloudSync>().swapInInitialDb(workspaces, downloaded) { } }
            drainReload(hostScope)
            assertFalse(repository.staleAfterRestore, "the host did not reload the repository after Copy from Cloud")
            assertEquals("C", repository.name, "the repository holds the downloaded workspace")

            saveOnMain(repository, "D")
            assertEquals("D", runBlocking { dao.workspace(repository.id) }!!.name)
        } finally {
            subscription.cancel()
            hostScope.cancel()
        }
    }

    /** CWProject's process-wide state, put back after the test (as in `DesktopAppStartupTest`). */
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

    private fun resetCwProject() {
        val method = runCatching { CWProject::class.java.getDeclaredMethod("reset") }
            .getOrElse { CWProject.Companion::class.java.getDeclaredMethod("reset") }
        method.isAccessible = true
        method.invoke(if (java.lang.reflect.Modifier.isStatic(method.modifiers)) null else CWProject.Companion)
    }
}
