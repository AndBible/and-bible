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

package net.bible.sharedui.host.reading

import kotlinx.coroutines.CoroutineScope

/**
 * Tilt-to-scroll for the reading view while it is composed (iOS: `IosTiltScroll` on CoreMotion). The app root starts it
 * on entering reading, follows the app lifecycle with [setAppActive] and stops it on leaving.
 */
@kotlin.native.HiddenFromObjC
interface TiltScrollSession {
    /** The app became active (true) or resigned active / entered the background (false). */
    fun setAppActive(active: Boolean)

    /** Starts scrolling on [scope] (the main dispatcher). */
    fun start(scope: CoroutineScope)

    /** Stops scrolling and the sensor. */
    fun stop()
}
