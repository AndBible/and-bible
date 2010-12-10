package net.bible.service.db.bookmark;

import java.util.ArrayList;
import java.util.List;

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

	public BookmarkDto insertBookmark(ContentValues newValues) {
		long newId = db.insert(Table.BOOKMARK, null, newValues);
		BookmarkDto newBookmark = getBookmark(newId);
		return newBookmark;
	}

	public boolean removeBookmark(BookmarkDto bookmark) {
		return db.delete(Table.BOOKMARK, BookmarkColumn._ID + "=" + bookmark.getId(), null) > 0;
	}

	public List<BookmarkDto> getAllBookmarks() {
		List<BookmarkDto> allBookmarks = new ArrayList<BookmarkDto>();
		Cursor c = db.query(BookmarkQuery.TABLE, BookmarkQuery.COLUMNS, null, null, null, null, null);
		try {
			if (c.moveToFirst()) {
		        while (!c.isAfterLast()) {
		    		BookmarkDto bookmark = getBookmarkDto(c);
		    		allBookmarks.add(bookmark);
		       	    c.moveToNext();
		        }
			}
		} finally {
	        c.close();
		}
        
        return allBookmarks;
	}

	public BookmarkDto getBookmark(long id) {
		BookmarkDto bookmark = null;
		
		Cursor c = db.query(BookmarkQuery.TABLE, BookmarkQuery.COLUMNS, BookmarkColumn._ID+"=?", new String[] {String.valueOf(id)}, null, null, null);
		try {
			if (c.moveToFirst()) {
				bookmark = getBookmarkDto(c);
			}
		} finally {
			c.close();
		}
		
		return bookmark;
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
	
	/** return Dto from current cursor position or null
	 * @param c
	 * @return
	 * @throws NoSuchKeyException
	 */
	private BookmarkDto getBookmarkDto(Cursor c) {
		BookmarkDto dto = new BookmarkDto();
		try {
			System.out.println("Num cols in cursor ="+c.getColumnCount());
			for (int i=0; i<c.getColumnCount(); i++) {
				System.out.println(i+" "+c.getColumnName(i));
			}
			Long id = c.getLong(BookmarkQuery.ID);
			dto.setId(id);
			
			String key = c.getString(BookmarkQuery.KEY);
			if (!TextUtils.isEmpty(key)) {
				dto.setKey(PassageKeyFactory.instance().getKey(key));
			}
		} catch (NoSuchKeyException nke) {
			Log.e(TAG, "Key error", nke);
		}
		
		return dto;
	}

	
	private interface BookmarkQuery {
        final String TABLE = Table.BOOKMARK;

		final String[] COLUMNS = new String[] {BookmarkColumn._ID, BookmarkColumn.KEY};

        final int ID = 0;
        final int KEY = 1;
    }
}
