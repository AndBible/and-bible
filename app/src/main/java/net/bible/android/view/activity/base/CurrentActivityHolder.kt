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
import net.bible.android.view.activity.page.MainBibleActivity

/** Allow operations form middle tier that require a reference to the current Activity
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */

object CurrentActivityHolder {
    private val activities = ArrayList<ActivityBase>()

    val currentActivity: ActivityBase? get() = try { activities.last() } catch (e: NoSuchElementException) {null}

    fun activate(activity: ActivityBase) {
        if(activity == currentActivity) return
        val wasEmpty = activities.isEmpty()
        activities.add(activity)
        activity.unFreeze()
        if (wasEmpty) {
            ABEventBus
                .post(AppToBackgroundEvent(AppToBackgroundEvent.Position.FOREGROUND))
        } else {
            for (a in activities.filterNot { it == activity }) {
                a.freeze()
            }
        }
    }

    val mainBibleActivities get() = activities.filterIsInstance<MainBibleActivity>().size

    fun deactivate(activity: ActivityBase) {
        activities.remove(activity)
        if (activities.isEmpty()) {
            ABEventBus
                .post(AppToBackgroundEvent(AppToBackgroundEvent.Position.BACKGROUND))
        } else {
            currentActivity!!.unFreeze()
        }
    }

    fun runOnUiThread(runnable: Runnable) {
        currentActivity?.runOnUiThread(runnable)
    }
}
