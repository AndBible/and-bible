package net.bible.android;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.Log;

import net.bible.android.activity.SpeakWidgetManager;
import net.bible.android.control.ApplicationComponent;
import net.bible.android.control.DaggerApplicationComponent;
import net.bible.android.control.event.ABEventBus;
import net.bible.android.view.util.locale.LocaleHelper;
import net.bible.service.common.CommonUtils;
import net.bible.service.device.ProgressNotificationManager;
import net.bible.service.device.ScreenSettings;
import net.bible.service.device.speak.TextToSpeechNotificationManager;
import net.bible.service.sword.SwordEnvironmentInitialisation;

import org.crosswire.common.util.Language;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.bridge.BookIndexer;

import java.util.List;
import java.util.Locale;


/** Main And Bible application singleton object
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class BibleApplication extends Application{

	private int errorDuringStartup = 0;

	private ApplicationComponent applicationComponent;
	
	private static final String TEXT_SIZE_PREF = "text_size_pref";
	
	private String localeOverrideAtStartup;

	// this was moved from the MainBibleActivity and has always been called this
	private static final String saveStateTag = "MainBibleActivity";

	private static BibleApplication singleton;

	private static final String TAG = "BibleApplication";
	private TextToSpeechNotificationManager ttsNotificationManager;
	private SpeakWidgetManager ttsWidgetManager;

	@Override
	public void onCreate() {
		super.onCreate();

		// save to a singleton to allow easy access from anywhere
		singleton = this;

		Log.i(TAG, "OS:"+System.getProperty("os.name")+" ver "+System.getProperty("os.version"));
		Log.i(TAG, "Java:"+System.getProperty("java.vendor")+" ver "+System.getProperty("java.version"));
		Log.i(TAG, "Java home:"+System.getProperty("java.home"));
		Log.i(TAG, "User dir:"+System.getProperty("user.dir")+" Timezone:"+System.getProperty("user.timezone"));
	
        // fix for null context class loader (http://code.google.com/p/android/issues/detail?id=5697)
        // this affected jsword dynamic classloading
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
        
		// This must be done before accessing JSword to prevent default folders being used
		SwordEnvironmentInitialisation.initialiseJSwordFolders();

		// Initialize the Dagger injector ApplicationScope objects
		applicationComponent = DaggerApplicationComponent.builder().build();

		// ideally this would be installed before initialiseJSwordFolders but the listener depends on applicationComponent
		SwordEnvironmentInitialisation.installJSwordErrorReportListener();

		// some changes may be required for different versions
		upgradePersistentData();
		
		// initialise link to Android progress control display in Notification bar
		ProgressNotificationManager.Companion.getInstance().initialise();

		// various initialisations required every time at app startup
		getApplicationComponent().warmUp().warmUpSwordEventually();

		localeOverrideAtStartup = LocaleHelper.getOverrideLanguage(this);

		ttsNotificationManager = new TextToSpeechNotificationManager();
		ttsWidgetManager = new SpeakWidgetManager();
	}

	public ApplicationComponent getApplicationComponent() {
		return applicationComponent;
	}

	public String getLocaleOverrideAtStartUp()
	{
		return localeOverrideAtStartup;
	}

	/**
	 * Override locale.  If user has selected a different ui language to the devices default language
	 */
	@Override
	protected void attachBaseContext(Context newBase) {
		super.attachBaseContext(LocaleHelper.onAttach(newBase));
	}

	public static BibleApplication getApplication() {
		return singleton;
	}

	private void upgradePersistentData() {
		SharedPreferences prefs = CommonUtils.getSharedPreferences();
		int prevInstalledVersion = prefs.getInt("version", -1);
		if (prevInstalledVersion < CommonUtils.getApplicationVersionNumber() && prevInstalledVersion>-1) {
			Editor editor = prefs.edit();
			
			// ver 16 and 17 needed text size pref to be changed to int from string
			if (prevInstalledVersion < 16) {
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

			// there was a problematic Chinese index architecture before ver 24 so delete any old indexes
			if (prevInstalledVersion < 24) {
				Log.d(TAG, "Deleting old Chinese indexes");
				Language CHINESE = new Language("zh");

				List<Book> books = applicationComponent.swordDocumentFacade().getDocuments();
				for (Book book : books) {
					if (CHINESE.equals(book.getLanguage())) {
						try {
							BookIndexer bookIndexer = new BookIndexer(book);
			                // Delete the book, if present
			                if (bookIndexer.isIndexed()) {
			                    Log.d(TAG, "Deleting index for "+book.getInitials());
			                    bookIndexer.deleteIndex();
			                }
						} catch (Exception e) {
							Log.e(TAG, "Error deleting index", e);
						}
					}
				}
			}
			// add new  
			if (prevInstalledVersion < 61) {
				if (prefs.contains(ScreenSettings.NIGHT_MODE_PREF_NO_SENSOR)) {
					String pref2Val = prefs.getBoolean(ScreenSettings.NIGHT_MODE_PREF_NO_SENSOR, false) ? "true" : "false";
					Log.d(TAG, "Setting new night mode pref list value:"+pref2Val);
					editor.putString(ScreenSettings.NIGHT_MODE_PREF_WITH_SENSOR, pref2Val);
				}
			}

			// clear old split screen config because it has changed a lot
			if (prevInstalledVersion < 154) {
				 editor.remove("screen1_weight");
				 editor.remove("screen2_minimized");
				 editor.remove("split_screen_pref");
			}
			
			// clear setting temporarily used for window state
			if (prevInstalledVersion < 157) {
				SharedPreferences appPrefs = getAppStateSharedPreferences();
				if (appPrefs.contains("screenStateArray")) {
					Log.d(TAG, "Removing screenStateArray");
					appPrefs.edit()
							.remove("screenStateArray")
							.commit();
				}
			}
			
			
			
			editor.putInt("version", CommonUtils.getApplicationVersionNumber());
			editor.commit();
			Log.d(TAG, "Finished all Upgrading");
		}
	}
	
	/** return false if old android and hebrew locale
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

	/**
	 * This is never called in real system (only in tests). See parent documentation.
	 */
	@Override
	public void onTerminate() {
		Log.i(TAG, "onTerminate");
		ttsNotificationManager.destroy();
		ttsWidgetManager.destroy();
		super.onTerminate();
		ABEventBus.getDefault().unregisterAll();
		singleton = null;
	}
	
	// difficult to show dialogs during Activity onCreate so save it until later
	public int getErrorDuringStartup() {
		return errorDuringStartup;
	}
	
    public SharedPreferences getAppStateSharedPreferences() {
    	return getSharedPreferences(saveStateTag, 0);
    }

	public Resources getLocalizedResources(String language) {
		BibleApplication app = getApplication();
		Configuration oldConf = app.getResources().getConfiguration();
		Configuration newConf = new Configuration(oldConf);
		newConf.setLocale(new Locale(language));
		return app.createConfigurationContext(newConf).getResources();
	}
}
