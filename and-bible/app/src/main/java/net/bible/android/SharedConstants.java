package net.bible.android;

import java.io.File;

import android.content.Context;
import android.os.Environment;

/** Not used much yet but need to move the some of the more generic constants here
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class SharedConstants {

	public static final String PACKAGE_NAME = "net.bible.android.activity";
    /**
     * Forms storage path
     */
    public static final File MODULE_DIR = getModuleDir();
    public static final File MANUAL_INSTALL_DIR = getManualInstallDir();
	private static final String MANUAL_INSTALL_SUBDIR = "jsword";
	
    public static final String FRONTEND_NAME = "and-bible";
    public static final File FRONTEND_DATA_DIR = new File(MODULE_DIR, FRONTEND_NAME);

	private static final String FONT_SUBDIR_NAME = "fonts";
    public static final File FONT_DIR = new File(MODULE_DIR, FONT_SUBDIR_NAME);
    public static final File MANUAL_FONT_DIR = new File(MANUAL_INSTALL_DIR, FONT_SUBDIR_NAME);

    private static final String BACKUP_SUBDIR_NAME = "andbible_backup";
    public static final File BACKUP_DIR = getBackupDir();

	public static final String READINGPLAN_DIR_NAME = "readingplan";
    public static final File MANUAL_READINGPLAN_DIR = new File(MANUAL_INSTALL_DIR, READINGPLAN_DIR_NAME);

    public static final int APPLICATION_THEME =  android.R.style.Theme_Light;
	public static final String DEFAULT_STYLESHEET = "style.css";
	public static final String NIGHT_MODE_STYLESHEET = "night_mode.css";
    
    private static final String CSS_SUBDIR_NAME = "css";
    public static final File MANUAL_CSS_DIR = new File(MANUAL_INSTALL_DIR, CSS_SUBDIR_NAME);
    public static final File MANUAL_CSS_STYLESHEET = new File(MANUAL_CSS_DIR, DEFAULT_STYLESHEET);
    public static final File MANUAL_CSS_NIGHT_MODE_STYLESHEET = new File(MANUAL_CSS_DIR, NIGHT_MODE_STYLESHEET);

    public static final String LINE_SEPARATOR = System.getProperty ( "line.separator" ); 
    
    // insist on 50 free megs before downloading 
    public static final long REQUIRED_MEGS_FOR_DOWNLOADS = 50; 

    public static final int NO_VALUE = -1;
    
    /** a directory that will be deleted when the application is uninstalled (only on Android 2.2+)
     * 
     * @return
     */
    static private File getModuleDir() {
		return BibleApplication.getApplication().getExternalFilesDir(null);
    }
    
    static private File getManualInstallDir() {
		File sdcard = Environment.getExternalStorageDirectory();
    	return new File(sdcard, MANUAL_INSTALL_SUBDIR);
    }
    
    static private File getBackupDir() {
		File sdcard = Environment.getExternalStorageDirectory();
    	return new File(sdcard, BACKUP_SUBDIR_NAME);
    }
}