package net.bible.android;

import net.bible.android.activity.R;
import net.bible.android.device.ProgressNotificationManager;
import net.bible.android.view.activity.base.AndBibleActivity;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.android.view.activity.base.Dialogs;

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
				showMsg(ev);
			}

			@Override
			public void reportMessage(final ReporterEvent ev) {
				showMsg(ev);
			}
			
			private void showMsg(ReporterEvent ev) {
				String msg = null;
				if (ev==null) {
					msg = getString(R.string.error_occurred);
				} else if (!StringUtils.isEmpty(ev.getMessage())) {
					msg = ev.getMessage();
				} else if (ev.getException()!=null && StringUtils.isEmpty(ev.getException().getMessage())) {
					msg = ev.getException().getMessage();
				} else {
					msg = getString(R.string.error_occurred);
				}
				
				Dialogs.getInstance().showErrorMsg(msg);
			}
        });
    }
    
	@Override
	public void onTerminate() {
		Log.i(TAG, "onTerminate");
		super.onTerminate();
	}
	
}
