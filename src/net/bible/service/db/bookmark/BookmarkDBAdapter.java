package net.bible.service.db.bookmark;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.util.Log;

public class BookmarkDBAdapter {
  private static final String DATABASE_NAME = "andBibleDatabase.db";
  private static final String BOOKMARK_TABLE = "bookmarkTable";
  private static final int DATABASE_VERSION = 1;
 
  // The index (key) column name for use in where clauses.
  public static final String BOOKMARK_ID="_id";

  // The name and column index of each column in your database.
  public static final String BOOKMARK_KEY="key"; 
  public static final String BOOKMARK_BOOK="book";
  
  // SQL Statement to create a new database.
  private static final String DATABASE_CREATE = "create table " + 
    BOOKMARK_TABLE + " (" + BOOKMARK_ID + " integer primary key autoincrement, " +
    BOOKMARK_KEY + " text not null, "+
  	BOOKMARK_BOOK + " text not null);";

  // Variable to hold the database instance
  private SQLiteDatabase db;
  // Context of the application using the database.
  private final Context context;
  // Database open/upgrade helper
  private myDbHelper dbHelper;

  public BookmarkDBAdapter(Context _context) {
    context = _context;
    dbHelper = new myDbHelper(context, DATABASE_NAME, null, DATABASE_VERSION);
  }

  public BookmarkDBAdapter open() throws SQLException {
	  SQLiteDatabase db;
	  try {
	    db = dbHelper.getWritableDatabase();
	  }
	  catch (SQLiteException ex){
	    db = dbHelper.getReadableDatabase();
	  } 
    return this;
  }

  public void close() {
      db.close();
  }

  public long insertEntry(ContentValues newValues) {

	  // Insert the row into your table
	  return db.insert(BOOKMARK_TABLE, null, newValues);
  }

  public boolean removeEntry(long _rowIndex) {
    return db.delete(BOOKMARK_TABLE, BOOKMARK_ID + "=" + _rowIndex, null) > 0;
  }

  public Cursor getAllEntries () {
    return db.query(BOOKMARK_TABLE, new String[] {BOOKMARK_ID, BOOKMARK_KEY, BOOKMARK_BOOK}, 
                    null, null, null, null, null);
  }

//  public BookmarkDto getEntry(long _rowIndex) {
//    // Return a cursor to a row from the database and
//    // use the values to populate an instance of MyObject
//    return objectInstance;
//  }

//  public boolean updateEntry(long _rowIndex, MyObject _myObject) {
//    // TODO: Create a new ContentValues based on the new object
//    // and use it to update a row in the database.
//    return true;
//  }

  private static class myDbHelper extends SQLiteOpenHelper {

    public myDbHelper(Context context, String name, 
                      CursorFactory factory, int version) {
      super(context, name, factory, version);
    }

    // Called when no database exists in disk and the helper class needs
    // to create a new one. 
    @Override
    public void onCreate(SQLiteDatabase _db) {
      _db.execSQL(DATABASE_CREATE);
    }

    // Called when there is a database version mismatch meaning that the version
    // of the database on disk needs to be upgraded to the current version.
    @Override
    public void onUpgrade(SQLiteDatabase _db, int _oldVersion, int _newVersion) {
      // Log the version upgrade.
      Log.w("TaskDBAdapter", "Upgrading from version " + 
                             _oldVersion + " to " +
                             _newVersion + ", which will destroy all old data");
        
      // Upgrade the existing database to conform to the new version. Multiple 
      // previous versions can be handled by comparing _oldVersion and _newVersion
      // values.

      // The simplest case is to drop the old table and create a new one.
      _db.execSQL("DROP TABLE IF EXISTS " + BOOKMARK_TABLE);
      // Create a new one.
      onCreate(_db);
    }
  }
}
