package net.bible.android;

import net.bible.android.activity.R;
import net.bible.android.activity.base.AndBibleActivity;
import net.bible.android.device.ProgressNotificationManager;

import org.apache.commons.lang.StringUtils;
import org.crosswire.common.util.Reporter;
import org.crosswire.common.util.ReporterEvent;
import org.crosswire.common.util.ReporterListener;

import android.app.Activity;
import android.app.Application;
import android.util.Log;
import android.widget.Toast;

public class BibleApplication extends Application{

	private static BibleApplication singleton;
	private static final String TAG = "BibleApplication";
	
	private Activity currentActivity;
	
	@Override
	public void onCreate() {
		super.onCreate();

		// save to a singleton to allow easy access from anywhere
		singleton = this;
	
		installJSwordErrorReportListener();
		
        //initialise link to Android progress control display in Notification bar
        ProgressNotificationManager.getInstance().initialise();
	}

	public static BibleApplication getApplication() {
		return singleton;
	}

    /** JSword calls back to this listener in the event of some types of error
     * 
     */
    private void installJSwordErrorReportListener() {
        Reporter.addReporterListener(new ReporterListener() {
			@Override
			public void reportException(final ReporterEvent ev) {
				Log.e(TAG, ev.getMessage(), ev.getException());
				showErrorMessage(ev.getMessage(), ev.getException());
			}

			@Override
			public void reportMessage(final ReporterEvent ev) {
				Log.w(TAG, ev.getMessage(), ev.getException());
				showErrorMessage(ev.getMessage(), ev.getException());
			}
        });
    }
    
    public void showErrorMessage(final String message, final Throwable e) {
    	if (currentActivity!=null) {
	    	currentActivity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					// find a string to show the user
					String nonEmptyMsg = getString(R.string.error_occurred);
					if (StringUtils.isNotEmpty(message)) {
						nonEmptyMsg = message; 
					} else if (e!=null && StringUtils.isNotEmpty(e.getMessage())) {
						nonEmptyMsg = e.getMessage();
					}
					// then show the message
					if (currentActivity instanceof AndBibleActivity) {
						((AndBibleActivity)currentActivity).showErrorMsg(nonEmptyMsg);
					} else {
						Toast.makeText(getApplicationContext(), nonEmptyMsg, Toast.LENGTH_LONG);
					}
				}
	    	});
    	}
    }
    
	@Override
	public void onTerminate() {
		Log.i(TAG, "onTerminate");
		super.onTerminate();
	}
	
	public void iAmNowCurrent(Activity activity) {
		currentActivity = activity;
	}
	public void iAmNoLongerCurrent(Activity activity) {
		// if the next activity has not already overwritten my registration 
		if (currentActivity!=null && currentActivity.equals(activity)) {
			currentActivity = null;
		}
	}
}
