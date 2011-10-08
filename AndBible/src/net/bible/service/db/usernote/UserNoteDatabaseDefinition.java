/**
 * 
 */
package net.bible.service.db.usernote;

import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.util.Log;

/**
 * @author John D. Lewis
 *
 */
public class UserNoteDatabaseDefinition {
	private static final String TAG = "UserNoteDatabaseDefinition";

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
    
	private static UserNoteDatabaseDefinition sSingleton = null;
	
    public static synchronized UserNoteDatabaseDefinition getInstance() {
        if (sSingleton == null) {
            sSingleton = new UserNoteDatabaseDefinition();
        }
        return sSingleton;
    }

    /**
     * Private constructor, callers except unit tests should obtain an instance through
     * {@link #getInstance(android.content.Context)} instead.
     */
    private UserNoteDatabaseDefinition() {
    }
	
	/** Called when no database exists in disk and the helper class needs
     *  to create a new one. 
     */
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
}
