package net.bible.desktop

import androidx.compose.ui.awt.ComposeWindow
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import net.bible.desktop.webview2.WebView2Engine
import net.bible.sharedcore.platform.desktop.DesktopDeviceSettings
import net.bible.sharedcore.platform.desktop.DesktopPaths
import net.bible.sharedui.desktop.assembly.DESKTOP_DEVICE_SETTINGS_FILE
import net.bible.sharedui.desktop.assembly.DesktopUiLanguage
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.desktop.webengine.DesktopWebEngine
import net.bible.sharedui.desktop.webengine.DesktopWebPane
import net.bible.sharedui.desktop.webview.DesktopPageAssetRoutes
import java.nio.file.Path
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal fun windowsWindowedRendering(kcefWindowed: Boolean): Boolean = kcefWindowed

internal enum class WindowsEngineChoice { WEBVIEW2, KCEF }

internal fun selectWindowsEngine(property: String, renderer: String?, runtimeAvailable: Boolean): WindowsEngineChoice {
    require(property == "auto" || property == "kcef") { "Invalid andbible.windows.webEngine: $property" }
    return if (property == "auto" && renderer == "DIRECT3D" && runtimeAvailable) WindowsEngineChoice.WEBVIEW2 else WindowsEngineChoice.KCEF
}

/**
 * Owns publication and disposal across engine startup racing with cancellation or a window close.
 *
 * The blocking engine start runs on one IO worker that owns its result; the suspended caller never waits for
 * it once cancelled or after [shutdown]. A start already running cannot be aborted, so a late engine is
 * disposed by the worker exactly once.
 */
internal class DeferredWindowsEngine(
    private val property: String,
    private val startWebView2: () -> Result<DesktopWebEngine>,
    private val startKcef: () -> DesktopWebEngine,
    private val stopWebView2: (DesktopWebEngine) -> Unit,
    private val stopKcef: (DesktopWebEngine) -> Unit,
) : DesktopWebEngine {
    private val lock = Any()
    private var closed = false
    private var abandoned = false
    private var started = false
    private var delegate: DesktopWebEngine? = null
    private var stop: ((DesktopWebEngine) -> Unit)? = null
    private var languages: (() -> String)? = null
    private var waiter: CancellableContinuation<Unit>? = null

    /** The message of the WebView2 failure that led to the KCEF fallback, kept for the both-failed report. */
    @Volatile var webView2Failure: String? = null
        private set

    suspend fun initialize(renderer: String?) {
        synchronized(lock) {
            if (closed) throw CancellationException("Windows engine closed")
            if (started) return
            started = true
        }
        val selected = selectWindowsEngine(property, renderer, true)
        suspendCancellableCoroutine { cont ->
            val alreadyClosed = synchronized(lock) {
                if (closed) true else { waiter = cont; false }
            }
            if (alreadyClosed) {
                cont.resumeWithException(CancellationException("Windows engine closed"))
                return@suspendCancellableCoroutine
            }
            cont.invokeOnCancellation { synchronized(lock) { abandoned = true; waiter = null } }
            Dispatchers.IO.dispatch(EmptyCoroutineContext, Runnable { runStartup(selected) })
        }
    }

    private fun mayStart(): Boolean = synchronized(lock) { !closed && !abandoned }

    private fun takeWaiter(): CancellableContinuation<Unit>? = synchronized(lock) { waiter.also { waiter = null } }

    /** Worker body: starts the engine, then publishes it or disposes it exactly once. */
    private fun runStartup(selected: WindowsEngineChoice) {
        var engine: DesktopWebEngine? = null
        var closer: (DesktopWebEngine) -> Unit = stopKcef
        try {
            if (selected == WindowsEngineChoice.WEBVIEW2) {
                if (!mayStart()) return
                val started = runCatching { startWebView2() }.getOrElse { Result.failure(it) }
                engine = started.getOrNull()
                if (engine != null) closer = stopWebView2
                else webView2Failure = started.exceptionOrNull()?.let { it.message ?: it.toString() }
            }
            if (engine == null) {
                if (!mayStart()) return
                engine = startKcef()
                closer = stopKcef
            }
            val created: DesktopWebEngine = engine
            var waiting: CancellableContinuation<Unit>? = null
            val publish = synchronized(lock) {
                if (closed || abandoned) false else {
                    languages?.let(created::setActiveLanguages)
                    delegate = created
                    stop = closer
                    waiting = waiter.also { waiter = null }
                    true
                }
            }
            if (publish) {
                engine = null
                waiting?.resume(Unit)
            }
        } catch (t: Throwable) {
            takeWaiter()?.resumeWithException(t)
        } finally {
            engine?.let { closer(it) }
        }
    }

    override fun setActiveLanguages(source: () -> String) = synchronized(lock) {
        languages = source
        delegate?.setActiveLanguages(source)
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
        val owned: Pair<DesktopWebEngine, (DesktopWebEngine) -> Unit>?
        val waiting: CancellableContinuation<Unit>?
        synchronized(lock) {
            if (closed) return
            closed = true
            owned = delegate?.let { it to checkNotNull(stop) }
            delegate = null
            waiting = waiter.also { waiter = null }
        }
        waiting?.resumeWithException(CancellationException("Windows engine closed"))
        owned?.second?.invoke(owned.first)
    }
}

