package net.bible.android.view.activity.base;

import android.app.Activity;
import android.util.Log;

public class CurrentActivityHolder {
	
	private Activity currentActivity;
	
	private static final CurrentActivityHolder singleton = new CurrentActivityHolder();
	
	private static final String TAG = "CurrentActivityHolder";
	
	public static CurrentActivityHolder getInstance() {
		return singleton;
	}
	
	public void setCurrentActivity(Activity activity) {
		currentActivity = activity;
	}

	
	public Activity getCurrentActivity() {
		return currentActivity;
	}

	public void iAmNoLongerCurrent(Activity activity) {
		// if the next activity has not already overwritten my registration 
		if (currentActivity!=null && currentActivity.equals(activity)) {
			Log.w(TAG, "Temporarily null current ativity");
			currentActivity = null;
		}
	}
	
}
