package net.bible.sharedui.desktop.webengine

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.bible.sharedcore.bibleview.JsHost

/**
 * The swappable desktop web engine (Chromium via KCEF, or an OS engine such as WebKitGTK). Nothing outside
 * this package and the engine wiring in `:desktopApp` may reference engine-specific (KCEF/JCEF) types.
 */
interface DesktopWebEngine {
    /**
     * Creates a pane that loads [url] after injecting [documentStartScripts] (in order, before page scripts);
     * every `window.androidBridge.postMessage(text)` from the page reaches [onMessage] on the UI thread.
     *
     * Ordering contract: an implementation must register every script, including its own shim, to run at
     * document start before it starts loading [url].
     *
     * Shim contract: each engine defines `window.androidBridge` itself (see [androidBridgeShim]); it must
     * provide both `postMessage(text)` and `getActiveLanguages()`, because the page calls the latter whenever
     * `androidBridge` exists. [documentStartScripts] are engine-neutral extras and may be empty (the factory
     * currently passes none); the parameter stays for OS engines and later features.
     */
    fun createPane(id: String, url: String, documentStartScripts: List<String>, onMessage: (String) -> Unit): DesktopWebPane

    /** The bundle's start URL for this engine, `index.html?lang=<lang>&night=<dark>` at this engine's origin. */
    fun bundleUrl(lang: String, dark: Boolean): String

    /**
     * The installed Bibles' languages as a JSON list, answered by the bridge shim's `getActiveLanguages()` of panes
     * created afterwards, in place of the URL's `lang`. The page also honours a later `window.__activeLanguages__`
     * (see [androidBridgeShim]), so a per-load refresh needs no engine call. Engines that do not support it ignore it.
     */
    fun setActiveLanguages(source: () -> String) {}
}

/** One engine web view. Also the [JsHost] of its window's JS channel. */
interface DesktopWebPane : JsHost {
    val id: String

    /** Hosts the web view in Compose. May be entered, left and re-entered; it must re-parent, never recreate. */
    @Composable
    fun Content(modifier: Modifier)

    /** Paints the pane's background with [argb] (Swing component and page); callable from any thread. Default: ignored. */
    fun setBackground(argb: Int) {}

    /**
     * Registers [listener] for the death of the pane's renderer process (crash, kill, out of memory). The listener
     * runs on the UI thread; the pane is unusable afterwards and the owner recreates it. Default: never fires.
     */
    fun setOnRendererGone(listener: () -> Unit) {}

    /** Releases the engine resources. Not called when leaving composition; see [DesktopBibleWebViews.disposeAll]. */
    fun dispose()
}

/**
 * Engine-neutral builder for the `window.androidBridge` shim: defines `postMessage(text)` (forwarding to the
 * JS expression [postMessageJs], a function reference or expression taking the text) and
 * `getActiveLanguages()` returning the live `window.__activeLanguages__` when a script has set it (the hoisted reading
 * assembly refreshes it before each document load, as on iOS), else [activeLanguagesJson] (a JSON list string such as
 * `["en"]`).
 */
fun androidBridgeShim(postMessageJs: String, activeLanguagesJson: String): String =
    "window.androidBridge = {" +
        "postMessage: function (t) { return ($postMessageJs)(t); }, " +
        "getActiveLanguages: function () { return (typeof window.__activeLanguages__ === 'string' && window.__activeLanguages__) ? window.__activeLanguages__ : '" +
        activeLanguagesJson.replace("\\", "\\\\").replace("'", "\\'") + "'; }" +
        "};"
