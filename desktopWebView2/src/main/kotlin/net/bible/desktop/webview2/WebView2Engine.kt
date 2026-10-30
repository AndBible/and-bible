package net.bible.desktop.webview2

import com.sun.jna.Native
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.platform.win32.Ole32
import kotlinx.coroutines.CoroutineDispatcher
import net.bible.sharedui.desktop.webengine.DesktopPageAssets
import net.bible.sharedui.desktop.webengine.DesktopWebEngine
import net.bible.sharedui.desktop.webengine.DesktopWebPane
import net.bible.sharedui.desktop.webengine.androidBridgeShim
import net.bible.sharedui.desktop.webengine.kcef.BUNDLE_ORIGIN
import net.bible.sharedui.desktop.webengine.kcef.BundleRequestHandler
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import kotlin.coroutines.CoroutineContext

/**
 * The WebView2 engine (W2 experiment, Windows only): Microsoft Edge WebView2 through JNA over its COM API, hosted in
 * composition (visual hosting) mode so Compose draws above the page.
 *
 * - Threads: every WebView2 object lives on one [StaThread]; handlers run there, bridge messages and evaluate results are
 *   delivered on the AWT event thread.
 * - Bundle: every request at [BUNDLE_ORIGIN] is answered by `WebResourceRequested` from [BundleRequestHandler] (bundle
 *   files, then [pageAssets]); nothing at that host reaches the network. Document-start scripts are injected into the
 *   served HTML, as KCEF does.
 * - Trust: messages only from the main frame of a document at [BUNDLE_ORIGIN]; navigation elsewhere and popups are
 *   cancelled and logged.
 *
 * Create it with [start], which fails softly ([WebView2UnavailableException]) when the runtime is missing or too old or
 * no user data folder can be used, so the caller can fall back to KCEF.
 */
