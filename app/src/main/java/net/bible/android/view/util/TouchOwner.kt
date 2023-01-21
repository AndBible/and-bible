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
package net.bible.android.view.util

import android.annotation.SuppressLint
import android.view.View

/** primarily to prevent long-touch being handled while dragging a separator on v slow mobiles
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */

private const val MAX_OWNERSHIP_TIME = (20 * 1000).toLong() // 20 seconds

@SuppressLint("StaticFieldLeak") // Used only in MainBibleActivity context
object TouchOwner {
    private var ownershipTime: Long = 0
    private var currentOwner: View? = null
    fun setTouchOwner(owner: View) {
        currentOwner = owner
        ownershipTime = System.currentTimeMillis()
    }

    fun releaseOwnership() {
        currentOwner = null
    }

    // Not owned
    val isTouchOwned: Boolean
        get() = if (currentOwner == null) {
            false
        } else if (System.currentTimeMillis() - ownershipTime > MAX_OWNERSHIP_TIME) {
            currentOwner = null
            false
        } else {
            true
        }
}
