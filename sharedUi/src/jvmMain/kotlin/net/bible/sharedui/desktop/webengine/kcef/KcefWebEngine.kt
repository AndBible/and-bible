package net.bible.sharedui.desktop.webengine.kcef

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import dev.datlag.kcef.KCEF
import dev.datlag.kcef.KCEFBrowser
import dev.datlag.kcef.KCEFBuilder
import kotlinx.coroutines.CoroutineDispatcher
import net.bible.sharedui.desktop.webengine.DesktopPageAssets
import net.bible.sharedui.desktop.webengine.DesktopWebEngine
import net.bible.sharedui.desktop.webengine.DesktopWebPane
import net.bible.sharedui.desktop.webengine.androidBridgeShim
import org.cef.browser.CefMessageRouter
import org.cef.browser.CefRendering
import java.awt.BorderLayout
import java.awt.Component
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JPanel
import javax.swing.SwingUtilities
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
 * - Rendering: off-screen into a Compose canvas (the default) or windowed (CEF's heavyweight component in a Compose
 *   `SwingPanel`, `-Dandbible.kcef.rendering=windowed`, see [KcefRenderingMode]). No GPU switches: without a usable
 *   GPU, Chromium's GPU process fails and it falls back to software compositing by itself.
 */
class KcefWebEngine(
    private val installDir: Path,
    private val bundleDir: Path,
    private val log: (String) -> Unit = ::println,
    /** Page asset routes (module files, background images, ...) served beside the bundle; default: none. */
    private val pageAssets: DesktopPageAssets = DesktopPageAssets.None,
) : DesktopWebEngine {
    @Volatile
    private var languagesSource: (() -> String)? = null

    override fun setActiveLanguages(source: () -> String) {
        languagesSource = source
    }

    override fun bundleUrl(lang: String, dark: Boolean): String = "$BUNDLE_ORIGIN/index.html?lang=$lang&night=$dark"

    override fun createPane(id: String, url: String, documentStartScripts: List<String>, onMessage: (String) -> Unit): DesktopWebPane {
        init(installDir).getOrThrow()
        val shim = androidBridgeShim(CEF_POST_MESSAGE_JS, languagesSource?.let { runCatching(it).getOrNull() } ?: activeLanguagesJson(url))
        val mode = KcefRenderingMode.current(log)
        val osrOnX11 = mode == KcefRenderingMode.Osr && runCatching { java.awt.Toolkit.getDefaultToolkit().javaClass.name == "sun.awt.X11.XToolkit" }.getOrDefault(false)
        val scripts = PaneFocusRequest.documentStartScripts(shim, documentStartScripts, osrOnX11)
        val handler = BundleRequestHandler(bundleDir, scripts, pageAssets)
        val paneLog = { msg: String -> log("kcef[$id]: $msg") }
        return when (mode) {
            KcefRenderingMode.Osr -> KcefOsrPane(id, url, handler, onMessage, paneLog)
            KcefRenderingMode.Windowed -> KcefPane(id, url, handler, onMessage, paneLog)
        }
    }

    companion object {
        /**
         * Whether this process renders the panes windowed (the fallback), from the same one-time resolution the engine
         * uses. The app reads it before any Compose window to choose the Compose layer type.
         */
        fun isWindowedRendering(log: (String) -> Unit = ::println): Boolean =
            KcefRenderingMode.current(log) == KcefRenderingMode.Windowed

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
        fun init(installDir: Path, extraArgs: List<String> = emptyList(), cacheDir: Path? = null, log: (String) -> Unit = ::println): Result<Unit> {
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
                            windowlessRenderingEnabled = KcefRenderingMode.current(log).windowless
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

/** The X11 focus helper (reflection on the JDK's X11 internals), resolved once per process and shared by every pane. */
private val sharedX11Focus: X11PaneFocus by lazy { X11PaneFocus.create { println("kcef: $it") } }

/** Windowed pane: CEF's heavyweight component hosted in a Compose `SwingPanel`. Everything shared is in [KcefPaneCore]. */
internal class KcefPane(
    override val id: String,
    url: String,
    bundle: BundleRequestHandler,
    onMessage: (String) -> Unit,
    private val log: (String) -> Unit,
) : DesktopWebPane {
    private val core: KcefPaneCore = KcefPaneCore(url, bundle, onMessage, log, requestFocus = {
        core.browser.setFocus(true)
        sharedX11Focus.focus(core.browser.uiComponent)
    })
    private val browser: KCEFBrowser = core.createBrowser(CefRendering.DEFAULT)

    /** The AWT host of the browser; created once, re-parented by every [Content]. */
    val component: Component by lazy { JPanel(BorderLayout()).apply { add(browser.uiComponent, BorderLayout.CENTER) } }

    @Composable
    override fun Content(modifier: Modifier) {
        SwingPanel(factory = { component }, modifier = modifier)
    }

    override fun setBackground(argb: Int) = core.setBackground(argb) { component.background = java.awt.Color(argb, true) }

    override fun setOnRendererGone(listener: () -> Unit) = core.setOnRendererGone(listener)

    override val isMainThread: Boolean get() = SwingUtilities.isEventDispatchThread()
    override val isAttached: Boolean get() = !core.isDisposed
    override val mainDispatcher: CoroutineDispatcher = SwingMainDispatcher

    override fun post(block: () -> Unit) = SwingUtilities.invokeLater { if (!core.isDisposed) block() }

    override fun evaluate(js: String, callback: ((String) -> Unit)?) = core.evaluate(js, callback)

    override fun dispose() = core.dispose()
}
