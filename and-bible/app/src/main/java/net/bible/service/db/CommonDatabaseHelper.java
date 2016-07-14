package net.bible.service.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import net.bible.android.BibleApplication;
import net.bible.service.db.bookmark.BookmarkDatabaseDefinition;
import net.bible.service.db.mynote.MyNoteDatabaseDefinition;

/**
 * Oversee database creation and upgrade based on version
 * There is a single And Bible database but creation and upgrade is implemented by the different db modules
 *  
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class CommonDatabaseHelper extends SQLiteOpenHelper {
	private static final String TAG = "CommonDatabaseHelper";
	static final int DATABASE_VERSION = 4;
	public static final String DATABASE_NAME = "andBibleDatabase.db";

	private static CommonDatabaseHelper sSingleton = null;
	
    public static synchronized CommonDatabaseHelper getInstance() {
        if (sSingleton == null) {
            sSingleton = new CommonDatabaseHelper(BibleApplication.getApplication().getApplicationContext());
        }
        return sSingleton;
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
		} catch (SQLiteException e) {
			Log.e(TAG, "onUpgrade: SQLiteException. " + e);
//TODO allow complete recreation if error - too scared to do this!
//			Log.e(TAG, "onUpgrade: SQLiteException, recreating db. " + e);
//			dropTables(db);
//			bootstrapDB(db);
		}
	}

}
