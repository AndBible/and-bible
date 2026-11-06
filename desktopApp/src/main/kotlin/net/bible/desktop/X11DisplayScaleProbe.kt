package net.bible.desktop

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.NativeLongByReference
import com.sun.jna.ptr.PointerByReference

/**
 * Reads the desktop's scale settings from the X server before AWT starts (#4006): the `RESOURCE_MANAGER` string
 * (`Xft.dpi`) and the XSETTINGS manager's `_XSETTINGS_SETTINGS` of the default screen. Its own short-lived display
 * connection; every failure (no libX11, no `DISPLAY`, no XSETTINGS manager, an X error) gives nulls and a reason, never
 * an exception or Xlib's default error handler (which exits the process).
 */
internal object X11DisplayScaleProbe {
    /**
     * What one read found. [settingsOwner]: an XSETTINGS manager owns the screen's selection. [failure]: why nothing could
     * be read. [waitedMs]: how long [readUntilSettled] waited for GNOME's settings daemon.
     */
    data class Result(
        val xsettings: XSettingsValues?,
        val xResources: String?,
        val settingsOwner: Boolean = xsettings != null,
        val failure: String? = null,
        val waitedMs: Long = 0,
    ) {
        /** Why no desktop scale may be found, for the log line; null when the settings were there. */
        fun note(): String? {
            val reason = failure ?: if (!settingsOwner && xResources == null) "no XSETTINGS manager and no X resources" else null
            val waited = if (waitedMs > 0) "waited $waitedMs ms for GNOME's settings daemon" else null
            return listOfNotNull(reason, waited).joinToString(", ").ifEmpty { null }
        }

        companion object {
            fun failed(reason: String) = Result(null, null, failure = reason)
        }
    }

    /** How long a GNOME Wayland start waits for gsd-xsettings, and how often it looks. */
    const val GNOME_WAIT_MS = 1500L
    const val GNOME_POLL_MS = 100L

    fun read(env: Map<String, String> = System.getenv()): Result =
        readUntilSettled(env, { readOnce(env) }, Thread::sleep, System::nanoTime)

    /**
     * [readOnce], repeated while [awaitsGnomeSettings] (at most [GNOME_WAIT_MS]). On GNOME Wayland the probe's own
     * XOpenDisplay may start Xwayland on demand (first launch after login): gsd-xsettings then takes `_XSETTINGS_S0` and
     * writes `Xft.dpi` only a moment later, and a single read would give scale 1. Each read is a new connection, since
     * Xlib caches `RESOURCE_MANAGER` at XOpenDisplay.
     */
    fun readUntilSettled(env: Map<String, String>, readOnce: () -> Result, sleep: (Long) -> Unit, nanoTime: () -> Long): Result {
        var r = readOnce()
        if (!awaitsGnomeSettings(env, r)) return r
        // Counted from the first read's end: opening the display is not waiting.
        val start = nanoTime()
        fun elapsedMs() = (nanoTime() - start) / 1_000_000
        while (awaitsGnomeSettings(env, r) && elapsedMs() < GNOME_WAIT_MS) {
            sleep(GNOME_POLL_MS)
            r = readOnce()
        }
        return r.copy(waitedMs = elapsedMs().coerceAtLeast(1))
    }

    /** The display answered but has no settings yet, in a GNOME Wayland session (where they come from gsd-xsettings). */
    fun awaitsGnomeSettings(env: Map<String, String>, r: Result): Boolean =
        r.failure == null && !r.settingsOwner && r.xResources == null &&
            !env["WAYLAND_DISPLAY"].isNullOrEmpty() &&
            env["XDG_CURRENT_DESKTOP"].orEmpty().split(':').any { it.trim().equals("GNOME", ignoreCase = true) }

    /** Offset of `error_code` in `XErrorEvent` (int type, Display*, XID resourceid, unsigned long serial, then the code). */
    fun errorCodeOffset(pointerSize: Int, longSize: Int): Int = (4 + pointerSize - 1) / pointerSize * pointerSize + pointerSize + 2 * longSize

    /** `int (*XErrorHandler)(Display*, XErrorEvent*)`. */
    fun interface XErrorHandler : Callback {
        fun invoke(display: Pointer?, event: Pointer?): Int
    }

