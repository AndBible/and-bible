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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import net.bible.sharedcore.platform.desktop.DesktopPaths
import net.bible.service.cloudsync.desktop.desktopSyncModule
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.assembly.HostWindowMetrics
import net.bible.sharedui.assembly.IOS_BOUND_PORT_TYPES
import net.bible.sharedui.assembly.IOS_HOST_BINDING_TYPES
import net.bible.service.cloudsync.host.HostBackupHost
import net.bible.sharedui.host.ports.HostWelcomeHostPlatform
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.platform.desktop.DesktopAppEnvironment
import net.bible.sharedlogic.platform.desktop.DesktopDatabaseHost
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The desktop counterpart of `IosAssemblyTest.boundPortListMatchesTheModule`: every type the iOS contract lists resolves
 * from the desktop core module plus the four stream modules, against a temporary data dir, without starting anything
 * (no database, no network). The Koin application is private to the test and closed in `finally`.
 */
class DesktopPortContractTest {
    private class FakeWindow(override var sizeDp: IntSize = IntSize(1200, 800), override val density: Float = 1.5f) : DesktopWindowHandle {
        var raised = 0
        override fun toFront() { raised++ }
    }

    private class FakeShell : HostShell {
        override val hostScope: CoroutineScope = CoroutineScope(Job())
        override fun exitHost() = Unit
        override fun setWindowTitle(title: String) = Unit
        override fun recreateHost() = Unit
        override fun openReading() = Unit
        override fun setHistoryRoute(owner: Any, route: String) = Unit
        override fun clearHistoryRoute(owner: Any) = Unit
        override fun goBackInHistory() = false
        override fun navigate(route: String) = Unit
    }

    private fun paths(root: Path) = DesktopPaths({ name -> if (name == "XDG_DATA_HOME") root.toString() else null }, "Linux", root)

    private fun <T> withKoin(window: DesktopWindowHandle = FakeWindow(), block: (Koin, Path) -> T): T {
        val root = Files.createTempDirectory("desktop-contract")
        val app: KoinApplication = koinApplication {
            modules(
                desktopPlatformModule(paths(root), window, uiLanguage = "en"),
                desktopReadingModule(), desktopDocumentModule(), desktopMiscModule(), desktopSyncModule(),
            )
        }
        // The settings service reads app settings when it is built, which needs a database host (not an open database).
        // The host is global: put back whatever was installed before.
        val previousHost = runCatching { DatabaseContainer.host }.getOrNull()
        try {
            DatabaseContainer.installHost(DesktopDatabaseHost(app.koin.get<DesktopAppEnvironment>()))
            return block(app.koin, root)
        } finally {
            previousHost?.let { DatabaseContainer.installHost(it) }
            app.close()
            root.toFile().deleteRecursively()
        }
    }

    @Test fun everyIosBoundPortTypeResolves() = withKoin { koin, _ ->
        val failures = (IOS_BOUND_PORT_TYPES + IOS_HOST_BINDING_TYPES).mapNotNull { type ->
            try { assertNotNull(koin.getOrNull<Any>(type), type.simpleName); null } catch (e: Throwable) { "${type.simpleName}: $e" }
        }
        if (failures.isNotEmpty()) fail("Unresolvable port types:\n" + failures.joinToString("\n"))
    }

    @Test fun resolvingCreatesNoDatabaseFile() = withKoin { koin, root ->
        (IOS_BOUND_PORT_TYPES + IOS_HOST_BINDING_TYPES).forEach { koin.getOrNull<Any>(it) }
        // The data dir may get its device-settings/config files, but never a database.
        val databases = Files.walk(root).use { stream -> stream.filter { it.toString().endsWith(".db") }.count() }
        assertEquals(0L, databases)
    }

    @Test fun windowMetricsFollowTheWindow() {
        val window = FakeWindow()
        withKoin(window) { koin, _ ->
            val metrics = koin.get<HostWindowMetrics>()
            assertEquals(1200.0, metrics.width)
            assertEquals(1.5f, metrics.scale)
            assertTrue(!metrics.isPortrait)
            window.sizeDp = IntSize(600, 900)
            assertTrue(metrics.isPortrait)
        }
    }

    @Test fun deviceSettingsLiveInTheConfigDirectory() = withKoin { koin, root ->
        koin.get<net.bible.sharedcore.settings.DeviceLocalPrefs>().setString("probe", "1")
        assertTrue(Files.exists(paths(root).config.resolve(DESKTOP_DEVICE_SETTINGS_FILE)))
    }

    @Test fun nightModeStubFollowsTheDevicePrefs() = withKoin { koin, _ ->
        val prefs = koin.get<net.bible.sharedcore.settings.DeviceLocalPrefs>()
        val night = koin.get<net.bible.sharedlogic.platform.NightModeState>()
        assertEquals(false, night.nightMode)          // absent mode is "system", the desktop answers light
        prefs.setString("night_mode_pref3", "manual")
        prefs.setBoolean("night_mode_pref", true)
        assertEquals(true, night.nightMode)
        prefs.setBoolean("night_mode_pref", false)
        assertEquals(false, night.nightMode)
    }

