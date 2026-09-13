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

import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.apptobackground.AppToBackgroundEvent

/** Allow operations form middle tier that require a reference to the current Activity
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */

object CurrentActivityHolder {
    private val activities = ArrayList<ActivityBase>()

    val currentActivity: ActivityBase? get() = try { activities.last() } catch (e: NoSuchElementException) {null}

    /**
     * Nav-graph slice 7 Task 6 (design §9) deleted `ActivityBase.freeze()`/`unFreeze()` and with
     * them this object's three calls into them, plus `mainBibleActivities` — the count that existed
     * only so `MainBibleActivity.freeze()` could skip itself when it was the only reading Activity.
     * Freezing swapped one Activity's content view out while another was on top; the migration
     * leaves one host, so there is nothing to swap. What stays is the FOREGROUND/BACKGROUND event
     * pair, which is about the app as a whole and has nothing to do with freezing.
     */
    fun activate(activity: ActivityBase) {
        if(activity == currentActivity) return
        val wasEmpty = activities.isEmpty()
        activities.add(activity)
        if (wasEmpty) {
            ABEventBus
                .post(AppToBackgroundEvent(AppToBackgroundEvent.Position.FOREGROUND))
        }
    }

    fun deactivate(activity: ActivityBase) {
        activities.remove(activity)
        if (activities.isEmpty()) {
            ABEventBus
                .post(AppToBackgroundEvent(AppToBackgroundEvent.Position.BACKGROUND))
        }
    }

    fun runOnUiThread(runnable: Runnable) {
        currentActivity?.runOnUiThread(runnable)
    }
}
