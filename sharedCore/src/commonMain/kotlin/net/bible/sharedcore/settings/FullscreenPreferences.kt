package net.bible.sharedcore.settings

import net.bible.sharedcore.platform.AppSettings

const val DOUBLE_TAP_TO_FULLSCREEN_KEY = "double_tap_to_fullscreen"

/** Only the missing-value default varies; an explicit choice is never rewritten. */
expect val doubleTapToFullscreenDefault: Boolean

internal fun doubleTapToFullscreenDefaultFor(isDesktop: Boolean, isIosAppOnMac: Boolean): Boolean =
    !isDesktop && !isIosAppOnMac

fun AppSettings.doubleTapToFullscreen(): Boolean =
    getBoolean(DOUBLE_TAP_TO_FULLSCREEN_KEY, doubleTapToFullscreenDefault)
