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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.assembly

/**
 * The size of the screen or window the shared host code lays out against, read at call time (it changes with
 * rotation or window resizing). Sizes are in logical points (iOS `UIScreen.mainScreen.bounds`), [scale] is the
 * pixel-per-point factor. iOS implements it with `IosWindowMetrics`.
 */
@kotlin.native.HiddenFromObjC
interface HostWindowMetrics {
    /** Current width in points. */
    val width: Double

    /** Current height in points. */
    val height: Double

    /** Device pixels per point. */
    val scale: Float

    /** True when the window is at least as tall as it is wide. */
    val isPortrait: Boolean get() = height >= width
}
