package net.bible.sharedui.desktop.webengine.kcef

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import dev.datlag.kcef.KCEF
import dev.datlag.kcef.KCEFBrowser
import dev.datlag.kcef.KCEFBuilder
import dev.datlag.kcef.KCEFClient
import kotlinx.coroutines.CoroutineDispatcher
import net.bible.sharedui.desktop.webengine.DesktopWebEngine
import net.bible.sharedui.desktop.webengine.DesktopWebPane
import net.bible.sharedui.desktop.webengine.androidBridgeShim
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.browser.CefRendering
import org.cef.callback.CefCallback
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefMessageRouterHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceHandler
import org.cef.handler.CefResourceHandlerAdapter
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefRequest
import org.cef.network.CefResponse
import java.awt.BorderLayout
import java.awt.Component
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

/**
 * [DesktopWebEngine] on embedded Chromium through KCEF (JetBrains JCEF).
 *
 * - Natives: [init] uses the CEF natives already placed in `installDir` at build time
 *   (`:desktopApp:prepareKcefNatives`); it never downloads them and fails, naming the directory, when they are absent.
 * - Bundle: served from [bundleDir] at [BUNDLE_ORIGIN] by [BundleRequestHandler] (no `file://`).
 * - Bridge: the shim posts through a `CefMessageRouter` (`window.cefQuery`); messages reach `onMessage` on the AWT
 *   event thread. The shim and the pane's document-start scripts are injected into the served HTML document
 *   (see [BundleRequestHandler]), registered before the browser starts loading.
 * - Trust: bridge messages (and eval replies) are accepted only from the main frame of a document at [BUNDLE_ORIGIN];
 *   the pane never navigates away from that origin and opens no popups. Blocked navigations are only logged: opening
 *   external links in the system browser belongs to a later desktop stage (D3b).
 * - Evaluate: every callback is answered exactly once, with `"null"` when no reply can come (pane disposed, main frame
 *   loading a new document, or [EVAL_TIMEOUT_MS] elapsed).
 * - Rendering: windowed (CEF's heavyweight component in a Compose `SwingPanel`). No GPU switches: without a usable
 *   GPU, Chromium's GPU process fails and it falls back to software compositing by itself.
 */
