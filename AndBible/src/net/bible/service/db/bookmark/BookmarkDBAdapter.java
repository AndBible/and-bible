package net.bible.service.db.bookmark;

import java.util.ArrayList;
import java.util.List;

import net.bible.service.db.bookmark.BookmarkDatabaseHelper.BookmarkColumn;
import net.bible.service.db.bookmark.BookmarkDatabaseHelper.BookmarkLabelColumn;
import net.bible.service.db.bookmark.BookmarkDatabaseHelper.LabelColumn;
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

	public BookmarkDto insertBookmark(BookmarkDto bookmark) {
		// Create a new row of values to insert.
		ContentValues newValues = new ContentValues();
		newValues.put(BookmarkColumn.KEY, bookmark.getKey().getName());

		long newId = db.insert(Table.BOOKMARK, null, newValues);
		BookmarkDto newBookmark = getBookmarkDto(newId);
		return newBookmark;
	}

	public boolean removeBookmark(BookmarkDto bookmark) {
		Log.d(TAG, "Removing bookmark:"+bookmark.getKey());
		return db.delete(Table.BOOKMARK, BookmarkColumn._ID + "=" + bookmark.getId(), null) > 0;
	}

	public boolean removeLabel(LabelDto label) {
		Log.d(TAG, "Removing label:"+label.getName());
		return db.delete(Table.LABEL, LabelColumn._ID + "=" + label.getId(), null) > 0;
	}

	public LabelDto insertLabel(LabelDto label) {
		// Create a new row of values to insert.
		ContentValues newValues = new ContentValues();
		newValues.put(LabelColumn.NAME, label.getName());

		long newId = db.insert(Table.LABEL, null, newValues);
		LabelDto newLabel = getLabelDto(newId);
		return newLabel;
	}

	public boolean removeBookmarkLabelJoin(BookmarkDto bookmark, LabelDto label) {
		return db.delete(Table.BOOKMARK_LABEL, BookmarkLabelColumn.BOOKMARK_ID + "=" + bookmark.getId()+" AND "+BookmarkLabelColumn.LABEL_ID + "=" + label.getId(), null) > 0;
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

	public List<BookmarkDto> getBookmarksWithLabel(LabelDto label) {
		String sql = "SELECT bookmark._id, bookmark.key "+
					 "FROM bookmark "+
					 "JOIN bookmark_label ON (bookmark._id = bookmark_label.bookmark_id) "+
					 "JOIN label ON (bookmark_label.label_id = label._id) "+
					 "WHERE label._id = ? ";
		
		List<BookmarkDto> allBookmarks = new ArrayList<BookmarkDto>();
		String[] args = new String[] {label.getId().toString()};
		Cursor c = db.rawQuery(sql, args);
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

	public List<LabelDto> getAllLabels() {
		List<LabelDto> allLabels = new ArrayList<LabelDto>();
		Cursor c = db.query(LabelQuery.TABLE, LabelQuery.COLUMNS, null, null, null, null, null);
		try {
			if (c.moveToFirst()) {
		        while (!c.isAfterLast()) {
		    		LabelDto bookmark = getLabelDto(c);
		    		allLabels.add(bookmark);
		       	    c.moveToNext();
		        }
			}
		} finally {
	        c.close();
		}
        
        return allLabels;
	}

	public List<LabelDto> getBookmarkLabels(BookmarkDto bookmark) {
		String sql = "SELECT label._id, label.name "+
					 "FROM label "+
					 "JOIN bookmark_label ON (label._id = bookmark_label.label_id) "+
					 "JOIN bookmark ON (bookmark_label.bookmark_id = bookmark._id) "+
					 "WHERE bookmark._id = ?";
		
		List<LabelDto> labels = new ArrayList<LabelDto>();
		String[] args = new String[] {bookmark.getId().toString()};
		Cursor c = db.rawQuery(sql, args);
		try {
			if (c.moveToFirst()) {
		        while (!c.isAfterLast()) {
		    		LabelDto label = getLabelDto(c);
		    		labels.add(label);
		       	    c.moveToNext();
		        }
			}
		} finally {
	        c.close();
		}
        
        return labels;
	}

	public void insertBookmarkLabelJoin(BookmarkDto bookmark, LabelDto label) {
		// Create a new row of values to insert.
		ContentValues newValues = new ContentValues();
		newValues.put(BookmarkLabelColumn.BOOKMARK_ID, bookmark.getId());
		newValues.put(BookmarkLabelColumn.LABEL_ID, label.getId());

		long newId = db.insert(Table.BOOKMARK_LABEL, null, newValues);
		Log.d(TAG, "*** inserted new label with id "+newId);
	}

	public BookmarkDto getBookmarkDto(long id) {
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

	public LabelDto getLabelDto(long id) {
		LabelDto label = null;
		
		Cursor c = db.query(LabelQuery.TABLE, LabelQuery.COLUMNS, LabelColumn._ID+"=?", new String[] {String.valueOf(id)}, null, null, null);
		try {
			if (c.moveToFirst()) {
				label = getLabelDto(c);
			}
		} finally {
			c.close();
		}
		
		return label;
	}

	/** return Dto from current cursor position or null
	 * @param c
	 * @return
	 * @throws NoSuchKeyException
	 */
	private LabelDto getLabelDto(Cursor c) {
		LabelDto dto = new LabelDto();

		System.out.println("Num cols in label cursor ="+c.getColumnCount());
		for (int i=0; i<c.getColumnCount(); i++) {
			System.out.println("label col: "+i+" "+c.getColumnName(i));
		}
		Long id = c.getLong(LabelQuery.ID);
		dto.setId(id);
		
		String name = c.getString(LabelQuery.NAME);
		dto.setName(name);
		
		return dto;
	}
	
	private interface BookmarkQuery {
        final String TABLE = Table.BOOKMARK;

		final String[] COLUMNS = new String[] {BookmarkColumn._ID, BookmarkColumn.KEY};

        final int ID = 0;
        final int KEY = 1;
    }
	private interface LabelQuery {
        final String TABLE = Table.LABEL;

		final String[] COLUMNS = new String[] {LabelColumn._ID, LabelColumn.NAME};

        final int ID = 0;
        final int NAME = 1;
    }
}
