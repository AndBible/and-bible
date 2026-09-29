/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.android.view.activity.nav

import android.os.Build
import android.view.WindowManager
import net.bible.sharedcore.nav.NavRoutes

/** The window's decor-fits flag and soft-input adjust mode for one destination. */
data class WindowMode(val decorFitsSystemWindows: Boolean, val softInputAdjust: Int)

/**
 * The ONE decision of how the nav host's window treats insets on [route] (fix batch 2 §2.1.1, F68).
 *
 * - Below [appOwnsImeInsetFromSdk] (API 30): `null`, i.e. do not touch the window. The framework
 *   resizes it under the manifest's `adjustResize` and `ReadingAppBootstrap.setSoftKeyboardMode`
 *   keeps its own below-30 branch (ADJUST_PAN in multi-window).
 * - `reading`: edge-to-edge + `ADJUST_NOTHING`. The reading tree owns every inset itself (toolbar:
 *   status bars; bottom bars / strip: nav bar; split: horizontal bars and cutout; `ReadingInsets`:
 *   the IME). Edge-to-edge is what lets the IME inset REACH it: with decor-fits the decor consumes
 *   it and `ime()` reads 0, which was F68.
 * - Anything else: `ADJUST_RESIZE` (the manifest's value, set explicitly because the window does not
 *   revert on its own), with decor-fits below 35 so the framework lifts text fields there. From 35
 *   the window is edge-to-edge anyway and `AbScaffold` consumes the IME inset (§2.1.2).
 */
fun windowModeFor(route: String?, sdkInt: Int, appOwnsImeInsetFromSdk: Int): WindowMode? {
    if (sdkInt < appOwnsImeInsetFromSdk) return null
    val onReading = route?.substringBefore('?') == NavRoutes.READING
    return if (onReading) {
        WindowMode(decorFitsSystemWindows = false, softInputAdjust = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
    } else {
        WindowMode(
            decorFitsSystemWindows = sdkInt < Build.VERSION_CODES.VANILLA_ICE_CREAM,
            softInputAdjust = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
        )
    }
}
