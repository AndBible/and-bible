package net.bible.sharedui.desktop.webengine

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.testTag
import net.bible.sharedcore.bibleview.BibleViewEvents
import net.bible.sharedcore.bibleview.BridgeDispatcher
import net.bible.sharedcore.bibleview.JsCall
import net.bible.sharedui.poc.LocalPocPaneCreated
import net.bible.sharedui.poc.PocBibleViewController
import net.bible.sharedui.webview.BibleWebViewFactory

/**
 * The desktop analogue of iOS's `IosWebViewHolder` + `IosBibleWebViewFactory`: a per-window-id pane cache
 * owned by this instance (a pane is created once and re-parented on recomposition, never recreated), and the
 * [factory] that draws a pane. Panes are not disposed when they leave composition; whoever owns this
 * instance calls [disposeAll] (or [dispose] for a window closed for good).
 *
 * The cache keeps the first controller per window id (as iOS does); a new controller for the same id must
 * go through [dispose] first.
 */
class DesktopBibleWebViews(private val engine: DesktopWebEngine, private val log: (String) -> Unit) {
    private val panes = mutableMapOf<String, DesktopWebPane>()

    val factory: BibleWebViewFactory = { controller, modifier ->
        val pane = obtain(controller)
        val onCreated = LocalPocPaneCreated.current
        DisposableEffect(controller.windowId) {
            onCreated(controller.windowId)
            onDispose {}
        }
        pane.Content(modifier.testTag("pane-${controller.windowId}"))
    }

    private fun obtain(controller: PocBibleViewController): DesktopWebPane =
        panes.getOrPut(controller.windowId) { create(controller) }

    private fun create(controller: PocBibleViewController): DesktopWebPane {
        val windowId = controller.windowId
        var pane: DesktopWebPane? = null
        // A message delivered while createPane runs (before `pane` is assigned) queues its JS until then.
        val pending = mutableListOf<String>()
        val sink: (JsCall) -> Unit = { call ->
            val p = pane
            if (p == null) pending += call.js else p.post { p.evaluate(call.js, null) }
        }
        val paneLog: (String) -> Unit = { msg -> log("bridge[$windowId]: $msg") }
        val dispatcher = BridgeDispatcher(controller.bridge(sink, paneLog), { callId -> sink(BibleViewEvents.response(callId, null)) }, paneLog)
        // No neutral document-start scripts: console and error forwarding live in the bundle (main.ts, native-bridge.ts)
        // and the engine's shim carries the active languages.
        val created = engine.createPane(windowId, engine.bundleUrl("en", controller.darkTheme), emptyList()) { text ->
            try {
                dispatcher.dispatch(text)
            } catch (t: Throwable) {
                paneLog("handler failure $t")
            }
        }
        pane = created
        pending.forEach { js -> created.post { created.evaluate(js, null) } }
        pending.clear()
        return created
    }

    /** Disposes and forgets the pane of a window closed for good. */
    fun dispose(windowId: String) {
        panes.remove(windowId)?.dispose()
    }

    fun disposeAll() {
        panes.keys.toList().forEach(::dispose)
    }
}

/** Convenience for callers that never dispose panes individually (D1: panes live as long as the process). */
fun desktopBibleWebViewFactory(engine: DesktopWebEngine, log: (String) -> Unit): BibleWebViewFactory =
    DesktopBibleWebViews(engine, log).factory
