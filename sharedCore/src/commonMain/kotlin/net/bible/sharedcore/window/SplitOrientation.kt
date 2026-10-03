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

package net.bible.sharedcore.window

/**
 * Whether the split lays its panes out side by side (a `Row`) rather than stacked (a `Column`).
 *
 * Decided from the WINDOW's shape, the rule classic used (`CommonUtils.isPortrait`) and the one
 * `BibleView.isSplitVertically` still uses to assign the panes' top/bottom roles, so Compose and the
 * WebView agree by construction. The keyboard cannot change the window's size (it shrinks only the
 * box the split is MEASURED in, under both `ADJUST_NOTHING` and `adjustResize`), so it can no longer
 * flip the split on any API. This replaces the IME latch, which held the pre-keyboard answer but
 * recomputed from the shrunk box whenever a rotation reset it (F69), and which could not stop
 * `adjustResize` from flipping a split on API 23–29 (F64 known debt).
 *
 * [boxWidthPx]/[boxHeightPx] are the fallback while the window size is not known (either side 0,
 * e.g. a first frame or a harness without a window).
 *
 * Pure so it is unit-testable without a window; see `SplitOrientationTest`.
 */
fun splitIsHorizontal(
    windowWidthPx: Int,
    windowHeightPx: Int,
    boxWidthPx: Float,
    boxHeightPx: Float,
    reverseSplitMode: Boolean,
): Boolean {
    val windowKnown = windowWidthPx > 0 && windowHeightPx > 0
    val wider = if (windowKnown) windowWidthPx > windowHeightPx else boxWidthPx > boxHeightPx
    return wider != reverseSplitMode
}
