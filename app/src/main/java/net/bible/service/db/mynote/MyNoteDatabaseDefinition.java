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

/**
 * 
 */
package net.bible.service.db.mynote;

import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.util.Log;

/**
 * MyNote database definitions
 * 
 * @author John D. Lewis [balinjdl at gmail dot com]
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class MyNoteDatabaseDefinition {
	private static final String TAG = "MyNoteDatabaseDef";

	public interface Table {
		public static final String MYNOTE = "mynote";
	}

	public interface Index {
		public static final String MYNOTE_KEY = "mynote_key";
	}

	public interface Join {
	}

	public interface View {
	}

	public interface Clause {
	}

	public interface MyNoteColumn {
		public static final String _ID = BaseColumns._ID;
		public static final String KEY = "key";
		public static final String VERSIFICATION = "versification";
		public static final String MYNOTE = "mynote";
		public static final String LAST_UPDATED_ON = "last_updated_on";
		public static final String CREATED_ON = "created_on";
	}
    
	private static MyNoteDatabaseDefinition sSingleton = null;
	
    public static synchronized MyNoteDatabaseDefinition getInstance() {
        if (sSingleton == null) {
            sSingleton = new MyNoteDatabaseDefinition();
        }
        return sSingleton;
    }

    /**
     * Private constructor, callers except unit tests should obtain an instance through
     * { link #getInstance(android.content.Context)} instead.
     */
    private MyNoteDatabaseDefinition() {
    }
	
	/** Called when no database exists in disk and the helper class needs
     *  to create a new one. 
     */
	public void onCreate(SQLiteDatabase db) {
		bootstrapDB(db);
	}

	@SuppressWarnings("SyntaxError")
	private void bootstrapDB(SQLiteDatabase db) {
		Log.i(TAG, "Bootstrapping And Bible database (MyNotes)");

		db.execSQL("CREATE TABLE " + Table.MYNOTE + " (" +
        		MyNoteColumn._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
        		MyNoteColumn.KEY + " TEXT NOT NULL, " +
        		MyNoteColumn.VERSIFICATION + " TEXT," +
        		MyNoteColumn.MYNOTE + " TEXT NOT NULL, " +
        		MyNoteColumn.LAST_UPDATED_ON + " INTEGER," +
        		MyNoteColumn.CREATED_ON + " INTEGER" +
        ");");

		// create an index on key
		db.execSQL("CREATE INDEX " + Index.MYNOTE_KEY +" ON "+Table.MYNOTE+"(" +
        		MyNoteColumn.KEY +
        ");");
	}
	
	public void upgradeToVersion3(SQLiteDatabase db) {
		Log.i(TAG, "Upgrading MyNote db to version 3");
		db.execSQL("ALTER TABLE " + Table.MYNOTE + " ADD COLUMN " + MyNoteColumn.VERSIFICATION + " TEXT;");
	}


}
