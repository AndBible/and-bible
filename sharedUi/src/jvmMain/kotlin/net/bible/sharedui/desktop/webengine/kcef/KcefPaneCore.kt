/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.sharedui.desktop.webengine.kcef

import dev.datlag.kcef.KCEF
import dev.datlag.kcef.KCEFBrowser
import dev.datlag.kcef.KCEFClient
import net.bible.sharedui.desktop.webengine.DesktopWebPane
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
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * What both pane kinds ([KcefPane] windowed, [KcefOsrPane] off-screen) share: the CEF client with the bridge message router
 * and its trust check, the bundle request handler, popup blocking, context-menu suppression, evaluation, the page background
 * and renderer-gone handling, and the disposal of client and router. Hosting, input and focus stay with the pane.
 *
 * Call [createBrowser] once; the rest is usable afterwards.
 *
 * @param onEditableFocus receives the page's "an editable element has focus" flag (see [PageEditableFocus]); false again when a new main document starts loading
 * @param onBrowserCreated runs on CEF's UI thread when the native browser exists (`onAfterCreated`); settings that need the
 *   native browser, such as the windowless frame rate, belong there
 * @param requestFocus moves keyboard focus into the pane when the page reports a press (see [PaneFocusRequest]); runs on the UI thread
 */
internal class KcefPaneCore(
    private val url: String,
    bundle: BundleRequestHandler,
    private val onMessage: (String) -> Unit,
    private val log: (String) -> Unit,
    private val requestFocus: () -> Unit,
    private val onEditableFocus: (Boolean) -> Unit = {},
    private val onBrowserCreated: (CefBrowser) -> Unit = {},
) {
    @Volatile
    private var disposed = false

    @Volatile
    private var backgroundArgb: Int? = null

    @Volatile
    private var onRendererGone: (() -> Unit)? = null
    private val evals = EvalReplies()

    /** The pane's first focus press is logged once (see [PaneFocusRequest.handle]). */
    private val focusPressLogged = java.util.concurrent.atomic.AtomicBoolean(false)

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
                // invokeLater: after AWT's own focus handling of the press.
                SwingUtilities.invokeLater {
                    if (disposed) return@invokeLater
                    val editable = PageEditableFocus.parse(text)
                    if (editable != null) onEditableFocus(editable)
                    else if (!PaneFocusRequest.handle(text, requestFocus, log, focusPressLogged) && !evals.consume(text)) onMessage(text)
                }
                return true
            }
        },
    )

    /** The pane's browser; set by [createBrowser]. */
    lateinit var browser: KCEFBrowser
        private set

    val isDisposed: Boolean get() = disposed

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
                if (frame?.isMain == true) SwingUtilities.invokeLater { evals.failAll(); if (!disposed) onEditableFocus(false) }
            }

            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                // The page's own style is gone after a (re)load: paint it again.
                if (frame?.isMain == true) backgroundArgb?.let { applyPageBackground(it) }
            }
        })
        client.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
            override fun onAfterCreated(browser: CefBrowser?) {
                if (browser != null) onBrowserCreated(browser)
            }

            override fun onBeforePopup(browser: CefBrowser?, frame: CefFrame?, targetUrl: String?, targetFrameName: String?): Boolean {
                log("blocked a popup to $targetUrl")
                return true
            }
        })
    }

    /** Creates the browser with [rendering] and starts loading the pane's URL. */
    fun createBrowser(rendering: CefRendering): KCEFBrowser {
        browser = client.createBrowser(url, rendering, false)
        return browser
    }

    /**
     * Remembers [argb] as the page background and applies it (again after every load). [onUiThread] runs first, on the UI
     * thread, for the pane's own part (e.g. the Swing component background).
     */
    fun setBackground(argb: Int, onUiThread: () -> Unit = {}) {
        backgroundArgb = argb
        SwingUtilities.invokeLater {
            if (disposed) return@invokeLater
            onUiThread()
            applyPageBackground(argb)
        }
    }

    fun setOnRendererGone(listener: () -> Unit) {
        onRendererGone = listener
    }

    /** BibleView-JS paints no page background of its own, so the root element gets the host colour (as \`setBackgroundColor\` on Android). */
    private fun applyPageBackground(argb: Int) {
        if (disposed) return
        val css = "rgba(${(argb shr 16) and 0xFF},${(argb shr 8) and 0xFF},${argb and 0xFF},${((argb shr 24) and 0xFF) / 255.0})"
        SwingUtilities.invokeLater {
            if (!disposed) browser.executeJavaScript("document.documentElement.style.background='$css';", browser.url ?: url, 0)
        }
    }

    /** See [DesktopWebPane.evaluate]; the callback is answered exactly once. */
    fun evaluate(js: String, callback: ((String) -> Unit)?) {
        if (disposed) {
            callback?.invoke(EvalReplies.NO_RESULT)
            return
        }
        if (!SwingUtilities.isEventDispatchThread()) {
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

    /** Fails pending evaluations, closes the browser and releases client and router. Idempotent. */
    fun dispose() {
        if (disposed) return
        disposed = true
        // dispose() runs on the UI thread (DesktopBibleWebViews' owner), where the eval registry lives.
        if (SwingUtilities.isEventDispatchThread()) evals.failAll() else SwingUtilities.invokeLater { evals.failAll() }
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
internal class BundleCefRequestHandler(
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

internal class BytesResourceHandler(
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
