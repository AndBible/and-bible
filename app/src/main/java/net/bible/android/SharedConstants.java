/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.android;

import java.io.File;

import android.os.Environment;

/** Not used much yet but need to move the some of the more generic constants here
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
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
		return BibleApplication.Companion.getApplication().getExternalFilesDir(null);
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
