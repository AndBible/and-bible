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

import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import net.bible.android.control.versification.BookInstallWatcher
import net.bible.service.cloudsync.desktop.desktopSyncModule
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.log.DesktopLogFile
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.log.configureDesktopLogFile
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.platform.desktop.DataDirUnwritableException
import net.bible.sharedcore.platform.desktop.DesktopPaths
import net.bible.sharedlogic.platform.desktop.DesktopDatabaseHost
import net.bible.sharedlogic.platform.desktop.DesktopLocaleProvider
import net.bible.sharedlogic.platform.host.IosSwordEnvironment
import net.bible.sharedlogic.startup.SharedAppStartup
import net.bible.sharedlogic.startup.StartupHooks
import net.bible.sharedui.assembly.HostStartup
import net.bible.sharedui.assembly.HostStartupInfo
import net.bible.sharedui.assembly.IosStringTableHolder
import net.bible.sharedui.assembly.StartupPhase
import net.bible.sharedui.host.app.IosWorkspaceSaver
import net.bible.sharedui.strings.keyed.StringTable
import okio.Path.Companion.toOkioPath
import org.crosswire.common.util.PropertyMap
import org.crosswire.common.util.ReporterEvent
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookPath
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.mp.KoinPlatform
import java.nio.file.Files
import java.nio.file.Path

private const val TAG = "DesktopAppStartup"

/** A desktop startup failure; the message always names the data directory, which the failure screen shows. */
class DesktopStartupException(val dataDir: Path, cause: Throwable) :
    Exception("AndBible could not start with the data directory $dataDir: ${cause.message ?: cause}", cause)

/**
 * The desktop app startup, the counterpart of `IosAppStartup` without the iOS gates (legacy migration, discrete
 * mode, keychain, background tasks). [ensureStarted] runs, in this order:
 * 1. checks that the data directory can be created and written ([DataDirUnwritableException] otherwise);
 * 2. starts the log file in `<data>/logs`;
 * 3. installs the JSword environment (repository site map, extra book types, locale provider);
 * 4. points JSword at `<data>/sword` (before anything touches JSword);
 * 5. builds the Koin graph (`desktopPlatformModule` + the four stream modules), installs the [DesktopDatabaseHost]
 *    over the graph's environment BEFORE anything can read a setting, then publishes the graph globally;
 * 6. runs the shared startup (download transport, store hooks, JSword error listener, book-install watcher);
 * 7. opens the databases (when asked), off the calling thread's UI: the caller is never the AWT event thread.
 *
 * [HostStartup] is registered from the first call (`isStarted` / `databasesOpen` turn true as the stages complete),
 * so the app root and the reading host read this startup's string table.
 *
 * A failure in any stage returns [StartupPhase.Failed] with a [DesktopStartupException] naming the data directory; a
 * later call resumes from the stage that failed. Repeated calls after success return [StartupPhase.Ready] at once and
 * keep the first configuration. Thread safe (one lock), blocking: never call it on the UI thread.
 */
object DesktopAppStartup {
    private val lock = Any()

    @Volatile private var koinApp: KoinApplication? = null
    @Volatile private var opened = false
    private val shownErrors = HashMap<String, Long>()

    private val info = object : HostStartupInfo {
        override val isStarted: Boolean get() = koinApp != null
        override val databasesOpen: Boolean get() = opened
        override val stringTable: StringTable
            get() = checkNotNull(koinApp) { "DesktopAppStartup.ensureStarted has not completed" }.koin.get<IosStringTableHolder>().live
    }

    /** True once the Koin graph is published (the databases may still be closed). */
    val isStarted: Boolean get() = koinApp != null

    /** True once the databases are open. */
    val databasesOpen: Boolean get() = opened

