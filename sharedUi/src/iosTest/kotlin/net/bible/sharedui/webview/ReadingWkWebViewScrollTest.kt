package net.bible.sharedui.webview

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.WebKit.WKWebViewConfiguration
import kotlin.test.Test
import kotlin.test.assertFalse

@OptIn(ExperimentalForeignApi::class)
class ReadingWkWebViewScrollTest {
    /**
     * A status-bar tap on iPad scrolls a `scrollsToTop` scroll view to the top of the loaded window; infinite
     * scroll then prepends chapters and the position notifier saves the first verse of the book as the reading
     * position. The reading WebView must therefore opt out from construction on (not only once attached).
     */
    @Test
    fun readingWebViewDoesNotScrollToTopOnStatusBarTap() {
        val webView = ReadingWkWebView(CGRectMake(0.0, 0.0, 320.0, 480.0), WKWebViewConfiguration())

        assertFalse(webView.scrollView.scrollsToTop, "a status-bar tap must not move the saved reading position")
    }
}
