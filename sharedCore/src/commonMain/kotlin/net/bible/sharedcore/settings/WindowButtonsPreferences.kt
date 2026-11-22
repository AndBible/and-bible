package net.bible.sharedcore.settings

import net.bible.sharedcore.platform.AppSettings

/**
 * #4184: let Bible text run to the bottom edge, under the window-button bar, instead of reserving the bar's strip in
 * every bottom window it spans. System insets (navigation bar, home indicator, taskbar) stay reserved either way.
 */
const val TEXT_UNDER_WINDOW_BUTTONS_KEY = "text_under_window_buttons"

/** Only the missing-value default varies (on for desktop windows, off on phones and tablets); a saved choice wins. */
expect val textUnderWindowButtonsDefault: Boolean

internal fun textUnderWindowButtonsDefaultFor(isDesktop: Boolean, isIosAppOnMac: Boolean): Boolean =
    isDesktop || isIosAppOnMac

fun AppSettings.textUnderWindowButtons(): Boolean =
    getBoolean(TEXT_UNDER_WINDOW_BUTTONS_KEY, textUnderWindowButtonsDefault)
