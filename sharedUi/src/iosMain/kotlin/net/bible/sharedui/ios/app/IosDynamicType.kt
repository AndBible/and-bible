@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.ios.app

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIContentSizeCategoryDidChangeNotification
import platform.darwin.NSObjectProtocol

/** Apple's body-point reference scale; Compose's own runtime mapping may differ slightly. */
@kotlin.native.HiddenFromObjC
fun iosFontScale(category: String): Float = when (category) {
    "UICTContentSizeCategoryXS" -> 14f / 17f
    "UICTContentSizeCategoryS" -> 15f / 17f
    "UICTContentSizeCategoryM" -> 16f / 17f
    "UICTContentSizeCategoryL" -> 1f
    "UICTContentSizeCategoryXL" -> 19f / 17f
    "UICTContentSizeCategoryXXL" -> 21f / 17f
    "UICTContentSizeCategoryXXXL" -> 23f / 17f
    "UICTContentSizeCategoryAccessibilityM" -> 28f / 17f
    "UICTContentSizeCategoryAccessibilityL" -> 33f / 17f
    "UICTContentSizeCategoryAccessibilityXL" -> 40f / 17f
    "UICTContentSizeCategoryAccessibilityXXL" -> 47f / 17f
    "UICTContentSizeCategoryAccessibilityXXXL" -> 53f / 17f
    else -> 1f
}

@kotlin.native.HiddenFromObjC
const val IOS_BAR_FONT_SCALE_CAP: Float = 23f / 17f

/** Main-thread observer of the reference scale, not a multiplier for Compose's built-in density. */
internal class IosDynamicTypeObserver(
    private val categorySource: () -> String = { UIApplication.sharedApplication.preferredContentSizeCategory.orEmpty() },
    private val notificationCenter: NSNotificationCenter = NSNotificationCenter.defaultCenter,
) {
    private val observedScale = mutableStateOf(iosFontScale(categorySource()))
    val fontScale: State<Float> get() = observedScale
    private val subscriptions = mutableListOf<NSObjectProtocol>()

    fun start() {
        if (subscriptions.isNotEmpty()) return
        for (name in listOf(UIContentSizeCategoryDidChangeNotification, UIApplicationDidBecomeActiveNotification)) {
            subscriptions += notificationCenter.addObserverForName(name, null, NSOperationQueue.mainQueue) {
                observedScale.value = iosFontScale(categorySource())
            }
        }
        observedScale.value = iosFontScale(categorySource())
    }

    fun stop() {
        subscriptions.forEach(notificationCenter::removeObserver)
        subscriptions.clear()
    }
}
