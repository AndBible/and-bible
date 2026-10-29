package net.bible.sharedui.desktop.webengine

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.testTag
import net.bible.sharedcore.bibleview.BibleViewEvents
import net.bible.sharedcore.bibleview.BridgeDispatcher
import net.bible.sharedcore.bibleview.JsCall
import net.bible.sharedui.poc.LocalPocPaneCreated
import net.bible.sharedui.poc.PocBibleViewController
import net.bible.sharedui.webview.BibleWebViewFactory

/** Engine-neutral: sets `window.__activeLanguages__` (as the iOS host does) to [json]. */
internal fun activeLanguagesScript(json: String): String =
    "window.__activeLanguages__ = '" + json.replace("\\", "\\\\").replace("'", "\\'") + "';"

/**
 * Engine-neutral console forwarder: reports console.* and page errors as bridge `console` calls through
 * `window.androidBridge` (the engine's own shim, which it injects before this script).
 */
internal const val CONSOLE_FORWARD_SCRIPT = """
(function () {
  if (window.__abConsole) return; window.__abConsole = true;
  function send(level, args) {
    try {
      var t = Array.prototype.map.call(args, function (x) {
        try { return typeof x === 'string' ? x : (x instanceof Error ? (x.stack || x.message) : JSON.stringify(x)); }
        catch (e) { return String(x); }
      }).join(' ');
      window.androidBridge.postMessage(JSON.stringify({method: 'console', args: [level, t.slice(0, 2000)]}));
    } catch (e) {}
  }
  ['log', 'info', 'debug', 'warn', 'error'].forEach(function (l) {
    var o = console[l];
    console[l] = function () { send(l, arguments); if (o) o.apply(console, arguments); };
  });
  window.addEventListener('error', function (e) { send('onerror', [e.message + ' @' + e.filename + ':' + e.lineno + ':' + e.colno]); });
  window.addEventListener('unhandledrejection', function (e) { send('unhandledrejection', [(e.reason && e.reason.stack) || String(e.reason)]); });
})();
"""

/**
 * The desktop analogue of iOS's `IosWebViewHolder` + `IosBibleWebViewFactory`: a per-window-id pane cache
 * owned by this instance (a pane is created once and re-parented on recomposition, never recreated), and the
 * [factory] that draws a pane. Panes are not disposed when they leave composition; whoever owns this
 * instance calls [disposeAll] (or [dispose] for a window closed for good).
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
        lateinit var pane: DesktopWebPane
        val sink: (JsCall) -> Unit = { call -> pane.post { pane.evaluate(call.js, null) } }
        val paneLog: (String) -> Unit = { msg -> log("bridge[$windowId]: $msg") }
        val dispatcher = BridgeDispatcher(controller.bridge(sink, paneLog), { callId -> sink(BibleViewEvents.response(callId, null)) }, paneLog)
        val scripts = listOf(CONSOLE_FORWARD_SCRIPT, activeLanguagesScript("[\"en\"]"))
        pane = engine.createPane(windowId, engine.bundleUrl("en", controller.darkTheme), scripts) { text ->
            try {
                dispatcher.dispatch(text)
            } catch (t: Throwable) {
                paneLog("handler failure $t")
            }
        }
        return pane
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
