/**
 * 
 */
package net.bible.android.control.mynote;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.service.common.CommonUtils;
import net.bible.service.db.mynote.MyNoteDBAdapter;
import net.bible.service.db.mynote.MyNoteDto;

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
	
	private static final String TAG = "MyNoteControl";

	public int getAddEditMenuText() {
		// current note is linked to current bible verse
		Key currentVerse = CurrentPageManager.getInstance().getCurrentBible().getSingleKey();

		MyNoteDto myNote = getMyNoteByKey(currentVerse);
		
		if (myNote!=null) {
			return R.string.mynote_view_edit;
		} else {
			return R.string.mynote_add;
		}
	}

	@Override
	public void showNoteView(MyNoteDto noteDto) {
		ControlFactory.getInstance().getCurrentPageControl().showMyNote(noteDto.getKey());
	}

	@Override
	public String getMyNoteTextByKey(Key verse) {
		// get a dto
		MyNoteDto myNote = getMyNoteByKey(verse);
		
		// return an empty note dto
		String noteText = "";
		if (myNote!=null) {
			noteText = myNote.getNoteText();
		}

		return noteText;
	}

	@Override
	public boolean saveMyNoteText(String myNote) {
		MyNoteDto dto = getCurrentMyNoteDto();
		dto.setNoteText(myNote);
		return saveMyNote(dto);
	}

	@Override
	public MyNoteDto getCurrentMyNoteDto() {
		Key verse = ControlFactory.getInstance().getCurrentPageControl().getCurrentMyNotePage().getKey();
		
		// get a dto
		MyNoteDto myNote = getMyNoteByKey(verse);
		
		// return an empty note dto
		if (myNote==null) {
			myNote = new MyNoteDto();
			myNote.setKey(verse);
		}

		return myNote;
	}

	/** save the note to the database if it is new or has been updated
	 */
	@Override
	public boolean saveMyNote(MyNoteDto myNoteDto) {
		Log.d(TAG, "saveMyNote started...");
		boolean isSaved = false;
		
		if (myNoteDto.isNew()) {
			if (!myNoteDto.isEmpty()) {
				myNoteDto = addMyNote(myNoteDto);
				isSaved = true;
			}
		} else {
			MyNoteDto oldNote = getMyNoteByKey(myNoteDto.getKey());
			// delete empty notes
			if (myNoteDto.isEmpty()) {
				deleteMyNote(myNoteDto);
			} else if (!myNoteDto.equals(oldNote)) {
				// update changed notes
				updateMyNote(myNoteDto);
				isSaved = true;
			}
		}
		if (isSaved) {
			Toast.makeText(BibleApplication.getApplication().getApplicationContext(), R.string.mynote_saved, Toast.LENGTH_SHORT).show();
		}
		return isSaved;
	}

	@Override
	public String getMyNoteText(MyNoteDto myNote, boolean abbreviated) {
		String text = "";
		try {
			text = myNote.getNoteText();
			if (abbreviated) {
				//TODO allow longer lines if portrait or tablet
				boolean singleLine = true;
				text = CommonUtils.limitTextLength(text, 40, singleLine);
			}
		} catch (Exception e) {
			Log.e(TAG, "Error getting user note text", e);
		}
		return text;
	}

	// pure myNote methods

	/** get all myNotes */
	public List<MyNoteDto> getAllMyNotes() {
		MyNoteDBAdapter db = new MyNoteDBAdapter();
		db.open();
		List<MyNoteDto> myNoteList = null;
		try {
			myNoteList = db.getAllMyNotes();
			Collections.sort(myNoteList);
		} finally {
			db.close();
		}

		return myNoteList;
	}

	/** get all user notes */
	public MyNoteDto getMyNoteById(Long id) {
		MyNoteDBAdapter db = new MyNoteDBAdapter();
		db.open();
		MyNoteDto myNote = null;
		try {
			myNote = db.getMyNoteDto(id);
		} finally {
			db.close();
		}

		return myNote;
	}

	/** get user note with this key if it exists or return null */
	public MyNoteDto getMyNoteByKey(Key key) {
		MyNoteDBAdapter db = new MyNoteDBAdapter();
		db.open();
		MyNoteDto myNote = null;
		try {
			myNote = db.getMyNoteByKey(key.getOsisID());
		} finally {
			db.close();
		}

		return myNote;
	}

	/** delete this user note (and any links to labels) */
	public boolean deleteMyNote(MyNoteDto myNote) {
		boolean bOk = false;
		if (myNote!=null && myNote.getId()!=null) {
			MyNoteDBAdapter db = new MyNoteDBAdapter();
			db.open();
			bOk = db.removeMyNote(myNote);
		}		
		return bOk;
	}

	/** create a new myNote */
	private MyNoteDto addMyNote(MyNoteDto myNote) {
		MyNoteDBAdapter db = new MyNoteDBAdapter();
		db.open();
		MyNoteDto newMyNote = null;
		try {
			newMyNote = db.insertMyNote(myNote);
		} finally {
			db.close();
		}
		return newMyNote;
	}

	/** create a new myNote */
	private MyNoteDto updateMyNote(MyNoteDto myNote) {
		MyNoteDBAdapter db = new MyNoteDBAdapter();
		db.open();
		MyNoteDto updatedMyNote = null;
		try {
			updatedMyNote = db.updateMyNote(myNote);
		} finally {
			db.close();
		}
		return updatedMyNote;
	}

	@Override
	public List<Key> getKeysWithNotesInPassage(Key passage) {
		MyNoteDBAdapter db = new MyNoteDBAdapter();
		db.open();
		List<MyNoteDto> myNoteList = null;
		try {
			myNoteList = db.getMyNotesInPassage(passage);
			Collections.sort(myNoteList);
		} finally {
			db.close();
		}

		List<Key> keysWithNotes = new ArrayList<Key>();
		if (myNoteList!=null) {
			for (MyNoteDto myNoteDto : myNoteList) {
				keysWithNotes.add(myNoteDto.getKey());
			}
		}
		return keysWithNotes;
	}
}
