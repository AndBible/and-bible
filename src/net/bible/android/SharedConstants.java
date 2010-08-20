package net.bible.android;

import java.io.File;

import android.os.Environment;

/** Not used much yet but need to move the some of the more generic constants here
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class SharedConstants {

    /**
     * Forms storage path
     */
    public static final String MODULE_PATH = getModulePath();

    /**
     * Identifies the location of the form used to launch form entry
     */
    public static final String FOLDERPATH_KEY = "FOLDER_PATH";
    
    
    public static final int APPLICATION_THEME =  android.R.style.Theme_Light;
    
    static private String getModulePath() {
		File sdcard = Environment.getExternalStorageDirectory();
    	return sdcard+"/jsword";
    }
    
}