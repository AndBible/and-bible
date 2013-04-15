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
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author John D. Lewis [balinjdl at gmail dot com]
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class MyNoteDatabaseDefinition {
	private static final String TAG = "MyNoteDatabaseDefinition";

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
