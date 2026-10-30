package net.bible.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.log.configureDesktopLogFile
import net.bible.sharedcore.platform.desktop.AlreadyRunningException
import net.bible.sharedcore.platform.desktop.DesktopDeviceSettings
import net.bible.sharedcore.platform.desktop.DesktopPaths
import net.bible.sharedcore.platform.desktop.InstanceLock
import net.bible.sharedcore.platform.desktop.files.InstanceHandOff
import java.nio.file.Paths
import net.bible.sharedui.assembly.StartupPhase
import net.bible.sharedui.desktop.assembly.DESKTOP_DEVICE_SETTINGS_FILE
import net.bible.sharedui.desktop.assembly.DesktopAppHostPlatform
import net.bible.sharedui.desktop.assembly.DesktopAppStartup
import net.bible.sharedui.desktop.assembly.DesktopHostLifecycle
import net.bible.sharedui.desktop.assembly.DesktopStartupException
import net.bible.sharedui.desktop.assembly.DesktopUiLanguage
import net.bible.sharedui.desktop.assembly.desktopCrashGate
import net.bible.sharedui.desktop.assembly.desktopRendererRecovery
import net.bible.sharedui.desktop.webengine.DesktopBibleWebViews
import net.bible.sharedui.desktop.webengine.DesktopWebPaneKeys
import net.bible.sharedui.desktop.webengine.kcef.KcefWebEngine
import net.bible.sharedui.desktop.webview.DesktopReadingInput
import net.bible.sharedui.desktop.webview.DesktopPageAssetRoutes
import net.bible.sharedui.desktop.webview.DesktopReadingPaneEngine
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.GraphicsEnvironment
import java.nio.file.Files
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.system.exitProcess
import javax.swing.SwingUtilities

private const val TAG = "DesktopMain"
private const val STARTUP_SETTLE_TIMEOUT_MS = 10_000L
private const val EXIT_SETTLE_MS = 200L

/** Held for the whole process: the lock on `<data>/instance.lock` lasts as long as this reference (and the JVM). */
@Volatile private var instanceLock: AutoCloseable? = null

/** The cold start's own scope (Dispatchers.IO); an uncaught failure is logged, never fatal. */
private val startupScope = CoroutineScope(
    SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> Log.e(TAG, "uncaught in startup scope", e) },
)

/** The `compose.layers.type` for the pane rendering mode: `WINDOW` when [windowed], else null (Compose's in-window default). */
internal fun composeLayersTypeFor(windowed: Boolean): String? = if (windowed) "WINDOW" else null

/**
 * Starts the desktop app: the real AndBible app by default, the D1 PoC when `andbible.poc.scenario` is set
 * ([PocLaunchOptions]). The CEF natives come from [CefNativesLocation]: `kcef.installDir` (set by
 * `./gradlew :desktopApp:run`, built by `:desktopApp:prepareKcefNatives`) or the packaged app's resources dir;
 * `kcef.args` optionally adds Chromium switches (space-separated). [args] (files to open, for example) reach the app
 * graph as `DesktopLaunchArgs` (read by D3b); the PoC ignores them.
 */
fun main(args: Array<String>) {
    // Windowed panes are heavyweight components, so Compose dialogs and popups need their own windows to show above them
    // (D1 verified it). Off-screen panes paint into a Compose canvas: popups stay in-window, correctly placed and without
    // black corners. The mode is the engine's own one-time resolution. Before any Compose window and before KCEF init.
    composeLayersTypeFor(KcefWebEngine.isWindowedRendering())?.let { System.setProperty("compose.layers.type", it) }
    if (System.getProperty("andbible.poc.scenario") != null) runPoc() else runApp(args.toList())
}

/**
 * The real app, in this order: instance lock (a second instance prints one line and exits 0 before anything else);
 * the log file in `<data>/logs` (when the data directory is usable); display check; the crash gate
 * (`desktopCrashGate`, D3d); the CEF cache directory check; CEF natives and `KcefWebEngine.init` (CEF cache in `<cache>/cef`); `DesktopAppStartup.ensureStarted`
 * off the UI thread; the window ([DesktopAppRoot]); window activation, deactivation and close wired to
 * [DesktopHostLifecycle]. A data directory that cannot be written skips CEF and the startup and shows the
 * startup-failed screen naming the directory.
 */
