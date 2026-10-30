package net.bible.desktop.webview2

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.W32APIOptions

/** The exports of `WebView2Loader.dll` this module uses. */
interface WebView2LoaderLibrary : Library {
    fun GetAvailableCoreWebView2BrowserVersionString(browserExecutableFolder: WString?, versionInfo: PointerByReference): Int
    fun CreateCoreWebView2EnvironmentWithOptions(browserExecutableFolder: WString?, userDataFolder: WString?, options: Pointer?, handler: Pointer): Int
    fun CompareBrowserVersions(version1: WString, version2: WString, result: IntByReference): Int
}

private interface DcompLibrary : Library {
    fun DCompositionCreateDevice2(renderingDevice: Pointer?, iid: Pointer, device: PointerByReference): Int
}

private interface ShlwapiLibrary : Library {
    fun SHCreateMemStream(init: ByteArray?, size: Int): Pointer?
}

private interface User32Extra : Library {
    fun SetWindowPos(hwnd: WinDef.HWND, after: WinDef.HWND?, x: Int, y: Int, cx: Int, cy: Int, flags: Int): Boolean
}

/** Win32 helpers for the STA thread (all called on it unless noted). */
object Win32 {
    const val WM_APP = 0x8000
    private const val WS_CHILD = 0x40000000
    private const val WS_VISIBLE = 0x10000000
    private const val WS_CLIPSIBLINGS = 0x04000000
    private const val WS_POPUP = 0x80000000.toInt()
    private const val WS_EX_NOPARENTNOTIFY = 0x4
    private const val COINIT_APARTMENTTHREADED = 0x2
    const val SWP_NOACTIVATE = 0x0010
    const val SWP_NOZORDER = 0x0004
    const val SWP_SHOWWINDOW = 0x0040
    const val SWP_HIDEWINDOW = 0x0080
    val HWND_BOTTOM = WinDef.HWND(Pointer(1))
    val HWND_MESSAGE = WinDef.HWND(Pointer(-3))

    private val user32 by lazy { User32.INSTANCE }
    private val user32Extra by lazy { Native.load("user32", User32Extra::class.java, W32APIOptions.DEFAULT_OPTIONS) }
    private val dcomp by lazy { Native.load("dcomp", DcompLibrary::class.java) }
    private val shlwapi by lazy { Native.load("shlwapi", ShlwapiLibrary::class.java) }

    private const val HOST_CLASS = "AndBibleWebView2Host"
    private const val PUMP_CLASS = "AndBibleWebView2Pump"
    private val defProc = WinUser.WindowProc { hwnd, msg, wParam, lParam -> user32.DefWindowProc(hwnd, msg, wParam, lParam) }
    private var hostClassRegistered = false

    fun coInitializeSta() {
        val hr = Ole32.INSTANCE.CoInitializeEx(null, COINIT_APARTMENTTHREADED).toInt()
        if (hr < 0) throw ComException("CoInitializeEx", hr)
    }

    private fun registerClass(name: String, proc: WinUser.WindowProc) {
        val wc = WinUser.WNDCLASSEX().apply {
            hInstance = Kernel32.INSTANCE.GetModuleHandle(null)
            lpszClassName = name
            lpfnWndProc = proc
        }
        if (user32.RegisterClassEx(wc).toInt() == 0) {
            val err = Kernel32.INSTANCE.GetLastError()
            if (err != 1410) error("RegisterClassEx($name) failed: $err") // 1410: class already exists
        }
    }

    /**
     * A child window of [parent] (a window of another thread: the AWT frame), created on the calling thread, at the bottom of
     * the z-order: below the Compose (Skia) canvas, so Compose draws above it wherever its canvas is not transparent.
     */
    fun createHostWindow(parent: WinDef.HWND): WinDef.HWND {
        if (!hostClassRegistered) {
            registerClass(HOST_CLASS, defProc)
            hostClassRegistered = true
        }
        // WS_EX_NOPARENTNOTIFY: no synchronous WM_PARENTNOTIFY to the AWT thread when the host is created or destroyed.
        val hwnd = user32.CreateWindowEx(
            WS_EX_NOPARENTNOTIFY, HOST_CLASS, "", WS_CHILD or WS_CLIPSIBLINGS, 0, 0, 1, 1, parent, null, Kernel32.INSTANCE.GetModuleHandle(null), null,
        ) ?: error("CreateWindowEx(host) failed: ${Kernel32.INSTANCE.GetLastError()}")
        return hwnd
    }

