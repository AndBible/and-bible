/**
 * 
 */
package net.bible.service.db.mynote;

import net.bible.service.db.bookmark.BookmarkDatabaseDefinition.BookmarkLabelColumn;
import net.bible.service.db.bookmark.BookmarkDatabaseDefinition.Table;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.util.Log;

/**
 * @author John D. Lewis
 *
 */
public class MyNoteDatabaseDefinition {
	private static final String TAG = "MyNoteDatabaseDefinition";

	public interface Table {
		public static final String USERNOTE = "usernote";
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
		public static final String USERNOTE = "usernote";
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
     * {@link #getInstance(android.content.Context)} instead.
     */
    private MyNoteDatabaseDefinition() {
    }
	
	/** Called when no database exists in disk and the helper class needs
     *  to create a new one. 
     */
	public void onCreate(SQLiteDatabase db) {
		bootstrapDB(db);
	}
	
	private void bootstrapDB(SQLiteDatabase db) {
		Log.i(TAG, "Bootstrapping And Bible database (MyNotes)");
		
		db.execSQL("CREATE TABLE " + Table.USERNOTE + " (" +
        		MyNoteColumn._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
        		MyNoteColumn.KEY + " TEXT NOT NULL, " +
        		MyNoteColumn.USERNOTE + " TEXT NOT NULL, " +
        		MyNoteColumn.LAST_UPDATED_ON + " INTEGER," +
        		MyNoteColumn.CREATED_ON + " INTEGER" +
        ");");
	}
}