private fun runApp(launchArgs: List<String>) {
    val paths = DesktopPaths.system()
    val lockFailure = InstanceLock.acquire(paths.data).fold(onSuccess = { instanceLock = it; null }, onFailure = { it })
    if (lockFailure is AlreadyRunningException) {
        val files = try { launchArgs.map { Paths.get(it).toAbsolutePath().normalize() } } catch (e: Exception) {
            System.err.println("AndBible could not resolve incoming files: ${e.message}")
            exitProcess(1)
        }
        if (InstanceHandOff.send(paths.data, files)) exitProcess(0)
        System.err.println("AndBible is already running, but incoming files could not be delivered")
        exitProcess(1)
    }
    // The lock proved the data directory writable: from here on every failure (CEF, natives) reaches the log file too.
    if (lockFailure == null) configureDesktopLogFile(paths.data.resolve("logs"))
    requireDisplay()

    val windowHandle = AwtWindowHandle()
    val lifecycle = DesktopHostLifecycle()
    val exitRequested = mutableStateOf(false)
    var shutdownEngine: () -> Unit = {}
    val state: DesktopStartupState
    if (lockFailure != null) {
        val failure = DesktopStartupException(paths.data, lockFailure)
        System.err.println("AndBible desktop: ${failure.message}")
        state = DesktopStartupState(
            language = DesktopUiLanguage.resolve(DesktopDeviceSettings(paths.config.resolve(DESKTOP_DEVICE_SETTINGS_FILE))),
            start = { StartupPhase.Failed(failure) },
            platform = { error("no app root without a startup") },
            onExit = { exitRequested.value = true },
        )
    } else {
        val language = DesktopUiLanguage.resolve(DesktopDeviceSettings(paths.config.resolve(DESKTOP_DEVICE_SETTINGS_FILE)))
        val crashGate = desktopCrashGate(paths)
        val cefCache = paths.cefCache
        requireWritableDirectory(cefCache, "the cache directory")
        val installDir = CefNativesLocation.resolve(System::getProperty).getOrElse { fail(it.message ?: it.toString()) }
        val args = System.getProperty("kcef.args").orEmpty().split(' ').filter { it.isNotBlank() }
        KcefWebEngine.init(installDir, args, cacheDir = cefCache).onFailure { fail(it.message ?: it.toString()) }

        val bundleRoot = Files.createTempDirectory("andbible-bibleview")
        val index = BibleViewBundle.extractTo(bundleRoot)
        // stdout: the readiness line `[desktop] reading ready <windowId>` is what the smoke checks wait for.
        val log: (String) -> Unit = { line -> println(line) }
        val engine = KcefWebEngine(
            installDir, index.parent, log,
            // Bound once the app graph exists (page requests start after it); nothing before that.
            pageAssets = DesktopPageAssetRoutes.koinBound(),
        )
        val panes = DesktopReadingPaneEngine(engine, log, lang = { language }, onRendererGone = desktopRendererRecovery())
        val platform by lazy { DesktopAppHostPlatform(lifecycle, panes, windowHandle) }
        val webViews = DesktopBibleWebViews(engine, log)
        state = DesktopStartupState(
            language = language,
            start = { openDatabases ->
                DesktopAppStartup.ensureStarted(
                    paths, windowHandle, language, openDatabases, launchArgs = launchArgs, lifecycleEvents = lifecycle.events,
                )
            },
            platform = { platform },
            bibleWebView = webViews.factory,
            crashRecoveryRequired = crashGate.recoveryRequired,
            reportBug = crashGate.reportBug,
            onExit = { exitRequested.value = true },
            onStartupReady = crashGate.onStartupReady,
            onCleanExit = crashGate.onCleanExit,
        )
        shutdownEngine = {
            try {
                webViews.disposeAll()
                KcefWebEngine.shutdown()
            } finally {
                bundleRoot.toFile().deleteRecursively()
            }
        }
        // No gate is up: start now, while the window is being created. With a gate, the root starts after it resolves.
        if (!state.crashRecoveryRequired()) state.launch(startupScope)
    }

    val watchdog = ExitWatchdog()
    // exitProcessOnExit = false: the exit below runs only after the windows are gone and the AWT thread is idle.
    application(exitProcessOnExit = false) {
        var closing by remember { mutableStateOf(false) }
        val requestClose: () -> Unit = {
            if (!closing) {
                // ResignActive and Background reach every observer (the workspace save) before the content goes away.
                lifecycle.onExit()
                closing = true
            }
        }
        if (exitRequested.value) LaunchedEffect(Unit) { requestClose() }
        if (closing) LaunchedEffect(Unit) { shutDown(state, shutdownEngine, watchdog) }
        Window(
            title = "AndBible",
            state = rememberWindowState(size = DpSize(AwtWindowHandle.DEFAULT_WIDTH_DP.dp, AwtWindowHandle.DEFAULT_HEIGHT_DP.dp)),
            onCloseRequest = requestClose,
            // Keys and back (D3a): the bound reading input, the no-op until the graph exists.
            // An off-screen web pane that has focus takes every key itself (the page's script owns the shortcuts).
            onPreviewKeyEvent = { DesktopWebPaneKeys.windowKey { DesktopReadingInput.current().onPreviewKeyEvent(it) } },
            onKeyEvent = { DesktopWebPaneKeys.windowKey { DesktopReadingInput.current().onKeyEvent(it) } },
        ) {
            val awtWindow = window
            DisposableEffect(awtWindow) {
                awtWindow.minimumSize = Dimension(AwtWindowHandle.MIN_WIDTH_DP, AwtWindowHandle.MIN_HEIGHT_DP)
                windowHandle.attach(awtWindow)
                // Active / ResignActive for the app as a whole: a Compose dialog or popup is a window of its own
                // owned by this one in windowed mode (layers=WINDOW; in-window and no window at all in off-screen mode), so moving the focus into it is not leaving the app.
                val tracker = DesktopAppFocusTracker(
                    isAppWindow = DesktopAppFocusTracker.ownedBy(awtWindow),
                    onActive = lifecycle::onWindowActivated,
                    onInactive = lifecycle::onWindowDeactivated,
                )
                val uninstall = DesktopAppFocusTracker.install(tracker)
                if (awtWindow.isActive) tracker.activated(awtWindow)
                onDispose { uninstall() }
            }
            // Closing is two-phase: the content (and with it the CEF panes' SwingPanels) leaves the composition first;
            // disposing the window while they are still in it removes the CEF canvases from an already-disposed
            // SkiaLayer ("SkiaLayer is disposed" crash, process left hanging).
            if (!closing) DesktopAppRoot(state)
        }
    }
    exitAfterWindows(watchdog)
}

