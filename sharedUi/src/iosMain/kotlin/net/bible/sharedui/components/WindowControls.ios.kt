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
package net.bible.sharedui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.cinterop.ExperimentalForeignApi
import net.bible.sharedcore.log.nsLog
import platform.UIKit.UIEdgeInsetsMake
import platform.UIKit.additionalSafeAreaInsets
import platform.UIKit.UIViewController

/** Last value [IosWindowControls.update] computed; read by the top bars through [platformWindowControls]. */
private var current by mutableStateOf(WindowControlsInsets.None)

@Composable
internal actual fun platformWindowControls(): WindowControlsInsets = current

/** The window controls' band (points) outside composition, for [MacWindowInsets]; a snapshot state read. */
internal val iosWindowControlsTop: Double get() = current.top

/**
 * The iOS app on a Mac ("Designed for iPad", macOS 26) gets a top safe-area band for the window controls (traffic
 * lights), which the top bars padded as an empty strip in the toolbar colour above the toolbar. The Swift host
 * (`iOSApp.swift`, Mac only) reports its view's plain safe area and the safe area adapted horizontally around the
 * controls' corner; this removes the controls' band from the Compose controller's safe area and hands the sideways
 * part to the top bars ([windowControlsSides]), so the bar starts at the window's top edge and its leading button
 * stays clear of the controls. iPhone and iPad never call this.
 */
object IosWindowControls {
    /** Call on the main thread whenever the host's safe area or layout changes; all values in points. */
    @OptIn(ExperimentalForeignApi::class)
    fun update(
        controller: UIViewController,
        safeTop: Double, safeLeft: Double, safeRight: Double,
        adaptedTop: Double, adaptedLeft: Double, adaptedRight: Double,
    ) {
        val controls = windowControlsInsets(safeTop, safeLeft, safeRight, adaptedTop, adaptedLeft, adaptedRight)
        if (controls == current) return
        nsLog(
            "WINDOW-CONTROLS safe=($safeTop,$safeLeft,$safeRight) adapted=($adaptedTop,$adaptedLeft,$adaptedRight) " +
                "-> $controls",
        )
        // The host's own safe area is not changed by its child's additional insets, so this does not feed back.
        controller.additionalSafeAreaInsets = UIEdgeInsetsMake(-controls.top, 0.0, 0.0, 0.0)
        current = controls
    }
}
