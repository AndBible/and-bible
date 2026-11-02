/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
@file:OptIn(InternalComposeUiApi::class, ExperimentalForeignApi::class)

package net.bible.sharedui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalPlatformWindowInsets
import androidx.compose.ui.platform.PlatformInsets
import androidx.compose.ui.platform.PlatformWindowInsets
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.interpretObjCPointerOrNull
import kotlinx.cinterop.invoke
import kotlinx.cinterop.rawValue
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCClass
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.value
import kotlinx.coroutines.delay
import net.bible.sharedcore.log.nsLog
import platform.Foundation.NSProcessInfo
import platform.Foundation.isiOSAppOnMac
import platform.objc.class_getInstanceMethod
import platform.objc.class_getName
import platform.objc.class_replaceMethod
import platform.objc.method_getTypeEncoding
import platform.objc.objc_copyClassList
import platform.objc.sel_registerName
import platform.posix.dlsym
import platform.posix.free
import platform.posix.strstr
import kotlin.math.roundToInt

/**
 * The iOS app running on a Mac ("Designed for iPad"). Compose Multiplatform (1.11.1) gets two insets wrong there:
 *
 * - **A phantom keyboard.** There is no software keyboard on a Mac, but CMP computes a keyboard overlap from
 *   UIKeyboard notifications and `UIScreen` geometry (`ComposeSceneKeyboardOffsetManager`), and a Mac window is not
 *   its screen. That overlap is `WindowInsets.ime`; app scaffolds pad it (`abScaffoldContentInsets`), and every
 *   material3 sheet and dialog is a CMP `Dialog` whose area CMP shortens by it (`useSoftwareKeyboardInset` is on, and
 *   material3 1.9.0 offers no way to turn it off). Both showed as an empty band ~5 cm high at the window's bottom.
 * - **The window controls' band at the top.** CMP reads the safe area as the union of the Compose view's and the
 *   window's root view (`UIKitWindowInsetsManager`), through `safeAreaLayoutRegionWithCornerAdaptation(vertical)` on
 *   iOS 26.1+. The root view is SwiftUI's hosting view, which `additionalSafeAreaInsets` on the Compose controller
 *   ([IosWindowControls]) does not reach, and the vertical corner adaptation keeps the band anyway; so the top bars
 *   kept padding the band as an empty strip.
 *
 * iPhone and iPad keep CMP's insets untouched.
 */
object IosAppOnMac {
    /** True for the iOS app on a Mac. */
    val isMac: Boolean by lazy { NSProcessInfo.processInfo.isiOSAppOnMac() }

    /**
     * Whether [ProvideIosAppOnMacInsets] corrects the insets: on a Mac, and in debug builds launched with
     * `-simulateMacInsets` (XCUITest `ReadingUITests.testMacInsetsKeepDialogOffTheKeyboard` runs the Mac path on a
     * simulator, where the software keyboard exists, to prove the keyboard overlap is really gone).
     */
    @OptIn(kotlin.experimental.ExperimentalNativeApi::class)
    val correctsInsets: Boolean by lazy {
        isMac || (kotlin.native.Platform.isDebugBinary &&
            NSProcessInfo.processInfo.arguments.any { it.toString() == "-simulateMacInsets" })
    }
}

/** [top] without the window controls' band (pixels), never below zero. */
internal fun macCorrectedTop(top: Int, bandPx: Int): Int = (top - bandPx).coerceAtLeast(0)

private class BandCorrectedInsets(private val insets: PlatformInsets, private val bandPx: () -> Int) : PlatformInsets {
    override val left: Int get() = insets.left
    override val top: Int get() = macCorrectedTop(insets.top, bandPx())
    override val right: Int get() = insets.right
    override val bottom: Int get() = insets.bottom
}

