package net.bible.service.db.bookmark;

import net.bible.service.db.bookmark.BookmarkDatabaseHelper.BookmarkColumn;
import net.bible.service.db.bookmark.BookmarkDatabaseHelper.Table;

import org.crosswire.jsword.passage.NoSuchKeyException;
import org.crosswire.jsword.passage.PassageKeyFactory;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;
import android.util.Log;

public class BookmarkDBAdapter {

	// Variable to hold the database instance
	private SQLiteDatabase db;

	// Database open/upgrade helper
	private SQLiteOpenHelper dbHelper;
	
	private static final String TAG = "BookmarkDBAdapter";

	public BookmarkDBAdapter(Context _context) {
		dbHelper =  BookmarkDatabaseHelper.getInstance(_context); 
	}

	public BookmarkDBAdapter open() throws SQLException {
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

	public long insertBookmark(ContentValues newValues) {
		long id = db.insert(Table.BOOKMARK, null, newValues);
		return id;
	}

	public boolean removeBookmark(long id) {
		return db.delete(Table.BOOKMARK, BookmarkColumn._ID + "=" + id, null) > 0;
	}

	public Cursor getAllBookmarks() {
		return db.query(BookmarkQuery.TABLE, BookmarkQuery.COLUMNS, null, null, null, null, null);
	}

	public Cursor getBookmark(long id) {
		Cursor c = db.query(BookmarkQuery.TABLE, BookmarkQuery.COLUMNS, BookmarkColumn._ID+"=?", new String[] {String.valueOf(id)}, null, null, null);
		return c;
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
	
	public BookmarkDto getBookmarkDto(long id) {
		BookmarkDto dto = null;
		try {
			Cursor c = getBookmark(id);
			dto = new BookmarkDto();
			dto.setId(id);
			if (c.moveToFirst()) {
				String key = c.getString(BookmarkQuery.KEY);
				if (!TextUtils.isEmpty(key)) {
					dto.setKey(PassageKeyFactory.instance().getKey(key));
				}
			}
		} catch (NoSuchKeyException nke) {
			Log.e(TAG, "Key error", nke);
		}

		return dto;
	}

	
	private interface BookmarkQuery {
        String TABLE = Table.BOOKMARK;

		String[] COLUMNS = new String[] {BookmarkColumn._ID, BookmarkColumn.KEY};

        int ID = 0;
        int KEY = 1;
    }
}