/**
 * The second phase of closing, after the content has left the composition: let the frames and AWT's own queued
 * removals run, write the workspace and settings (bounded), shut CEF down, then exit.
 */
internal fun persistAndMarkClean(persist: () -> Unit, clean: () -> Unit) {
    persist()
    clean()
}

private suspend fun ApplicationScope.shutDown(state: DesktopStartupState, shutdownEngine: () -> Unit, watchdog: ExitWatchdog) {
    withFrameNanos { } // the frame that applies the removal of the panes
    withFrameNanos { }
    repeat(2) { suspendCoroutine<Unit> { c -> SwingUtilities.invokeLater { c.resume(Unit) } } }
    // A close during the startup: let it finish (bounded), so nothing is persisted or shut down under a half-built graph.
    if (!state.awaitStartupSettled(STARTUP_SETTLE_TIMEOUT_MS)) Log.w(TAG, "closing while the startup still runs")
    withContext(Dispatchers.IO) { persistAndMarkClean({ DesktopAppStartup.persistBeforeExit() }, state::markCleanExit) }
    try {
        shutdownEngine()
    } catch (e: Throwable) {
        Log.e(TAG, "engine shutdown failed", e)
    }
    watchdog.markPersisted()
    exitApplication()
}

/**
 * The process exit after `application` returned (windows disposed). The AWT event queue is drained and the toolkit given
 * [EXIT_SETTLE_MS] so AWT-XAWT is back in its native poll and not holding the Xlib display lock when the exit safepoint
 * freezes it (libGLX's exit handler waits for that lock). The [ExitWatchdog] backs that up on Linux.
 */