/**
 * CMP's [platform] window insets as they should read on a Mac: no keyboard overlap ([ime] zero), and every top inset
 * without the window controls' band ([bandPx], read whenever an inset is read, so it follows the window).
 *
 * Only `WindowInsets.*` (and what is built from them: `windowInsetsPadding`, Material's defaults) read this. CMP's
 * inset modifiers (`Modifier.imePadding()`, `statusBarsPadding()`, `safeDrawingPadding()`, ...) and the insets rulers
 * take the scene's raw insets from the root node instead, so shared UI must not use them for a top or IME inset
 * (`InsetModifierGuardTest`).
 */
internal class MacWindowInsets(
    private val platform: PlatformWindowInsets,
    private val bandPx: () -> Int,
) : PlatformWindowInsets {
    private val excluded = HashMap<Pair<Boolean, Boolean>, PlatformWindowInsets>()
    private fun PlatformInsets.corrected(): PlatformInsets = BandCorrectedInsets(this, bandPx)

    override val displayCutouts get() = platform.displayCutouts
    override val cutoutPath get() = platform.cutoutPath
    override val captionBar get() = platform.captionBar.corrected()
    override val displayCutout get() = platform.displayCutout.corrected()
    override val ime: PlatformInsets get() = PlatformInsets.Zero
    override val mandatorySystemGestures get() = platform.mandatorySystemGestures.corrected()
    override val navigationBars get() = platform.navigationBars.corrected()
    override val statusBars get() = platform.statusBars.corrected()
    override val systemBars get() = platform.systemBars.corrected()
    override val systemGestures get() = platform.systemGestures.corrected()
    override val tappableElement get() = platform.tappableElement.corrected()
    override val waterfall get() = platform.waterfall.corrected()

    override fun excluding(safeInsets: Boolean, ime: Boolean): PlatformWindowInsets =
        excluded.getOrPut(safeInsets to ime) { MacWindowInsets(platform.excluding(safeInsets, ime), bandPx) }
}

/**
 * Silences CMP's keyboard listener on the Mac, so no keyboard overlap is ever computed: CMP's sheets and dialogs live
 * in their own scene layers, which provide their own insets, so [MacWindowInsets] cannot reach them. The listener is
 * one process-wide Kotlin `NSObject` (`androidx.compose.ui.window.NativeKeyboardVisibilityListener`, CMP 1.11.1)
 * observing the UIKeyboard notifications; its show and frame-change handlers are replaced with no-ops, so its
 * keyboard frame stays empty. Its hide handler is kept (it only clears the frame).
 *
 * Kotlin/Native registers a Kotlin subclass of an Objective-C class under an anonymous name (`<module>_kobjc<N>`,
 * measured: `Test_kobjc1`), so the listener is found by shape instead: a Kotlin class answering all three keyboard
 * selectors ([LISTENER_SELECTORS]). Nothing else in the app or CMP implements them as Objective-C methods.
 */
internal object CmpKeyboardOverlap {
    private const val KOTLIN_CLASS_MARK = "_kobjc"
    val LISTENER_SELECTORS = listOf("keyboardWillShow:", "keyboardWillChangeFrame:", "keyboardWillHide:")
    private val SILENCED = listOf("keyboardWillShow:", "keyboardWillChangeFrame:")

    private val noop = staticCFunction { _: COpaquePointer?, _: COpaquePointer?, _: COpaquePointer? -> }

    // The runtime's C functions through plain function pointers: the class list holds root classes that are not
    // `NSObject`s (e.g. `Object`), and turning one into a Kotlin reference messages it, which aborts the process.
    // Darwin's RTLD_DEFAULT is ((void *) -2).
    private val classGetName by lazy {
        dlsym((-2L).toCPointer<CPointed>(), "class_getName")
            ?.reinterpret<CFunction<(COpaquePointer?) -> CPointer<ByteVar>?>>()
    }
    private val classGetInstanceMethod by lazy {
        dlsym((-2L).toCPointer<CPointed>(), "class_getInstanceMethod")
            ?.reinterpret<CFunction<(COpaquePointer?, COpaquePointer?) -> COpaquePointer?>>()
    }

