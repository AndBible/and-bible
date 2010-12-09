package net.bible.service.db.bookmark;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

//http://android.git.kernel.org/?p=platform/packages/providers/ContactsProvider.git;a=blob;f=src/com/android/providers/contacts/ContactsDatabaseHelper.java;hb=HEAD
public class BookmarkDatabaseHelper extends SQLiteOpenHelper {
	private static final String TAG = "BookmarkDatabaseHelper";
	static final int DATABASE_VERSION = 1;
	private static final String DATABASE_NAME = "andBibleDatabase.db";

	public interface Table {
		public static final String BOOKMARK = "bookmark";
		
		// many-to-many cross-reference/join table between bookmark and label
		public static final String BOOKMARK_LABEL = "bookmark_label";
		
		public static final String LABEL = "label";
	}

	public interface Join {
		// http://stackoverflow.com/questions/973790/sql-multiple-join-on-many-to-many-tables-comma-separation
		public static final String BOOKMARK_JOIN_LABEL = "bookmark "
				+ "JOIN bookmark_label ON (groups.package_id = packages._id)";

	}

	public interface View {
	}

	public interface Clause {
	}

	public interface BookmarkColumn {
		public static final String _ID = Table.BOOKMARK + "." + BaseColumns._ID;
		public static final String KEY = "key";
	}

	public interface BookmarkLabelColumn {
		public static final String BOOKMARK_ID = Table.BOOKMARK + "." + BaseColumns._ID;
		public static final String LABEL_ID = Table.LABEL + "." + BaseColumns._ID;
	}
	
	public interface LabelColumn {
		public static final String _ID = Table.LABEL + "." + BaseColumns._ID;
		public static final String NAME = "name";

	}

	private final Context mContext;
    
	private static BookmarkDatabaseHelper sSingleton = null;
	
	
    public static synchronized BookmarkDatabaseHelper getInstance(Context context) {
        if (sSingleton == null) {
            sSingleton = new BookmarkDatabaseHelper(context);
        }
        return sSingleton;
    }

    /**
     * Private constructor, callers except unit tests should obtain an instance through
     * {@link #getInstance(android.content.Context)} instead.
     */
    BookmarkDatabaseHelper(Context context) {
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
		Log.i(TAG, "Bootstrapping And Bible database");
		
        db.execSQL("CREATE TABLE " + Table.BOOKMARK + " (" +
                BookmarkColumn._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                BookmarkColumn.KEY + " TEXT NOT NULL" +
        ");");

        // Intersection table
        db.execSQL("CREATE TABLE " + Table.BOOKMARK_LABEL + " (" +
                BookmarkLabelColumn.BOOKMARK_ID + " INTEGER NOT NULL," +
                BookmarkLabelColumn.LABEL_ID + " INTEGER NOT NULL" +
        ");");

        db.execSQL("CREATE TABLE " + Table.LABEL + " (" +
                LabelColumn._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                LabelColumn.NAME + " TEXT NOT NULL" +
        ");");
		
        // SQLite version in android 1.6 is 3.5.9 which doesn't support foreign keys so use a trigger
	    // Trigger to remove join table rows when either side of the join is deleted
        db.execSQL("CREATE TRIGGER bookmark_cleanup DELETE ON "+Table.BOOKMARK+" " +
                "BEGIN " +
                "DELETE FROM "+Table.BOOKMARK_LABEL+" WHERE "+BookmarkLabelColumn.BOOKMARK_ID+" = old._id;" +
                "END");
        db.execSQL("CREATE TRIGGER label_cleanup DELETE ON "+Table.LABEL+" " +
                "BEGIN " +
                "DELETE FROM "+Table.BOOKMARK_LABEL+" WHERE "+BookmarkLabelColumn.LABEL_ID+" = old._id;" +
                "END");
	}
	
	@Override
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
