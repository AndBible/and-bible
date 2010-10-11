package net.bible.android.application;

import net.bible.android.device.ProgressNotificationManager;
import android.app.Application;
import android.util.Log;

public class BibleApplication extends Application{

	private static BibleApplication singleton;
	private static final String TAG = "BibleApplication";
	
	@Override
	public void onCreate() {
		super.onCreate();

		// save to a singleton to allow easy access from anywhere
		singleton = this;
		
        //initialise link to Android progress control display in Notification bar
        ProgressNotificationManager.getInstance().initialise();
	}

	public static BibleApplication getApplication() {
		return singleton;
	}

	@Override
	public void onTerminate() {
		Log.i(TAG, "onTerminate");
		super.onTerminate();
	}
}
