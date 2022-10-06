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

import android.annotation.SuppressLint
import android.app.Activity
import android.util.Log
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.apptobackground.AppToBackgroundEvent
import net.bible.service.common.CommonUtils

/** Allow operations form middle tier that require a reference to the current Activity
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
private const val TAG = "CurrentActivityHolder"

object CurrentActivityHolder {
    @SuppressLint("StaticFieldLeak") // it is what it is
    var currentActivity: ActivityBase? = null
        set(value) {
            field = value
            // if activity changes then app must be in foreground so use this to trigger appToForeground event if it was in background
            appIsNowInForeground()
        }

    private var appIsInForeground = false

    fun iAmNoLongerCurrent(activity: Activity) {
        // if the next activity has not already overwritten my registration 
        if (currentActivity != null && currentActivity == activity) {
            Log.w(TAG, "Temporarily null current ativity")
            currentActivity = null
            if (appIsInForeground) {
                appIsInForeground = false
                ABEventBus
                    .post(AppToBackgroundEvent(AppToBackgroundEvent.Position.BACKGROUND))
                if (CommonUtils.initialized && CommonUtils.settings.getBoolean("show_calculator", false)) {
                    Log.d(TAG, "Closing app to start from calculator again...")
                    CommonUtils.forceStopApp()
                }
            }
        }
    }

    /** really need to check for app being restored after an exit
     */
    private fun appIsNowInForeground() {
        if (!appIsInForeground) {
            Log.i(TAG, "AppIsInForeground firing event")
            appIsInForeground = true
            ABEventBus
                .post(AppToBackgroundEvent(AppToBackgroundEvent.Position.FOREGROUND))
        }
    }

    /** convenience task with error checking
     */
    fun runOnUiThread(runnable: Runnable) {
        currentActivity?.runOnUiThread(runnable)
    }
}
