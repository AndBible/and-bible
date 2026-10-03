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

package net.bible.sharedcore.reading

/** Outcome of a vertical drag on a pane's floating ☰ window button. */
enum class PaneButtonAction { None, Minimise, Maximise }

/**
 * Classifies a vertical drag on the per-pane ☰ window button (Plan B Task 5's floating overlay,
 * anchored via [net.bible.sharedui.reading.SplitContent]'s `paneOverlay` slot): swiping the button
 * up past [thresholdPx] maximises the pane, swiping it down past [thresholdPx] minimises it;
 * anything smaller is not a gesture ([PaneButtonAction.None]), so a tap (or a jitter below the
 * threshold) falls through to the button's own click handling instead.
 *
 * [dragDy] is the accumulated vertical drag distance in pixels (screen convention: negative = up,
 * positive = down), [thresholdPx] the minimum absolute distance that counts as an intentional
 * swipe rather than noise. Boundary values (`dragDy == ±thresholdPx`) count as a swipe.
 */
fun paneButtonDragAction(dragDy: Float, thresholdPx: Float): PaneButtonAction = when {
    dragDy <= -thresholdPx -> PaneButtonAction.Maximise
    dragDy >= thresholdPx -> PaneButtonAction.Minimise
    else -> PaneButtonAction.None
}
