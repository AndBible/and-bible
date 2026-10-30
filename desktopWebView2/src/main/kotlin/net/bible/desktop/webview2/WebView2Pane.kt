package net.bible.desktop.webview2

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.WinDef
import kotlinx.coroutines.CoroutineDispatcher
import net.bible.sharedui.desktop.webengine.DesktopWebPane
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.util.Timer
import java.util.TimerTask
import javax.swing.SwingUtilities
import kotlin.math.roundToInt

/**
 * One WebView2 pane in composition (visual hosting) mode.
 *
 * - Hosting: the page renders into a DirectComposition visual on a child window ("host") of the Compose frame, kept at the
 *   bottom of the frame's z-order, below Compose's own canvas; [Content] clears its area of that canvas, so the page shows
 *   through and every Compose overlay drawn above it covers it. While the pane is not in a Compose window the host lives in
 *   a hidden parking window: re-entering composition re-parents it, never recreates it.
 * - Input: the Compose canvas above receives the mouse; [Content] forwards it with `SendMouseInput`. A press moves Win32
 *   focus into WebView2 (`MoveFocus`), whose own input window then takes the keyboard and IME directly.
 * - All WebView2 calls run on the engine's [StaThread].
 */
internal class WebView2Pane(
    override val id: String,
    private val url: String,
    private val runtime: WebView2Engine.Runtime,
    private val router: Wv2ResourceRouter,
    private val onMessage: (String) -> Unit,
    private val log: (String) -> Unit,
    private val hostWindow: () -> java.awt.Window?,
) : DesktopWebPane {
    private val sta = runtime.sta

    @Volatile
    private var disposed = false

    // STA-thread state.
    private var host: WinDef.HWND? = null
    private var parking: WinDef.HWND? = null
    private var dcomp: ComPtr? = null
    private var target: ComPtr? = null
    private var visual: ComPtr? = null
    private var composition: ComPtr? = null
    private var controller: ComPtr? = null
    private var webView: ComPtr? = null
    private val whenReady = ArrayList<(ComPtr) -> Unit>()
    private var lastBounds = Bounds(0, 0, 1, 1)
    private var attachedTo: WinDef.HWND? = null
    private var backgroundArgb: Int? = null

    @Volatile
    private var rendererGone: (() -> Unit)? = null
    private var cursor by mutableStateOf<PointerIcon>(PointerIcon.Default)

    private val replies = EvalReplies(
        deliver = { SwingUtilities.invokeLater(it) },
        scheduleTimeout = { delay, task -> evalTimer.schedule(object : TimerTask() { override fun run() = task.run() }, delay) },
    )

    /** The Compose density, applied as the rasterization scale (also when the controller is created later). */
    @Volatile
    private var scale = 1.0

    private data class Bounds(val x: Int, val y: Int, val w: Int, val h: Int)

    init {
        backgroundArgb = if (url.contains("night=true")) NIGHT_BACKGROUND else null
        sta.post { guarded("create") { create() } }
    }

    /**
     * Runs a creation step; a failure leaves the pane unusable, so it is reported like a renderer loss (the owner recreates
     * the pane) and pending evaluates are answered, instead of a blank pane that only logs.
     */
    private fun guarded(what: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            failCreation("$what failed: $t")
        }
    }

    private fun failCreation(reason: String) {
        log(reason)
        whenReady.clear()
        replies.failAll()
        rendererGone?.let { l -> SwingUtilities.invokeLater { if (!disposed) l() } }
    }

    private fun create() {
        val park = Win32.createParkingWindow().also { parking = it }
        val h = Win32.createHostWindow(park).also { host = it }
        val device = Win32.createDcompDevice().also { dcomp = it }
        val t = device.getPtr(Wv2.Dcomp.CreateTargetForHwnd, h.pointer, 1) ?: error("CreateTargetForHwnd failed")
        target = t
        val v = device.getPtr(Wv2.Dcomp.CreateVisual) ?: error("CreateVisual failed")
        visual = v
        t.check(Wv2.Dcomp.Target_SetRoot, "IDCompositionTarget::SetRoot", v.ptr)
        device.check(Wv2.Dcomp.Commit, "IDCompositionDevice::Commit")
        val env3 = runtime.environment.query(Wv2.Iids.Environment3) ?: error("ICoreWebView2Environment3 missing")
        try {
            ComCallback(Wv2.Iids.CreateCompositionControllerCompleted) { hr, ptr ->
                val code = ComCallback.hresult(hr)
                if (code < 0 || ptr == null) {
                    failCreation("composition controller failed: ${hresultString(code)}")
                } else if (disposed) {
                    ComPtr(ptr).query(Wv2.Iids.Controller2)?.let { it.call(Wv2.Controller.Close); it.release() }
                } else {
                    guarded("controller setup") { onControllerCreated(ComPtr(ptr).also { it.addRef() }) }
                }
                S_OK
            }.use { handler -> env3.check(Wv2.Env.CreateCoreWebView2CompositionController, "CreateCoreWebView2CompositionController", h.pointer, handler) }
        } finally {
            env3.release()
        }
    }

    private fun onControllerCreated(comp: ComPtr) {
        composition = comp
        comp.check(Wv2.Composition.put_RootVisualTarget, "put_RootVisualTarget", visual!!.ptr)
        dcomp!!.check(Wv2.Dcomp.Commit, "IDCompositionDevice::Commit")
        // Runtime >= RuntimeCheck.MIN_MAJOR always has Controller3 (the rasterization scale lives there).
        val ctl = comp.query(Wv2.Iids.Controller3) ?: error("ICoreWebView2Controller3 missing")
        controller = ctl
        // The scale is Compose's density, not the monitor's as WebView2 would track it on its own.
        ctl.call(Wv2.Controller.put_ShouldDetectMonitorScaleChanges, 0)
        ctl.call(Wv2.Controller.put_RasterizationScale, scale)
        backgroundArgb?.let { applyBackground(ctl, it) }
        applyBounds()
        // Created while the host was parked (hidden): the page starts hidden. attach() may have run before the controller
        // existed, so apply the current state now.
        ctl.call(Wv2.Controller.put_IsVisible, if (attachedTo != null) 1 else 0)
        if (attachedTo != null) ctl.call(Wv2.Controller.NotifyParentWindowPositionChanged)
        val wv = ctl.getPtr(Wv2.Controller.get_CoreWebView2) ?: error("get_CoreWebView2 failed")
        webView = wv
        configureSettings(wv)
        register(wv, comp)
        wv.check(Wv2.WebView.AddWebResourceRequestedFilter, "AddWebResourceRequestedFilter", WString(Wv2ResourceRouter.FILTER), Wv2.RESOURCE_CONTEXT_ALL)
        wv.check(Wv2.WebView.Navigate, "Navigate", WString(url))
        log("created (runtime ${runtime.version}), browser pid ${wv.getInt(Wv2.WebView.get_BrowserProcessId)}")
        val queued = whenReady.toList()
        whenReady.clear()
        queued.forEach { it(wv) }
    }

    private fun configureSettings(wv: ComPtr) {
        val settings = wv.getPtr(Wv2.WebView.get_Settings) ?: return
        try {
            // The page has its own selection menu; zoom is the app's (Ctrl+wheel); no Edge chrome or dialogs.
            settings.call(Wv2.Settings.put_AreDefaultContextMenusEnabled, 0)
            settings.call(Wv2.Settings.put_IsZoomControlEnabled, 0)
            settings.call(Wv2.Settings.put_IsStatusBarEnabled, 0)
            settings.call(Wv2.Settings.put_AreDefaultScriptDialogsEnabled, 0)
            settings.call(Wv2.Settings.put_AreHostObjectsAllowed, 0)
            settings.call(Wv2.Settings.put_AreDevToolsEnabled, if (System.getProperty("andbible.webview2.devtools") == "true") 1 else 0)
            settings.query(Wv2.Iids.Settings3)?.let {
                // F5, Ctrl+F, Ctrl+P etc. belong to the page script and the app, not to Edge.
                it.call(Wv2.Settings.put_AreBrowserAcceleratorKeysEnabled, 0)
                it.release()
            }
        } finally {
            settings.release()
        }
    }

    private fun register(wv: ComPtr, comp: ComPtr) {
        val token = Memory(8)
        ComCallback(Wv2.Iids.WebMessageReceived) { _, args ->
            val a = ComPtr(args!!)
            val source = a.getString(Wv2.WebMessageArgs.get_Source)
            if (!Wv2Trust.acceptMessage(source)) {
                log("message from $source refused")
            } else {
                a.getString(Wv2.WebMessageArgs.TryGetWebMessageAsString)?.let { text ->
                    SwingUtilities.invokeLater { if (!disposed) onMessage(text) }
                }
            }
            S_OK
        }.use { wv.check(Wv2.WebView.add_WebMessageReceived, "add_WebMessageReceived", it, token) }

        ComCallback(Wv2.Iids.NavigationStarting) { _, args ->
            val a = ComPtr(args!!)
            val uri = a.getString(Wv2.NavigationStartingArgs.get_Uri)
            if (!Wv2Trust.allowNavigation(uri)) {
                a.call(Wv2.NavigationStartingArgs.put_Cancel, 1)
                log("navigation to $uri blocked")
            } else {
                // A new document: evaluate calls of the old one are never answered by it.
                replies.failAll()
            }
            S_OK
        }.use { wv.check(Wv2.WebView.add_NavigationStarting, "add_NavigationStarting", it, token) }

        ComCallback(Wv2.Iids.NewWindowRequested) { _, args ->
            val a = ComPtr(args!!)
            log("popup to ${a.getString(Wv2.NewWindowArgs.get_Uri)} blocked")
            a.call(Wv2.NewWindowArgs.put_Handled, 1)
            S_OK
        }.use { wv.check(Wv2.WebView.add_NewWindowRequested, "add_NewWindowRequested", it, token) }

        ComCallback(Wv2.Iids.WebResourceRequested) { _, args -> serve(ComPtr(args!!)); S_OK }
            .use { wv.check(Wv2.WebView.add_WebResourceRequested, "add_WebResourceRequested", it, token) }

        ComCallback(Wv2.Iids.ProcessFailed) { _, args ->
            val kind = ComPtr(args!!).getInt(Wv2.ProcessFailedArgs.get_ProcessFailedKind)
            log("process failed, kind $kind")
            if (kind == Wv2.PROCESS_FAILED_RENDER_EXITED || kind == Wv2.PROCESS_FAILED_BROWSER_EXITED) {
                replies.failAll()
                rendererGone?.let { l -> SwingUtilities.invokeLater { if (!disposed) l() } }
            }
            S_OK
        }.use { wv.check(Wv2.WebView.add_ProcessFailed, "add_ProcessFailed", it, token) }

        ComCallback(Wv2.Iids.CursorChanged) { _, _ ->
            val id = comp.getInt(Wv2.Composition.get_SystemCursorId) ?: 0
            val icon = when (id) {
                IDC_IBEAM -> PointerIcon.Text
                IDC_HAND -> PointerIcon.Hand
                IDC_CROSS -> PointerIcon.Crosshair
                else -> PointerIcon.Default
            }
            SwingUtilities.invokeLater { cursor = icon }
            S_OK
        }.use { comp.check(Wv2.Composition.add_CursorChanged, "add_CursorChanged", it, token) }
    }

    /**
     * Answers a resource request with a deferral: the file is read on a worker thread (a slow disk must not stall the STA
     * thread, nor through the attached input queues the AWT thread), the response is built and completed on the STA thread.
     */
    private fun serve(args: ComPtr) {
        val request = args.getPtr(Wv2.ResourceArgs.get_Request) ?: return
        val uri = try { request.getString(Wv2.Request.get_Uri) } finally { request.release() } ?: return
        val deferral = args.getPtr(Wv2.ResourceArgs.GetDeferral) ?: return
        args.addRef()
        resourceReader.execute {
            val response = try { router.respond(uri) } catch (t: Throwable) { log("serving $uri failed: $t"); null }
            sta.post {
                try {
                    if (response != null) {
                        val stream = Win32.memStream(response.body)
                        try {
                            runtime.environment.getPtr(
                                Wv2.Env.CreateWebResourceResponse, stream.ptr, response.status, WString(response.reason), WString(response.headers),
                            )?.let { out -> try { args.call(Wv2.ResourceArgs.put_Response, out.ptr) } finally { out.release() } }
                        } finally {
                            stream.release()
                        }
                    }
                } finally {
                    deferral.call(Wv2.Deferral.Complete)
                    deferral.release()
                    args.release()
                }
            }
        }
    }

    private fun applyBackground(ctl: ComPtr, argb: Int) {
        // COREWEBVIEW2_COLOR is {A, R, G, B} bytes, passed by value in one register.
        val a = 0xFF
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        ctl.call(Wv2.Controller.put_DefaultBackgroundColor, a or (r shl 8) or (g shl 16) or (b shl 24))
    }

    private fun applyBounds() {
        val ctl = controller ?: return
        val rect = Memory(16).apply {
            setInt(0, 0); setInt(4, 0); setInt(8, lastBounds.w); setInt(12, lastBounds.h)
        }
        ctl.call(Wv2.Controller.put_Bounds, rect)
    }

    // ---- Compose side ----

    @Composable
    override fun Content(modifier: Modifier) {
        val window = hostWindow()
        val density = LocalDensity.current.density
        DisposableEffect(window) {
            val frame = window?.let { WinDef.HWND(Native.getComponentPointer(it)) }
            if (frame != null) sta.post { attach(frame) }
            // A moved frame changes no Compose layout, but WebView2 needs it for IME candidate and accessibility positions.
            val moves = object : java.awt.event.ComponentAdapter() {
                override fun componentMoved(e: java.awt.event.ComponentEvent?) {
                    sta.post { controller?.call(Wv2.Controller.NotifyParentWindowPositionChanged) }
                }
            }
            window?.addComponentListener(moves)
            onDispose {
                window?.removeComponentListener(moves)
                sta.post { detach() }
            }
        }
        DisposableEffect(density) {
            scale = density.toDouble()
            sta.post { controller?.call(Wv2.Controller.put_RasterizationScale, scale) }
            onDispose { }
        }
        Box(
            modifier
                .onGloballyPositioned { c ->
                    val p = c.positionInWindow()
                    val b = Bounds(p.x.roundToInt(), p.y.roundToInt(), c.size.width, c.size.height)
                    sta.post { place(b) }
                }
                // Punch a hole in the Compose canvas: the page's visual below shows through. Anything Compose draws later
                // (drawers, sheets, menus) lands above it.
                .drawBehind { drawRect(Color.Black, blendMode = BlendMode.Clear) }
                .pointerHoverIcon(cursor)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            val x = change.position.x.roundToInt()
                            val y = change.position.y.roundToInt()
                            val awt = event.awtEventOrNull
                            var keys = 0
                            if (event.buttons.isPrimaryPressed) keys = keys or Wv2.Mouse.VK_LEFT
                            if (event.buttons.isSecondaryPressed) keys = keys or Wv2.Mouse.VK_RIGHT
                            if (event.buttons.isTertiaryPressed) keys = keys or Wv2.Mouse.VK_MIDDLE
                            if (event.keyboardModifiers.isShiftPressed) keys = keys or Wv2.Mouse.VK_SHIFT
                            if (event.keyboardModifiers.isCtrlPressed) keys = keys or Wv2.Mouse.VK_CONTROL
                            val button = (awt as? MouseEvent)?.button ?: MouseEvent.NOBUTTON
                            val clicks = (awt as? MouseEvent)?.clickCount ?: 1
                            val kind: Int? = when (event.type) {
                                PointerEventType.Move, PointerEventType.Enter -> Wv2.Mouse.MOVE
                                PointerEventType.Exit -> Wv2.Mouse.LEAVE
                                PointerEventType.Press -> when (button) {
                                    MouseEvent.BUTTON1 -> if (clicks >= 2 && clicks % 2 == 0) Wv2.Mouse.LEFT_DOUBLE_CLICK else Wv2.Mouse.LEFT_DOWN
                                    MouseEvent.BUTTON2 -> Wv2.Mouse.MIDDLE_DOWN
                                    MouseEvent.BUTTON3 -> Wv2.Mouse.RIGHT_DOWN
                                    else -> null // back/forward: the app's toolkit-wide listener navigates
                                }
                                PointerEventType.Release -> when (button) {
                                    MouseEvent.BUTTON1 -> Wv2.Mouse.LEFT_UP
                                    MouseEvent.BUTTON2 -> Wv2.Mouse.MIDDLE_UP
                                    MouseEvent.BUTTON3 -> Wv2.Mouse.RIGHT_UP
                                    else -> null
                                }
                                PointerEventType.Scroll -> null
                                else -> null
                            }
                            if (event.type == PointerEventType.Scroll) {
                                val wheel = awt as? MouseWheelEvent
                                val notches = wheel?.preciseWheelRotation ?: change.scrollDelta.y.toDouble()
                                val horizontal = wheel?.isShiftDown == true
                                val data = (-notches * WHEEL_DELTA).roundToInt()
                                sta.post { sendMouse(if (horizontal) Wv2.Mouse.HORIZONTAL_WHEEL else Wv2.Mouse.WHEEL, keys, data, x, y) }
                                change.consume()
                                continue
                            }
                            if (kind != null) {
                                if (event.type == PointerEventType.Press) sta.post { focusPage() }
                                sta.post { sendMouse(kind, keys, 0, x, y) }
                                change.consume()
                            }
                        }
                    }
                },
        )
    }

    private fun sendMouse(kind: Int, keys: Int, data: Int, x: Int, y: Int) {
        val comp = composition ?: return
        // POINT is 8 bytes, passed by value in one register: x in the low half.
        val point = (x.toLong() and 0xFFFFFFFFL) or (y.toLong() shl 32)
        comp.call(Wv2.Composition.SendMouseInput, kind, keys, data, point)
    }

    private fun focusPage() {
        controller?.call(Wv2.Controller.MoveFocus, Wv2.MOVE_FOCUS_PROGRAMMATIC)
    }

    private fun attach(frame: WinDef.HWND) {
        val h = host ?: return
        attachedTo = frame
        Win32.setParent(h, frame)
        place(lastBounds, force = true)
        controller?.call(Wv2.Controller.put_IsVisible, 1)
        controller?.call(Wv2.Controller.NotifyParentWindowPositionChanged)
    }

    private fun detach() {
        val h = host ?: return
        controller?.call(Wv2.Controller.put_IsVisible, 0)
        attachedTo = null
        parking?.let { Win32.setParent(h, it) }
    }

    private fun place(b: Bounds, force: Boolean = false) {
        if (!force && b == lastBounds) return
        lastBounds = b
        val h = host ?: return
        Win32.placeAtBottom(h, b.x, b.y, b.w, b.h, visible = attachedTo != null)
        applyBounds()
    }

    // ---- DesktopWebPane / JsHost ----

    override fun setBackground(argb: Int) {
        sta.post {
            backgroundArgb = argb
            controller?.let { applyBackground(it, argb) }
        }
    }

    override fun setOnRendererGone(listener: () -> Unit) {
        rendererGone = listener
    }

    override val isMainThread: Boolean get() = SwingUtilities.isEventDispatchThread()
    override val isAttached: Boolean get() = !disposed
    override val mainDispatcher: CoroutineDispatcher = SwingDispatcher

    override fun post(block: () -> Unit) = SwingUtilities.invokeLater { if (!disposed) block() }

    override fun evaluate(js: String, callback: ((String) -> Unit)?) {
        if (disposed) {
            callback?.let { cb -> SwingUtilities.invokeLater { cb(EvalReplies.NO_RESULT) } }
            return
        }
        val id = callback?.let(replies::register)
        sta.post {
            val run = { wv: ComPtr ->
                ComCallback(Wv2.Iids.ExecuteScriptCompleted) { hr, json ->
                    id?.let { replies.complete(it, ComCallback.hresult(hr), json?.getWideString(0)) }
                    S_OK
                }.use { handler ->
                    val hr = wv.call(Wv2.WebView.ExecuteScript, WString(js), handler)
                    if (hr < 0) id?.let { replies.complete(it, hr, null) }
                }
            }
            val wv = webView
            if (wv != null) run(wv) else if (!disposed) whenReady.add(run)
        }
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        replies.close()
        sta.post {
            whenReady.clear()
            controller?.call(Wv2.Controller.Close)
            listOf(webView, controller, composition, visual, target, dcomp).forEach { it?.release() }
            webView = null; controller = null; composition = null; visual = null; target = null; dcomp = null
            host?.let { Win32.destroyWindow(it) }
            parking?.let { Win32.destroyWindow(it) }
            host = null; parking = null
        }
    }

    /** The browser process id (for measurements), or null before creation. */
    fun browserProcessId(): Int? = runCatching { sta.call { webView?.getInt(Wv2.WebView.get_BrowserProcessId) } }.getOrNull()

    private companion object {
        /** Evaluate timeouts of all panes; never cancelled (a pane's dispose answers its own pending calls). */
        val evalTimer = Timer("webview2-eval", true)

        /** Reads bundle files and page assets off the STA thread. */
        val resourceReader: java.util.concurrent.ExecutorService = java.util.concurrent.Executors.newFixedThreadPool(2) { r ->
            Thread(r, "webview2-resources").apply { isDaemon = true }
        }

        const val WHEEL_DELTA = 120
        const val IDC_IBEAM = 32513
        const val IDC_HAND = 32649
        const val IDC_CROSS = 32515

        /** The page's night background before its CSS loads (as the D5 engine). */
        const val NIGHT_BACKGROUND = 0x000000

        @Suppress("unused")
        private fun ptr(p: Pointer?): Long = Pointer.nativeValue(p)
    }
}
