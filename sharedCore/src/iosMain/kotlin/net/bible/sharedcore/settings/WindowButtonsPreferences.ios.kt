package net.bible.sharedcore.settings

import platform.Foundation.NSProcessInfo
import platform.Foundation.isiOSAppOnMac

actual val textUnderWindowButtonsDefault: Boolean
    get() = textUnderWindowButtonsDefaultFor(
        isDesktop = false,
        isIosAppOnMac = NSProcessInfo.processInfo.isiOSAppOnMac(),
    )
