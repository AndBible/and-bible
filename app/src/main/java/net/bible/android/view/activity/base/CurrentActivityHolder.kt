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

    /**
     * The incoming Activity is unfrozen and every Activity underneath it is frozen — see
     * [ActivityBase.freeze] for what that is and for why it is still here.
     *
     * Nav-graph slice 7 Task 6 deleted these three calls (and [mainBibleActivities]) on the premise
     * that the migration leaves one host with nothing to swap. It does not, yet:
     * `StartupActivity.gotoMainBibleActivity()`'s `FLAG_ACTIVITY_MULTIPLE_TASK` branch makes a
     * SECOND live `MainBibleActivity` reachable from any `ACTION_VIEW` deep link, and two
     * instances registered on `ABEventBus` at once handle every event twice. Task 6 fix round 2
     * restored them; they die with `MainBibleActivity` in Task 13.
     *
     * The FOREGROUND/BACKGROUND event pair is unrelated to any of this — it is about the app as a
     * whole.
     */
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

    /**
     * How many live `MainBibleActivity` instances there are. Read only by
     * `MainBibleActivity.freeze()`, which must NOT swap its content view out when it is the only
     * reading Activity there is — the ordinary case, where the thing on top is a secondary screen
     * and the reading view underneath it should simply stay as it is.
     */
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
