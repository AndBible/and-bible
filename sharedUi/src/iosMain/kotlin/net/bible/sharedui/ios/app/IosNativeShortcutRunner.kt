package net.bible.sharedui.ios.app

import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.activity.page.SelectionMenuItem
import net.bible.sharedui.host.app.HostRootLinks
import net.bible.sharedui.host.reading.HostReadingWindows
import net.bible.sharedui.host.reading.HostNativeShortcutTarget
import net.bible.sharedui.host.reading.dispatchNativeShortcut
import net.bible.sharedui.reading.NativeShortcut
import net.bible.sharedui.webview.IosReadingWebViews
import net.bible.sharedui.webview.ReadingWkWebView
import org.koin.mp.KoinPlatform

/** Runs the native reading shortcuts of #4177 with the actions the desktop shares ([HostShortcutActions]). */
internal object IosNativeShortcutRunner {
    private const val SELECTION_SCRIPT =
        "(function(){try{var s=window.bibleView&&window.bibleView.querySelection?window.bibleView.querySelection():null;" +
            "var g=window.getSelection?window.getSelection():null;" +
            "return s!=null&&g&&!g.isCollapsed?JSON.stringify(s):'';}catch(e){return '';}})()"

    /** Returns false (key not consumed) when the reading screen is not current. */
    fun run(shortcut: NativeShortcut): Boolean {
        if (!HostRootLinks.isOnReadingScreen()) return false
        return dispatchNativeShortcut(shortcut, target)
    }

    private val target = HostNativeShortcutTarget(::bookmarkSelectionOrChooser)

    private fun bookmarkSelectionOrChooser(): Boolean {
        val id = KoinPlatform.getKoin().get<WindowControl>().activeWindow.id.toString()
        val webView = IosReadingWebViews.webViewFor(id) as? ReadingWkWebView ?: return false
        webView.evaluateJavaScript(SELECTION_SCRIPT) { result, _ ->
            val json = result as? String
            val menu = HostReadingWindows.selectionMenu
            if (!json.isNullOrEmpty() && menu != null) {
                menu.cacheSelection(id, json)
                menu.run(id, SelectionMenuItem.AddBookmarkSelection)
            } else {
                webView.deliverReadingShortcut("KeyB", ctrl = true, alt = false)
            }
        }
        return true
    }
}