    /** A hidden top-level window that holds the host windows of panes that are not in a Compose window. */
    fun createParkingWindow(): WinDef.HWND {
        if (!hostClassRegistered) {
            registerClass(HOST_CLASS, defProc)
            hostClassRegistered = true
        }
        return user32.CreateWindowEx(0, HOST_CLASS, "", WS_POPUP, 0, 0, 1, 1, null, null, Kernel32.INSTANCE.GetModuleHandle(null), null)
            ?: error("CreateWindowEx(parking) failed: ${Kernel32.INSTANCE.GetLastError()}")
    }

    fun setParent(child: WinDef.HWND, parent: WinDef.HWND) {
        user32.SetParent(child, parent)
    }

    /** Moves [hwnd] to ([x], [y]) with the size [w] x [h] (parent client pixels), at the bottom of the z-order, shown or hidden. */
    fun placeAtBottom(hwnd: WinDef.HWND, x: Int, y: Int, w: Int, h: Int, visible: Boolean) {
        user32Extra.SetWindowPos(hwnd, HWND_BOTTOM, x, y, w.coerceAtLeast(1), h.coerceAtLeast(1), SWP_NOACTIVATE or if (visible) SWP_SHOWWINDOW else SWP_HIDEWINDOW)
    }

    fun destroyWindow(hwnd: WinDef.HWND) {
        user32.DestroyWindow(hwnd)
    }

    /** A DirectComposition device (`IDCompositionDevice`, created without a rendering device, as WebView2's samples do). */
    fun createDcompDevice(): ComPtr {
        val out = PointerByReference()
        val hr = dcomp.DCompositionCreateDevice2(null, IID_IDCompositionDevice.memory, out)
        if (hr < 0) throw ComException("DCompositionCreateDevice2", hr)
        return ComPtr(out.value)
    }

    /** An `IStream` over a copy of [bytes]. */
    fun memStream(bytes: ByteArray): ComPtr =
        ComPtr(shlwapi.SHCreateMemStream(bytes, bytes.size) ?: error("SHCreateMemStream failed"))

    /** A message-only window whose [WM_APP] calls [onWake]; created on the calling thread. */
    fun createPumpWindow(onWake: () -> Unit): Pair<WinDef.HWND, WinUser.WindowProc> {
        val proc = WinUser.WindowProc { hwnd, msg, wParam, lParam ->
            if (msg == WM_APP) {
                onWake()
                WinDef.LRESULT(0)
            } else {
                user32.DefWindowProc(hwnd, msg, wParam, lParam)
            }
        }
        registerClass(PUMP_CLASS, defProc)
        val hwnd = user32.CreateWindowEx(0, PUMP_CLASS, "", 0, 0, 0, 0, 0, HWND_MESSAGE, null, null, null)
            ?: error("CreateWindowEx(pump) failed: ${Kernel32.INSTANCE.GetLastError()}")
        // The class is shared; each pump window gets its own procedure.
        user32.SetWindowLongPtr(hwnd, WinUser.GWL_WNDPROC, com.sun.jna.CallbackReference.getFunctionPointer(proc))
        return hwnd to proc
    }

    fun post(hwnd: WinDef.HWND, msg: Int) {
        user32.PostMessage(hwnd, msg, WinDef.WPARAM(0), WinDef.LPARAM(0))
    }

    fun pumpMessages() {
        val msg = WinUser.MSG()
        while (user32.GetMessage(msg, null, 0, 0) > 0) {
            user32.TranslateMessage(msg)
            user32.DispatchMessage(msg)
        }
    }

    fun postQuit() = user32.PostQuitMessage(0)

    fun setEnvironmentVariable(name: String, value: String) {
        Kernel32.INSTANCE.SetEnvironmentVariable(name, value)
    }

    fun memory(bytes: Int): Memory = Memory(bytes.toLong()).apply { clear() }

    val IID_IDCompositionDevice = Iid("C37EA93A-E7AA-450D-B16F-9746CB0407F3")
}

/** The Win32 pump: a COM STA, a message-only window for wake-ups, and `GetMessage` until [quit]. */
class Win32MessagePump : MessagePump {
    @Volatile
    private var hwnd: WinDef.HWND? = null
    private var proc: WinUser.WindowProc? = null // kept reachable for the window's lifetime
    @Volatile
    private var wakeRequested = false

    override fun run(drain: () -> Unit) {
        Win32.coInitializeSta()
        val (h, p) = Win32.createPumpWindow(drain)
        proc = p
        hwnd = h
        drain()
        if (wakeRequested) Win32.post(h, Win32.WM_APP)
        Win32.pumpMessages()
        Win32.destroyWindow(h)
    }

    override fun wake() {
        val h = hwnd
        if (h == null) wakeRequested = true else Win32.post(h, Win32.WM_APP)
    }

    override fun quit() = Win32.postQuit()
}