    /** Every registered Kotlin class that implements all of [LISTENER_SELECTORS] (CMP's listener; tests add probes). */
    fun findListenerClasses(): List<ObjCClass> = memScoped {
        val getName = classGetName ?: return@memScoped emptyList()
        val getMethod = classGetInstanceMethod ?: return@memScoped emptyList()
        val mark = KOTLIN_CLASS_MARK.cstr.ptr
        val selectors = LISTENER_SELECTORS.map { sel_registerName(it) }
        val count = alloc<UIntVar>()
        val list = objc_copyClassList(count.ptr) ?: return@memScoped emptyList()
        try {
            val raw = list.reinterpret<COpaquePointerVar>()
            // Raw pointers and strstr on the C names: no Kotlin object or string per class (tens of thousands).
            (0 until count.value.toInt()).mapNotNull { i ->
                val cls = raw[i] ?: return@mapNotNull null
                val className = getName(cls) ?: return@mapNotNull null
                if (strstr(className, mark) == null) return@mapNotNull null
                if (selectors.any { getMethod(cls, it) == null }) return@mapNotNull null
                interpretObjCPointerOrNull<ObjCClass>(cls.rawValue)
            }
        } finally {
            free(list)
        }
    }

    /** Replaces [SILENCED] on [cls] with no-ops; returns how many of them the class had. */
    fun silence(cls: ObjCClass): Int = SILENCED.count { selector ->
        val sel = sel_registerName(selector)
        val method = class_getInstanceMethod(cls, sel) ?: return@count false
        class_replaceMethod(cls, sel, noop.reinterpret(), method_getTypeEncoding(method)?.toKString())
        true
    }

    private var silenced = false

    /** Silences the listener once it exists (CMP creates it when the first scene appears); true when done. */
    fun silenceOnce(): Boolean {
        if (silenced) return true
        val classes = findListenerClasses()
        if (classes.isEmpty()) return false
        for (cls in classes) {
            val replaced = silence(cls)
            nsLog("MAC-IME silenced ${class_getName(cls)?.toKString()} ($replaced of ${SILENCED.size} handlers)")
        }
        silenced = true
        return true
    }
}

/**
 * Wraps the app's root content. On a Mac ([IosAppOnMac.correctsInsets]) it provides [MacWindowInsets] (no keyboard
 * overlap, no window-controls band in the top insets) to the main scene and silences CMP's keyboard listener
 * ([CmpKeyboardOverlap]) for the sheet and dialog layers; elsewhere it is just [content]. Each change of the raw and
 * corrected values is logged (`MAC-INSETS`; Console.app, subsystem org.andbible.ios).
 */
@Composable
fun ProvideIosAppOnMacInsets(content: @Composable () -> Unit) {
    if (!IosAppOnMac.correctsInsets) {
        content()
        return
    }
    val platform = LocalPlatformWindowInsets.current
    val density by rememberUpdatedState(LocalDensity.current.density)
    // Read whenever an inset is read: the band is snapshot state set by the Swift host (IosWindowControls).
    val corrected = remember(platform) { MacWindowInsets(platform) { (iosWindowControlsTop * density).roundToInt() } }
    LaunchedEffect(Unit) {
        // CMP's listener exists once the first scene appeared; a keyboard notification can only follow a text field.
        repeat(100) {
            if (CmpKeyboardOverlap.silenceOnce()) return@LaunchedEffect
            delay(100)
        }
        nsLog("MAC-IME CMP keyboard listener not found: keyboard overlap stays on")
    }
    LaunchedEffect(platform, corrected) {
        snapshotFlow {
            "MAC-INSETS raw: top=${platform.systemBars.top} ime=${platform.ime.bottom} bottom=${platform.systemBars.bottom}" +
                " corrected: top=${corrected.systemBars.top} ime=${corrected.ime.bottom} bandPx=${(iosWindowControlsTop * density).roundToInt()}"
        }.collect { nsLog(it) }
    }
    CompositionLocalProvider(LocalPlatformWindowInsets provides corrected, content = content)
}
