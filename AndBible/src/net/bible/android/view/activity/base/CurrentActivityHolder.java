package net.bible.android.view.activity.base;

import net.bible.android.control.event.apptobackground.AppToBackgroundEvent;
import net.bible.android.control.event.apptobackground.AppToBackgroundListener;

import org.crosswire.common.util.EventListenerList;

import android.app.Activity;
import android.util.Log;

public class CurrentActivityHolder {
	
	private Activity currentActivity;
	
	private boolean appIsInForeground = false;
	
	private static final CurrentActivityHolder singleton = new CurrentActivityHolder();
	
	private static final String TAG = "CurrentActivityHolder";
	
	private EventListenerList appToBackgroundListeners = new EventListenerList();
	
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
			fireAppToBackground(true);
			appIsInForeground = false;
		}
	}
	
	public void addAppToBackgroundListener(AppToBackgroundListener listener) 
	{
	     appToBackgroundListeners.add(AppToBackgroundListener.class, listener);
	}
	public void removeAppToBackgroundListener(AppToBackgroundListener listener) 
	{
	     appToBackgroundListeners.remove(AppToBackgroundListener.class, listener);
	}
	
	/** really need to check for app being restored after an exit
	 */
	private void appIsNowInForeground() {
		if (!appIsInForeground) {
			Log.d(TAG, "AppIsInForeground firing event");
			fireAppToBackground(false);
			appIsInForeground = true;
		}
	}
	
	protected void fireAppToBackground(boolean isNowBackGround) {
		AppToBackgroundEvent event = new AppToBackgroundEvent();
		Object[] listeners = appToBackgroundListeners.getListenerList();
		// loop through each listener and pass on the event if needed
		int numListeners = listeners.length;
		for (int i = 0; i < numListeners; i += 2) {
			if (listeners[i] == AppToBackgroundListener.class) {
				// pass the event to the listeners event dispatch method
				if (isNowBackGround) {
					((AppToBackgroundListener) listeners[i + 1]).applicationNowInBackground(event);
				} else {
					((AppToBackgroundListener) listeners[i + 1]).applicationReturnedFromBackground(event);
				}
			}
		}
	}
}
