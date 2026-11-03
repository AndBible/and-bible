package net.bible.desktop

import androidx.compose.ui.awt.ComposeWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.bible.desktop.webview2.WebView2Engine
import net.bible.sharedcore.platform.desktop.DesktopPaths
import net.bible.sharedui.desktop.webengine.DesktopWebEngine
import net.bible.sharedui.desktop.webengine.DesktopWebPane
import net.bible.sharedui.desktop.webview.DesktopPageAssetRoutes
import java.nio.file.Path

internal enum class WindowsEngineChoice { WEBVIEW2, KCEF }

internal fun selectWindowsEngine(property: String, renderer: String?, runtimeAvailable: Boolean): WindowsEngineChoice {
    require(property == "auto" || property == "kcef") { "Invalid andbible.windows.webEngine: $property" }
    return if (property == "auto" && renderer == "DIRECT3D" && runtimeAvailable) WindowsEngineChoice.WEBVIEW2 else WindowsEngineChoice.KCEF
}

/** Owns publication and disposal across startup racing with a window close. */
internal class DeferredWindowsEngine(
    private val property: String,
    private val startWebView2: () -> Result<DesktopWebEngine>,
    private val startKcef: () -> DesktopWebEngine,
    private val stopWebView2: (DesktopWebEngine) -> Unit,
    private val stopKcef: (DesktopWebEngine) -> Unit,
) : DesktopWebEngine {
    private val lock = Any()
    private var closed = false
    private var started = false
    private var delegate: DesktopWebEngine? = null
    private var stop: ((DesktopWebEngine) -> Unit)? = null
    private var languages: (() -> String)? = null

    suspend fun initialize(renderer: String?) {
        synchronized(lock) {
            if (closed || started) return
            started = true
        }
        val selected = selectWindowsEngine(property, renderer, true)
        withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
            var engine: DesktopWebEngine? = null
            var closer: (DesktopWebEngine) -> Unit = stopKcef
            if (selected == WindowsEngineChoice.WEBVIEW2) {
                engine = startWebView2().getOrNull()
                if (engine != null) closer = stopWebView2
            }
            if (engine == null) {
                synchronized(lock) { if (closed) return@withContext }
                engine = startKcef()
            }
            val created = engine
            val dispose = synchronized(lock) {
                if (closed) true else {
                    languages?.let(created::setActiveLanguages)
                    delegate = created
                    stop = closer
                    false
                }
            }
            if (dispose) closer(created)
        }
    }

    override fun setActiveLanguages(source: () -> String) = synchronized(lock) {
        languages = source
        delegate?.setActiveLanguages(source)
        Unit
    }
    private fun ready(): DesktopWebEngine = synchronized(lock) { checkNotNull(delegate) { "Windows engine not ready" } }
    override fun bundleUrl(lang: String, dark: Boolean) = ready().bundleUrl(lang, dark)
    override fun createPane(id: String, url: String, documentStartScripts: List<String>, onMessage: (String) -> Unit): DesktopWebPane =
        ready().createPane(id, url, documentStartScripts, onMessage)
    fun shutdown() {
        val owned = synchronized(lock) {
            if (closed) return
            closed = true
            val pair = delegate?.let { it to checkNotNull(stop) }
            delegate = null
            pair
        }
        owned?.second?.invoke(owned.first)
    }
}

class WindowsEngineProvider : DesktopEngineProvider {
    private val kcef = KcefEngineProvider()
    private var deferred: DeferredWindowsEngine? = null
    private var window: ComposeWindow? = null
    override val windowedRendering: Boolean get() = false

    override fun create(bundleDir: Path, paths: DesktopPaths, log: (String) -> Unit): DesktopWebEngine {
        val property = System.getProperty("andbible.windows.webEngine", "auto")
        selectWindowsEngine(property, null, false)
        return DeferredWindowsEngine(
            property = property,
            startWebView2 = {
                WebView2Engine.start(bundleDir, paths.cache.resolve("webview2"), log, DesktopPageAssetRoutes.koinBound())
                    .onFailure { log("webview2 unavailable: ${it.message}; falling back to KCEF") }
                    .map { it.also { engine -> engine.window = window } }
            },
            startKcef = { kcef.create(bundleDir, paths, log) },
            stopWebView2 = { (it as WebView2Engine).shutdown() },
            stopKcef = { kcef.shutdown() },
        ).also { deferred = it }
    }

    override suspend fun awaitWindowReady(window: ComposeWindow) {
        this.window = window
        var renderer: String? = null
        for (attempt in 0 until 100) {
            renderer = window.renderApi.toString()
            if (renderer != "UNKNOWN") break
            delay(10)
        }
        checkNotNull(deferred).initialize(renderer)
    }
    override fun shutdown() { deferred?.shutdown() }
}
