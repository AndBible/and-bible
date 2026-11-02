@file:OptIn(InternalComposeUiApi::class, BetaInteropApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.components

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.PlatformInsets
import androidx.compose.ui.platform.PlatformWindowInsets
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ObjCAction
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIKeyboardWillChangeFrameNotification
import platform.UIKit.UIKeyboardWillHideNotification
import platform.UIKit.UIKeyboardWillShowNotification
import platform.darwin.NSObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The iOS-app-on-Mac inset corrections: the window-controls band off the top, no keyboard overlap. */
class IosAppOnMacInsetsTest {
    private class Insets(override val left: Int, override val top: Int, override val right: Int, override val bottom: Int) :
        PlatformInsets

    /** CMP's window insets as the Mac reports them: the controls' band (52 px) in every safe-area top, a phantom IME. */
    private class MacLikePlatformInsets(var top: Int = 52) : PlatformWindowInsets {
        var excludedWith: Pair<Boolean, Boolean>? = null
        override val ime: PlatformInsets get() = Insets(0, 0, 0, 300)
        override val statusBars: PlatformInsets get() = Insets(0, top, 0, 0)
        override val systemBars: PlatformInsets get() = Insets(10, top, 12, 20)
        override val displayCutout: PlatformInsets get() = Insets(0, top, 0, 0)
        override val mandatorySystemGestures: PlatformInsets get() = Insets(0, top, 0, 20)
        override val tappableElement: PlatformInsets get() = Insets(0, top, 0, 0)
        override val systemGestures: PlatformInsets get() = Insets(8, top + 8, 8, 28)
        override fun excluding(safeInsets: Boolean, ime: Boolean): PlatformWindowInsets {
            excludedWith = safeInsets to ime
            return MacLikePlatformInsets(if (safeInsets) 0 else top)
        }
    }

    @Test fun topLosesTheBandButNeverGoesNegative() {
        assertEquals(0, macCorrectedTop(52, 52))
        assertEquals(10, macCorrectedTop(62, 52))
        assertEquals(0, macCorrectedTop(20, 52))
        assertEquals(44, macCorrectedTop(44, 0))
    }

    @Test fun imeIsZeroOnTheMac() {
        val insets = MacWindowInsets(MacLikePlatformInsets()) { 0 }
        assertEquals(0, insets.ime.bottom)
        // safeDrawing is built from ime too: the phantom keyboard must not come back through it.
        assertEquals(20, maxOf(insets.ime.bottom, insets.systemBars.bottom))
    }

    @Test fun everySafeAreaTopLosesTheBandAndOnlyTheTop() {
        val insets = MacWindowInsets(MacLikePlatformInsets()) { 52 }
        for (top in listOf(insets.statusBars, insets.systemBars, insets.displayCutout, insets.mandatorySystemGestures,
            insets.tappableElement)) {
            assertEquals(0, top.top)
        }
        assertEquals(8, insets.systemGestures.top)
        with(insets.systemBars) {
            assertEquals(10, left); assertEquals(12, right); assertEquals(20, bottom)
        }
    }

    @Test fun theBandIsReadWhenTheInsetIsRead() {
        var band = 0
        val raw = MacLikePlatformInsets()
        val systemBars = MacWindowInsets(raw) { band }.systemBars
        assertEquals(52, systemBars.top)
        band = 52
        assertEquals(0, systemBars.top)
        raw.top = 70
        assertEquals(18, systemBars.top)
    }

    @Test fun excludingKeepsTheCorrectionAndIsCached() {
        val raw = MacLikePlatformInsets()
        val insets = MacWindowInsets(raw) { 52 }
        val excluded = insets.excluding(safeInsets = false, ime = true)
        assertEquals(false to true, raw.excludedWith)
        assertEquals(0, excluded.ime.bottom)
        assertEquals(0, excluded.statusBars.top)
        assertSame(excluded, insets.excluding(safeInsets = false, ime = true))
    }

    /** Stands in for CMP's `NativeKeyboardVisibilityListener` (same selectors, a Kotlin NSObject subclass too). */
    private class ProbeKeyboardListenerForMacTest : NSObject() {
        var shows = 0
        var changes = 0
        var hides = 0

        @ObjCAction fun keyboardWillShow(arg: NSNotification) { shows++ }
        @ObjCAction fun keyboardWillChangeFrame(arg: NSNotification) { changes++ }
        @ObjCAction fun keyboardWillHide(arg: NSNotification) { hides++ }
    }

    @Test fun silencedListenerIgnoresShowAndFrameChangesButStillHides() {
        val center = NSNotificationCenter()
        val probe = ProbeKeyboardListenerForMacTest()
        for ((selector, name) in listOf(
            "keyboardWillShow:" to UIKeyboardWillShowNotification,
            "keyboardWillChangeFrame:" to UIKeyboardWillChangeFrameNotification,
            "keyboardWillHide:" to UIKeyboardWillHideNotification,
        )) center.addObserver(probe, NSSelectorFromString(selector), name, null)
        fun postAll() = listOf(UIKeyboardWillShowNotification, UIKeyboardWillChangeFrameNotification,
            UIKeyboardWillHideNotification).forEach { center.postNotificationName(it, null) }

        postAll()
        assertEquals(listOf(1, 1, 1), listOf(probe.shows, probe.changes, probe.hides), "the probe must see the notifications")

        val cls = assertNotNull(CmpKeyboardOverlap.findListenerClass("ProbeKeyboardListenerForMacTest"),
            "the probe's Objective-C class is not found by name")
        assertEquals(2, CmpKeyboardOverlap.silence(cls))
        postAll()
        assertEquals(listOf(1, 1, 2), listOf(probe.shows, probe.changes, probe.hides))
        center.removeObserver(probe)
    }

    @Test fun anAbsentListenerClassIsNotFound() {
        assertNull(CmpKeyboardOverlap.findListenerClass("NoSuchKeyboardListener-${'$'}x"))
        assertTrue(CmpKeyboardOverlap.LISTENER_CLASS_NAME.isNotEmpty())
    }
}
