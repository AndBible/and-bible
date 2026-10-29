package net.bible.desktop.webkitgtk

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.PointerByReference
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/*
 * Thin JNA bindings for GTK 4 + WebKitGTK 6.0 (D5 experiment). DRAFT written without the libraries installed:
 * every signature below was written from the 6.0 C API and has not been run yet.
 *
 * 64-bit Linux only: `gssize`/`gsize`/`gint64`/`gulong`/`XID`/`Window` are all mapped to Kotlin `Long`.
 * Strings are UTF-8 (the option is set explicitly: verse text goes through `evaluate_javascript`).
 */

/** `void (*GAsyncReadyCallback)(GObject* source, GAsyncResult* res, gpointer user_data)`. */
interface AsyncReadyCallback : Callback {
    fun invoke(source: Pointer?, result: Pointer?, userData: Pointer?)
}

/** `void (*)(WebKitUserContentManager*, JSCValue*, gpointer)` (script-message-received) and the like. */
interface PointerPointerCallback : Callback {
    fun invoke(instance: Pointer?, value: Pointer?, userData: Pointer?)
}

/** `void (*)(WebKitWebView*, WebKitLoadEvent, gpointer)` (load-changed). */
interface LoadChangedCallback : Callback {
    fun invoke(view: Pointer?, event: Int, userData: Pointer?)
}

/** `void (*WebKitURISchemeRequestCallback)(WebKitURISchemeRequest*, gpointer)`. */
/** `GtkGestureClick::pressed`: `void (GtkGestureClick*, gint n_press, gdouble x, gdouble y, gpointer)`. */
interface ClickPressedCallback : Callback {
    fun invoke(gesture: Pointer?, nPress: Int, x: Double, y: Double, userData: Pointer?)
}

interface SchemeRequestCallback : Callback {
    fun invoke(request: Pointer?, userData: Pointer?)
}

/** `gboolean (*GSourceFunc)(gpointer)`; returns 0 (G_SOURCE_REMOVE). */
interface SourceFunc : Callback {
    fun invoke(userData: Pointer?): Int
}

/** `GdkRGBA`: four floats in 0..1 (GTK4; GTK3 used doubles). */
@Structure.FieldOrder("red", "green", "blue", "alpha")
class GdkRGBA(
    @JvmField var red: Float = 0f,
    @JvmField var green: Float = 0f,
    @JvmField var blue: Float = 0f,
    @JvmField var alpha: Float = 1f,
) : Structure()

interface GLib : Library {
    fun g_main_loop_new(context: Pointer?, isRunning: Int): Pointer
    fun g_main_loop_run(loop: Pointer)
    fun g_main_loop_quit(loop: Pointer)
    fun g_main_loop_unref(loop: Pointer)
    fun g_idle_add_full(priority: Int, function: SourceFunc, data: Pointer?, notify: Pointer?): Int
    fun g_free(mem: Pointer?)
    fun g_malloc(size: Long): Pointer
    fun g_quark_from_string(string: String): Int
    fun g_error_new_literal(domain: Int, code: Int, message: String): Pointer
    fun g_error_free(error: Pointer)
}

interface GObject : Library {
    fun g_signal_connect_data(instance: Pointer, detailedSignal: String, handler: Callback, data: Pointer?, destroyData: Pointer?, flags: Int): Long
    fun g_object_unref(obj: Pointer)
}

interface Gio : Library {
    fun g_memory_input_stream_new_from_data(data: Pointer?, len: Long, destroy: Pointer?): Pointer
}

/** GTK 4 and GDK (GDK is part of libgtk-4). */
interface Gtk4 : Library {
    fun gdk_set_allowed_backends(backends: String)
    fun gtk_init_check(): Int
    fun gtk_window_new(): Pointer
    fun gtk_window_set_decorated(window: Pointer, setting: Int)
    fun gtk_window_set_child(window: Pointer, child: Pointer?)
    fun gtk_window_set_default_size(window: Pointer, width: Int, height: Int)
    fun gtk_window_present(window: Pointer)
    fun gtk_window_destroy(window: Pointer)
    fun gtk_gesture_click_new(): Pointer
    fun gtk_event_controller_set_propagation_phase(controller: Pointer, phase: Int)
    fun gtk_widget_add_controller(widget: Pointer, controller: Pointer)
    fun gtk_widget_realize(widget: Pointer)
    fun gtk_native_get_surface(native: Pointer): Pointer?
    fun gdk_display_get_default(): Pointer?
    fun gdk_x11_surface_get_xid(surface: Pointer): Long
    fun gdk_x11_display_get_xdisplay(display: Pointer): Pointer
}

