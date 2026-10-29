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
     * The `window.androidBridge` shim is engine-specific: each engine prepends its own shim to the
     * engine-neutral [documentStartScripts] (console forwarding, active languages).
     */
    fun createPane(id: String, url: String, documentStartScripts: List<String>, onMessage: (String) -> Unit): DesktopWebPane

    /** The bundle's start URL for this engine, `index.html?lang=<lang>&night=<dark>` at this engine's origin. */
    fun bundleUrl(lang: String, dark: Boolean): String
}

/** One engine web view. Also the [JsHost] of its window's JS channel. */
interface DesktopWebPane : JsHost {
    val id: String

    /** Hosts the web view in Compose. May be entered, left and re-entered; it must re-parent, never recreate. */
    @Composable
    fun Content(modifier: Modifier)

    /** Releases the engine resources. Not called when leaving composition; see [DesktopBibleWebViews.disposeAll]. */
    fun dispose()
}
