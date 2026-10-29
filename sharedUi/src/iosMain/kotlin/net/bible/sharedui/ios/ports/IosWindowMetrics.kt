@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.ios.ports

import kotlinx.cinterop.useContents
import net.bible.sharedui.assembly.HostWindowMetrics
import platform.UIKit.UIScreen

/** [HostWindowMetrics] over `UIScreen.mainScreen`, read on every access. */
@kotlin.native.HiddenFromObjC
class IosWindowMetrics : HostWindowMetrics {
    override val width: Double get() = UIScreen.mainScreen.bounds.useContents { size.width }
    override val height: Double get() = UIScreen.mainScreen.bounds.useContents { size.height }
    override val scale: Float get() = UIScreen.mainScreen.scale.toFloat()
    override val isPortrait: Boolean get() = UIScreen.mainScreen.bounds.useContents { size.height >= size.width }
}
