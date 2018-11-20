/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

package net.bible.android.control.backup;

import android.os.Environment;
import android.util.Log;
import android.view.Menu;

import net.bible.android.SharedConstants;
import net.bible.android.activity.R;
import net.bible.android.control.ApplicationScope;
import net.bible.android.view.activity.base.Callback;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.service.common.FileManager;
import net.bible.service.db.CommonDatabaseHelper;

import java.io.File;

import javax.inject.Inject;

/**
 * Support backup and restore of the And bible database which contains bookmarks and notes.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
public class BackupControl {
	
	// this is now unused because And Bible databases are held on the SD card to facilitate easier backup by file copy
	private static final File internalDbDir = new File(Environment.getDataDirectory(), "/data/"+SharedConstants.PACKAGE_NAME+"/databases/");

	private static final String TAG = "BackupControl";

	@Inject
	public BackupControl() {
	}

	public void updateOptionsMenu(Menu menu) {
		// always allow backup and restore to be attempted
	}

	/** backup database to sd card
	 */
	public void backupDatabase() {
		boolean ok = FileManager.copyFile(CommonDatabaseHelper.DATABASE_NAME, internalDbDir, SharedConstants.BACKUP_DIR);

		if (ok) {
			Log.d(TAG, "Copied database to SD card successfully");
			Dialogs.getInstance().showMsg(R.string.backup_success, SharedConstants.BACKUP_DIR.getName());
		} else {
			Log.e(TAG, "Error copying database to SD card");
			Dialogs.getInstance().showErrorMsg(R.string.error_occurred);
		}
	}
	
	/** restore database from sd card
	 */
	public void restoreDatabase() {
		if (!isBackupFileExists()) {
			Dialogs.getInstance().showErrorMsg(R.string.error_no_backup_file);
		} else {
			Dialogs.getInstance().showMsg(R.string.restore_confirmation, true, new Callback() {
				@Override
				public void okay() {
					boolean ok = FileManager.copyFile(CommonDatabaseHelper.DATABASE_NAME, SharedConstants.BACKUP_DIR, internalDbDir);

					if (ok) {
						Log.d(TAG, "Copied database from SD card successfully");
						Dialogs.getInstance().showMsg(R.string.restore_success, SharedConstants.BACKUP_DIR.getName());
					} else {
						Log.e(TAG, "Error copying database from SD card");
						Dialogs.getInstance().showErrorMsg(R.string.error_occurred);
					}
				}
			});
		}
	}
	
	/** return true if a backup has been done and the file is on the sd card.
	 */
	private boolean isBackupFileExists() {
		return new File(SharedConstants.BACKUP_DIR, CommonDatabaseHelper.DATABASE_NAME).exists();
	}
}
