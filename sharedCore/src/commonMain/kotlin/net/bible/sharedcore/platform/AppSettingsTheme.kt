package net.bible.sharedcore.platform

import net.bible.service.common.DisplayColorMode
import net.bible.service.common.THEME_PALETTE_STYLE_KEY
import net.bible.service.common.ThemePaletteStyle

/*
 * The theme-relevant reads over [AppSettings]. They live in common code so the common host theme
 * (`AbHostTheme`) resolves exactly what Android's `CommonUtils.AndBibleSettings` resolves; the
 * only platform input is whether the device is an Onyx e-ink device ([isOnyx]), which changes the
 * defaults.
 */

/** The user's colour mode; unset falls back to [DisplayColorMode.defaultFor]. */
fun AppSettings.resolveDisplayColorMode(isOnyx: Boolean): DisplayColorMode =
    DisplayColorMode.fromValue(getString("display_color_mode", null)) ?: DisplayColorMode.defaultFor(isOnyx)

/** The user's palette style; unset or unknown is [ThemePaletteStyle.DEFAULT]. */
fun AppSettings.resolveThemePaletteStyle(): ThemePaletteStyle =
    ThemePaletteStyle.fromValue(getString(THEME_PALETTE_STYLE_KEY, null)) ?: ThemePaletteStyle.DEFAULT

/** "No animations" setting; unset defaults to true on Onyx devices. */
fun AppSettings.resolveDisableAnimations(isOnyx: Boolean): Boolean = getBoolean("disable_animations", isOnyx)
