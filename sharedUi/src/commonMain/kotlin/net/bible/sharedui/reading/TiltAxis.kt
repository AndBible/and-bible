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

package net.bible.sharedui.reading

import kotlin.math.PI

/**
 * The interface (screen) orientation a tilt sample is taken in, named as UIKit's `UIInterfaceOrientation` names it:
 * [LandscapeLeft] is the device held with the home button / Dynamic Island side on the LEFT of the reader (the top of
 * the interface along the device's -x axis), [LandscapeRight] the mirror image.
 */
@kotlin.native.HiddenFromObjC
enum class TiltScreenRotation { Portrait, PortraitUpsideDown, LandscapeLeft, LandscapeRight }

/**
 * The pitch the common tilt control wants (degrees, Android's convention: upright = -90, the sign flipped from CoreMotion's
 * +90) for a CoreMotion attitude, corrected for the screen rotation as `TiltSensor` promises.
 *
 * CoreMotion's `pitch` is the rotation about the device's x axis (the short edge in portrait) and `roll` the rotation
 * about its y axis. Only the axis that runs along the interface's up direction measures the tilt the reader feels, so in
 * portrait it is the pitch and in landscape the roll (Android switches rotation-vector axes the same way for rotation 90
 * and 270). For a device held upright the chosen angle is +90 degrees in every orientation:
 * - portrait: pitch = +90; upside down: pitch = -90;
 * - landscape with the interface top along device +x (UIKit `LandscapeRight`): roll = -90;
 * - landscape with the interface top along device -x (`LandscapeLeft`): roll = +90.
 *
 * The signs follow from the right-handed rotations; the feel on a real device is owed (a Mac/device step).
 */
@kotlin.native.HiddenFromObjC
fun tiltPitchDegrees(pitchRadians: Double, rollRadians: Double, rotation: TiltScreenRotation): Float {
    val upright = when (rotation) {
        TiltScreenRotation.Portrait -> pitchRadians
        TiltScreenRotation.PortraitUpsideDown -> -pitchRadians
        TiltScreenRotation.LandscapeRight -> -rollRadians
        TiltScreenRotation.LandscapeLeft -> rollRadians
    }
    return (-upright * 180.0 / PI).toFloat()
}
