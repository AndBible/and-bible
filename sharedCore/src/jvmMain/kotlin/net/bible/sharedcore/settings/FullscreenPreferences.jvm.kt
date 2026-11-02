package net.bible.sharedcore.settings

actual val doubleTapToFullscreenDefault: Boolean =
    doubleTapToFullscreenDefaultFor(isDesktop = true, isIosAppOnMac = false)
