/**
 * 
 */
package net.bible.service.db.usernote;

import java.util.ArrayList;
import java.util.List;

import net.bible.service.db.CommonDatabaseHelper;
import net.bible.service.db.usernote.UserNoteDatabaseDefinition.Table;
import net.bible.service.db.usernote.UserNoteDatabaseDefinition.UserNoteColumn;

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
public class UserNoteDBAdapter {

	// Variable to hold the database instance
	private SQLiteDatabase db;

	// Database open/upgrade helper
	private SQLiteOpenHelper dbHelper;
	
	private static final String TAG = "UserNoteDBAdapter";

	public UserNoteDBAdapter(Context _context) {
		dbHelper =  CommonDatabaseHelper.getInstance(_context); 
		Log.d(TAG, "got dbHelper: " + dbHelper.toString());
	}

	public UserNoteDBAdapter open() throws SQLException {
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

	public UserNoteDto insertUserNote(UserNoteDto usernote) {
		// Create a new row of values to insert.
		Log.d(TAG, "about to insertUserNote: " + usernote.getKey() + "; note text = " + usernote.getNoteText());
		ContentValues newValues = new ContentValues();
		newValues.put(UserNoteColumn.KEY, usernote.getKey().getOsisID());
		newValues.put(UserNoteColumn.USERNOTE, usernote.getNoteText());
		
		Log.d(TAG, "--> newValues = " + newValues.toString());
		
		long newId = db.insert(Table.USERNOTE, null, newValues);
		UserNoteDto newUserNote = getUserNoteDto(newId);
		Log.d(TAG, "about to leave insertUserNote after adding newUserNote: " + newUserNote.getKey() + ": " + newUserNote.getNoteText()); // TODO: Remove in cleanup JDL
		return newUserNote;
	}

	public boolean removeUserNote(UserNoteDto usernote) {
		Log.d(TAG, "Removing user note:" + usernote.getKey());
		return db.delete(Table.USERNOTE, UserNoteColumn._ID + "=" + usernote.getId(), null) > 0;
	}

	public List<UserNoteDto> getAllUserNotes() {
		Log.d(TAG, "about to getAllUserNotes");
		List<UserNoteDto> allUserNotes = new ArrayList<UserNoteDto>();
		Cursor c = db.query(UserNoteQuery.TABLE, UserNoteQuery.COLUMNS, null, null, null, null, null);
		try {
			if (c.moveToFirst()) {
		        while (!c.isAfterLast()) {
		        	UserNoteDto usernote = getUserNoteDto(c);
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

	public UserNoteDto getUserNoteDto(long id) {
		UserNoteDto usernote = null;
		
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

	public UserNoteDto getUserNoteByKey(String key) {
		UserNoteDto usernote = null;
		
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
	private UserNoteDto getUserNoteDto(Cursor c) {
		UserNoteDto dto = new UserNoteDto();
		try {
			Long id = c.getLong(UserNoteQuery.ID);
			dto.setId(id);
			
			String key = c.getString(UserNoteQuery.KEY);
			if (!TextUtils.isEmpty(key)) {
				dto.setKey(PassageKeyFactory.instance().getKey(key));
			}
			
			String usernote = c.getString(UserNoteQuery.USERNOTE);
			dto.setNoteText(usernote);
			
		} catch (NoSuchKeyException nke) {
			Log.e(TAG, "Key error", nke);
		}
		
		return dto;
	}
	
	private interface UserNoteQuery {
        final String TABLE = Table.USERNOTE;

		final String[] COLUMNS = new String[] {UserNoteColumn._ID, UserNoteColumn.KEY, UserNoteColumn.USERNOTE};

        final int ID = 0;
        final int KEY = 1;
        final int USERNOTE = 2;
    }	
}
