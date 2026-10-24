package net.bible.sharedui.webview

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.WebKit.WKWebViewConfiguration
import kotlin.test.Test
import kotlin.test.assertFalse

@OptIn(ExperimentalForeignApi::class)
class ReadingWkWebViewScrollTest {
    /**
     * UIKit's scroll-to-top moved the iPad reading page to the top of the loaded window; infinite scroll then
     * prepended chapters and the position notifier saved the first verse of the book. The gesture cannot be
     * driven from a unit test, so this pins the opt-out from construction on (not only once attached); the
     * behavior proof is the iPad UI run of `EndToEndUITests.testRestoreKeepsLiveSettings`.
     */
    @Test
    fun readingWebViewOptsOutOfScrollToTop() {
        val webView = ReadingWkWebView(CGRectMake(0.0, 0.0, 320.0, 480.0), WKWebViewConfiguration())

        assertFalse(webView.scrollView.scrollsToTop, "scroll-to-top must not move the saved reading position")
    }
}
