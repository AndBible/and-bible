package net.bible.android;

import java.io.File;

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
    
    
    public static final int APPLICATION_THEME =  1;
    
    public static final String LINE_SEPARATOR = System.getProperty ( "line.separator" ); 
    
    /** a directory that will be deleted when the application is uninstalled (only on Android 2.2+)
     * 
     * @return
     */
    static private File getModuleDir() {
    	// see here: http://developer.android.com/guide/topics/data/data-storage.html#filesExternal
    	// On api level >=8 this is just Environment.getExternalFilesDir()
    	//
    	// If you're using API Level 7 or lower, use getExternalStorageDirectory(), to open a File representing the root of the external storage. 
    	// You should then write your data in the following directory:
		return new File("./temp");
    }
    
    static private File getManualInstallDir() {
		return new File("./temp");
    }
    
}