class KcefWebEngine(
    private val installDir: Path,
    private val bundleDir: Path,
    private val log: (String) -> Unit = ::println,
) : DesktopWebEngine {
    @Volatile
    private var languagesSource: (() -> String)? = null

    override fun setActiveLanguages(source: () -> String) {
        languagesSource = source
    }

    override fun bundleUrl(lang: String, dark: Boolean): String = "$BUNDLE_ORIGIN/index.html?lang=$lang&night=$dark"

    override fun createPane(id: String, url: String, documentStartScripts: List<String>, onMessage: (String) -> Unit): DesktopWebPane {
        init(installDir).getOrThrow()
        val scripts = listOf(androidBridgeShim(CEF_POST_MESSAGE_JS, languagesSource?.let { runCatching(it).getOrNull() } ?: activeLanguagesJson(url))) + documentStartScripts
        return KcefPane(id, url, BundleRequestHandler(bundleDir, scripts), onMessage) { msg -> log("kcef[$id]: $msg") }
    }

    companion object {
        /** JS function that sends its argument to the native side through the pane's message router. */
        internal const val CEF_POST_MESSAGE_JS =
            "function (t) { window.cefQuery({request: String(t), persistent: false, onSuccess: function () {}, onFailure: function () {}}); }"

        /** How long an evaluate callback waits for the page's reply before it gets `"null"`. */
        const val EVAL_TIMEOUT_MS = 10_000

        private const val INIT_TIMEOUT_S = 30L

        private var initialized = false

        /** Set once init has reached KCEF and failed: CEF cannot be re-initialised in the same process. */
        private var terminalFailure: Throwable? = null

        /**
         * Initialises CEF once per process from the natives in [installDir]. Idempotent once it has succeeded.
         * Fails (without any network access) when [installDir] lacks the natives; [extraArgs] are Chromium switches.
         *
         * Once CEF itself has been started and failed (an error, or no "initialised" within [INIT_TIMEOUT_S] s, after
         * which it may still be half-started), every later call returns that same failure: the app must restart.
         * A missing-natives failure happens before CEF is touched and can be retried.
         *
         * Sandbox: off by default. Chromium's sandbox needs a setuid `chrome-sandbox` or unprivileged user
         * namespaces, which containers and AppArmor-restricted distributions (Ubuntu 24.04+) refuse, and then
         * Chromium aborts; JBR/IntelliJ run JCEF unsandboxed too. The pane only ever loads the bundled origin
         * (navigation and popups are locked). `-Dkcef.sandbox=true` turns it on where the host supports it.
         *
         * [cacheDir], when given, is the CEF cache directory, e.g. `<cache>/cef` of the desktop app;
         * null keeps the KCEF default.
         */
        @Synchronized
        fun init(installDir: Path, extraArgs: List<String> = emptyList(), cacheDir: Path? = null): Result<Unit> {
            if (initialized) return Result.success(Unit)
            terminalFailure?.let { return Result.failure(it) }
            nativesProblem(installDir)?.let { return Result.failure(IllegalStateException(it)) }
            var error: Throwable? = null
            var restartRequired = false
            val initialised = CountDownLatch(1)
            val dir = installDir.toAbsolutePath()
            try {
                KCEF.initBlocking(
                    builder = {
                        installDir(dir.toFile())
                        progress { onInitialized { initialised.countDown() } }
                        // KCEF's own args() never reach Chromium (its default app handler is built with none): the
                        // switches must go through the app handler.
                        appHandler(KCEF.AppHandler(extraArgs.toTypedArray()))
                        settings {
                            // Explicit, because KCEF seeds these from JCefAppConfig, which (outside the JetBrains
                            // Runtime) points them at the running JDK's java.home/lib; KCEF fills only empty ones.
                            browserSubProcessPath = dir.resolve("jcef_helper").toString()
                            resourcesDirPath = dir.toString()
                            localesDirPath = dir.resolve("locales").toString()
                            noSandbox = System.getProperty("kcef.sandbox") != "true"
                            // Diagnosis: -Dkcef.logFile=<path> writes CEF's own log (Info and up) there.
                            val logPath = System.getProperty("kcef.logFile")
                            logSeverity = if (logPath == null) KCEFBuilder.Settings.LogSeverity.Disable else KCEFBuilder.Settings.LogSeverity.Info
                            if (logPath != null) logFile = logPath
                            windowlessRenderingEnabled = false
                            if (cacheDir != null) cachePath = cacheDir.toAbsolutePath().toString()
                        }
                    },
                    onError = { error = it ?: IllegalStateException("unknown KCEF error") },
                    onRestartRequired = { restartRequired = true },
                )
                if (error == null && !restartRequired && !initialised.await(INIT_TIMEOUT_S, TimeUnit.SECONDS)) {
                    error = IllegalStateException("CEF did not finish initialising within $INIT_TIMEOUT_S s")
                }
            } catch (t: Throwable) {
                error = t
            }
            val failure = error ?: if (restartRequired) IllegalStateException("KCEF asked for a restart") else null
            if (failure != null) {
                return Result.failure(
                    IllegalStateException("CEF failed to initialise from the natives in $installDir: $failure", failure)
                        .also { terminalFailure = it },
                )
            }
            initialized = true
            return Result.success(Unit)
        }

        /** Shuts CEF down for good (application exit); no pane may be created afterwards. */
        @Synchronized
        fun shutdown() {
            if (initialized) runCatching { KCEF.disposeBlocking() }
            markShutDown()
        }

        /** Makes the engine terminal: a later [init] (and so every pane creation) fails clearly instead of blocking. */
        private fun markShutDown() {
            initialized = false
            terminalFailure = IllegalStateException("The KCEF engine was shut down; CEF cannot be restarted in this process")
        }

        /** Test seam: [shutdown]'s state change without touching CEF. */
        @Synchronized
        internal fun markShutDownForTest() = markShutDown()

        /** Test seam: forgets every terminal state so unit tests do not leak into each other. */
        @Synchronized
        internal fun resetStateForTest() {
            initialized = false
            terminalFailure = null
        }

        /** Why [dir] cannot serve as the CEF install dir, or null when it holds the natives. */
        internal fun nativesProblem(dir: Path): String? {
            val hint = "build them with ./gradlew :desktopApp:prepareKcefNatives (CEF natives are never downloaded at run time)"
            if (!Files.isDirectory(dir)) return "CEF natives directory $dir does not exist; $hint"
            if (!Files.isRegularFile(dir.resolve("install.lock"))) return "CEF natives directory $dir has no install.lock; $hint"
            val libraries = listOf("libcef.so", "libcef.dll", "Frameworks")
            if (libraries.none { Files.exists(dir.resolve(it)) }) return "CEF natives directory $dir has no CEF library (${libraries.joinToString()}); $hint"
            return null
        }

        /** `["<lang>"]` from the `lang` query parameter of [url] (`en` when absent or not a plain language tag). */
        internal fun activeLanguagesJson(url: String): String {
            val lang = url.substringAfter('?', "").split('&')
                .firstOrNull { it.startsWith("lang=") }?.substringAfter('=')
                ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,20}")) } ?: "en"
            return "[\"$lang\"]"
        }
    }
}