interface WebKit : Library {
    fun webkit_web_view_new(): Pointer
    fun webkit_web_view_load_uri(view: Pointer, uri: String)
    fun webkit_web_view_get_user_content_manager(view: Pointer): Pointer
    fun webkit_web_view_get_context(view: Pointer): Pointer
    fun webkit_web_view_set_background_color(view: Pointer, color: GdkRGBA)
    fun webkit_web_view_evaluate_javascript(
        view: Pointer, script: String, length: Long, worldName: String?, sourceUri: String?,
        cancellable: Pointer?, callback: AsyncReadyCallback, userData: Pointer?,
    )
    fun webkit_web_view_evaluate_javascript_finish(view: Pointer, result: Pointer, error: PointerByReference): Pointer?

    fun webkit_user_content_manager_register_script_message_handler(manager: Pointer, name: String, worldName: String?): Int
    fun webkit_user_content_manager_add_script(manager: Pointer, script: Pointer)
    fun webkit_user_script_new(source: String, injectedFrames: Int, injectionTime: Int, allowList: Pointer?, blockList: Pointer?): Pointer
    fun webkit_user_script_unref(script: Pointer)

    fun webkit_web_context_get_default(): Pointer
    fun webkit_web_context_new(): Pointer
    fun webkit_web_view_get_type(): Long
    fun webkit_web_context_register_uri_scheme(context: Pointer, scheme: String, callback: SchemeRequestCallback, userData: Pointer?, destroy: Pointer?)
    fun webkit_web_context_get_security_manager(context: Pointer): Pointer
    fun webkit_security_manager_register_uri_scheme_as_secure(manager: Pointer, scheme: String)
    fun webkit_security_manager_register_uri_scheme_as_cors_enabled(manager: Pointer, scheme: String)

    fun webkit_uri_scheme_request_get_uri(request: Pointer): String
    fun webkit_uri_scheme_response_new(stream: Pointer, streamLength: Long): Pointer
    fun webkit_uri_scheme_response_set_content_type(response: Pointer, contentType: String)
    fun webkit_uri_scheme_request_finish_with_response(request: Pointer, response: Pointer)
    fun webkit_uri_scheme_request_finish_error(request: Pointer, error: Pointer)
}

interface Jsc : Library {
    fun jsc_value_to_string(value: Pointer): Pointer?
    fun jsc_value_to_json(value: Pointer, indent: Int): Pointer?
}

/** libc's `exit`, to end the process from the GTK thread (see `WebKitGtkEngine.exitProcess`). */
object LibC {
    private val exitFunction by lazy { com.sun.jna.NativeLibrary.getInstance("c").getFunction("exit") }
    fun exit(status: Int) = exitFunction.invokeVoid(arrayOf(status))
}

interface X11 : Library {
    fun XReparentWindow(display: Pointer, window: Long, parent: Long, x: Int, y: Int): Int
    fun XMoveResizeWindow(display: Pointer, window: Long, x: Int, y: Int, width: Int, height: Int): Int
    fun XMapWindow(display: Pointer, window: Long): Int
    fun XUnmapWindow(display: Pointer, window: Long): Int
    fun XSync(display: Pointer, discard: Int): Int
    fun XDefaultRootWindow(display: Pointer): Long
    fun XSetInputFocus(display: Pointer, window: Long, revertTo: Int, time: Long): Int
    fun XChangeWindowAttributes(display: Pointer, window: Long, valueMask: Long, attributes: Pointer): Int
}

/** WebKitGTK's `WEBKIT_USER_CONTENT_INJECT_TOP_FRAME`, `WEBKIT_USER_SCRIPT_INJECT_AT_DOCUMENT_START`, `WEBKIT_LOAD_STARTED`. */
const val WEBKIT_INJECT_TOP_FRAME = 1
const val WEBKIT_INJECT_AT_DOCUMENT_START = 0
const val WEBKIT_LOAD_STARTED = 0

/** The libraries, loaded lazily (first use is on the GTK thread; a missing library fails there with a clear message). */
internal object Gtk {
    private val options = mapOf<String, Any>(Library.OPTION_STRING_ENCODING to "UTF-8")

    private fun open(vararg candidates: String): String {
        var last: Throwable? = null
        for (name in candidates) {
            try {
                NativeLibrary.getInstance(name)
                return name
            } catch (e: UnsatisfiedLinkError) {
                last = e
            }
        }
        throw IllegalStateException(
            "Cannot load any of ${candidates.joinToString()}: install GTK 4 and WebKitGTK 6.0 (Debian/Ubuntu: libgtk-4-1 libwebkitgtk-6.0-4)",
            last,
        )
    }

    private fun <T : Library> load(cls: Class<T>, vararg candidates: String): T = Native.load(open(*candidates), cls, options)

