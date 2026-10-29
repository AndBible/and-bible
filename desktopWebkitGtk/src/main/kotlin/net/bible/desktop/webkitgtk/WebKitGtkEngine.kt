package net.bible.desktop.webkitgtk

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import kotlinx.coroutines.CoroutineDispatcher
import net.bible.sharedui.desktop.webengine.DesktopWebEngine
import net.bible.sharedui.desktop.webengine.DesktopWebPane
import net.bible.sharedui.desktop.webengine.androidBridgeShim
import java.awt.BorderLayout
import java.awt.Canvas
import java.awt.Component
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.coroutines.CoroutineContext

/**
 * [DesktopWebEngine] on WebKitGTK 6.0 (GTK 4) through JNA. D5 experiment, not shipped.
 *
 * - Threads: one [GtkThread] (a `GMainLoop`) per engine, started lazily by the first [createPane]. Every GTK, WebKit and
 *   X11 call runs inside `gtk.call`/`gtk.post`; the GTK thread never waits for the AWT event thread (that would deadlock
 *   against [GtkHostCanvas.removeNotify]'s synchronous `gtk.call`).
 * - AWT must stay off GTK: the JVM must not load GTK 3 (the AWT GTK look-and-feel, `FileDialog`, the JDK's GTK
 *   file chooser) into the process that loads GTK 4, because their symbols clash. Use the default (Metal) look and feel
 *   and Compose's own dialogs only.
 * - Bundle: served from [bundleDir] on the custom scheme [BUNDLE_SCHEME] ([BUNDLE_BASE]) by [BundleSchemeHandler]; the
 *   scheme is registered secure and CORS-enabled once per process. A refused or missing path gets a 404 error, never the
 *   network. The pane does not (yet) block navigations away from the bundle, and a script message cannot be attributed
 *   to a frame in WebKitGTK 6.0: unlike the KCEF engine there is no per-message origin check (D5 gap, see the report).
 * - Bridge: the shim posts through a `bridge` script message handler (`window.webkit.messageHandlers.bridge`); messages
 *   reach `onMessage` on the AWT event thread. The shim and the document-start scripts are user scripts registered at
 *   document start BEFORE `webkit_web_view_load_uri`.
 * - Embedding (approach a, X11 reparenting): the web view lives in its own undecorated GTK window, whose X window is
 *   reparented into the X window of a heavyweight AWT [Canvas] shown through a Compose `SwingPanel`. HiDPI is
 *   approximate: sizes are multiplied by the canvas's default transform scale.
 * - Night mode: the web view background is set from the `night` query parameter of the URL (black or white), which
 *   avoids D1's white flash in the dark theme.
 */
