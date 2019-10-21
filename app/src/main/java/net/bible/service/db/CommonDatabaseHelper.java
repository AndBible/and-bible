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

package net.bible.service.db;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import net.bible.android.BibleApplication;
import net.bible.service.db.bookmark.BookmarkDatabaseDefinition;
import net.bible.service.db.mynote.MyNoteDatabaseDefinition;
import net.bible.service.db.readingplan.ReadingPlanDatabaseOperations;

/**
 * Oversee database creation and upgrade based on version
 * There is a single And Bible database but creation and upgrade is implemented by the different db modules
 *  
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class CommonDatabaseHelper extends SQLiteOpenHelper {
	private static final String TAG = "CommonDatabaseHelper";
	static final int DATABASE_VERSION = 6;
	public static final String DATABASE_NAME = "andBibleDatabase.db";

	private static CommonDatabaseHelper sSingleton = null;
	
    public static synchronized CommonDatabaseHelper getInstance() {
        if (sSingleton == null) {
            sSingleton = new CommonDatabaseHelper(BibleApplication.Companion.getApplication().getApplicationContext());
        }
        return sSingleton;
    }

    public static void sync() {
		// Sync all data so far into database file
		Cursor cur = getInstance().getWritableDatabase()
				.rawQuery("PRAGMA wal_checkpoint(FULL)", null);
		cur.moveToFirst();
		cur.close();
	}

    public static void reset() {
    	sSingleton = null;
	}

    /**
     * Private constructor, callers except unit tests should obtain an instance through
     * {@link #getInstance()} instead.
     */
    CommonDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }
	
	/** Called when no database exists in disk and the helper class needs
     *  to create a new one. 
     */
	@Override
	public void onCreate(SQLiteDatabase db) {
		BookmarkDatabaseDefinition.getInstance().onCreate(db);
		MyNoteDatabaseDefinition.getInstance().onCreate(db);
	}
	
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		Log.i(TAG, "Upgrading DB from version " + oldVersion + " to " + newVersion);
		try {
			if (oldVersion < 1) {
				BookmarkDatabaseDefinition.getInstance().onCreate(db);
				oldVersion = 1;
			}
			if (oldVersion == 1) {
				MyNoteDatabaseDefinition.getInstance().onCreate(db);
				oldVersion += 1;
			}
			if (oldVersion == 2) {
				BookmarkDatabaseDefinition.getInstance().upgradeToVersion3(db);
				MyNoteDatabaseDefinition.getInstance().upgradeToVersion3(db);
				oldVersion += 1;
			}
			if (oldVersion == 3) {
				BookmarkDatabaseDefinition.getInstance().upgradeToVersion4(db);
				oldVersion += 1;
			}
			if (oldVersion == 4) {
				BookmarkDatabaseDefinition.getInstance().upgradeToVersion5(db);
				oldVersion += 1;
			}
			if (oldVersion == 5) {
				ReadingPlanDatabaseOperations.Companion.getInstance().onCreate(db);
				oldVersion += 1;
			}
		} catch (SQLiteException e) {
			Log.e(TAG, "onUpgrade: SQLiteException. " + e);
//TODO allow complete recreation if error - too scared to do this!
//			Log.e(TAG, "onUpgrade: SQLiteException, recreating db. " + e);
//			dropTables(db);
//			bootstrapDB(db);
		}
	}

}
