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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
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

    @Test fun aWorkspaceChangeAfterCopyFromCloudIsSaved() {
        val paths = paths()
        // Reading needs a Bible (the first start's example bookmarks use the default one).
        StartupFixture.files.forEach { (name, bytes) ->
            val target = paths.sword.resolve(name)
            Files.createDirectories(target.parent)
            Files.write(target, bytes)
        }
        assertEquals(
            StartupPhase.Ready,
            DesktopAppStartup.ensureStarted(paths, FakeWindow(), "en", repositories = { PropertyMap() }),
        )
        val koin = KoinPlatform.getKoin()
        runBlocking { HostReadingReady.ensure(HostStartup.stringTable) }
        val repository = koin.get<WindowControl>().windowRepository
        saveOnMain(repository, "A")

        val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val subscription = installRestoreReload(koin, hostScope)
        try {
            val downloaded = downloadNamed(repository, "C")
            runBlocking { koin.get<CloudSync>().swapInInitialDb(workspaces, downloaded) { } }
            // The subscription hops to Main and launches the reload on [hostScope]: drain both.
            runBlocking {
                withTimeout(30_000) {
                    repeat(3) {
                        withContext(Dispatchers.Main) { }
                        hostScope.coroutineContext[Job]!!.children.toList().forEach { it.join() }
                    }
                }
            }
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
