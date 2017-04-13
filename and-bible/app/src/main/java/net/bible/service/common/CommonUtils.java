package net.bible.service.common;

import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.support.v7.widget.PopupMenu;
import android.util.Log;

import net.bible.android.BibleApplication;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.crosswire.common.util.IOUtil;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.passage.VerseRange;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class CommonUtils {

	private static final String COLON = ":";
	private static final int DEFAULT_MAX_TEXT_LENGTH = 250;
	private static final String ELLIPSIS = "...";

	private static final String TAG = "CommonUtils"; 
	static private boolean isAndroid = true;
	
	static {
		try {
	        if (android.os.Build.ID != null) {
	            isAndroid = true;
	        }
		} catch (Exception cnfe) {
			isAndroid = false;
		}
		System.out.println("isAndroid:"+isAndroid);
	}

	public static boolean isAndroid() {
		return isAndroid;
	}

	public static String getApplicationVersionName() {
		String versionName;
		try
        {
            PackageManager manager = BibleApplication.getApplication().getPackageManager();
            PackageInfo info = manager.getPackageInfo(BibleApplication.getApplication().getPackageName(), 0);
            versionName = info.versionName;
        }
        catch ( final NameNotFoundException e )
        {
            Log.e(TAG, "Error getting package name.", e);
            versionName = "Error";
        }
        return versionName;
	}
	public static int getApplicationVersionNumber() {
		int versionNumber;
		try
        {
            PackageManager manager = BibleApplication.getApplication().getPackageManager();
            PackageInfo info = manager.getPackageInfo(BibleApplication.getApplication().getPackageName(), 0);
            versionNumber = info.versionCode;
        }
        catch ( final NameNotFoundException e )
        {
            Log.e(TAG, "Error getting package name.", e);
            versionNumber = -1;
        }
        return versionNumber;
	}
	
	public static boolean isFroyoPlus() {
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO;
	}

	public static boolean isHoneycombPlus() {
		return Build.VERSION.SDK_INT >= 11;
	}
	public static boolean isIceCreamSandwichPlus() {
		return Build.VERSION.SDK_INT >= 14;
	}
	public static boolean isJellyBeanPlus() {
		return Build.VERSION.SDK_INT >= 16;
	}
	public static boolean isKitKatPlus() {
		return Build.VERSION.SDK_INT >= 19;
	}
	public static boolean isNougatPlus() {
		return Build.VERSION.SDK_INT >= 24;
	}

	public static long getSDCardMegsFree() {
		long bytesAvailable = getFreeSpace(Environment.getExternalStorageDirectory().getPath());
		long megAvailable = bytesAvailable / 1048576;
		Log.d(TAG, "Megs available on SD card :"+megAvailable);
		return megAvailable;
	}
	public static long getFreeSpace(String path) {
		StatFs stat = new StatFs(path);
		long bytesAvailable = (long)stat.getBlockSize() * (long)stat.getAvailableBlocks();
		Log.d(TAG, "Free space :"+bytesAvailable);
		return bytesAvailable;
	}
	
	/** shorten text for display in lists etc.
	 * 
	 * @param text
	 * @return
	 */
	public static String limitTextLength(String text) {
		return limitTextLength(text, DEFAULT_MAX_TEXT_LENGTH);
	}
	public static String limitTextLength(String text, int maxLength) {
		return limitTextLength(text, maxLength, false);
	}
	public static String limitTextLength(String text, int maxLength, boolean singleLine) {
		if (text!=null) {
			int origLength = text.length();
			
			if (singleLine) {
				// get first line but limit length in case there are no line breaks
				text = StringUtils.substringBefore(text,"\n");
			}
			
			if (text.length()>maxLength) {
				// break on a space rather than mid-word
				int cutPoint = text.indexOf(" ", maxLength);
				if (cutPoint >= maxLength) {
					text = text.substring(0, cutPoint+1);
				}
			}
			
			if (text.length() != origLength) {
				text += ELLIPSIS;
			}
		}
		return text;
	}
	
    public static boolean isInternetAvailable() {
    	String testUrl = "http://www.crosswire.org/ftpmirror/pub/sword/packages/rawzip/";
    	return CommonUtils.isHttpUrlAvailable(testUrl);
    }

    /** return true if URL is accessible
     * 
     * Since Android 3 must do on different or NetworkOnMainThreadException is thrown
     */
    public static boolean isHttpUrlAvailable(final String urlString) {
		boolean isAvailable = false;
		final int TIMEOUT_MILLIS = 3000;

		try {
			class CheckUrlThread extends Thread {
				public boolean checkUrlSuccess = false;
	
				public void run() {
			 	    HttpURLConnection connection = null;
					try {
	    	    		// might as well test for the url we need to access
	    		 	    URL url = new URL(urlString);
	
	    		 	    Log.d(TAG, "Opening test connection");
	    		 	    connection = (HttpURLConnection)url.openConnection();
	    		 	    connection.setConnectTimeout(TIMEOUT_MILLIS);
	    		 	    connection.setReadTimeout(TIMEOUT_MILLIS);
	    		 	    connection.setRequestMethod("HEAD");
	    		 	    Log.d(TAG, "Connecting to test internet connection");
	    		 	    connection.connect();
	    		 	    checkUrlSuccess = (connection.getResponseCode() == HttpURLConnection.HTTP_OK);
	    		 	    Log.d(TAG, "Url test result for:"+urlString+" is "+checkUrlSuccess);
			    	} catch (IOException e) {
			    		Log.i(TAG, "No internet connection");
			    		checkUrlSuccess = false;
			    	} finally {
			    		if (connection!=null) {
			    			connection.disconnect();
			    		}
			    	}
				}
			}
			
			CheckUrlThread checkThread = new CheckUrlThread();
			checkThread.start();
			checkThread.join(TIMEOUT_MILLIS);
			isAvailable = checkThread.checkUrlSuccess;
    	} catch (InterruptedException e) {
    		Log.e(TAG, "Interrupted waiting for url check to complete", e);
    	}
 	    return isAvailable;
    }

	public static void ensureDirExists(File dir) {
		if (!dir.exists() || !dir.isDirectory()) {
			dir.mkdirs();
		}
	}

	public static boolean deleteDirectory(File path) {
		Log.d(TAG, "Deleting directory:"+path.getAbsolutePath());
		if (path.exists()) {
			if (path.isDirectory()) {
				File[] files = path.listFiles();
				for (int i = 0; i < files.length; i++) {
					if (files[i].isDirectory()) {
						deleteDirectory(files[i]);
					} else {
						files[i].delete();
						Log.d(TAG, "Deleted "+files[i]);
					}
				}
			}
			boolean deleted = path.delete();
			if (!deleted) {
				Log.w(TAG, "Failed to delete:"+path.getAbsolutePath());
			}
			return deleted;
		}
		return false;
	}

	public static Properties loadProperties(File propertiesFile) {
		Properties properties = new Properties();
		if (propertiesFile.exists()) {
			FileInputStream in = null;
			try {
            	in = new FileInputStream(propertiesFile);
            	properties.load(in);
			} catch (Exception e) {
				Log.e(TAG, "Error loading properties", e);
			} finally {
            	IOUtil.close(in);
			}
		}
		return properties;
	}
	
    public static void pause(int seconds) {
   		pauseMillis(seconds*1000);
    }
    public static void pauseMillis(int millis) {
    	try {
    		Thread.sleep(millis);
    	} catch (Exception e) {
    		Log.e(TAG, "Error sleeping", e);
    	}
    }
    
    public static boolean isPortrait() {
    	return BibleApplication.getApplication().getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
    }

    public static String getLocalePref() {
    	return getSharedPreferences().getString("locale_pref", "");
    }
    
	/** get preferences used by User Prefs screen
	 * 
	 * @return
	 */
	public static SharedPreferences getSharedPreferences() {
		return PreferenceManager.getDefaultSharedPreferences(BibleApplication.getApplication().getApplicationContext());
	}

	public static String getSharedPreference(String key, String defaultValue) {
    	return getSharedPreferences().getString(key, defaultValue);
	}
	public static void saveSharedPreference(String key, String value) {
		SharedPreferences prefs = CommonUtils.getSharedPreferences();
		prefs.edit()
			.putString(key, value)
			.commit();
	}
	
	public static String getResourceString(int resourceId, Object... formatArgs) {
		return BibleApplication.getApplication().getResources().getString(resourceId, formatArgs);
	}

	public static int getResourceInteger(int resourceId) {
		return BibleApplication.getApplication().getResources().getInteger(resourceId);
	}
	
	public static boolean getResourceBoolean(int resourceId) {
		return BibleApplication.getApplication().getResources().getBoolean(resourceId);
	}

	public static int getResourceColor(int resourceId) {
		return BibleApplication.getApplication().getResources().getColor(resourceId);
	}
	/**
	 * convert dip measurements to pixels
	 */
	public static int convertDipsToPx(int dips) {
		// Converts 14 dip into its equivalent px
		float scale = BibleApplication.getApplication().getResources().getDisplayMetrics().density;
		return (int) ( dips * scale + 0.5f );
	}
	
	/**
	 * convert dip measurements to pixels
	 */
	public static int convertPxToDips(int px) {
		float scale = BibleApplication.getApplication().getResources().getDisplayMetrics().density;
		return Math.round(px/scale);
	}

	/**
	 * StringUtils methods only compare with a single char and hence create lots
	 * of temporary Strings This method compares with all chars and just creates
	 * one new string for each original string. This is to minimise memory
	 * overhead & gc.
	 * 
	 * @param str
	 * @param removeChars
	 * @return
	 */
	public static String remove(String str, char[] removeChars) {
		if (StringUtils.isEmpty(str)
				|| !StringUtils.containsAny(str, removeChars)) {
			return str;
		}

		StringBuilder r = new StringBuilder(str.length());
		// for all chars in string
		for (int i = 0; i < str.length(); i++) {
			char strCur = str.charAt(i);

			// compare with all chars to be removed
			boolean matched = false;
			for (int j = 0; j < removeChars.length && !matched; j++) {
				if (removeChars[j] == strCur) {
					matched = true;
				}
			}
			// if current char does not match any in the list then add it to the
			if (!matched) {
				r.append(strCur);
			}
		}
		return r.toString();
	}
	
	public static Date getTruncatedDate() {
		return DateUtils.truncate(new Date(), Calendar.DAY_OF_MONTH);
	}
	
	/** format seconds duration as h:m:s
	 * 
	 * @param secs duration
	 * @return h:m:s
	 */
	public static String getHoursMinsSecs(long secs) {
		int h = (int) (secs / 3600);
		int m = (int) ((secs / 60) % 60);
		int s = (int) (secs % 60);
		
		StringBuilder hms = new StringBuilder();
		if (h>0) {
			hms.append(h).append(COLON);
		}

		// add padding for 1 digit mins
		if (m<10) {
			hms.append(0);
		}
		hms.append(m).append(COLON);
		
		// add padding for 1 digit secs
		if (s<10) {
			hms.append(0);
		}
		hms.append(s);
		return hms.toString();
	}
	
	public static String getKeyDescription(Key key) {
		String name;
		try {
			name = key.getName();

			// do not show verse 0
			if (key instanceof Verse) {
				Verse verseKey = (Verse)key;
				if (verseKey.getVerse()==0 && name.endsWith("0")) {
					final String verse0 = "[\\W]0$";
					name = name.replaceAll(verse0, "");
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "Error getting key name - could that Versification does not contain book");
			// but this normally works
			name = key.getOsisRef().replace('.', ' ');
		}
		return name;
	}
	
	public static Key getWholeChapter(Verse currentVerse) {
		Log.i(TAG, "Get Chapter for:"+currentVerse.getOsisID());
		Versification versification = currentVerse.getVersification();
		BibleBook book = currentVerse.getBook();
		int chapter = currentVerse.getChapter();

		Verse targetChapterFirstVerse = new Verse(versification, book, chapter, 0);
		Verse targetChapterLastVerse = new Verse(versification, book, chapter, versification.getLastVerse(book, chapter));

		// convert to full chapter before returning because bible view is for a full chapter
		return new VerseRange(versification, targetChapterFirstVerse, targetChapterLastVerse);
	}
	
	/** enable performance adjustments for slow devices
	 */
	public static boolean isSlowDevice() {
		return Runtime.getRuntime().availableProcessors()==1;
	}
	
	/**
	 * By default popup menus do not show icons, but see the trick below
	 * http://stackoverflow.com/questions/6805756/is-it-possible-to-display-icons-in-a-popupmenu
	 */
	public static void forcePopupMenuToShowIcons(PopupMenu popup) {
		try {
		    Field[] fields = popup.getClass().getDeclaredFields();
		    for (Field field : fields) {
		        if ("mPopup".equals(field.getName())) {
		            field.setAccessible(true);
		            Object menuPopupHelper = field.get(popup);
		            Class<?> classPopupHelper = Class.forName(menuPopupHelper.getClass().getName());
		            Method setForceIcons = classPopupHelper.getMethod("setForceShowIcon", boolean.class);
		            setForceIcons.invoke(menuPopupHelper, true);
		            break;
		        }
		    }
		} catch (Exception e) {
		    e.printStackTrace();
		}
	}
}
