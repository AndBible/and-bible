package net.bible.android.util;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

import android.util.Log;

public class CommonUtil {

	private static final String TAG = "CommonUtil"; 
	
    public static boolean isInternetAvailable() {
    	// I found this snippet here: http://www.anddev.org/solved_checking_internet_connection-t5194.html
//		ConnectivityManager connec = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
//
//		return (connec.getNetworkInfo(0).getState() == NetworkInfo.State.CONNECTED);
    	try {
    		// might as well test for the url we need to access
	    	String testUrl = "http://www.crosswire.org/ftpmirror/pub/sword/packages/rawzip/";
	 	    URL url = new URL(testUrl);
	 	         
	 	    URLConnection connection;
	 	    connection = url.openConnection();
	 	    connection.connect();
	 	    return true;
    	} catch (IOException e) {
    		Log.i(TAG, "No internet connection");
    		return false;
    	}
    }
    
    public static void pause(int seconds) {
    	try {
    		Thread.sleep(seconds*1000);
    	} catch (Exception e) {
    		Log.e(TAG, "error sleeping", e);
    	}
    }
}
