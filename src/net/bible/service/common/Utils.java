package net.bible.service.common;

import java.io.File;

import android.util.Log;

public class Utils {

	private static final int DEFAULT_MAX_TEXT_LENGTH = 250;
	private static final String ELLIPSIS = "...";

	private static final String TAG = "Utils"; 
	static private boolean isAndroid;
	
	//todo have to finish implementing switchable logging here
	static {
		try {
			Class.forName("android.util.Log");
			isAndroid = true;
		} catch (ClassNotFoundException cnfe) {
			isAndroid = false;
		}
		System.out.println("isAndroid:"+isAndroid);
	}

	public static boolean isAndroid() {
		return isAndroid;
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

}
