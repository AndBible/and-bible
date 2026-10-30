package net.bible.desktop.webview2

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import net.bible.desktop.AwtWindowHandle
import net.bible.desktop.BibleViewBundle
import net.bible.desktop.DesktopAppFocusTracker
import net.bible.desktop.DesktopAppRoot
import net.bible.desktop.DesktopStartupState
import net.bible.sharedcore.platform.desktop.DesktopDeviceSettings
import net.bible.sharedcore.platform.desktop.DesktopPaths
import net.bible.sharedui.desktop.assembly.DESKTOP_DEVICE_SETTINGS_FILE
import net.bible.sharedui.desktop.assembly.DesktopAppHostPlatform
import net.bible.sharedui.desktop.assembly.DesktopAppStartup
import net.bible.sharedui.desktop.assembly.DesktopHostLifecycle
import net.bible.sharedui.desktop.assembly.DesktopUiLanguage
import net.bible.sharedui.desktop.assembly.desktopRendererRecovery
import net.bible.sharedui.desktop.webengine.DesktopBibleWebViews
import net.bible.sharedui.desktop.webengine.DesktopWebPaneKeys
import net.bible.sharedui.desktop.webview.DesktopPageAssetRoutes
import net.bible.sharedui.desktop.webview.DesktopReadingInput
import net.bible.sharedui.desktop.webview.DesktopReadingPaneEngine
import java.awt.Dimension
import java.nio.file.Files
import java.nio.file.Paths
import javax.swing.SwingUtilities
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.system.exitProcess

/**
 * The real AndBible desktop app on the WebView2 engine, for the W2 live checks only: a trimmed copy of `:desktopApp`'s
 * `runApp` (no instance lock and hand-off, crash gate, log file or exit watchdog; those are engine-independent and
 * `:desktopApp` must not change in W2). Same data directories as the real app.
 */
fun appMain() {
    if (!System.getProperty("os.name").startsWith("Windows")) fail("the WebView2 engine runs on Windows only")
    System.setProperty("compose.interop.blending", "true")
    val paths = DesktopPaths.system()
    val windowHandle = AwtWindowHandle()
    val lifecycle = DesktopHostLifecycle()
    val exitRequested = mutableStateOf(false)
    val language = DesktopUiLanguage.resolve(DesktopDeviceSettings(paths.config.resolve(DESKTOP_DEVICE_SETTINGS_FILE)))
    val bundleRoot = Files.createTempDirectory("andbible-bibleview")
    val index = BibleViewBundle.extractTo(bundleRoot)
    val log: (String) -> Unit = { line -> println(line) }
    val wv2 = WebView2Engine.start(index.parent, paths.cache.resolve("webview2"), log, DesktopPageAssetRoutes.koinBound())
        .getOrElse { fail("WebView2 unavailable (the app would fall back to KCEF): ${it.message}") }
    val engine = System.getProperty("andbible.w2.evalDir")?.let { EvalProbeEngine(wv2, Paths.get(it)) } ?: wv2
    val panes = DesktopReadingPaneEngine(engine, log, lang = { language }, onRendererGone = desktopRendererRecovery())
    val platform by lazy { DesktopAppHostPlatform(lifecycle, panes, windowHandle) }
    val webViews = DesktopBibleWebViews(engine, log)
    val state = DesktopStartupState(
        language = language,
        start = { openDatabases ->
            DesktopAppStartup.ensureStarted(paths, windowHandle, language, openDatabases, launchArgs = emptyList(), lifecycleEvents = lifecycle.events)
        },
        platform = { platform },
        bibleWebView = webViews.factory,
        onExit = { exitRequested.value = true },
    )
    state.launch(CoroutineScope(SupervisorJob() + Dispatchers.IO))

    application(exitProcessOnExit = false) {
        var closing by remember { mutableStateOf(false) }
        val requestClose: () -> Unit = {
            if (!closing) {
                lifecycle.onExit()
                closing = true
            }
        }
        if (exitRequested.value) LaunchedEffect(Unit) { requestClose() }
        if (closing) {
            LaunchedEffect(Unit) {
                withFrameNanos { }
                withFrameNanos { }
                repeat(2) { suspendCoroutine<Unit> { c -> SwingUtilities.invokeLater { c.resume(Unit) } } }
                state.awaitStartupSettled(10_000)
                withContext(Dispatchers.IO) { DesktopAppStartup.persistBeforeExit() }
                webViews.disposeAll()
                wv2.shutdown()
                bundleRoot.toFile().deleteRecursively()
                exitApplication()
            }
        }
        Window(
            title = "AndBible (WebView2)",
            state = rememberWindowState(size = DpSize(AwtWindowHandle.DEFAULT_WIDTH_DP.dp, AwtWindowHandle.DEFAULT_HEIGHT_DP.dp)),
            onCloseRequest = requestClose,
            onPreviewKeyEvent = { DesktopWebPaneKeys.windowKey { DesktopReadingInput.current().onPreviewKeyEvent(it) } },
            onKeyEvent = { DesktopWebPaneKeys.windowKey { DesktopReadingInput.current().onKeyEvent(it) } },
        ) {
            val awtWindow = window
            wv2.window = awtWindow
            DisposableEffect(awtWindow) {
                awtWindow.minimumSize = Dimension(AwtWindowHandle.MIN_WIDTH_DP, AwtWindowHandle.MIN_HEIGHT_DP)
                windowHandle.attach(awtWindow)
                val tracker = DesktopAppFocusTracker(
                    isAppWindow = DesktopAppFocusTracker.ownedBy(awtWindow),
                    onActive = lifecycle::onWindowActivated,
                    onInactive = lifecycle::onWindowDeactivated,
                )
                val uninstall = DesktopAppFocusTracker.install(tracker)
                if (awtWindow.isActive) tracker.activated(awtWindow)
                onDispose { uninstall() }
            }
            if (!closing) DesktopAppRoot(state)
        }
    }
    exitProcess(0)
}

private fun fail(message: String): Nothing {
    System.err.println("AndBible desktop (WebView2): $message")
    exitProcess(1)
}
