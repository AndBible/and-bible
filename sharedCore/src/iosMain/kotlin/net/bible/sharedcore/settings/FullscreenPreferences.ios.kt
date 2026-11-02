package net.bible.sharedcore.settings

import platform.Foundation.NSProcessInfo
import platform.Foundation.isiOSAppOnMac

actual val doubleTapToFullscreenDefault: Boolean
    get() = doubleTapToFullscreenDefaultFor(
        isDesktop = false,
        isIosAppOnMac = NSProcessInfo.processInfo.isiOSAppOnMac(),
    )
