/**
 * 
 */
package net.bible.service.db.mynote;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import net.bible.service.db.CommonDatabaseHelper;
import net.bible.service.db.mynote.MyNoteDatabaseDefinition.MyNoteColumn;
import net.bible.service.db.mynote.MyNoteDatabaseDefinition.Table;

import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.NoSuchKeyException;
import org.crosswire.jsword.passage.PassageKeyFactory;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;
import android.util.Log;

/**
 * MyNote database update methods
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author John D. Lewis [balinjdl at gmail dot com]
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class MyNoteDBAdapter {

	// Variable to hold the database instance
	private SQLiteDatabase db;

	// Database open/upgrade helper
	private SQLiteOpenHelper dbHelper;
	
	private static final String TAG = "MyNoteDBAdapter";

	public MyNoteDBAdapter() {
		dbHelper =  CommonDatabaseHelper.getInstance(); 
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

	public MyNoteDto insertMyNote(MyNoteDto mynote) {
		// Create a new row of values to insert.
		Log.d(TAG, "about to insertMyNote: " + mynote.getKey());
        // Gets the current system time in milliseconds
        Long now = Long.valueOf(System.currentTimeMillis());

		ContentValues newValues = new ContentValues();
		newValues.put(MyNoteColumn.KEY, mynote.getKey().getOsisID());
		newValues.put(MyNoteColumn.MYNOTE, mynote.getNoteText());
		newValues.put(MyNoteColumn.LAST_UPDATED_ON, now);
		newValues.put(MyNoteColumn.CREATED_ON, now);
		
		long newId = db.insert(Table.MYNOTE, null, newValues);
		MyNoteDto newMyNote = getMyNoteDto(newId);
		return newMyNote;
	}

	public MyNoteDto updateMyNote(MyNoteDto mynote) {
		// Create a new row of values to insert.
		Log.d(TAG, "about to updateMyNote: " + mynote.getKey());
        // Gets the current system time in milliseconds
        Long now = Long.valueOf(System.currentTimeMillis());

		ContentValues newValues = new ContentValues();
		newValues.put(MyNoteColumn.KEY, mynote.getKey().getOsisID());
		newValues.put(MyNoteColumn.MYNOTE, mynote.getNoteText());
		newValues.put(MyNoteColumn.LAST_UPDATED_ON, now);
		
		long rowsUpdated = db.update(Table.MYNOTE, newValues, "_id=?", new String []{String.valueOf(mynote.getId())});
		Log.d(TAG, "Rows updated:"+rowsUpdated);
		
		return getMyNoteDto(mynote.getId());
	}

	public boolean removeMyNote(MyNoteDto mynote) {
		Log.d(TAG, "Removing my note:" + mynote.getKey());
		return db.delete(Table.MYNOTE, MyNoteColumn._ID + "=" + mynote.getId(), null) > 0;
	}

	public List<MyNoteDto> getAllMyNotes() {
		Log.d(TAG, "about to getAllMyNotes");
		List<MyNoteDto> allMyNotes = new ArrayList<MyNoteDto>();
		Cursor c = db.query(MyNoteQuery.TABLE, MyNoteQuery.COLUMNS, null, null, null, null, null);
		try {
			if (c.moveToFirst()) {
		        while (!c.isAfterLast()) {
		        	MyNoteDto mynote = getMyNoteDto(c);
		    		allMyNotes.add(mynote);
		       	    c.moveToNext();
		        }
			}
		} finally {
	        c.close();
		}
        
		Log.d(TAG, "allMyNotes set to " + allMyNotes.size() + " item long list");
        return allMyNotes;
	}
	
	public List<MyNoteDto> getMyNotesInPassage(Key passage) {
		Log.d(TAG, "about to getMyNotesInPassage:"+passage.getOsisID());
		List<MyNoteDto> notesList = new ArrayList<MyNoteDto>();
		Cursor c = db.query(MyNoteQuery.TABLE, MyNoteQuery.COLUMNS, MyNoteColumn.KEY+" LIKE ?", new String []{String.valueOf(passage.getOsisID()+"%")}, null, null, null);
		try {
			if (c.moveToFirst()) {
		        while (!c.isAfterLast()) {
		        	MyNoteDto mynote = getMyNoteDto(c);
		    		notesList.add(mynote);
		       	    c.moveToNext();
		        }
			}
		} finally {
	        c.close();
		}
        
		Log.d(TAG, "myNotesInPassage set to " + notesList.size() + " item long list");
        return notesList;
	}

	public MyNoteDto getMyNoteDto(long id) {
		MyNoteDto mynote = null;
		
		Cursor c = db.query(MyNoteQuery.TABLE, MyNoteQuery.COLUMNS, MyNoteColumn._ID+"=?", new String[] {String.valueOf(id)}, null, null, null);
		try {
			if (c.moveToFirst()) {
				mynote = getMyNoteDto(c);
			}
		} finally {
			c.close();
		}
		
		return mynote;
	}

	public MyNoteDto getMyNoteByKey(String key) {
		MyNoteDto mynote = null;
		
		Cursor c = db.query(MyNoteQuery.TABLE, MyNoteQuery.COLUMNS, MyNoteColumn.KEY+"=?", new String[] {key}, null, null, null);
		try {
			if (c.moveToFirst()) {
				mynote = getMyNoteDto(c);
			}
		} finally {
			c.close();
		}
		
		return mynote;
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
			
			String mynote = c.getString(MyNoteQuery.MYNOTE);
			dto.setNoteText(mynote);
			
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
        final String TABLE = Table.MYNOTE;

		final String[] COLUMNS = new String[] {MyNoteColumn._ID, MyNoteColumn.KEY, MyNoteColumn.MYNOTE, MyNoteColumn.LAST_UPDATED_ON, MyNoteColumn.CREATED_ON};

        final int ID = 0;
        final int KEY = 1;
        final int MYNOTE = 2;
        final int LAST_UPDATED_ON = 3;
        final int CREATED_ON = 4;
    }	
}
