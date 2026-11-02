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
package net.bible.sharedui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * How much of a window's top safe-area inset belongs to the system window controls (the traffic lights of the iOS app
 * on a Mac), and how far sideways the controls reach, in points. The iOS host removes [top] from the app's safe area
 * and the top bars pad [left]/[right] instead (see [windowControlsSides]), so the bar starts at the window's top edge
 * without putting a button under the controls.
 */
@kotlin.native.HiddenFromObjC
data class WindowControlsInsets(val top: Double, val left: Double, val right: Double) {
    companion object {
        val None = WindowControlsInsets(0.0, 0.0, 0.0)
    }
}

/**
 * Compares the plain safe area with the safe area adapted horizontally around the window's corner controls
 * (UIKit `safeAreaLayoutRegionWithCornerAdaptation(horizontal)`): the top inset the adaptation removes is the
 * controls' band, the side insets it adds are where they sit. Physical sides; all values in points.
 *
 * Returns [WindowControlsInsets.None] when the adaptation removes nothing from the top (a real status bar, or full
 * screen), and also when it removes top inset but adds none sideways: then where the controls are is unknown, and
 * removing the band could put a bar button under them.
 */
@kotlin.native.HiddenFromObjC
fun windowControlsInsets(
    safeTop: Double, safeLeft: Double, safeRight: Double,
    adaptedTop: Double, adaptedLeft: Double, adaptedRight: Double,
): WindowControlsInsets {
    val top = safeTop - adaptedTop
    val left = (adaptedLeft - safeLeft).coerceAtLeast(0.0)
    val right = (adaptedRight - safeRight).coerceAtLeast(0.0)
    if (top <= 0.0 || (left == 0.0 && right == 0.0)) return WindowControlsInsets.None
    return WindowControlsInsets(top, left, right)
}

/** Overrides the platform's window controls (tests). Null (the default) reads [platformWindowControls]. */
@kotlin.native.HiddenFromObjC
val LocalWindowControls = compositionLocalOf<WindowControlsInsets?> { null }

/** The window controls of the iOS app on a Mac; [WindowControlsInsets.None] on every other platform. */
@kotlin.native.HiddenFromObjC
@Composable
fun windowControls(): WindowControlsInsets = LocalWindowControls.current ?: platformWindowControls()

/** The window controls' side insets a top bar pads by instead of the band above it (points are dp on iOS). */
@kotlin.native.HiddenFromObjC
@Composable
fun windowControlsSides(): WindowInsets = windowControls().let { WindowInsets(left = it.left.dp, right = it.right.dp) }

/**
 * The window controls' band at the top, for content that hangs from the window's top edge under the controls'
 * corner but has no top bar to pad sideways (the reading drawer).
 */
@kotlin.native.HiddenFromObjC
@Composable
fun windowControlsBand(): WindowInsets = WindowInsets(top = windowControls().top.dp)

/**
 * The top inset full-screen reading clears (no toolbar there to clear the controls sideways): the safe area's top
 * (a notch or Dynamic Island) and, on a Mac, the window controls' band.
 */
@kotlin.native.HiddenFromObjC
@Composable
fun fullScreenReadingTopInsets(): WindowInsets =
    WindowInsets.safeDrawing.only(WindowInsetsSides.Top).union(windowControlsBand())

/** The platform's window controls (see [WindowControlsInsets]); [WindowControlsInsets.None] where there are none. */
@Composable
internal expect fun platformWindowControls(): WindowControlsInsets
