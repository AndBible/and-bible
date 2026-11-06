package net.bible.desktop

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
 * connection; every failure (no libX11, no `DISPLAY`, no XSETTINGS manager) gives nulls, never an exception.
 */
internal object X11DisplayScaleProbe {
    data class Result(val xsettings: XSettingsValues?, val xResources: String?)

    @Suppress("FunctionName")
    private interface X11 : Library {
        fun XOpenDisplay(name: String?): Pointer?
        fun XCloseDisplay(display: Pointer): Int
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

    fun read(): Result {
        if (System.getenv("DISPLAY").isNullOrEmpty()) return Result(null, null)
        val x = runCatching { Native.load("X11", X11::class.java) }.getOrNull() ?: return Result(null, null)
        val dpy = runCatching { x.XOpenDisplay(null) }.getOrNull() ?: return Result(null, null)
        return try {
            val resources = runCatching { x.XResourceManagerString(dpy) }.getOrNull()
            val xsettings = runCatching { readXSettings(x, dpy) }.getOrNull()
            Result(xsettings, resources)
        } finally {
            x.XCloseDisplay(dpy)
        }
    }

    private fun readXSettings(x: X11, dpy: Pointer): XSettingsValues? {
        val selection = x.XInternAtom(dpy, "_XSETTINGS_S${x.XDefaultScreen(dpy)}", 0)
        val owner = x.XGetSelectionOwner(dpy, selection)
        if (owner.toLong() == 0L) return null
        val property = x.XInternAtom(dpy, "_XSETTINGS_SETTINGS", 0)
        val type = NativeLongByReference()
        val format = IntByReference()
        val items = NativeLongByReference()
        val after = NativeLongByReference()
        val data = PointerByReference()
        // AnyPropertyType (0); length in 32-bit units.
        val status = x.XGetWindowProperty(dpy, owner, property, NativeLong(0), NativeLong(65536), 0, NativeLong(0), type, format, items, after, data)
        val p = data.value ?: return null
        try {
            if (status != 0 || format.value != 8) return null
            return DisplayScaleDetector.parseXSettings(p.getByteArray(0, items.value.toInt()))
        } finally {
            x.XFree(p)
        }
    }
}
