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
 * [previous] latches the answer while the IME is visible, and the reason is not cosmetic. The Compose
 * split decides its orientation from the space it is MEASURED in, so anything that steals height flips
 * it: on API 35+ `MainBibleActivity` pads the container the Compose tree mounts into by the keyboard
 * height, and below API 35 `SOFT_INPUT_ADJUST_RESIZE` shrinks the window itself. Either way a stacked
 * portrait split would become side-by-side mid-typing (A/B finding F6-B1). Classic never had this
 * because it read the CONFIGURATION orientation (`CommonUtils.isPortrait`), which no keyboard can
 * change.
 *
 * A `null` [previous] means "first composition, nothing to hold" and computes normally — which is also
 * what a real rotation gets, since `MainBibleActivity`'s `configChanges` omits `orientation` and the
 * activity is therefore recreated.
 *
 * Pure and parameterised rather than reading insets itself so it is unit-testable: `:app` has no
 * `ComposeTestRule` and there is no golden over the reading-view host, so a decision left inside the
 * composable could only ever be verified by hand on a device.
 */
fun splitIsHorizontal(
    widthPx: Float,
    heightPx: Float,
    reverseSplitMode: Boolean,
    imeVisible: Boolean,
    previous: Boolean?,
): Boolean =
    if (imeVisible && previous != null) previous
    else (widthPx > heightPx) != reverseSplitMode
