@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.ios.discrete

import platform.UIKit.UIApplication
import platform.UIKit.supportsAlternateIcons
import platform.UIKit.alternateIconName
import platform.UIKit.setAlternateIconName
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time

/** Changes only the icon; U7 hides content before PIN unlock and ADR 0007 keeps the real app name. */
@kotlin.native.HiddenFromObjC
object IosAppIcon {
    private val request = AppIconRequest(
        supportsAlternateIcons = { UIApplication.sharedApplication.supportsAlternateIcons },
        currentName = { UIApplication.sharedApplication.alternateIconName },
        setIcon = { name, complete ->
            UIApplication.sharedApplication.setAlternateIconName(name) { error ->
                dispatch_async(dispatch_get_main_queue()) { complete(error == null) }
            }
        },
        schedule = { milliseconds, action ->
            dispatch_after(dispatch_time(DISPATCH_TIME_NOW, milliseconds * 1_000_000), dispatch_get_main_queue(), action)
        },
    )

    fun apply(discrete: Boolean) {
        dispatch_async(dispatch_get_main_queue()) { request.apply(discrete) }
    }
}
