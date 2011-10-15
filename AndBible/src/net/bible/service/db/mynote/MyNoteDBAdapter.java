/**
 * 
 */
package net.bible.service.db.mynote;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import net.bible.service.db.CommonDatabaseHelper;
import net.bible.service.db.mynote.MyNoteDatabaseDefinition.Table;
import net.bible.service.db.mynote.MyNoteDatabaseDefinition.MyNoteColumn;

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

/**
 * @author John D. Lewis
 *
 * Based on corresponding Bookmark class(es)
 * 
 */
public class MyNoteDBAdapter {

	// Variable to hold the database instance
	private SQLiteDatabase db;

	// Database open/upgrade helper
	private SQLiteOpenHelper dbHelper;
	
	private static final String TAG = "MyNoteDBAdapter";

	public MyNoteDBAdapter(Context _context) {
		dbHelper =  CommonDatabaseHelper.getInstance(_context); 
		Log.d(TAG, "got dbHelper: " + dbHelper.toString());
	}

	public MyNoteDBAdapter open() throws SQLException {
		try {
			Log.d(TAG, "about to getWritableDatabase");
			db = dbHelper.getWritableDatabase();
		} catch (SQLiteException ex) {
			Log.d(TAG, "about to getReadableDatabase");
			db = dbHelper.getReadableDatabase();
		}
		return this;
	}

	public void close() {
		db.close();
	}

	public MyNoteDto insertMyNote(MyNoteDto usernote) {
		// Create a new row of values to insert.
		Log.d(TAG, "about to insertMyNote: " + usernote.getKey());
        // Gets the current system time in milliseconds
        Long now = Long.valueOf(System.currentTimeMillis());

		ContentValues newValues = new ContentValues();
		newValues.put(MyNoteColumn.KEY, usernote.getKey().getOsisID());
		newValues.put(MyNoteColumn.USERNOTE, usernote.getNoteText());
		newValues.put(MyNoteColumn.LAST_UPDATED_ON, now);
		newValues.put(MyNoteColumn.CREATED_ON, now);
		
		long newId = db.insert(Table.USERNOTE, null, newValues);
		MyNoteDto newMyNote = getMyNoteDto(newId);
		return newMyNote;
	}

	public MyNoteDto updateMyNote(MyNoteDto usernote) {
		// Create a new row of values to insert.
		Log.d(TAG, "about to updateMyNote: " + usernote.getKey());
        // Gets the current system time in milliseconds
        Long now = Long.valueOf(System.currentTimeMillis());

		ContentValues newValues = new ContentValues();
		newValues.put(MyNoteColumn.KEY, usernote.getKey().getOsisID());
		newValues.put(MyNoteColumn.USERNOTE, usernote.getNoteText());
		newValues.put(MyNoteColumn.LAST_UPDATED_ON, now);
		
		long rowsUpdated = db.update(Table.USERNOTE, newValues, "_id=?", new String []{String.valueOf(usernote.getId())});
		Log.d(TAG, "Rows updated:"+rowsUpdated);
		
		return getMyNoteDto(usernote.getId());
	}

	public boolean removeMyNote(MyNoteDto usernote) {
		Log.d(TAG, "Removing user note:" + usernote.getKey());
		return db.delete(Table.USERNOTE, MyNoteColumn._ID + "=" + usernote.getId(), null) > 0;
	}

	public List<MyNoteDto> getAllMyNotes() {
		Log.d(TAG, "about to getAllMyNotes");
		List<MyNoteDto> allMyNotes = new ArrayList<MyNoteDto>();
		Cursor c = db.query(MyNoteQuery.TABLE, MyNoteQuery.COLUMNS, null, null, null, null, null);
		try {
			if (c.moveToFirst()) {
		        while (!c.isAfterLast()) {
		        	MyNoteDto usernote = getMyNoteDto(c);
		    		allMyNotes.add(usernote);
		       	    c.moveToNext();
		        }
			}
		} finally {
			Log.d(TAG, "closing db in getAllMyNotes");
	        c.close();
		}
        
		Log.d(TAG, "allMyNotes set to " + allMyNotes.size() + " item long list");
        return allMyNotes;
	}

	public MyNoteDto getMyNoteDto(long id) {
		MyNoteDto usernote = null;
		
		Cursor c = db.query(MyNoteQuery.TABLE, MyNoteQuery.COLUMNS, MyNoteColumn._ID+"=?", new String[] {String.valueOf(id)}, null, null, null);
		try {
			if (c.moveToFirst()) {
				usernote = getMyNoteDto(c);
			}
		} finally {
			c.close();
		}
		
		return usernote;
	}

	public MyNoteDto getMyNoteByKey(String key) {
		MyNoteDto usernote = null;
		
		Cursor c = db.query(MyNoteQuery.TABLE, MyNoteQuery.COLUMNS, MyNoteColumn.KEY+"=?", new String[] {key}, null, null, null);
		try {
			if (c.moveToFirst()) {
				usernote = getMyNoteDto(c);
			}
		} finally {
			c.close();
		}
		
		return usernote;
	}
	
	/** return Dto from current cursor position or null
	 * @param c
	 * @return
	 * @throws NoSuchKeyException
	 */
	private MyNoteDto getMyNoteDto(Cursor c) {
		MyNoteDto dto = new MyNoteDto();
		try {
			Long id = c.getLong(MyNoteQuery.ID);
			dto.setId(id);
			
			String key = c.getString(MyNoteQuery.KEY);
			if (!TextUtils.isEmpty(key)) {
				dto.setKey(PassageKeyFactory.instance().getKey(key));
			}
			
			String usernote = c.getString(MyNoteQuery.USERNOTE);
			dto.setNoteText(usernote);
			
			long updated = c.getLong(MyNoteQuery.LAST_UPDATED_ON);
			dto.setLastUpdatedOn(new Date(updated));

			long created = c.getLong(MyNoteQuery.CREATED_ON);
			dto.setCreatedOn(new Date(created));
			
		} catch (NoSuchKeyException nke) {
			Log.e(TAG, "Key error", nke);
		}
		
		return dto;
	}
	
	private interface MyNoteQuery {
        final String TABLE = Table.USERNOTE;

		final String[] COLUMNS = new String[] {MyNoteColumn._ID, MyNoteColumn.KEY, MyNoteColumn.USERNOTE, MyNoteColumn.LAST_UPDATED_ON, MyNoteColumn.CREATED_ON};

        final int ID = 0;
        final int KEY = 1;
        final int USERNOTE = 2;
        final int LAST_UPDATED_ON = 3;
        final int CREATED_ON = 4;
    }	
}
