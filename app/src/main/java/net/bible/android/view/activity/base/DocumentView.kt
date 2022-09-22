/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

    // allow stop/start of autoscroll
    fun onScreenTurnedOn()

    fun onScreenTurnedOff()

    fun pageDown(toBottom: Boolean): Boolean

    /** same as this but of type View  */
    fun asView(): View
}