    /**
     * Starts the app for [paths] and [window] in the UI [language]. [openDatabases] false stops after Koin (the
     * crash-recovery backup path needs the databases closed); a later call with true opens them. [repositories] is the
     * repository site map (`repositories.txt` on the classpath; tests pass their own).
     */
    fun ensureStarted(
        paths: DesktopPaths,
        window: DesktopWindowHandle,
        language: String,
        openDatabases: Boolean = true,
        repositories: () -> PropertyMap = ::classpathRepositories,
    ): StartupPhase = synchronized(lock) {
        HostStartup.register(info)
        try {
            if (koinApp == null) startCore(paths, window, language, repositories)
            if (openDatabases && !opened) openDatabasesLocked()
            StartupPhase.Ready
        } catch (e: Throwable) {
            val failure = e as? DesktopStartupException ?: DesktopStartupException(paths.data, e)
            Log.e(TAG, failure.message ?: "startup failed", e)
            StartupPhase.Failed(failure)
        }
    }

    private fun startCore(paths: DesktopPaths, window: DesktopWindowHandle, language: String, repositories: () -> PropertyMap) {
        ensureWritableDirectory(paths.data)
        configureDesktopLogFile(paths.data.resolve("logs"))
        Log.i(TAG, "Starting (data ${paths.data}, config ${paths.config}, cache ${paths.cache}, language $language)")
        SharedAppStartup.installEnvironment(repositories(), DesktopLocaleProvider())
        DesktopSwordFolders.initialise(paths.sword)
        check(KoinPlatform.getKoinOrNull() == null) { "a Koin context is already running; the desktop startup owns it" }
        val app = KoinApplication.init()
        try {
            app.modules(
                desktopPlatformModule(paths, window, language),
                desktopReadingModule(), desktopDocumentModule(), desktopMiscModule(), desktopSyncModule(),
            )
            // Before any eager instance or other reader can touch a setting: the settings store opens through this host.
            DatabaseContainer.installHost(DesktopDatabaseHost(app.koin.get()))
            app.createEagerInstances()
            startKoin(app)
        } catch (e: Throwable) {
            if (KoinPlatform.getKoinOrNull() === app.koin) runCatching { stopKoin() } else runCatching { app.close() }
            throw e
        }
        try {
            SharedAppStartup.start(StartupHooks(
                initialiseJSwordFolders = { DesktopSwordFolders.initialise(paths.sword) },
                koinModules = { error("Koin is started by DesktopAppStartup") },
                reportJSwordError = ::reportJSwordError,
                isSwordLoaded = { DesktopSwordFolders.isLoaded },
            ))
        } catch (e: Throwable) {
            detachBookInstallWatcher(app.koin)
            runCatching { stopKoin() }
            SharedAppStartup.resetForTest() // drops the JSword error listener, if it was installed
            throw e
        }
        koinApp = app
    }

    /** As iOS: [DatabaseContainer.ready] on, the container built through the host, every database touched. */
    private fun openDatabasesLocked() {
        DatabaseContainer.ready = true
        try {
            runBlocking { DatabaseContainer.instance.sync() } // the caller is off the UI thread (see ensureStarted)
        } catch (e: Throwable) {
            DatabaseContainer.ready = false
            throw e
        }
        opened = true
        Log.i(TAG, "Databases open")
    }

    /**
     * The last writes before the process exits (window close, after the lifecycle's `Background`): the workspace save
     * that the `ResignActive`/`Background` observers started is joined (or redone) here, bounded by [timeoutMs], and the
     * queued settings are flushed. Blocking; never throws.
     */
    fun persistBeforeExit(timeoutMs: Long = 5_000) {
        val koin = koinApp?.koin ?: return
        try {
            val saved = runBlocking { withTimeoutOrNull(timeoutMs) { IosWorkspaceSaver.saveNow(koin) } }
            if (saved == null) Log.w(TAG, "Workspace save did not finish within $timeoutMs ms before exit")
        } catch (e: Throwable) {
            Log.e(TAG, "Workspace save before exit failed", e)
        }
        if (opened) {
            runCatching { DatabaseContainer.flushSettingsBeforeExit(timeoutMs = 2_000) }
                .onFailure { Log.e(TAG, "Settings flush before exit failed", it) }
        }
    }

