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

package net.bible.android.view.activity.base;

import net.bible.android.control.event.ABEventBus;
import net.bible.android.control.event.apptobackground.AppToBackgroundEvent;
import net.bible.android.control.event.apptobackground.AppToBackgroundEvent.Position;
import android.app.Activity;
import android.util.Log;

/** Allow operations form middle tier that require a reference to the current Activity
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class CurrentActivityHolder {
	
	private Activity currentActivity;
	
	private boolean appIsInForeground = false;
	
	private static final CurrentActivityHolder singleton = new CurrentActivityHolder();
	
	private static final String TAG = "CurrentActivityHolder";
	
	public static CurrentActivityHolder getInstance() {
		return singleton;
	}
	
	public void setCurrentActivity(Activity activity) {
		currentActivity = activity;
		
		// if activity changes then app must be in foreground so use this to trigger appToForeground event if it was in background
		appIsNowInForeground();
	}
	
	public Activity getCurrentActivity() {
		return currentActivity;
	}

	public void iAmNoLongerCurrent(Activity activity) {
		// if the next activity has not already overwritten my registration 
		if (currentActivity!=null && currentActivity.equals(activity)) {
			Log.w(TAG, "Temporarily null current ativity");
			currentActivity = null;
			if (appIsInForeground) {
				appIsInForeground = false;
				ABEventBus.getDefault().post(new AppToBackgroundEvent(Position.BACKGROUND));
			}
		}
	}
	
	/** really need to check for app being restored after an exit
	 */
	private void appIsNowInForeground() {
		if (!appIsInForeground) {
			Log.d(TAG, "AppIsInForeground firing event");
			appIsInForeground = true;
			ABEventBus.getDefault().post(new AppToBackgroundEvent(Position.FOREGROUND));
		}
	}
	
	/** convenience task with error checking
	 */
	public void runOnUiThread(Runnable runnable) {
		Activity activity = getCurrentActivity();
		if (activity!=null) {
			getCurrentActivity().runOnUiThread(runnable);
		}
	}
}