/** The AWT event thread, the desktop UI thread. */
internal object SwingMainDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) = SwingUtilities.invokeLater(block)
    override fun isDispatchNeeded(context: CoroutineContext): Boolean = !SwingUtilities.isEventDispatchThread()
}

internal class KcefPane(
    override val id: String,
    private val url: String,
    private val bundle: BundleRequestHandler,
    private val onMessage: (String) -> Unit,
    private val log: (String) -> Unit,
) : DesktopWebPane {
    @Volatile
    private var disposed = false

    @Volatile
    private var backgroundArgb: Int? = null

    @Volatile
    private var onRendererGone: (() -> Unit)? = null
    private val evals = EvalReplies()
    private val client: KCEFClient = KCEF.newClientBlocking()

    private val router: CefMessageRouter = CefMessageRouter.create(
        CefMessageRouter.CefMessageRouterConfig("cefQuery", "cefQueryCancel"),
        object : CefMessageRouterHandlerAdapter() {
            override fun onQuery(
                browser: CefBrowser?, frame: CefFrame?, queryId: Long, request: String?, persistent: Boolean,
                callback: CefQueryCallback?,
            ): Boolean {
                if (frame?.isMain != true || !isAtBundleOrigin(frame.url)) {
                    // Not our document (a subframe or a foreign page): no bridge access, no forged eval replies.
                    callback?.failure(403, "refused")
                    log("refused a bridge message from ${frame?.url}")
                    return true
                }
                callback?.success("")
                val text = request ?: return true
                SwingUtilities.invokeLater { if (!disposed && !evals.consume(text)) onMessage(text) }
                return true
            }
        },
    )

    private val browser: KCEFBrowser

    init {
        // Everything the page needs (router, bundle + document-start scripts) is registered before loading starts.
        client.addMessageRouter(router)
        client.addRequestHandler(BundleCefRequestHandler(bundle, log) { status ->
            log("renderer process gone: $status")
            SwingUtilities.invokeLater { if (!disposed) onRendererGone?.invoke() }
        })
        client.addContextMenuHandler(SuppressChromiumMenuHandler())
        client.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadStart(browser: CefBrowser?, frame: CefFrame?, transitionType: CefRequest.TransitionType?) {
                // A new main document cannot answer the old document's evaluations.
                if (frame?.isMain == true) SwingUtilities.invokeLater { evals.failAll() }
            }

            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                // The page's own style is gone after a (re)load: paint it again.
                if (frame?.isMain == true) backgroundArgb?.let { applyPageBackground(it) }
            }
        })
        client.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
            override fun onBeforePopup(browser: CefBrowser?, frame: CefFrame?, targetUrl: String?, targetFrameName: String?): Boolean {
                log("blocked a popup to $targetUrl")
                return true
            }
        })
        browser = client.createBrowser(url, CefRendering.DEFAULT, false)
    }

    /** The AWT host of the browser; created once, re-parented by every [Content]. */
    val component: Component by lazy {
        val ui = browser.uiComponent
        // Native mouse events of the CEF window are forwarded to the component's listeners (CefBrowserWr).
        ui.addMouseListener(PaneFocusOnPress({ ui.isFocusOwner }, { ui.requestFocusInWindow() }))
        JPanel(BorderLayout()).apply { add(ui, BorderLayout.CENTER) }
    }

    @Composable
    override fun Content(modifier: Modifier) {
        SwingPanel(factory = { component }, modifier = modifier)
    }

    override fun setBackground(argb: Int) {
        backgroundArgb = argb
        SwingUtilities.invokeLater {
            if (disposed) return@invokeLater
            component.background = java.awt.Color(argb, true)
            applyPageBackground(argb)
        }
    }

    override fun setOnRendererGone(listener: () -> Unit) {
        onRendererGone = listener
    }

    /** BibleView-JS paints no page background of its own, so the root element gets the host colour (as `setBackgroundColor` on Android). */
    private fun applyPageBackground(argb: Int) {
        if (disposed) return
        val css = "rgba(${(argb shr 16) and 0xFF},${(argb shr 8) and 0xFF},${argb and 0xFF},${((argb shr 24) and 0xFF) / 255.0})"
        SwingUtilities.invokeLater {
            if (!disposed) browser.executeJavaScript("document.documentElement.style.background='$css';", browser.url ?: url, 0)
        }
    }

    override val isMainThread: Boolean get() = SwingUtilities.isEventDispatchThread()
    override val isAttached: Boolean get() = !disposed
    override val mainDispatcher: CoroutineDispatcher = SwingMainDispatcher

    override fun post(block: () -> Unit) = SwingUtilities.invokeLater { if (!disposed) block() }

    override fun evaluate(js: String, callback: ((String) -> Unit)?) {
        if (disposed) {
            callback?.invoke(EvalReplies.NO_RESULT)
            return
        }
        if (!isMainThread) {
            // Not through post(): that drops the block after dispose, and the callback must still be answered.
            SwingUtilities.invokeLater { evaluate(js, callback) }
            return
        }
        if (callback == null) {
            browser.executeJavaScript(js, browser.url ?: url, 0)
            return
        }
        val wrapped = evals.wrap(js, KcefWebEngine.CEF_POST_MESSAGE_JS, callback)
        Timer(KcefWebEngine.EVAL_TIMEOUT_MS) { evals.expire(wrapped.id) }.apply { isRepeats = false }.start()
        browser.executeJavaScript(wrapped.script, browser.url ?: url, 0)
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        // dispose() runs on the UI thread (DesktopBibleWebViews' owner), where the eval registry lives.
        if (isMainThread) evals.failAll() else SwingUtilities.invokeLater { evals.failAll() }
        runCatching { browser.close(true) }
        runCatching { client.removeMessageRouter(router) }
        runCatching { router.dispose() }
        runCatching { client.dispose() }
    }
}

