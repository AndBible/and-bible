/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.android.view.activity.base

import android.view.View


/**
 * Base class for boble and My Note document views
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
interface DocumentView {

    /** prevent swipe right if the user is scrolling the page right  */
    val isPageNextOkay: Boolean

    /** prevent swipe left if the user is scrolling the page left  */
    val isPagePreviousOkay: Boolean

    val currentPosition: Float

    fun applyPreferenceSettings()

    /** may need updating depending on environmental brightness
     */
    fun changeBackgroundColour()

    // allow stop/start of autoscroll
    fun onScreenTurnedOn()

    fun onScreenTurnedOff()

    fun pageDown(toBottom: Boolean): Boolean

    /** same as this but of type View  */
    fun asView(): View
}
