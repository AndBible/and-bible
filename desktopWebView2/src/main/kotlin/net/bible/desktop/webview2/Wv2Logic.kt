package net.bible.desktop.webview2

import net.bible.sharedui.desktop.webengine.kcef.BundleRequestHandler
import net.bible.sharedui.desktop.webengine.kcef.isAtBundleOrigin
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong

/** Why WebView2 cannot be used; the app then falls back to KCEF. */
class WebView2UnavailableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Asks the installed WebView2 runtime for its version (`GetAvailableCoreWebView2BrowserVersionString`). */
fun interface RuntimeProbe {
    /** The runtime's version, null when none is installed; throws when the loader itself cannot be loaded. */
    fun installedVersion(): String?
}

/**
 * Decides whether the WebView2 runtime is usable: installed (Windows 10 LTSC and offline machines may have none) and at
 * least [MIN_MAJOR] (the composition controller, virtual hosts and `ICoreWebView2_11` are all older than that).
 */
object RuntimeCheck {
    const val MIN_MAJOR = 110

    fun check(probe: RuntimeProbe): Result<String> {
        val version = try {
            probe.installedVersion()
        } catch (t: Throwable) {
            return Result.failure(WebView2UnavailableException("the WebView2 loader could not be loaded: ${t.message ?: t}", t))
        } ?: return Result.failure(WebView2UnavailableException("no WebView2 runtime is installed"))
        val major = version.substringBefore('.').trim().toIntOrNull()
            ?: return Result.failure(WebView2UnavailableException("unreadable WebView2 runtime version '$version'"))
        if (major < MIN_MAJOR) return Result.failure(WebView2UnavailableException("WebView2 runtime $version is older than $MIN_MAJOR"))
        return Result.success(version)
    }
}

/**
 * Creates the WebView2 environment in a user data folder, failing softly: the folder may be held by another AndBible
 * process or by the browser processes of a crashed one (WebView2 refuses a folder in use with other options, and a stale
 * lock fails creation). Then a per-process folder next to it is tried once; if that fails too, the result is a
 * [WebView2UnavailableException] naming both HRESULTs, never a crash.
 *
 * [create] starts one creation in a folder and calls back once with the HRESULT and the environment (null on failure);
 * it may also fail synchronously by returning a failed HRESULT.
 */
class EnvironmentStartup<E : Any>(
    private val primaryFolder: Path,
    private val fallbackFolder: Path,
    private val create: (folder: Path, done: (hr: Int, env: E?) -> Unit) -> Int,
) {
    /** Starts creation; [done] gets the environment and the folder used, or the failure. Calls back exactly once. */
    fun start(done: (Result<Pair<E, Path>>) -> Unit) {
        attempt(primaryFolder) { first ->
            if (first.isSuccess) {
                done(first)
                return@attempt
            }
            attempt(fallbackFolder) { second ->
                done(
                    if (second.isSuccess) second else Result.failure(
                        WebView2UnavailableException(
                            "WebView2 environment could not be created in $primaryFolder (${first.exceptionOrNull()?.message}) " +
                                "or $fallbackFolder (${second.exceptionOrNull()?.message})",
                        ),
                    ),
                )
            }
        }
    }

    private fun attempt(folder: Path, done: (Result<Pair<E, Path>>) -> Unit) {
        var answered = false
        val once = { r: Result<Pair<E, Path>> -> if (!answered) { answered = true; done(r) } }
        val hr = try {
            create(folder) { hr, env ->
                once(if (hr >= 0 && env != null) Result.success(env to folder) else Result.failure(IllegalStateException(hresultString(hr))))
            }
        } catch (t: Throwable) {
            once(Result.failure(t))
            return
        }
        if (hr < 0) once(Result.failure(IllegalStateException(hresultString(hr))))
    }
}

/**
 * The pane's trust rules, as KCEF's: bridge messages are accepted only from a document at the bundle origin (WebView2's
 * `WebMessageReceived` on the `ICoreWebView2` itself only fires for the main frame), and the pane never navigates away from
 * that origin.
 */
object Wv2Trust {
    fun acceptMessage(source: String?): Boolean = isAtBundleOrigin(source)

    fun allowNavigation(uri: String?): Boolean = isAtBundleOrigin(uri)
}

/** One response to a `WebResourceRequested` event. */
class Wv2Response(val status: Int, val reason: String, val headers: String, val body: ByteArray)

/**
 * Answers every request at the bundle origin (`https://bibleview.andbible.local/` and below) from [handler]: bundle files first, then
 * the page assets ([net.bible.sharedui.desktop.webengine.DesktopPageAssets]) behind it; anything else at that origin gets a
 * 404 and never reaches the network. Requests to other origins are not ours (null).
 */
class Wv2ResourceRouter(private val handler: BundleRequestHandler) {
    fun respond(uri: String): Wv2Response? {
        if (!handler.isBundleUrl(uri)) return null
        val resource = handler.resolve(uri) ?: return Wv2Response(404, "Not Found", "Content-Type: text/plain", ByteArray(0))
        val body = try {
            handler.body(resource)
        } catch (_: Exception) {
            return Wv2Response(404, "Not Found", "Content-Type: text/plain", ByteArray(0))
        }
        return Wv2Response(200, "OK", "Content-Type: ${resource.contentType}\r\nCache-Control: no-store", body)
    }

    companion object {
        /** The filter registered with `AddWebResourceRequestedFilter`. */
        const val FILTER = "https://bibleview.andbible.local/*"
    }
}

/**
 * Evaluate-callback bookkeeping (the KCEF contract): every callback is answered exactly once, with the result JSON or
 * [NO_RESULT] for a failed script, a timeout, a new document or dispose. Callbacks run through [deliver], never under the lock.
 */
class EvalReplies(
    private val deliver: (Runnable) -> Unit,
    private val scheduleTimeout: (delayMs: Long, task: Runnable) -> Unit,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    private val lock = Any()
    private val pending = HashMap<Long, (String) -> Unit>()
    private val nextId = AtomicLong(1)

    val pendingCount: Int get() = synchronized(lock) { pending.size }

    fun register(callback: (String) -> Unit): Long {
        val id = nextId.getAndIncrement()
        synchronized(lock) { pending[id] = callback }
        scheduleTimeout(timeoutMs, Runnable { answer(id, NO_RESULT) })
        return id
    }

    /** `ExecuteScript` completed: a failed [hr] or a null result gives [NO_RESULT]. */
    fun complete(id: Long, hr: Int, json: String?) = answer(id, if (hr < 0 || json == null) NO_RESULT else json)

    fun failAll() {
        val all = synchronized(lock) { pending.values.toList().also { pending.clear() } }
        all.forEach { dispatch(it, NO_RESULT) }
    }

    private fun answer(id: Long, json: String) {
        val callback = synchronized(lock) { pending.remove(id) } ?: return
        dispatch(callback, json)
    }

    private fun dispatch(callback: (String) -> Unit, json: String) {
        deliver(Runnable {
            try {
                callback(json)
            } catch (t: Throwable) {
                System.err.println("webview2: eval callback failed: $t")
            }
        })
    }

    companion object {
        const val NO_RESULT = "null"
        const val DEFAULT_TIMEOUT_MS = 10_000L
    }
}