/**
 * Serves [BUNDLE_ORIGIN] from the bundle (a refused or missing path gets a 404, never the network) and keeps the pane
 * on that origin: any navigation elsewhere is cancelled and logged.
 */
private class BundleCefRequestHandler(
    private val bundle: BundleRequestHandler,
    private val log: (String) -> Unit,
    private val onRenderProcessGone: (org.cef.handler.CefRequestHandler.TerminationStatus) -> Unit = {},
) : CefRequestHandlerAdapter() {
    override fun onRenderProcessTerminated(browser: CefBrowser?, status: org.cef.handler.CefRequestHandler.TerminationStatus?) {
        if (status != null) onRenderProcessGone(status)
    }

    override fun onBeforeBrowse(
        browser: CefBrowser?, frame: CefFrame?, request: CefRequest?, userGesture: Boolean, isRedirect: Boolean,
    ): Boolean {
        val target = request?.url
        if (isAtBundleOrigin(target)) return false
        log("blocked navigation to $target")
        return true
    }

    override fun getResourceRequestHandler(
        browser: CefBrowser?, frame: CefFrame?, request: CefRequest?, isNavigation: Boolean, isDownload: Boolean,
        requestInitiator: String?, disableDefaultHandling: BoolRef?,
    ): CefResourceRequestHandler? {
        val url = request?.url ?: return null
        if (!bundle.isBundleUrl(url)) return null
        return object : CefResourceRequestHandlerAdapter() {
            override fun getResourceHandler(browser: CefBrowser?, frame: CefFrame?, request: CefRequest?): CefResourceHandler {
                val resource = bundle.resolve(url)
                return if (resource == null) {
                    BytesResourceHandler(404, "text/plain", "text/plain; charset=utf-8", "Not found".encodeToByteArray())
                } else {
                    BytesResourceHandler(200, resource.mimeType, resource.contentType, bundle.body(resource))
                }
            }
        }
    }
}

private class BytesResourceHandler(
    private val status: Int,
    private val mimeType: String,
    private val contentType: String,
    private val bytes: ByteArray,
) :
    CefResourceHandlerAdapter() {
    private var offset = 0

    override fun processRequest(request: CefRequest?, callback: CefCallback?): Boolean {
        callback?.Continue()
        return true
    }

    override fun getResponseHeaders(response: CefResponse?, responseLength: IntRef?, redirectUrl: StringRef?) {
        response?.apply {
            status = this@BytesResourceHandler.status
            statusText = if (this@BytesResourceHandler.status == 200) "OK" else "Not Found"
            mimeType = this@BytesResourceHandler.mimeType
            setHeaderByName("Content-Type", contentType, true)
            setHeaderByName("Cache-Control", "no-store", true)
        }
        responseLength?.set(bytes.size)
    }

    override fun readResponse(dataOut: ByteArray?, bytesToRead: Int, bytesRead: IntRef?, callback: CefCallback?): Boolean {
        if (dataOut == null || offset >= bytes.size) {
            bytesRead?.set(0)
            return false
        }
        val n = minOf(bytesToRead, bytes.size - offset, dataOut.size)
        System.arraycopy(bytes, offset, dataOut, 0, n)
        offset += n
        bytesRead?.set(n)
        return true
    }
}
