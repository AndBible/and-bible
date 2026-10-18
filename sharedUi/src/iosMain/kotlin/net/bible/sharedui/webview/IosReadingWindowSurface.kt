@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.webview

import net.bible.sharedcore.bibleview.JsHost
import net.bible.sharedcore.log.nsLog
import net.bible.sharedui.reading.bridge.LogOnce
import net.bible.sharedui.reading.bridge.ReadingWindowSurface
import platform.UIKit.UIPasteboard

/**
 * iOS per-window actions of a reading view. Copy and selection work; sharing, toasts as UI and web search
 * are pending (Stream D owns `IosPresenter`): they log "pending: <name>" once and do nothing else.
 */
@kotlin.native.HiddenFromObjC
class IosReadingWindowSurface(private val host: JsHost) : ReadingWindowSurface {
    private val pending = LogOnce { nsLog(it) }

    override fun copyText(label: String?, text: String) {
        host.post { UIPasteboard.generalPasteboard.string = text }
    }

    override fun shareText(text: String, subject: String?) {
        pending.pending("shareText")
    }

    override fun shareHtml(html: String, title: String) {
        pending.pending("shareHtml")
    }

    override fun stopSelection(removeRanges: Boolean) {
        host.evaluate("window.getSelection().removeAllRanges()", null)
    }

    override fun toast(text: String) {
        nsLog("toast: $text")
        pending.pending("toast")
    }

    override fun openWebSearch(query: String) {
        pending.pending("openWebSearch")
    }
}
