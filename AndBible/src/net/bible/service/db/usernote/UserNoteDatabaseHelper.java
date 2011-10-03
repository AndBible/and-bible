/**
 * 
 */
package net.bible.service.db.usernote;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

/**
 * @author John D. Lewis
 *
 */
public class UserNoteDatabaseHelper extends SQLiteOpenHelper {
	private static final String TAG = "UserNoteDatabaseHelper";
	static final int DATABASE_VERSION = 1;
	private static final String DATABASE_NAME = "andBibleDatabaseUserNotes.db";

	public interface Table {
		public static final String USERNOTE = "usernote";
	}

	public interface Join {
	}

	public interface View {
	}

	public interface Clause {
	}

	public interface UserNoteColumn {
		public static final String _ID = BaseColumns._ID;
		public static final String KEY = "key";
		public static final String USERNOTE = "usernote";
	}

	private final Context mContext; // TODO: is mContext really needed? JDL
    
	private static UserNoteDatabaseHelper sSingleton = null;
	
    public static synchronized UserNoteDatabaseHelper getInstance(Context context) {
        if (sSingleton == null) {
            sSingleton = new UserNoteDatabaseHelper(context);
        }
        return sSingleton;
    }

    /**
     * Private constructor, callers except unit tests should obtain an instance through
     * {@link #getInstance(android.content.Context)} instead.
     */
    UserNoteDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        mContext = context;
    }
	
	/** Called when no database exists in disk and the helper class needs
     *  to create a new one. 
     */
	@Override
	public void onCreate(SQLiteDatabase db) {
		bootstrapDB(db);
	}
	
	private void bootstrapDB(SQLiteDatabase db) {
		Log.i(TAG, "Bootstrapping And Bible database (UserNotes)");
		
        String createSql = "CREATE TABLE " + Table.USERNOTE + " (" +
        		UserNoteColumn._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
        		UserNoteColumn.KEY + " TEXT NOT NULL, " +
        		UserNoteColumn.USERNOTE + " TEXT NOT NULL" +
        ");";

		Log.d(TAG, "Creating database table: " + createSql); // TODO: Remove in cleanup JDL
        db.execSQL(createSql);
		Log.d(TAG, "Done creating database table"); // TODO: Remove in cleanup JDL
	}
	
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		Log.i(TAG, "Upgrading DB from version " + oldVersion + " to "
				+ newVersion);
		try {
//			if (oldVersion < 1) {
//				upgradeToVersion1(db); // From 50 or 51
//				oldVersion = 1;
//			}
//			if (oldVersion == 2) {
//				upgradeToVersion2(db);
//				oldVersion += 1;
//			}
		} catch (SQLiteException e) {
			Log.e(TAG, "onUpgrade: SQLiteException, recreating db. " + e);
//todo			dropTables(db);
			bootstrapDB(db);
			return; // this was lossy
		}
	}
}