    @Test fun hostPortsBuildWithTheHoistedClassesTheAppRootCasts() = withKoin { koin, _ ->
        val shell = FakeShell()
        try {
            val ports = buildDesktopHostPorts(koin, shell)
            assertIs<HostBackupHost>(ports.backupHost)
            assertIs<HostWelcomeHostPlatform>(ports.welcomePlatform())
            // The lazily built platforms construct without reaching a stub that throws.
            ports.installZipPlatform(); ports.downloadPlatform(); ports.cloudDocumentsPlatform()
            ports.bookmarkPlatform(); ports.readingPlanPlatform(); ports.chooserPlatform(); ports.aiPlatform()
            assertEquals(Unit, ports.orientationLock.lockCurrentOrientation().invoke())
        } finally {
            shell.hostScope.coroutineContext[Job]?.cancel()
        }
    }

    @Test fun anUnstartedStubAnswersNeutrallyAndNeverThrows() = withKoin { koin, _ ->
        kotlinx.coroutines.runBlocking {
            assertEquals(emptyList(), koin.get<net.bible.sharedui.app.ports.DocumentPicker>().openDocuments(listOf("*/*"), true))
            assertEquals(null, koin.get<net.bible.sharedui.app.ports.ImagePicker>().pickImage())
            assertEquals(null, koin.get<net.bible.sharedcore.platform.files.FilePicker>().pickBackupFile())
            assertEquals(false, koin.get<net.bible.sharedcore.platform.sync.SyncScheduler>().startDatabaseSync().await())
            assertEquals(listOf(net.bible.service.cloudsync.desktop.DESKTOP_PENDING_ADAPTER_ID), koin.get<net.bible.service.cloudsync.CloudAdapterFactory>().available)
        }
        // CloudSync reads adapterId (available.first()) and builds an adapter on sign-in: neither may throw. CloudSync.signIn()
        // itself is not called: after a false answer it awaits the "sign in failed" dialog, which only a user can dismiss.
        val cloud = koin.get<net.bible.service.cloudsync.CloudSync>()
        assertTrue(cloud.adapterId.isNotEmpty())
        val adapter = koin.get<net.bible.service.cloudsync.CloudAdapterFactory>().create(cloud.adapterId)
        kotlinx.coroutines.runBlocking { kotlinx.coroutines.withTimeout(10_000) { assertEquals(false, adapter.signIn()) } }
        assertEquals(false, adapter.signedIn)
        assertEquals(false, cloud.signedIn)
        koin.get<net.bible.sharedui.app.ports.ExternalLinks>().open("https://example.org")
        koin.get<net.bible.sharedui.app.ports.ClipboardText>().write("x", "y")
    }

    /** The wave-3 seams (D3a..D3d): each resolves from the core + stream modules and answers today's behaviour. */
    @Test fun everyWaveThreeSeamResolvesWithTodaysBehaviour() = withKoin { koin, _ ->
        val seams = listOf(
            net.bible.sharedui.desktop.webview.DesktopReadingInput::class,
            DesktopIncomingFiles::class, DesktopFileShare::class, DesktopLaunchArgs::class,
            net.bible.sharedcore.platform.desktop.DesktopExitTasks::class,
            net.bible.sharedcore.platform.desktop.DesktopLifecycleEvents::class,
            DesktopNightModeSource::class,
        )
        val failures = seams.mapNotNull { type ->
            try { assertNotNull(koin.getOrNull<Any>(type), type.simpleName); null } catch (e: Throwable) { "${type.simpleName}: $e" }
        }
        if (failures.isNotEmpty()) fail("Unresolvable seam types:\n" + failures.joinToString("\n"))

        assertTrue(koin.get<net.bible.sharedui.desktop.webview.DesktopReadingInput>().documentStartScripts("w1").isEmpty())
        assertEquals(false, koin.get<DesktopFileShare>().canShare)
        assertEquals(emptyList(), koin.get<DesktopLaunchArgs>().args)
        // The night-mode source is the graph's NightModeState, so the toolbar, the panes and the app agree.
        assertTrue(koin.get<DesktopNightModeSource>() === koin.get<net.bible.sharedlogic.platform.NightModeState>())
        // No incoming files: the port suspends until cancelled.
        kotlinx.coroutines.runBlocking {
            val result = kotlinx.coroutines.withTimeoutOrNull(200) { koin.get<DesktopIncomingFiles>().receive({ _, _ -> fail("no files") }, { fail("no failure") }) }
            assertEquals(null, result)
        }
        // No crash gate before Koin.
        val gate = desktopCrashGate(paths(Files.createTempDirectory("crash-gate")))
        assertEquals(false, gate.recoveryRequired())
    }

    @Test fun nightModeSourceReportsManualAndChangesOfItsKeysOnly() = withKoin { koin, _ ->
        val prefs = koin.get<net.bible.sharedcore.settings.DeviceLocalPrefs>()
        val source = koin.get<DesktopNightModeSource>()
        var changes = 0
        val stop = source.observeChanges { changes++ }
        try {
            assertEquals(false, source.manual)
            prefs.setString("night_mode_pref3", "manual")
            assertEquals(true, source.manual)
            prefs.setString("unrelated", "x")
            prefs.setBoolean("night_mode_pref", true)
            assertEquals(2, changes)
            assertEquals(true, source.nightMode)
        } finally {
            stop()
        }
    }
}