class WebKitGtkEngine(
    private val bundleDir: Path,
    private val log: (String) -> Unit = ::println,
) : DesktopWebEngine, AutoCloseable {
    private val lock = Any()
    private var gtkThread: GtkThread? = null
    private var closed = false

    private fun gtk(): GtkThread = synchronized(lock) {
        check(!closed) { "The WebKitGTK engine is closed" }
        gtkThread ?: GtkThread(GtkMainLoopDriver()).also { gtkThread = it }
    }

    override fun bundleUrl(lang: String, dark: Boolean): String = "$BUNDLE_BASE/index.html?lang=$lang&night=$dark"

    override fun createPane(id: String, url: String, documentStartScripts: List<String>, onMessage: (String) -> Unit): DesktopWebPane {
        val thread = gtk()
        val handler = BundleSchemeHandler(bundleDir)
        thread.call { BundleScheme.install(handler, log) }
        val scripts = listOf(androidBridgeShim(WEBKIT_POST_MESSAGE_JS, activeLanguagesJson(url))) + documentStartScripts
        return WebKitGtkPane(id, url, thread, scripts, onMessage) { msg -> log("webkitgtk[$id]: $msg") }
    }

    /** Stops the GTK thread. Panes must have been disposed first. Idempotent. */
    override fun close() {
        val thread = synchronized(lock) {
            closed = true
            gtkThread.also { gtkThread = null }
        }
        thread?.close()
    }

    /**
     * Ends the process from the GTK thread with libc `exit(status)` and never returns. WebKit registers C exit handlers
     * that tear down its statics and abort when they do not run on WebKit's main thread (the GTK thread). A normal JVM
     * exit runs them on the JVM's VM thread: every window close ended in SIGABRT (exit 134). Calling `exit` on the GTK
     * thread runs them there; JVM shutdown hooks do not run (the caller must have released everything already).
     */
    fun exitProcess(status: Int): Nothing {
        val thread = synchronized(lock) { gtkThread }
        if (thread != null) {
            thread.post { LibC.exit(status) }
            while (true) Thread.sleep(1_000)
        }
        Runtime.getRuntime().halt(status)
        error("unreachable")
    }

    companion object {
        /** JS function that sends its argument to the native side through the `bridge` script message handler. */
        internal const val WEBKIT_POST_MESSAGE_JS = "function (t) { window.webkit.messageHandlers.bridge.postMessage(String(t)); }"

        /** How long an evaluate callback waits for the page's reply before it gets `"null"`. */
        const val EVAL_TIMEOUT_MS = 10_000L

        /** `["<lang>"]` from the `lang` query parameter of [url] (`en` when absent or not a plain language tag). */
        internal fun activeLanguagesJson(url: String): String {
            val lang = url.substringAfter('?', "").split('&')
                .firstOrNull { it.startsWith("lang=") }?.substringAfter('=')
                ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,20}")) } ?: "en"
            return "[\"$lang\"]"
        }

        /** True when the `night` query parameter of [url] is `true`. */
        internal fun isNight(url: String): Boolean =
            url.substringAfter('?', "").split('&').any { it == "night=true" }
    }
}

/**
 * The process-wide `andbible://` scheme registration. A second registration of a scheme on the same context is a GLib
 * critical, so it happens once and dispatches to the handler of the most recent engine.
 */
private const val GTK_PHASE_CAPTURE = 1
private const val X_REVERT_TO_PARENT = 2

internal object BundleScheme {
    private val handler = AtomicReference<BundleSchemeHandler?>()
    private var registered = false
    private var log: (String) -> Unit = {}

    /** Must stay referenced for the process lifetime. */
    private val callback = object : SchemeRequestCallback {
        override fun invoke(request: Pointer?, userData: Pointer?) {
            if (request != null) serve(request)
        }
    }

    /**
     * The engine's own web context (GTK thread), not `webkit_web_context_get_default()`: the default one is a static
     * that WebKit tears down from the C exit handlers, which the JVM runs on its VM thread, and WebKit aborts there
     * (not its main thread). Created on first [install].
     */
    var context: Pointer? = null
        private set

    /** GTK thread only. */
    fun install(bundle: BundleSchemeHandler, logger: (String) -> Unit) {
        handler.set(bundle)
        log = logger
        if (registered) return
        val wk = Gtk.webkit
        val ctx = wk.webkit_web_context_new().also { context = it }
        wk.webkit_web_context_register_uri_scheme(ctx, BUNDLE_SCHEME, callback, null, null)
        val security = wk.webkit_web_context_get_security_manager(ctx)
        wk.webkit_security_manager_register_uri_scheme_as_secure(security, BUNDLE_SCHEME)
        wk.webkit_security_manager_register_uri_scheme_as_cors_enabled(security, BUNDLE_SCHEME)
        registered = true
    }

    private fun serve(request: Pointer) {
        val wk = Gtk.webkit
        try {
            val uri = wk.webkit_uri_scheme_request_get_uri(request)
            val resource = handler.get()?.resolve(uri)
            if (resource == null) {
                log("bundle: 404 for $uri")
                notFound(request)
                return
            }
            val bytes = Files.readAllBytes(resource.first)
            // GLib owns this copy and frees it with g_free when the stream is released.
            val mem = Gtk.glib.g_malloc(maxOf(bytes.size, 1).toLong())
            mem.write(0, bytes, 0, bytes.size)
            val stream = Gtk.gio.g_memory_input_stream_new_from_data(mem, bytes.size.toLong(), Gtk.gFreeNotify)
            val response = wk.webkit_uri_scheme_response_new(stream, bytes.size.toLong())
            wk.webkit_uri_scheme_response_set_content_type(response, resource.second)
            wk.webkit_uri_scheme_request_finish_with_response(request, response)
            Gtk.gobject.g_object_unref(response)
            Gtk.gobject.g_object_unref(stream)
        } catch (t: Throwable) {
            log("bundle: request failed: $t")
            runCatching { notFound(request) }
        }
    }