    /** Shows a JSword error once per five minutes per message, as Android's `reportJSwordError` and iOS do. */
    private fun reportJSwordError(ev: ReporterEvent) {
        val koin = KoinPlatform.getKoinOrNull() ?: return
        val message = when {
            !ev.message.isNullOrEmpty() -> ev.message!!
            !ev.exception?.message.isNullOrEmpty() ->
                koin.get<IosStringTableHolder>().current.fmt("error_occurred_with_link", ev.exception?.message)
            else -> koin.get<CoreStrings>().errorOccurred
        }
        val now = System.currentTimeMillis()
        val recent = synchronized(shownErrors) {
            val last = shownErrors[message]
            shownErrors[message] = now
            last != null && now - last < FIVE_MINUTES_MS
        }
        if (recent) return
        koin.get<UserNotifier>().showError(message, ev.exception)
    }

    private fun detachBookInstallWatcher(koin: Koin) {
        runCatching { koin.getOrNull<BookInstallWatcher>()?.let { Books.installed().removeBooksListener(it.listener) } }
    }

    /**
     * Test only: forgets everything [ensureStarted] set up (app scope, Koin, JSword error listener and install watcher,
     * open databases, log file, the [HostStartup] registration) so the next test starts clean. The database host stays
     * installed (there is no uninstall; tests put back their previous host). JSword's folder setup is process-wide; the
     * next start re-points it at its own `<data>/sword`.
     */
    fun resetForTest(): Unit = synchronized(lock) {
        runBlocking {
            withTimeout(30_000) {
                val koin = koinApp?.koin
                if (koin != null) {
                    runCatching {
                        koin.getOrNull<AppCoroutineScope>()?.let { appScope ->
                            appScope.cancel()
                            withTimeoutOrNull(1_000) { appScope.coroutineContext[Job]?.children?.toList()?.forEach { it.join() } }
                        }
                    }
                    detachBookInstallWatcher(koin)
                }
                if (KoinPlatform.getKoinOrNull() === koin) runCatching { stopKoin() }
                SharedAppStartup.resetForTest()
                if (opened) DatabaseContainer.aiLifetime.replace { DatabaseContainer.resetOwned(it) }
                DatabaseContainer.ready = false
                DatabaseContainer.forgetReplacesForTest()
                DatabaseContainer.detachInstanceForTest()
            }
        }
        DesktopLogFile.close()
        HostStartup.register(null)
        koinApp = null
        opened = false
        synchronized(shownErrors) { shownErrors.clear() }
    }

    private fun ensureWritableDirectory(dir: Path) {
        try {
            Files.createDirectories(dir)
        } catch (e: Exception) {
            throw DataDirUnwritableException(dir, e)
        }
        if (!Files.isDirectory(dir) || !Files.isWritable(dir)) throw DataDirUnwritableException(dir)
    }

    private const val FIVE_MINUTES_MS = 5 * 60 * 1000L
}

/**
 * JSword's module folder on the desktop: `<data>/sword` only. [IosSwordEnvironment] sets JSword's process-wide state up
 * once (passage key type, frontend name, project home, network timeout); a start with another directory (tests: each
 * one has its own data directory) re-points the download dir and the augment path, which re-registers the Sword driver,
 * so the installed book list follows the new folder.
 */
internal object DesktopSwordFolders {
    @Volatile private var current: Path? = null

    val isLoaded: Boolean get() = current != null && IosSwordEnvironment.isLoaded

    fun initialise(dir: Path) {
        val modules = dir.toOkioPath()
        IosSwordEnvironment.createModuleFolders(modules) // throws: a module folder that cannot be created fails startup
        IosSwordEnvironment.initialiseJSwordFolders(modules)
        if (current == dir) return
        if (SwordBookPath.getDownloadDir() != modules) {
            SwordBookPath.setDownloadDir(modules)
            SwordBookPath.setAugmentPath(arrayOf(modules))
        }
        current = dir
    }
}

/** The repository site map, `repositories.txt` on the classpath (the build copies `app/src/main/res/raw/repositories.txt`). */
internal fun classpathRepositories(): PropertyMap {
    val loader = DesktopAppStartup::class.java.classLoader
    val stream = listOf("repositories.txt", "repositories", "raw/repositories.txt").firstNotNullOfOrNull { loader.getResourceAsStream(it) }
        ?: error("repositories.txt is not on the classpath (package app/src/main/res/raw/repositories.txt)")
    return PropertyMap().apply { load(stream.use { it.readBytes() }) }
}