/** Resolves the loader dir, registers it with JNA for `WebView2Loader`, then starts WebView2; any failure is a failed Result. */
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
    result.onFailure { log("webview2 unavailable: ${it.message}; falling back to KCEF") }
    return result
}

internal fun windowsEngineFailureMessage(webView2Failure: String?, kcefFailure: Throwable, logPath: Path): String =
    "AndBible could not start a web engine for the Bible text.\n\n" +
        "WebView2: ${webView2Failure ?: "not attempted"}\n" +
        "KCEF: ${kcefFailure.message ?: kcefFailure}\n\n" +
        "Details are in the log file:\n$logPath"

/**
 * Initializes the engine; when both engines failed, shows [present] once (title, message) and rethrows the original
 * exception. Cancellation and close are not failures and show nothing.
 */
internal suspend fun DeferredWindowsEngine.initializeReportingFailure(
    renderer: String?,
    logPath: Path,
    title: String,
    present: (title: String, message: String) -> Unit,
) {
    try {
        initialize(renderer)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        runCatching { present(title, windowsEngineFailureMessage(webView2Failure, e, logPath)) }
        throw e
    }
}

/** Modal error dialog on the AWT event thread; skipped when headless. */
internal fun showEngineFailureDialog(title: String, message: String) {
    if (java.awt.GraphicsEnvironment.isHeadless()) return
    val show = Runnable { javax.swing.JOptionPane.showMessageDialog(null, message, title, javax.swing.JOptionPane.ERROR_MESSAGE) }
    if (javax.swing.SwingUtilities.isEventDispatchThread()) show.run() else javax.swing.SwingUtilities.invokeAndWait(show)
}

class WindowsEngineProvider : DesktopEngineProvider {
    private val kcef = KcefEngineProvider { throw IllegalStateException(it) }
    private var deferred: DeferredWindowsEngine? = null
    private var log: (String) -> Unit = {}
    private var logPath: Path = Path.of("andbible.log")
    private var errorTitle: String = "Error"
    internal var presenter: (String, String) -> Unit = ::showEngineFailureDialog
    override val windowedRendering: Boolean get() = windowsWindowedRendering(kcef.windowedRendering)

    override fun create(bundleDir: Path, paths: DesktopPaths, log: (String) -> Unit): DesktopWebEngine {
        this.log = log
        logPath = paths.data.resolve("logs").resolve("andbible.log")
        errorTitle = runCatching {
            val language = DesktopUiLanguage.resolve(DesktopDeviceSettings(paths.config.resolve(DESKTOP_DEVICE_SETTINGS_FILE)))
            iosStrings(language).errorOccurred
        }.getOrDefault("Error")
        val property = System.getProperty("andbible.windows.webEngine", "auto")
        selectWindowsEngine(property, null, false)
        return DeferredWindowsEngine(
            property = property,
            startWebView2 = {
                startWebView2WithLoader(
                    locate = { WebView2LoaderLocation.resolve(System::getProperty) },
                    addSearchPath = com.sun.jna.NativeLibrary::addSearchPath,
                    start = { WebView2Engine.start(bundleDir, paths.cache.resolve("webview2"), log, DesktopPageAssetRoutes.koinBound()) },
                    log = log,
                )
            },
            startKcef = { kcef.create(bundleDir, paths, log) },
            stopWebView2 = { (it as WebView2Engine).shutdown() },
            stopKcef = { kcef.shutdown() },
        ).also { deferred = it }
    }

    override suspend fun awaitWindowReady(window: ComposeWindow) {
        var renderer: String? = null
        for (attempt in 0 until 100) {
            renderer = window.renderApi.toString()
            if (renderer != "UNKNOWN") break
            delay(10)
        }
        val property = System.getProperty("andbible.windows.webEngine", "auto")
        val choice = selectWindowsEngine(property, renderer, true)
        log("desktop renderer: ${renderer ?: "UNKNOWN"}; engine: $choice" +
            if (property == "kcef") " (forced)" else if (choice == WindowsEngineChoice.KCEF) " (renderer is not DIRECT3D)" else "")
        val engine = checkNotNull(deferred)
        engine.initializeReportingFailure(renderer, logPath, errorTitle, presenter)
        currentCoroutineContext().ensureActive()
        engine.bindWindow(window)
    }
    override fun shutdown() { deferred?.shutdown() }
}
