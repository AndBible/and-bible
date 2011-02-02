package net.bible.android;

import java.util.Locale;

import net.bible.android.activity.R;
import net.bible.android.device.ProgressNotificationManager;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.service.common.CommonUtils;

import org.apache.commons.lang.StringUtils;
import org.crosswire.common.util.Reporter;
import org.crosswire.common.util.ReporterEvent;
import org.crosswire.common.util.ReporterListener;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;

public class BibleApplication extends Application{

	private static final String TEXT_SIZE_PREF = "text_size_pref";
	private static BibleApplication singleton;
	private static final String TAG = "BibleApplication";
	
	@Override
	public void onCreate() {
		super.onCreate();

		// save to a singleton to allow easy access from anywhere
		singleton = this;

		Log.i(TAG, "OS:"+System.getProperty("os.name")+" ver "+System.getProperty("os.version"));
		Log.i(TAG, "Java:"+System.getProperty("java.vendor")+" ver "+System.getProperty("java.version"));
		Log.i(TAG, "Java home:"+System.getProperty("java.home"));
		Log.i(TAG, "User dir:"+System.getProperty("user.dir")+" Timezone:"+System.getProperty("user.timezone"));
		Locale locale = Locale.getDefault();

		Log.i(TAG, "Locale language:"+locale.getLanguage()+" Variant:"+locale.getDisplayName());
	
		// apparently even early Android devices in Israel had Hebrew fonts installed so I commented out the below
		
//		// if we try to show hebrew resources on an old phone it will not work
//		if (!isLocaleSupported(locale)) {
//			Log.i(TAG, "Changing to English Locale");
//			Locale.setDefault(Locale.ENGLISH);
//		}
		
		installJSwordErrorReportListener();

		// some changes may be required for different versions
		upgradePersistentData();
		
        //initialise link to Android progress control display in Notification bar
       ProgressNotificationManager.getInstance().initialise();
	}

	public static BibleApplication getApplication() {
		return singleton;
	}

	private void upgradePersistentData() {
		SharedPreferences prefs = CommonUtils.getSharedPreferences();
		if (prefs.getInt("version", -1) < CommonUtils.getApplicationVersionNumber()) {
			Editor editor = prefs.edit();
			
			// ver 16 and 17 needed text size pref to be changed to int from string
			if (prefs.getInt("version", -1) < 16) {
				Log.d(TAG, "Upgrading preference");
				String textSize = "16";
				if (prefs.contains(TEXT_SIZE_PREF)) {
					Log.d(TAG, "text size pref exists");
					try {
						textSize = prefs.getString(TEXT_SIZE_PREF, "16");
					} catch (Exception e) {
						// maybe the conversion has already taken place e.g. in debug environment
						textSize = Integer.toString(prefs.getInt(TEXT_SIZE_PREF, 16));
					}
					Log.d(TAG, "existing value:"+textSize);
					editor.remove(TEXT_SIZE_PREF);
				}
				
				int textSizeInt = Integer.parseInt(textSize);
				editor.putInt(TEXT_SIZE_PREF, textSizeInt);
				
				Log.d(TAG, "Finished Upgrading preference");
			}
			editor.putInt("version", CommonUtils.getApplicationVersionNumber());
			editor.commit();
			Log.d(TAG, "Finished all Upgrading");
		}
	}
	
	/** return false if old android and hebrew locale
	 * 
	 * @param locale
	 * @return
	 */
//	private boolean isLocaleSupported(Locale locale) {
//		String langCode = locale.getLanguage();
//		boolean isHebrew = langCode.equalsIgnoreCase("he") || langCode.equalsIgnoreCase("iw");
//		if (isHebrew && !CommonUtils.isHebrewFonts()) {
//			// Locale is Hebrew but OS is old and has no Hebrew fonts
//			return false;
//		} else {
//			return true;
//		}
//	}
	
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