private fun exitAfterWindows(watchdog: ExitWatchdog): Nothing {
    runCatching { EventQueue.invokeAndWait { } }
    runCatching { Thread.sleep(EXIT_SETTLE_MS) }
    runCatching { EventQueue.invokeAndWait { } }
    watchdog.arm()
    exitProcess(0)
}

/** CEF cannot start without a display and then takes the JVM down silently (D1 defect 4): fail clearly instead. */
private fun requireDisplay() {
    val linux = System.getProperty("os.name").orEmpty().lowercase().contains("linux")
    val noDisplay = linux && System.getenv("DISPLAY").isNullOrBlank() && System.getenv("WAYLAND_DISPLAY").isNullOrBlank()
    if (GraphicsEnvironment.isHeadless() || noDisplay) {
        fail("no graphical display (DISPLAY and WAYLAND_DISPLAY are not set, or the JVM is headless); AndBible needs a desktop session")
    }
}

/**
 * The D1 PoC on the KCEF engine. The scenario, theme and spike diagnostics come from the `andbible.poc.*` properties
 * ([PocLaunchOptions]).
 */
private fun runPoc() {
    val options = runCatching { PocLaunchOptions.from(System::getProperty) }.getOrElse { fail(it.message ?: it.toString()) }
    val installDir = CefNativesLocation.resolve(System::getProperty).getOrElse { fail(it.message ?: it.toString()) }
    val args = System.getProperty("kcef.args").orEmpty().split(' ').filter { it.isNotBlank() }
    KcefWebEngine.init(installDir, args).onFailure { fail(it.message ?: it.toString()) }

    val bundleRoot = Files.createTempDirectory("andbible-bibleview")
    val index = BibleViewBundle.extractTo(bundleRoot)
    val session = DesktopWebSession(
        // Spike-only (D1 measurements), remove in D2: the SpikeTimingEngine wrapper.
        engine = KcefWebEngine(installDir, index.parent).let { if (options.spikeLog) SpikeTimingEngine(it) else it },
        shutdownEngine = KcefWebEngine::shutdown,
        cleanUp = { bundleRoot.toFile().deleteRecursively() },
    )
    val documentJson = BibleViewBundle.pocDocumentJson()

    application {
        // Closing is two-phase (see runApp): onCloseRequest only flips [closing], which takes the panes out of the
        // composition; the engine is then shut down and the application exits.
        var closing by remember { mutableStateOf(false) }
        if (closing) {
            LaunchedEffect(Unit) {
                withFrameNanos { } // the frame that applies the removal of the panes
                withFrameNanos { }
                repeat(2) { suspendCoroutine<Unit> { c -> SwingUtilities.invokeLater { c.resume(Unit) } } } // AWT's own queued removals
                session.close()
                exitApplication()
            }
        }
        Window(
            title = "AndBible",
            onCloseRequest = { closing = true },
        ) {
            // Spike-only (D1 measurements): the theme-flip timer and SpikeOverlay. With only andbible.poc.scenario set
            // this is the plain PoC: light theme, no overlay.
            var dark by remember { mutableStateOf(options.dark) }
            options.themeFlipMs?.let { period ->
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(period)
                        dark = !dark
                        if (options.spikeLog) spikeLog("theme flip -> dark=$dark")
                    }
                }
            }
            Box {
                if (!closing) {
                    DesktopPocRoot(documentJson, session.webViews.factory, options.scenario, dark)
                    options.overlay?.let { SpikeOverlay(it) }
                }
            }
        }
    }
}

/**
 * Fails clearly (stderr, the log file once configured) when [dir] cannot be created or written; CEF would otherwise fail
 * with an unclear message, or not at all until later.
 */
private fun requireWritableDirectory(dir: java.nio.file.Path, what: String) {
    val problem = try {
        Files.createDirectories(dir)
        if (Files.isDirectory(dir) && Files.isWritable(dir)) null else "not writable"
    } catch (e: Exception) {
        e.toString()
    }
    if (problem != null) fail("$what $dir cannot be used ($problem); check the cache location (XDG_CACHE_HOME on Linux) and its permissions")
}

private fun fail(message: String): Nothing {
    // Log.e writes stderr and, once main() configured it, the log file.
    Log.e(TAG, "AndBible desktop: $message")
    exitProcess(1)
}
