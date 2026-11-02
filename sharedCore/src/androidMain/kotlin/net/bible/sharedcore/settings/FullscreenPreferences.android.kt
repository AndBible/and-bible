package net.bible.sharedcore.settings

actual val doubleTapToFullscreenDefault: Boolean =
    doubleTapToFullscreenDefaultFor(isDesktop = false, isIosAppOnMac = false)
