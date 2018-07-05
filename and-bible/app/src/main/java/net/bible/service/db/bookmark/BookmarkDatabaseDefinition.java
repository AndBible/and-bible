package net.bible.service.db.bookmark;

import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.util.Log;


/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class BookmarkDatabaseDefinition {
	
	private static final String TAG = "BookmarkDatabaseDefn";

	public interface Table {
		String BOOKMARK = "bookmark";
		
		// many-to-many cross-reference/join table between bookmark and label
		String BOOKMARK_LABEL = "bookmark_label";
		
		String LABEL = "label";
	}

	public interface Join {
		// http://stackoverflow.com/questions/973790/sql-multiple-join-on-many-to-many-tables-comma-separation
		String BOOKMARK_JOIN_LABEL = "bookmark "
				+ "JOIN bookmark_label ON (groups.package_id = packages._id)";

	}

	public interface View {
	}

	public interface Clause {
	}

	public interface BookmarkColumn {
		String _ID = BaseColumns._ID;
		String KEY = "key";
		String VERSIFICATION = "versification";
		String CREATED_ON = "created_on";
		String PLAYBACK_SETTINGS = "speak_settings";
	}

	public interface BookmarkLabelColumn {
		String BOOKMARK_ID = "bookmark_id";
		String LABEL_ID = "label_id";
	}
	
	public interface LabelColumn {
		String _ID = BaseColumns._ID;
		String NAME = "name";
		String BOOKMARK_STYLE = "bookmark_style";
	}

	private static BookmarkDatabaseDefinition sSingleton = null;
	
	
    public static synchronized BookmarkDatabaseDefinition getInstance() {
        if (sSingleton == null) {
            sSingleton = new BookmarkDatabaseDefinition();
        }
        return sSingleton;
    }

	/** Called when no database exists in disk and the helper class needs
     *  to create a new one. 
     */
	public void onCreate(SQLiteDatabase db) {
		bootstrapDB(db);
	}

	public void upgradeToVersion5(SQLiteDatabase db) {
		Log.i(TAG, "Upgrading Bookmark db to version 5");
		db.execSQL("ALTER TABLE " + Table.BOOKMARK + " ADD COLUMN " + BookmarkColumn.PLAYBACK_SETTINGS + " TEXT DEFAULT null;");
	}

	public void upgradeToVersion4(SQLiteDatabase db) {
		Log.i(TAG, "Upgrading Bookmark db to version 4");
		db.execSQL("ALTER TABLE " + Table.LABEL + " ADD COLUMN " + LabelColumn.BOOKMARK_STYLE + " TEXT;");
	}

	public void upgradeToVersion3(SQLiteDatabase db) {
		Log.i(TAG, "Upgrading Bookmark db to version 3");
		db.execSQL("ALTER TABLE " + Table.BOOKMARK + " ADD COLUMN " + BookmarkColumn.VERSIFICATION + " TEXT;");
		db.execSQL("ALTER TABLE " + Table.BOOKMARK + " ADD COLUMN " + BookmarkColumn.CREATED_ON + " INTEGER DEFAULT 0;");
	}

	private void bootstrapDB(SQLiteDatabase db) {
		Log.i(TAG, "Bootstrapping And Bible database (Bookmarks)");
		
        db.execSQL("CREATE TABLE " + Table.BOOKMARK + " (" +
                BookmarkColumn._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                BookmarkColumn.KEY + " TEXT NOT NULL," +
                BookmarkColumn.VERSIFICATION + " TEXT," +
                BookmarkColumn.CREATED_ON + " INTEGER DEFAULT 0," +
                BookmarkColumn.PLAYBACK_SETTINGS + " TEXT DEFAULT NULL" +
        ");");

        // Intersection table
        db.execSQL("CREATE TABLE " + Table.BOOKMARK_LABEL + " (" +
                BookmarkLabelColumn.BOOKMARK_ID + " INTEGER NOT NULL," +
                BookmarkLabelColumn.LABEL_ID + " INTEGER NOT NULL" +
        ");");

        db.execSQL("CREATE TABLE " + Table.LABEL + " (" +
                LabelColumn._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                LabelColumn.NAME + " TEXT NOT NULL," +
				LabelColumn.BOOKMARK_STYLE + " TEXT" +
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
}
