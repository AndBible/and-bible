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
import net.bible.sharedcore.platform.desktop.DesktopPaths
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
                engine = runCatching { startWebView2() }.getOrElse { Result.failure(it) }.getOrNull()
                if (engine != null) closer = stopWebView2
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

class WindowsEngineProvider : DesktopEngineProvider {
    private val kcef = KcefEngineProvider { throw IllegalStateException(it) }
    private var deferred: DeferredWindowsEngine? = null
    private var log: (String) -> Unit = {}
    override val windowedRendering: Boolean get() = windowsWindowedRendering(kcef.windowedRendering)

    override fun create(bundleDir: Path, paths: DesktopPaths, log: (String) -> Unit): DesktopWebEngine {
        this.log = log
        val property = System.getProperty("andbible.windows.webEngine", "auto")
        selectWindowsEngine(property, null, false)
        return DeferredWindowsEngine(
            property = property,
            startWebView2 = {
                WebView2Engine.start(bundleDir, paths.cache.resolve("webview2"), log, DesktopPageAssetRoutes.koinBound())
                    .onFailure { log("webview2 unavailable: ${it.message}; falling back to KCEF") }
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
        engine.initialize(renderer)
        currentCoroutineContext().ensureActive()
        engine.bindWindow(window)
    }
    override fun shutdown() { deferred?.shutdown() }
}
