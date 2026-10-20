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

package net.bible.sharedui.reading.session

/**
 * The keep-screen-on decision of the reading view (Android: `ActivityBase.refreshScreenKeepOn`, preference
 * `screen_keep_on_pref`). The screen is kept on only while the reading route is visible, the app is active and
 * the preference [read]s true; every other combination answers false so the host resets the platform flag
 * (iOS: `idleTimerDisabled`). [apply] is called only when the answer changes.
 */
@kotlin.native.HiddenFromObjC
class ReadingKeepScreenOn(private val read: () -> Boolean, private val apply: (Boolean) -> Unit) {
    private var reading = false
    private var active = true
    private var applied: Boolean? = null

    fun setReading(value: Boolean) { reading = value; refresh() }

    fun setActive(value: Boolean) { active = value; refresh() }

    /** Re-evaluates (call when the preference may have changed). */
    fun refresh() {
        val keepOn = reading && active && read()
        if (applied != keepOn) {
            applied = keepOn
            apply(keepOn)
        }
    }
}
