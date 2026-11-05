@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.webview

import platform.CoreGraphics.CGRectMake
import platform.UIKit.UITextField
import platform.UIKit.UIView
import platform.WebKit.WKWebViewConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Issue #3968: WebKit presents no edit menu for a long-press selection when the web view's content view only becomes
 * first responder during that selection gesture (the first selection after launch or after a route return, while the
 * window has no first responder). The reading pane therefore claims first responder for the web view once its route is resumed.
 * Standalone Native tests have no app scene, so UIKit's actual first-responder switch is injected (`become`); the
 * end-to-end proof is `ReadingUITests.testTwoStepBookmarkFromSelection` on the simulator.
 */
class SelectionFirstResponderTest {
    private val webView = ReadingWkWebView(CGRectMake(0.0, 0.0, 320.0, 480.0), WKWebViewConfiguration())

    private fun claim(current: UIView?): Pair<Boolean, Int> {
        var calls = 0
        val claimed = claimFirstResponderForSelection(webView, current) { calls++; true }
        return claimed to calls
    }

    @Test
    fun noFirstResponderIsClaimedBeforeTheSelectionGesture() {
        val (claimed, calls) = claim(null)
        assertTrue(claimed, "a window without a first responder must hand it to the web view before WebKit selects")
        assertEquals(1, calls)
    }

    @Test
    fun aNonTextViewOutsideTheWebViewGivesWay() {
        // After a route returns, Compose's container view can hold first responder (the Mac run's ComposeContainerView).
        val (claimed, calls) = claim(UIView(CGRectMake(0.0, 0.0, 10.0, 10.0)))
        assertTrue(claimed)
        assertEquals(1, calls)
    }

    @Test
    fun theWebViewsOwnContentViewKeepsItWithoutAnotherCall() {
        val inside = UIView(CGRectMake(0.0, 0.0, 10.0, 10.0))
        webView.scrollView.addSubview(inside)
        val (claimed, calls) = claim(inside)
        assertFalse(claimed)
        assertEquals(0, calls, "the web view is already first responder; claiming again would reset WebKit's selection state")
    }

    @Test
    fun aFocusedTextFieldElsewhereKeepsTheKeyboard() {
        val (claimed, calls) = claim(UITextField(CGRectMake(0.0, 0.0, 100.0, 30.0)))
        assertFalse(claimed, "showing the pane must not dismiss the keyboard of a focused text field outside it")
        assertEquals(0, calls)
    }

    @Test
    fun firstResponderLookupFindsNothingInAViewTreeWithoutOne() {
        val root = UIView(CGRectMake(0.0, 0.0, 100.0, 100.0)).apply { addSubview(webView) }
        assertEquals(null, firstResponderIn(root))
    }
}
