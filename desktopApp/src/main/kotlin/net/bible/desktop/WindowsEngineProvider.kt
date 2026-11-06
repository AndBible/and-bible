package net.bible.desktop

import androidx.compose.ui.awt.ComposeWindow
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import net.bible.desktop.webview2.WebView2Engine
import net.bible.sharedcore.platform.desktop.DesktopDeviceSettings
import net.bible.sharedcore.platform.desktop.DesktopPaths
import net.bible.sharedui.assembly.hostStringTable
import net.bible.sharedui.desktop.assembly.DESKTOP_DEVICE_SETTINGS_FILE
import net.bible.sharedui.desktop.assembly.DesktopUiLanguage
import net.bible.sharedui.desktop.webengine.DesktopWebEngine
import net.bible.sharedui.desktop.webengine.DesktopWebPane
import net.bible.sharedui.desktop.webview.DesktopPageAssetRoutes
import net.bible.sharedui.strings.keyed.StringTable
import java.nio.file.Path
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class UnsupportedWindowsRendererException(val renderer: String?) :
    IllegalStateException("WebView2 requires DIRECT3D; renderer: ${renderer ?: "UNKNOWN"}")

/** Owns startup, publication and disposal when cancellation or window closure races a blocking start. */
internal class DeferredWindowsEngine(
    private val startWebView2: () -> Result<DesktopWebEngine>,
    private val stopWebView2: (DesktopWebEngine) -> Unit,
) : DesktopWebEngine {
    private val lock = Any()
    private var closed = false
    private var abandoned = false
    private var started = false
    private var delegate: DesktopWebEngine? = null
    private var languages: (() -> String)? = null
    private var paneWindowIdSource: ((String) -> (() -> String?))? = null
    private var waiter: CancellableContinuation<Unit>? = null

    val isClosed: Boolean get() = synchronized(lock) { closed }

    suspend fun initialize(renderer: String?) {
        synchronized(lock) {
            if (closed) throw CancellationException("Windows engine closed")
            if (started) return
            if (renderer != "DIRECT3D") throw UnsupportedWindowsRendererException(renderer)
            started = true
        }
        suspendCancellableCoroutine { cont ->
            val alreadyClosed = synchronized(lock) {
                if (closed) true else { waiter = cont; false }
            }
            if (alreadyClosed) {
                cont.resumeWithException(CancellationException("Windows engine closed"))
                return@suspendCancellableCoroutine
            }
            cont.invokeOnCancellation { synchronized(lock) { abandoned = true; waiter = null } }
            Dispatchers.IO.dispatch(EmptyCoroutineContext, Runnable { runStartup() })
        }
    }

    private fun runStartup() {
        var engine: DesktopWebEngine? = null
        try {
            if (synchronized(lock) { closed || abandoned }) return
            engine = startWebView2().getOrThrow()
            val created = engine
            var waiting: CancellableContinuation<Unit>? = null
            val publish = synchronized(lock) {
                if (closed || abandoned) false else {
                    languages?.let(created::setActiveLanguages)
                    paneWindowIdSource?.let(created::setPaneWindowIdSource)
                    delegate = created
                    waiting = waiter.also { waiter = null }
                    true
                }
            }
            if (publish) {
                engine = null
                waiting?.resume(Unit)
            }
        } catch (t: Throwable) {
            synchronized(lock) { waiter.also { waiter = null } }?.resumeWithException(t)
        } finally {
            engine?.let(stopWebView2)
        }
    }

    override fun setActiveLanguages(source: () -> String) = synchronized(lock) {
        languages = source
        delegate?.setActiveLanguages(source)
        Unit
    }
    override fun setPaneWindowIdSource(source: (String) -> (() -> String?)) = synchronized(lock) {
        paneWindowIdSource = source
        delegate?.setPaneWindowIdSource(source)
        Unit
    }
    private fun ready(): DesktopWebEngine = synchronized(lock) { checkNotNull(delegate) { "Windows engine not ready" } }
    fun bindWindow(window: ComposeWindow) = synchronized(lock) {
        (delegate as? WebView2Engine)?.window = window
    }
    override fun bundleUrl(lang: String, dark: Boolean) = ready().bundleUrl(lang, dark)
    override fun createPane(id: String, url: String, documentStartScripts: List<String>, onMessage: (String) -> Unit): DesktopWebPane =
        ready().createPane(id, url, documentStartScripts, onMessage)
    fun shutdown() {
        val owned: DesktopWebEngine?
        val waiting: CancellableContinuation<Unit>?
        synchronized(lock) {
            if (closed) return
            closed = true
            owned = delegate
            delegate = null
            waiting = waiter.also { waiter = null }
        }
        waiting?.resumeWithException(CancellationException("Windows engine closed"))
        owned?.let(stopWebView2)
    }
}

