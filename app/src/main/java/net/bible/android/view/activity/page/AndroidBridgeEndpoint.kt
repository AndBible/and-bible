package net.bible.android.view.activity.page

import android.webkit.JavascriptInterface
import net.bible.sharedcore.bibleview.BridgeDispatcher
import net.bible.sharedcore.log.Log

/**
 * The only native object exposed to BibleView, as `window.androidBridge`. Both entry points run
 * synchronously on WebView's JavaBridge thread, just like the handlers they replace. Neither
 * provider, callback nor logging failures may escape this platform boundary.
 */
class AndroidBridgeEndpoint(
    private val dispatcher: BridgeDispatcher,
    private val activeLanguages: () -> String,
) {
    @JavascriptInterface
    fun postMessage(json: String) {
        try {
            dispatcher.dispatch(json)
        } catch (error: Throwable) {
            logFailure("postMessage failed", error)
        }
    }

    @JavascriptInterface
    fun getActiveLanguages(): String = try {
        activeLanguages()
    } catch (error: Throwable) {
        logFailure("getActiveLanguages failed", error)
        "[\"en\"]"
    }

    private fun logFailure(message: String, error: Throwable) {
        runCatching { Log.e("AndroidBridgeEndpoint", message, error) }
    }
}