    @Suppress("FunctionName")
    private interface X11 : Library {
        fun XOpenDisplay(name: String?): Pointer?
        fun XCloseDisplay(display: Pointer): Int
        fun XSync(display: Pointer, discard: Int): Int
        fun XSetErrorHandler(handler: XErrorHandler?): Pointer?
        fun XSetErrorHandler(previous: Pointer?): Pointer?
        fun XResourceManagerString(display: Pointer): String?
        fun XDefaultScreen(display: Pointer): Int
        fun XInternAtom(display: Pointer, name: String, onlyIfExists: Int): NativeLong
        fun XGetSelectionOwner(display: Pointer, selection: NativeLong): NativeLong
        fun XGetWindowProperty(
            display: Pointer, window: NativeLong, property: NativeLong, offset: NativeLong, length: NativeLong,
            delete: Int, reqType: NativeLong, actualType: NativeLongByReference, actualFormat: IntByReference,
            nItems: NativeLongByReference, bytesAfter: NativeLongByReference, prop: PointerByReference,
        ): Int
        fun XFree(data: Pointer): Int
    }

    /** libX11 by its runtime soname first (no `-dev` package installs the bare `libX11.so`), as GtkBindings does. */
    private fun loadX11(): X11 = runCatching { Native.load("libX11.so.6", X11::class.java) }
        .recoverCatching { Native.load("X11", X11::class.java) }.getOrThrow()

    private fun readOnce(env: Map<String, String>): Result {
        val display = env["DISPLAY"]
        if (display.isNullOrEmpty()) return Result.failed("DISPLAY is not set")
        val x = runCatching { loadX11() }.getOrElse { return Result.failed("libX11 not loadable: ${it.message}") }
        // Between XGetSelectionOwner and XGetWindowProperty the manager window may vanish (BadWindow): Xlib's default
        // handler would exit the process. A temporary handler records the error instead; restored before AWT starts.
        var xError: Int? = null
        val handler = XErrorHandler { _, event ->
            xError = event?.getByte(errorCodeOffset(Native.POINTER_SIZE, NativeLong.SIZE).toLong())?.toInt()?.and(0xff) ?: -1
            0
        }
        val previous = x.XSetErrorHandler(handler)
        try {
            val dpy = x.XOpenDisplay(null) ?: return Result.failed("XOpenDisplay($display) failed")
            val result = try {
                val resources = runCatching { x.XResourceManagerString(dpy) }.getOrNull()
                val (owner, xsettings) = runCatching { readXSettings(x, dpy) }.getOrElse { true to null }
                x.XSync(dpy, 0)
                Result(xsettings, resources, settingsOwner = owner)
            } finally {
                x.XCloseDisplay(dpy)
            }
            val code = xError
            return if (code != null && result.xsettings == null) result.copy(failure = "X error $code reading XSETTINGS") else result
        } finally {
            x.XSetErrorHandler(previous)
            java.lang.ref.Reference.reachabilityFence(handler)
        }
    }

    /** Whether an XSETTINGS manager owns the screen's selection, and its parsed settings. */
    private fun readXSettings(x: X11, dpy: Pointer): Pair<Boolean, XSettingsValues?> {
        val selection = x.XInternAtom(dpy, "_XSETTINGS_S${x.XDefaultScreen(dpy)}", 0)
        val owner = x.XGetSelectionOwner(dpy, selection)
        if (owner.toLong() == 0L) return false to null
        val property = x.XInternAtom(dpy, "_XSETTINGS_SETTINGS", 0)
        val type = NativeLongByReference()
        val format = IntByReference()
        val items = NativeLongByReference()
        val after = NativeLongByReference()
        val data = PointerByReference()
        // AnyPropertyType (0); length in 32-bit units.
        val status = x.XGetWindowProperty(dpy, owner, property, NativeLong(0), NativeLong(65536), 0, NativeLong(0), type, format, items, after, data)
        val p = data.value ?: return true to null
        try {
            if (status != 0 || format.value != 8) return true to null
            return true to DisplayScaleDetector.parseXSettings(p.getByteArray(0, items.value.toInt()))
        } finally {
            x.XFree(p)
        }
    }
}
