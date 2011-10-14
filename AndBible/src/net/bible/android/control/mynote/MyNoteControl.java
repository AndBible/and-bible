/**
 * 
 */
package net.bible.android.control.mynote;

import java.util.Collections;
import java.util.List;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.service.common.CommonUtils;
import net.bible.service.db.mynote.MyNoteDBAdapter;
import net.bible.service.db.mynote.MyNoteDto;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.passage.Key;

import android.util.Log;
import android.widget.Toast;

/**
 * User Note controller methods
 *
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author John D. Lewis [balinjdl at gmail dot com]
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class MyNoteControl implements MyNote {
	
	private static final String TAG = "UserNoteControl";

	@Override
	public void editStarted() {
		ControlFactory.getInstance().getCurrentPageControl().setShowingMyNote(true);
	}
	
	@Override
	public boolean saveUsernoteCurrentVerse(String usertext) {
		Log.d(TAG, "usernoteCurrentVerse started...");
		
		boolean bOk = false;
		if (CurrentPageManager.getInstance().isBibleShown() || CurrentPageManager.getInstance().isCommentaryShown()) {
			Log.d(TAG, "usernoteCurrentVerse: either Bible or Commentary is showing...");
			
			Key currentVerse = CurrentPageManager.getInstance().getCurrentBible().getSingleKey();

			MyNoteDto usernoteDto = new MyNoteDto();
			
			if (getUserNoteByKey(currentVerse)!=null) {
				// user note for this verse already exists
				Log.d(TAG, "usernoteCurrentVerse: Already have a note for this passage, so delete the existing note first before adding a replacement note");
//				Toast.makeText(BibleApplication.getApplication().getApplicationContext(), R.string.usernote_exists, Toast.LENGTH_SHORT).show();
				
				usernoteDto = getUserNoteByKey(currentVerse);
				deleteUserNote(usernoteDto);
			} else {
				Log.d(TAG, "usernoteCurrentVerse: Creating a new note for this passage...");
			}				

			// prepare new user note and add to db
			usernoteDto = new MyNoteDto();
			usernoteDto.setKey(currentVerse);
			usernoteDto.setNoteText(usertext);
			Log.d(TAG, "usernoteDto in UserNoteControl; UserNote text = " + usernoteDto.getNoteText());
			MyNoteDto newUserNote = addUserNote(usernoteDto);
			
			if (newUserNote!=null) {
				Log.i(TAG, "usernoteCurrentVerse: New note successfully created for this passage!");
				// success
				Toast.makeText(BibleApplication.getApplication().getApplicationContext(), R.string.mynote_saved, Toast.LENGTH_SHORT).show();
				bOk = true;
			} else {
				Log.e(TAG, "usernoteCurrentVerse: Error adding a new note for this passage");
				Dialogs.getInstance().showErrorMsg(R.string.error_occurred);
			}
		}
		return bOk;
	}

	@Override
	public String getUserNoteText(MyNoteDto usernote, boolean abbreviated) {
		String text = "";
		try {
			text = usernote.getNoteText();
			if (abbreviated) {
				// get first line but limit length in case there are no line breaks
				text = StringUtils.substringBefore(text,"\n");
				//TODO allow longer lines if portrait or tablet
				text = CommonUtils.limitTextLength(text, 40);
			}
		} catch (Exception e) {
			Log.e(TAG, "Error getting user note text", e);
		}
		return text;
	}

	// pure usernote methods

	/** get all usernotes */
	public List<MyNoteDto> getAllUserNotes() {
		MyNoteDBAdapter db = new MyNoteDBAdapter(BibleApplication.getApplication().getApplicationContext());
		db.open();
		List<MyNoteDto> usernoteList = null;
		try {
			usernoteList = db.getAllUserNotes();
			Collections.sort(usernoteList);
		} finally {
			db.close();
		}

		return usernoteList;
	}

	/** create a new usernote */
	public MyNoteDto addUserNote(MyNoteDto usernote) {
		MyNoteDBAdapter db = new MyNoteDBAdapter(BibleApplication.getApplication().getApplicationContext());
		db.open();
		MyNoteDto newUserNote = null;
		try {
			newUserNote = db.insertUserNote(usernote);
		} finally {
			db.close();
		}
		return newUserNote;
	}

	/** get all user notes */
	public MyNoteDto getUserNoteById(Long id) {
		MyNoteDBAdapter db = new MyNoteDBAdapter(BibleApplication.getApplication().getApplicationContext());
		db.open();
		MyNoteDto usernote = null;
		try {
			usernote = db.getUserNoteDto(id);
		} finally {
			db.close();
		}

		return usernote;
	}

	/** get user note with this key if it exists or return null */
	public MyNoteDto getUserNoteByKey(Key key) {
		MyNoteDBAdapter db = new MyNoteDBAdapter(BibleApplication.getApplication().getApplicationContext());
		db.open();
		MyNoteDto usernote = null;
		try {
			usernote = db.getUserNoteByKey(key.getOsisID());
		} finally {
			db.close();
		}

		return usernote;
	}

	/** delete this user note (and any links to labels) */
	public boolean deleteUserNote(MyNoteDto usernote) {
		boolean bOk = false;
		if (usernote!=null && usernote.getId()!=null) {
			MyNoteDBAdapter db = new MyNoteDBAdapter(BibleApplication.getApplication().getApplicationContext());
			db.open();
			bOk = db.removeUserNote(usernote);
		}		
		return bOk;
	}
}
