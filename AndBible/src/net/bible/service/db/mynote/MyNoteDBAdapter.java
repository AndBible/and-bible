/**
 * 
 */
package net.bible.service.db.mynote;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import net.bible.service.db.CommonDatabaseHelper;
import net.bible.service.db.mynote.MyNoteDatabaseDefinition.Table;
import net.bible.service.db.mynote.MyNoteDatabaseDefinition.UserNoteColumn;

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
	
	private static final String TAG = "UserNoteDBAdapter";

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

	public MyNoteDto insertUserNote(MyNoteDto usernote) {
		// Create a new row of values to insert.
		Log.d(TAG, "about to insertUserNote: " + usernote.getKey());
        // Gets the current system time in milliseconds
        Long now = Long.valueOf(System.currentTimeMillis());

		ContentValues newValues = new ContentValues();
		newValues.put(UserNoteColumn.KEY, usernote.getKey().getOsisID());
		newValues.put(UserNoteColumn.USERNOTE, usernote.getNoteText());
		newValues.put(UserNoteColumn.LAST_UPDATED_ON, now);
		newValues.put(UserNoteColumn.CREATED_ON, now);
		
		long newId = db.insert(Table.USERNOTE, null, newValues);
		MyNoteDto newUserNote = getUserNoteDto(newId);
		return newUserNote;
	}

	public MyNoteDto updateUserNote(MyNoteDto usernote) {
		// Create a new row of values to insert.
		Log.d(TAG, "about to updateUserNote: " + usernote.getKey());
        // Gets the current system time in milliseconds
        Long now = Long.valueOf(System.currentTimeMillis());

		ContentValues newValues = new ContentValues();
		newValues.put(UserNoteColumn.KEY, usernote.getKey().getOsisID());
		newValues.put(UserNoteColumn.USERNOTE, usernote.getNoteText());
		newValues.put(UserNoteColumn.LAST_UPDATED_ON, now);
		
		long rowsUpdated = db.update(Table.USERNOTE, newValues, "_id=?", new String []{String.valueOf(usernote.getId())});
		Log.d(TAG, "Rows updated:"+rowsUpdated);
		
		return getUserNoteDto(usernote.getId());
	}

	public boolean removeUserNote(MyNoteDto usernote) {
		Log.d(TAG, "Removing user note:" + usernote.getKey());
		return db.delete(Table.USERNOTE, UserNoteColumn._ID + "=" + usernote.getId(), null) > 0;
	}

	public List<MyNoteDto> getAllUserNotes() {
		Log.d(TAG, "about to getAllUserNotes");
		List<MyNoteDto> allUserNotes = new ArrayList<MyNoteDto>();
		Cursor c = db.query(UserNoteQuery.TABLE, UserNoteQuery.COLUMNS, null, null, null, null, null);
		try {
			if (c.moveToFirst()) {
		        while (!c.isAfterLast()) {
		        	MyNoteDto usernote = getUserNoteDto(c);
		    		allUserNotes.add(usernote);
		       	    c.moveToNext();
		        }
			}
		} finally {
			Log.d(TAG, "closing db in getAllUserNotes");
	        c.close();
		}
        
		Log.d(TAG, "allUserNotes set to " + allUserNotes.size() + " item long list");
        return allUserNotes;
	}

	public MyNoteDto getUserNoteDto(long id) {
		MyNoteDto usernote = null;
		
		Cursor c = db.query(UserNoteQuery.TABLE, UserNoteQuery.COLUMNS, UserNoteColumn._ID+"=?", new String[] {String.valueOf(id)}, null, null, null);
		try {
			if (c.moveToFirst()) {
				usernote = getUserNoteDto(c);
			}
		} finally {
			c.close();
		}
		
		return usernote;
	}

	public MyNoteDto getUserNoteByKey(String key) {
		MyNoteDto usernote = null;
		
		Cursor c = db.query(UserNoteQuery.TABLE, UserNoteQuery.COLUMNS, UserNoteColumn.KEY+"=?", new String[] {key}, null, null, null);
		try {
			if (c.moveToFirst()) {
				usernote = getUserNoteDto(c);
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
	private MyNoteDto getUserNoteDto(Cursor c) {
		MyNoteDto dto = new MyNoteDto();
		try {
			Long id = c.getLong(UserNoteQuery.ID);
			dto.setId(id);
			
			String key = c.getString(UserNoteQuery.KEY);
			if (!TextUtils.isEmpty(key)) {
				dto.setKey(PassageKeyFactory.instance().getKey(key));
			}
			
			String usernote = c.getString(UserNoteQuery.USERNOTE);
			dto.setNoteText(usernote);
			
			long updated = c.getLong(UserNoteQuery.LAST_UPDATED_ON);
			dto.setLastUpdatedOn(new Date(updated));

			long created = c.getLong(UserNoteQuery.CREATED_ON);
			dto.setCreatedOn(new Date(created));
			
		} catch (NoSuchKeyException nke) {
			Log.e(TAG, "Key error", nke);
		}
		
		return dto;
	}
	
	private interface UserNoteQuery {
        final String TABLE = Table.USERNOTE;

		final String[] COLUMNS = new String[] {UserNoteColumn._ID, UserNoteColumn.KEY, UserNoteColumn.USERNOTE, UserNoteColumn.LAST_UPDATED_ON, UserNoteColumn.CREATED_ON};

        final int ID = 0;
        final int KEY = 1;
        final int USERNOTE = 2;
        final int LAST_UPDATED_ON = 3;
        final int CREATED_ON = 4;
    }	
}