    private val glibName by lazy { open("libglib-2.0.so.0", "glib-2.0") }
    val glib: GLib by lazy { Native.load(glibName, GLib::class.java, options) }
    val gobject: GObject by lazy { load(GObject::class.java, "libgobject-2.0.so.0", "gobject-2.0") }
    val gio: Gio by lazy { load(Gio::class.java, "libgio-2.0.so.0", "gio-2.0") }
    val gtk: Gtk4 by lazy { load(Gtk4::class.java, "libgtk-4.so.1", "gtk-4") }
    val webkit: WebKit by lazy { load(WebKit::class.java, "libwebkitgtk-6.0.so.4", "webkitgtk-6.0") }
    val jsc: Jsc by lazy { load(Jsc::class.java, "libjavascriptcoregtk-6.0.so.1", "javascriptcoregtk-6.0") }
    val x11: X11 by lazy { load(X11::class.java, "libX11.so.6", "X11") }

    /**
     * Marks [window] override-redirect, so no window manager ever manages (frames, moves, reparents) it. A GTK toplevel
     * embedded into an AWT canvas must never be mapped as a managed toplevel first. LP64 `XSetWindowAttributes`
     * layout: `override_redirect` is the `Bool` at offset 88 of the 112-byte struct; `CWOverrideRedirect` is 1 shl 9.
     */
    fun setOverrideRedirect(display: Pointer, window: Long) {
        val attributes = com.sun.jna.Memory(112).apply { clear(); setInt(88, 1) }
        x11.XChangeWindowAttributes(display, window, 1L shl 9, attributes)
    }

    /** `g_object_new(WEBKIT_TYPE_WEB_VIEW, "web-context", context, NULL)`: a web view on [context]. */
    fun newWebView(context: Pointer): Pointer {
        val f = NativeLibrary.getInstance("gobject-2.0").getFunction("g_object_new")
        return f.invokePointer(arrayOf<Any?>(webkit.webkit_web_view_get_type(), "web-context", context, null))
    }

    /** The address of `g_free`, usable as a `GDestroyNotify`. */
    val gFreeNotify: Pointer by lazy { NativeLibrary.getInstance(glibName).getFunction("g_free") }

    /** Reads a `gchar*` returned by a transfer-full call and frees it. */
    fun takeString(p: Pointer?): String? {
        if (p == null) return null
        try {
            return p.getString(0, "UTF-8")
        } finally {
            glib.g_free(p)
        }
    }

    /** The message of a `GError*` (`domain: guint32, code: gint, message: gchar*`). */
    fun errorMessage(error: Pointer?): String =
        error?.getPointer(8)?.getString(0, "UTF-8") ?: "unknown error"
}

/**
 * The real [MainLoopDriver]: a `GMainLoop` on the default context, run on the calling (GTK) thread.
 *
 * - Backend: GDK is restricted to X11 (`gdk_set_allowed_backends("x11")`, before `gtk_init_check`), because the web view
 *   window is reparented into an AWT window by X11 id. Under Wayland that means Xwayland.
 * - [invoke] queues the task and, if no drain is scheduled, `g_idle_add_full`s [drain] (one persistent JNA callback,
 *   referenced here for the driver's lifetime). `g_idle_add_full` is thread-safe.
 */
class GtkMainLoopDriver : MainLoopDriver {
    private val queue = ConcurrentLinkedQueue<Runnable>()
    private val scheduled = AtomicBoolean(false)

    @Volatile private var loop: Pointer? = null

    /** Must stay referenced: JNA keeps callbacks only weakly. */
    private val drain = object : SourceFunc {
        override fun invoke(userData: Pointer?): Int {
            scheduled.set(false)
            while (true) {
                val task = queue.poll() ?: break
                try {
                    task.run()
                } catch (t: Throwable) {
                    System.err.println("GtkMainLoopDriver: task failed: $t")
                }
            }
            return 0
        }
    }

    override fun run(onStarted: () -> Unit) {
        Gtk.gtk.gdk_set_allowed_backends("x11")
        if (Gtk.gtk.gtk_init_check() == 0) error("gtk_init_check failed: no usable X11 display (is DISPLAY set?)")
        val l = Gtk.glib.g_main_loop_new(null, 0)
        loop = l
        onStarted()
        // Tasks queued before the loop runs are drained by the idle source as soon as it does.
        Gtk.glib.g_main_loop_run(l)
        Gtk.glib.g_main_loop_unref(l)
        loop = null
    }

    override fun invoke(task: Runnable) {
        queue.add(task)
        if (scheduled.compareAndSet(false, true)) {
            Gtk.glib.g_idle_add_full(G_PRIORITY_DEFAULT, drain, null, null)
        }
    }

    override fun quit() {
        loop?.let { Gtk.glib.g_main_loop_quit(it) }
    }

    private companion object {
        const val G_PRIORITY_DEFAULT = 0
    }
}
