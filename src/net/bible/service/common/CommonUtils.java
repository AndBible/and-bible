package net.bible.service.common;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import net.bible.android.BibleApplication;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.util.Log;

public class CommonUtils {

	private static final int DEFAULT_MAX_TEXT_LENGTH = 250;
	private static final String ELLIPSIS = "...";

	private static final String TAG = "CommonUtils"; 
	static private boolean isAndroid = false;
	
	//todo have to finish implementing switchable logging here
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
		String versionName = null;
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

	public static long getSDCardMegsFree() {
		StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
		long bytesAvailable = (long)stat.getBlockSize() *(long)stat.getAvailableBlocks();
		long megAvailable = bytesAvailable / 1048576;
		Log.d(TAG, "Megs available on SD card :"+megAvailable);
		return megAvailable;
	}
	
	/** shorten text for display in lists etc.
	 * 
	 * @param text
	 * @return
	 */
	public static String limitTextLength(String text) {
		if (text!=null && text.length()>DEFAULT_MAX_TEXT_LENGTH) {
			// break on a space rather than mid-word
			int cutPoint = text.indexOf(" ", DEFAULT_MAX_TEXT_LENGTH);
			if (cutPoint >= DEFAULT_MAX_TEXT_LENGTH) {
				text = text.substring(0, cutPoint+1)+ELLIPSIS;
			}
		}
		return text;
	}
	
    public static boolean isInternetAvailable() {
    	String testUrl = "http://www.crosswire.org/ftpmirror/pub/sword/packages/rawzip/";
    	return CommonUtils.isHttpUrlAvailable(testUrl);
    }

    public static boolean isHttpUrlAvailable(String urlString) {
 	    HttpURLConnection connection = null;
    	try {
    		// might as well test for the url we need to access
	 	    URL url = new URL(urlString);
	 	         
	 	    Log.d(TAG, "Opening test connection");
	 	    connection = (HttpURLConnection)url.openConnection();
	 	    connection.setConnectTimeout(3000);
	 	    Log.d(TAG, "Connecting to test internet connection");
	 	    connection.connect();
	 	    boolean success = (connection.getResponseCode() == HttpURLConnection.HTTP_OK);
	 	    Log.d(TAG, "Url test result for:"+urlString+" is "+success);
	 	    return success;
    	} catch (IOException e) {
    		Log.i(TAG, "No internet connection");
    		return false;
    	} finally {
    		if (connection!=null) {
    			connection.disconnect();
    		}
    	}
    }

	static public boolean deleteDirectory(File path) {
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

    public static void pause(int seconds) {
    	try {
    		Thread.sleep(seconds*1000);
    	} catch (Exception e) {
    		Log.e(TAG, "error sleeping", e);
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
}