/** Registers the native loader before starting WebView2, returning any failure to the caller. */
internal fun startWebView2WithLoader(
    locate: () -> Result<Path>,
    addSearchPath: (library: String, dir: String) -> Unit,
    start: (loaderDir: Path) -> Result<DesktopWebEngine>,
    log: (String) -> Unit,
): Result<DesktopWebEngine> {
    val result = runCatching {
        val dir = locate().getOrThrow()
        addSearchPath("WebView2Loader", dir.toString())
        start(dir).getOrThrow()
    }
    result.onFailure { log("webview2 unavailable: ${it.message ?: it}") }
    return result
}

internal suspend fun DeferredWindowsEngine.initializeReportingFailure(
    renderer: String?,
    logPath: Path,
    strings: StringTable,
    present: (title: String, message: String) -> Unit,
) {
    try {
        initialize(renderer)
    } catch (e: Throwable) {
        if (e is CancellationException && (!currentCoroutineContext().isActive || isClosed)) throw e
        val message = if (e is UnsupportedWindowsRendererException) {
            strings.fmt("desktop_webview2_graphics_error", e.renderer ?: "UNKNOWN", logPath)
        } else {
            strings.fmt("desktop_webview2_startup_error", "https://developer.microsoft.com/microsoft-edge/webview2/", logPath)
        }
        runCatching { present(strings.text("error_occurred"), message) }
        throw e
    }
}

internal fun showEngineFailureDialog(title: String, message: String) {
    if (java.awt.GraphicsEnvironment.isHeadless()) return
    val show = Runnable { javax.swing.JOptionPane.showMessageDialog(null, message, title, javax.swing.JOptionPane.ERROR_MESSAGE) }
    if (javax.swing.SwingUtilities.isEventDispatchThread()) show.run() else javax.swing.SwingUtilities.invokeAndWait(show)
}

class WindowsEngineProvider : DesktopEngineProvider {
    private var deferred: DeferredWindowsEngine? = null
    private var log: (String) -> Unit = {}
    private var logPath: Path = Path.of("andbible.log")
    private var strings: StringTable = hostStringTable("en")
    internal var presenter: (String, String) -> Unit = ::showEngineFailureDialog
    override val windowedRendering: Boolean get() = false

    override fun create(bundleDir: Path, paths: DesktopPaths, log: (String) -> Unit): DesktopWebEngine {
        this.log = log
        logPath = paths.data.resolve("logs").resolve("andbible.log")
        val language = runCatching {
            DesktopUiLanguage.resolve(DesktopDeviceSettings(paths.config.resolve(DESKTOP_DEVICE_SETTINGS_FILE)))
        }.getOrDefault("en")
        strings = hostStringTable(language)
        return DeferredWindowsEngine(
            startWebView2 = {
                startWebView2WithLoader(
                    locate = { WebView2LoaderLocation.resolve(System::getProperty) },
                    addSearchPath = com.sun.jna.NativeLibrary::addSearchPath,
                    start = { WebView2Engine.start(bundleDir, paths.cache.resolve("webview2"), log, DesktopPageAssetRoutes.koinBound()) },
                    log = log,
                )
            },
            stopWebView2 = { (it as WebView2Engine).shutdown() },
        ).also { deferred = it }
    }

    override suspend fun awaitWindowReady(window: ComposeWindow) {
        var renderer: String? = null
        for (attempt in 0 until 100) {
            renderer = window.renderApi.toString()
            if (renderer != "UNKNOWN") break
            delay(10)
        }
        log("desktop renderer: ${renderer ?: "UNKNOWN"}; engine: WEBVIEW2")
        val engine = checkNotNull(deferred)
        engine.initializeReportingFailure(renderer, logPath, strings, presenter)
        currentCoroutineContext().ensureActive()
        engine.bindWindow(window)
    }
    override fun shutdown() { deferred?.shutdown() }
}
