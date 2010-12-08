package net.bible.service.db.bookmark;

import net.bible.service.db.bookmark.BookmarkDatabaseHelper.Table;
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

	// The index (key) column name for use in where clauses.
	public static final String BOOKMARK_ID = "_id";

	// The name and column index of each column in your database.
	public static final String BOOKMARK_KEY = "key";
	public static final String BOOKMARK_BOOK = "book";

	// SQL Statement to create a new database.
	private static final String DATABASE_CREATE = "create table "
			+ Table.BOOKMARK + " (" + BOOKMARK_ID
			+ " integer primary key autoincrement, " + BOOKMARK_KEY
			+ " text not null, " + BOOKMARK_BOOK + " text not null);";

	// Variable to hold the database instance
	private SQLiteDatabase db;
	// Context of the application using the database.
	private final Context context;
	// Database open/upgrade helper
	private SQLiteOpenHelper dbHelper;

	public BookmarkDBAdapter(Context _context) {
		context = _context;
		dbHelper =  BookmarkDatabaseHelper.getInstance(context); 
	}

	public BookmarkDBAdapter open() throws SQLException {
		SQLiteDatabase db;
		try {
			db = dbHelper.getWritableDatabase();
		} catch (SQLiteException ex) {
			db = dbHelper.getReadableDatabase();
		}
		return this;
	}

	public void close() {
		db.close();
	}

	public long insertEntry(ContentValues newValues) {

		// Insert the row into your table
		return db.insert(Table.BOOKMARK, null, newValues);
	}

	public boolean removeEntry(long _rowIndex) {
		return db.delete(Table.BOOKMARK, BOOKMARK_ID + "=" + _rowIndex, null) > 0;
	}

	public Cursor getAllEntries() {
		return db.query(Table.BOOKMARK, new String[] { BOOKMARK_ID,
				BOOKMARK_KEY, BOOKMARK_BOOK }, null, null, null, null, null);
	}

	// public BookmarkDto getEntry(long _rowIndex) {
	// // Return a cursor to a row from the database and
	// // use the values to populate an instance of MyObject
	// return objectInstance;
	// }

	// public boolean updateEntry(long _rowIndex, MyObject _myObject) {
	// // TODO: Create a new ContentValues based on the new object
	// // and use it to update a row in the database.
	// return true;
	// }


}