    private fun notFound(request: Pointer) {
        val err = Gtk.glib.g_error_new_literal(Gtk.glib.g_quark_from_string("andbible-bundle"), 404, "Not found")
        Gtk.webkit.webkit_uri_scheme_request_finish_error(request, err)
        Gtk.glib.g_error_free(err)
    }
}

/** The AWT event thread, the desktop UI thread. */
private object SwingMainDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) = SwingUtilities.invokeLater(block)
    override fun isDispatchNeeded(context: CoroutineContext): Boolean = !SwingUtilities.isEventDispatchThread()
}

/**
 * The heavyweight AWT host of a web view window: it provides the X window the GTK window is reparented into.
 * [addNotify]/[removeNotify] report the X id (valid only while the canvas is displayable).
 */
private class GtkHostCanvas(
    private val onAttach: (xid: Long) -> Unit,
    private val onDetach: () -> Unit,
) : Canvas() {
    override fun addNotify() {
        super.addNotify()
        onAttach(Native.getComponentID(this))
    }

    override fun removeNotify() {
        // Before super: that destroys the canvas X window and with it every child window, including the GTK one.
        onDetach()
        super.removeNotify()
    }
}

internal class WebKitGtkPane(
    override val id: String,
    private val url: String,
    private val gtk: GtkThread,
    scripts: List<String>,
    private val onMessage: (String) -> Unit,
    private val log: (String) -> Unit,
) : DesktopWebPane {
    @Volatile
    private var disposed = false
    private val evals = EvalBridge(
        deliver = { SwingUtilities.invokeLater(it) },
        scheduleTimeout = { ms, task -> Timer(ms.toInt()) { task.run() }.apply { isRepeats = false }.start() },
        timeoutMs = WebKitGtkEngine.EVAL_TIMEOUT_MS,
    )

    // GTK-thread state.
    private var window: Pointer? = null
    private var view: Pointer? = null
    private var display: Pointer? = null
    private var gtkXid = 0L
    private var canvasXid = 0L
    private var pixelWidth = 1
    private var pixelHeight = 1

    // JNA callbacks: strongly referenced for the pane's lifetime.
    private val messageCallback = object : PointerPointerCallback {
        override fun invoke(instance: Pointer?, value: Pointer?, userData: Pointer?) {
            val text = value?.let { Gtk.takeString(Gtk.jsc.jsc_value_to_string(it)) } ?: return
            SwingUtilities.invokeLater { if (!disposed) onMessage(text) }
        }
    }
    /**
     * Keyboard focus. X sends key events to the X focus window, which stays AWT's while the pointer works on the
     * embedded GTK window, so a press anywhere in the pane moves the X input focus to the GTK window (GDK then sees
     * FocusIn and routes keys and IME to the web view). Capture phase, so WebKit's own handling is untouched.
     */
    private val clickPressedCallback = object : ClickPressedCallback {
        override fun invoke(gesture: Pointer?, nPress: Int, x: Double, y: Double, userData: Pointer?) {
            val dpy = display ?: return
            if (gtkXid == 0L || canvasXid == 0L) return
            val rc = Gtk.x11.XSetInputFocus(dpy, gtkXid, X_REVERT_TO_PARENT, 0L)
            if (System.getProperty("andbible.d5.focusLog") == "true") log("click n=$nPress: XSetInputFocus(${java.lang.Long.toHexString(gtkXid)}) = $rc")
        }
    }

    private val loadChangedCallback = object : LoadChangedCallback {
        override fun invoke(view: Pointer?, event: Int, userData: Pointer?) {
            // A new main document cannot answer the old document's evaluations.
            if (event == WEBKIT_LOAD_STARTED) evals.failAll()
        }
    }
    private val evalFinished = object : AsyncReadyCallback {
        override fun invoke(source: Pointer?, result: Pointer?, userData: Pointer?) {
            val evalId = Pointer.nativeValue(userData)
            if (source == null || result == null) {
                evals.fail(evalId)
                return
            }
            val error = PointerByReference()
            val value = Gtk.webkit.webkit_web_view_evaluate_javascript_finish(source, result, error)
            val errorPointer = error.value
            if (value == null) {
                if (errorPointer != null) {
                    log("evaluate failed: ${Gtk.errorMessage(errorPointer)}")
                    Gtk.glib.g_error_free(errorPointer)
                }
                evals.fail(evalId)
                return
            }
            // NULL for `undefined`: complete() maps it to "null".
            val json = Gtk.takeString(Gtk.jsc.jsc_value_to_json(value, 0))
            Gtk.gobject.g_object_unref(value)
            evals.complete(evalId, json)
        }
    }

    private val canvas = GtkHostCanvas(
        onAttach = { xid ->
            val (w, h) = pixelSize()
            gtk.post {
                canvasXid = xid
                pixelWidth = w
                pixelHeight = h
                attachIfReady()
            }
        },
        onDetach = {
            // Synchronous: the GTK window must be out of the canvas before the canvas X window is destroyed.
            runCatching {
                gtk.call {
                    val dpy = display
                    if (dpy != null && window != null && gtkXid != 0L && canvasXid != 0L) {
                        Gtk.x11.XUnmapWindow(dpy, gtkXid)
                        Gtk.x11.XReparentWindow(dpy, gtkXid, Gtk.x11.XDefaultRootWindow(dpy), 0, 0)
                        Gtk.x11.XSync(dpy, 0)
                    }
                    canvasXid = 0L
                }
            }.onFailure { log("detach failed: $it") }
        },
    ).apply {
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent?) {
                val (w, h) = pixelSize()
                gtk.post {
                    pixelWidth = w
                    pixelHeight = h
                    resizeWindow()
                }
            }
        })
    }

    init {
        gtk.call { createWindow(scripts) }
    }

    /** The AWT host; created once, re-parented by every [Content]. Exposed for tests. */
    internal val component: Component by lazy { JPanel(BorderLayout()).apply { add(canvas, BorderLayout.CENTER) } }

    /** Canvas size in device pixels (approximate HiDPI: the default transform scale). EDT. */
    private fun pixelSize(): Pair<Int, Int> {
        val scale = canvas.graphicsConfiguration?.defaultTransform?.scaleX ?: 1.0
        return maxOf(1, (canvas.width * scale).toInt()) to maxOf(1, (canvas.height * scale).toInt())
    }

    /** GTK thread: creates the web view and its window, registers every script, then loads [url]. */
    private fun createWindow(scripts: List<String>) {
        val wk = Gtk.webkit
        val gtkLib = Gtk.gtk
        val v = Gtk.newWebView(checkNotNull(BundleScheme.context) { "BundleScheme.install must run first" })
        view = v
        val rgb = if (WebKitGtkEngine.isNight(url)) 0f else 1f
        wk.webkit_web_view_set_background_color(v, GdkRGBA(rgb, rgb, rgb, 1f))

        val ucm = wk.webkit_web_view_get_user_content_manager(v)
        if (wk.webkit_user_content_manager_register_script_message_handler(ucm, "bridge", null) == 0) {
            log("registering the bridge script message handler failed")
        }
        Gtk.gobject.g_signal_connect_data(ucm, "script-message-received::bridge", messageCallback, null, null, 0)
        Gtk.gobject.g_signal_connect_data(v, "load-changed", loadChangedCallback, null, null, 0)
        for (source in scripts) {
            val script = wk.webkit_user_script_new(source, WEBKIT_INJECT_TOP_FRAME, WEBKIT_INJECT_AT_DOCUMENT_START, null, null)
            wk.webkit_user_content_manager_add_script(ucm, script)
            wk.webkit_user_script_unref(script)
        }

        val win = gtkLib.gtk_window_new()
        window = win
        gtkLib.gtk_window_set_decorated(win, 0)
        gtkLib.gtk_window_set_child(win, v)
        val click = gtkLib.gtk_gesture_click_new()
        gtkLib.gtk_event_controller_set_propagation_phase(click, GTK_PHASE_CAPTURE)
        Gtk.gobject.g_signal_connect_data(click, "pressed", clickPressedCallback, null, null, 0)
        gtkLib.gtk_widget_add_controller(win, click)
        gtkLib.gtk_window_set_default_size(win, pixelWidth, pixelHeight)
        // Realize (not present) first, so the X window exists and can be reparented before it is ever mapped at the root.
        gtkLib.gtk_widget_realize(win)
        val surface = gtkLib.gtk_native_get_surface(win)
        val dpy = gtkLib.gdk_display_get_default()
        if (surface == null || dpy == null) error("GTK window has no surface or display after realize")
        gtkXid = gtkLib.gdk_x11_surface_get_xid(surface)
        display = gtkLib.gdk_x11_display_get_xdisplay(dpy).also { Gtk.setOverrideRedirect(it, gtkXid) }
        // Presented only once it sits inside the canvas (attachIfReady): mapped at the root first, a window manager
        // would take it over (Weston's Xwayland WM framed it and the panes stayed blank).
        attachIfReady()
        wk.webkit_web_view_load_uri(v, url)
    }

    /** GTK thread: whether the GTK window was presented (it is, at its first attach). */
    private var presented = false

    /** GTK thread: reparents the GTK window into the canvas once both exist. */
    private fun attachIfReady() {
        val dpy = display ?: return
        if (window == null || gtkXid == 0L || canvasXid == 0L) return
        val x = Gtk.x11
        x.XReparentWindow(dpy, gtkXid, canvasXid, 0, 0)
        x.XMoveResizeWindow(dpy, gtkXid, 0, 0, pixelWidth, pixelHeight)
        if (!presented) {
            presented = true
            Gtk.gtk.gtk_window_present(window!!)
        } else {
            x.XMapWindow(dpy, gtkXid)
        }
        x.XSync(dpy, 0)
    }

    /** GTK thread. */
    private fun resizeWindow() {
        val dpy = display ?: return
        if (window == null || gtkXid == 0L || canvasXid == 0L) return
        Gtk.x11.XMoveResizeWindow(dpy, gtkXid, 0, 0, pixelWidth, pixelHeight)
        Gtk.x11.XSync(dpy, 0)
    }

    @Composable
    override fun Content(modifier: Modifier) {
        SwingPanel(factory = { component }, modifier = modifier)
    }

    override val isMainThread: Boolean get() = SwingUtilities.isEventDispatchThread()
    override val isAttached: Boolean get() = !disposed
    override val mainDispatcher: CoroutineDispatcher = SwingMainDispatcher

    override fun post(block: () -> Unit) = SwingUtilities.invokeLater { if (!disposed) block() }

    override fun evaluate(js: String, callback: ((String) -> Unit)?) {
        if (disposed) {
            callback?.invoke(EvalBridge.NO_RESULT)
            return
        }
        if (callback == null) {
            gtk.post { runEvaluate(js, 0L) }
            return
        }
        val evalId = evals.register(callback)
        gtk.post { runEvaluate(js, evalId) }
    }

    /** GTK thread. [evalId] 0 means fire-and-forget (the callback still runs, and ignores the unknown id). */
    private fun runEvaluate(js: String, evalId: Long) {
        val v = view
        if (v == null || disposed) {
            if (evalId != 0L) evals.fail(evalId)
            return
        }
        Gtk.webkit.webkit_web_view_evaluate_javascript(v, js, -1L, null, null, null, evalFinished, Pointer.createConstant(evalId))
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        evals.failAll()
        gtk.post {
            val win = window ?: return@post
            window = null
            view = null
            Gtk.gtk.gtk_window_destroy(win)
        }
    }
}