class WebView2Engine private constructor(
    internal val runtime: Runtime,
    private val bundleDir: Path,
    private val log: (String) -> Unit,
    private val pageAssets: DesktopPageAssets,
) : DesktopWebEngine {
    @Volatile
    private var languagesSource: (() -> String)? = null

    val version: String get() = runtime.version

    /**
     * The Compose window the panes are composed in (Compose's `LocalWindow` is internal); set by the app's window. Without
     * it the first showing frame is used.
     */
    @Volatile
    var window: java.awt.Window? = null

    private fun hostWindow(): java.awt.Window? = window ?: java.awt.Frame.getFrames().firstOrNull { it.isShowing }

    override fun setActiveLanguages(source: () -> String) {
        languagesSource = source
    }

    override fun bundleUrl(lang: String, dark: Boolean): String = "$BUNDLE_ORIGIN/index.html?lang=$lang&night=$dark"

    override fun createPane(id: String, url: String, documentStartScripts: List<String>, onMessage: (String) -> Unit): DesktopWebPane {
        val langs = languagesSource?.let { runCatching(it).getOrNull() } ?: activeLanguagesJson(url)
        val shim = androidBridgeShim(POST_MESSAGE_JS, langs)
        val handler = BundleRequestHandler(bundleDir, listOf(shim) + documentStartScripts, pageAssets)
        return WebView2Pane(id, url, runtime, Wv2ResourceRouter(handler), onMessage, { log("webview2[$id]: $it") }, ::hostWindow)
    }

    /** Stops the STA thread after releasing the environment; panes must be disposed first. */
    fun shutdown() {
        runCatching { runtime.sta.call(5_000) { runtime.environment.release() } }
        runtime.sta.close()
    }

    /** The process-wide WebView2 state shared by the panes. */
    internal class Runtime(val sta: StaThread, val environment: ComPtr, val version: String, val userDataFolder: Path)

    companion object {
        /** `window.androidBridge.postMessage` over WebView2's `chrome.webview.postMessage`. */
        const val POST_MESSAGE_JS = "function (t) { window.chrome.webview.postMessage(String(t)); }"
        private const val ENVIRONMENT_TIMEOUT_S = 60L

        /**
         * Loads `WebView2Loader.dll` (JNA search path: `jna.library.path`, the jars' directory, PATH), checks the runtime,
         * starts the STA thread and creates the environment in [userDataFolder] (or a per-process folder next to it).
         * [browserArguments] go to `WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS`.
         */
        fun start(
            bundleDir: Path,
            userDataFolder: Path,
            log: (String) -> Unit = ::println,
            pageAssets: DesktopPageAssets = DesktopPageAssets.None,
            browserArguments: String? = null,
        ): Result<WebView2Engine> {
            // The by-value struct marshalling (RECT by hidden pointer, POINT in one register) is the x64 ABI's.
            if (System.getProperty("os.arch") !in setOf("amd64", "x86_64")) {
                return Result.failure(WebView2UnavailableException("the WebView2 engine supports Windows x64 only"))
            }
            val loader = try {
                Native.load("WebView2Loader", WebView2LoaderLibrary::class.java)
            } catch (t: Throwable) {
                return Result.failure(WebView2UnavailableException("WebView2Loader.dll could not be loaded: ${t.message}", t))
            }
            val version = RuntimeCheck.check { installedVersion(loader) }.getOrElse { return Result.failure(it) }
            browserArguments?.let { Win32.setEnvironmentVariable("WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS", it) }
            val sta = StaThread("webview2-sta", Win32MessagePump())
            val result = CompletableFuture<Result<Pair<ComPtr, Path>>>()
            val fallback = userDataFolder.resolveSibling("${userDataFolder.fileName}-${ProcessHandle.current().pid()}")
            sta.post {
                EnvironmentStartup<ComPtr>(userDataFolder, fallback) { folder, done ->
                    Files.createDirectories(folder)
                    ComCallback(Wv2.Iids.CreateEnvironmentCompleted) { hr, env ->
                        val code = ComCallback.hresult(hr)
                        if (code >= 0) env?.let { ComPtr(it).addRef() }
                        done(code, if (code >= 0) env?.let(::ComPtr) else null)
                        S_OK
                    }.use { handler -> loader.CreateCoreWebView2EnvironmentWithOptions(null, WString(folder.toString()), null, handler) }
                }.start { result.complete(it) }
            }
            val created = try {
                result.get(ENVIRONMENT_TIMEOUT_S, TimeUnit.SECONDS)
            } catch (t: Throwable) {
                Result.failure(WebView2UnavailableException("WebView2 environment creation timed out", t))
            }
            val (env, folder) = created.getOrElse {
                sta.close()
                return Result.failure(it as? WebView2UnavailableException ?: WebView2UnavailableException(it.message ?: "$it", it))
            }
            if (folder != userDataFolder) log("webview2: user data folder $userDataFolder unusable, using $folder")
            return Result.success(WebView2Engine(Runtime(sta, env, version, folder), bundleDir, log, pageAssets))
        }

        private fun installedVersion(loader: WebView2LoaderLibrary): String? {
            val out = PointerByReference()
            val hr = loader.GetAvailableCoreWebView2BrowserVersionString(null, out)
            // HRESULT_FROM_WIN32(ERROR_FILE_NOT_FOUND): no runtime.
            if (hr == 0x80070002.toInt()) return null
            if (hr < 0) throw ComException("GetAvailableCoreWebView2BrowserVersionString", hr)
            val p = out.value ?: return null
            return try { p.getWideString(0) } finally { Ole32.INSTANCE.CoTaskMemFree(p) }
        }

        @Suppress("unused")
        private fun compare(loader: WebView2LoaderLibrary, a: String, b: String): Int =
            IntByReference().also { loader.CompareBrowserVersions(WString(a), WString(b), it) }.value

        /** The `lang` parameter of [url] as a one-element JSON list (KCEF's rule). */
        internal fun activeLanguagesJson(url: String): String {
            val lang = url.substringAfter('?', "").split('&').firstOrNull { it.startsWith("lang=") }?.substringAfter('=')
                ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,20}")) } ?: "en"
            return "[\"$lang\"]"
        }
    }
}

/** Runs on the AWT event thread (the pane's main thread, as for KCEF). */
internal object SwingDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) = SwingUtilities.invokeLater(block)
    override fun isDispatchNeeded(context: CoroutineContext): Boolean = !SwingUtilities.isEventDispatchThread()
}
