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

import androidx.annotation.VisibleForTesting
import net.bible.sharedcore.event.EventSource
import net.bible.sharedcore.event.Events

/** Whether the app as a whole is on screen. */
enum class AppPosition { FOREGROUND, BACKGROUND }

/** Allow operations form middle tier that require a reference to the current Activity
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */

object CurrentActivityHolder {
    private val activities = ArrayList<ActivityBase>()

    private var positionSource = EventSource<AppPosition>()
    /** The first activity started or the last one stopped; synchronous on the caller's thread. */
    val appPositionChanges: Events<AppPosition> get() = positionSource

    @VisibleForTesting internal fun notifyAppPosition(position: AppPosition) {
        positionSource.emit(position)
    }

    @VisibleForTesting fun resetSubscribersForTest() { positionSource = EventSource() }

    /**
     * Test teardown: forgets the activities too. Robolectric destroys a test's application but not necessarily the
     * activities a test built and never stopped, so without this the next test (any class, one JVM) starts with
     * "an activity is already active" and never sees its first activation as [AppPosition.FOREGROUND].
     */
    @VisibleForTesting fun resetForTest() {
        activities.clear()
        resetSubscribersForTest()
    }

    val currentActivity: ActivityBase? get() = try { activities.last() } catch (e: NoSuchElementException) {null}

    /**
     * The incoming Activity is unfrozen and every Activity underneath it is frozen — see
     * [ActivityBase.freeze] for what that is and for why it is still here.
     *
     * The freeze/unfreeze hooks stay although no Activity overrides them since slice 8 deleted
     * `MainBibleActivity`: `StartupActivity`'s `ACTION_VIEW` handoff (`FLAG_ACTIVITY_MULTIPLE_TASK`) can still make a
     * SECOND live `NavHostComposeActivity`, and whether that host needs a real `freeze()` (two instances each subscribed to
     * the owner `Events` streams) is an open question recorded in the slice 8 plan (Correction 11) -- not decided by deleting the hook.
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
            notifyAppPosition(AppPosition.FOREGROUND)
        } else {
            for (a in activities.filterNot { it == activity }) {
                a.freeze()
            }
        }
    }

    fun deactivate(activity: ActivityBase) {
        activities.remove(activity)
        if (activities.isEmpty()) {
            notifyAppPosition(AppPosition.BACKGROUND)
        } else {
            currentActivity!!.unFreeze()
        }
    }

    fun runOnUiThread(runnable: Runnable) {
        currentActivity?.runOnUiThread(runnable)
    }
}
