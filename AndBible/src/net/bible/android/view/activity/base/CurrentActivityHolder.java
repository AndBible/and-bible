package net.bible.android.view.activity.base;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.bible.android.control.event.apptobackground.AppToBackgroundEvent;
import net.bible.android.control.event.apptobackground.AppToBackgroundListener;

import android.app.Activity;
import android.util.Log;

/** Allow operations form middle tier that require a reference to the current Activity
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class CurrentActivityHolder {
	
	private Activity currentActivity;
	
	private boolean appIsInForeground = false;
	
	private static final CurrentActivityHolder singleton = new CurrentActivityHolder();
	
	private static final String TAG = "CurrentActivityHolder";
	
	private List<AppToBackgroundListener> appToBackgroundListeners = new CopyOnWriteArrayList<AppToBackgroundListener>();
	
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
				fireAppToBackground(true);
			}
		}
	}
	
	public void addAppToBackgroundListener(AppToBackgroundListener listener) 
	{
	     appToBackgroundListeners.add(listener);
	}
	public void removeAppToBackgroundListener(AppToBackgroundListener listener) 
	{
	     appToBackgroundListeners.remove(listener);
	}
	
	/** really need to check for app being restored after an exit
	 */
	private void appIsNowInForeground() {
		if (!appIsInForeground) {
			Log.d(TAG, "AppIsInForeground firing event");
			appIsInForeground = true;
			fireAppToBackground(false);
		}
	}
	
	protected void fireAppToBackground(boolean isNowBackGround) {
		AppToBackgroundEvent event = new AppToBackgroundEvent();
		// loop through each listener and pass on the event if needed
		for (AppToBackgroundListener listener : appToBackgroundListeners) {
			// pass the event to the listeners event dispatch method
			if (isNowBackGround) {
				listener.applicationNowInBackground(event);
			} else {
				listener.applicationReturnedFromBackground(event);
			}
		}
	}
}
