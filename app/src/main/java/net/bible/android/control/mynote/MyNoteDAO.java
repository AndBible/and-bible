package net.bible.android.control.mynote;

import net.bible.android.activity.R;
import net.bible.android.control.ApplicationScope;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.service.db.mynote.MyNoteDBAdapter;
import net.bible.service.db.mynote.MyNoteDto;

import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;
import org.crosswire.jsword.passage.Verse;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.inject.Inject;

/**
 * MYNote Data access object
 */
@ApplicationScope
public class MyNoteDAO {

	@Inject
	public MyNoteDAO() {
	}

	public String getMyNoteTextByKey(Key verseRange) {
		// get a dto
		MyNoteDto myNote = getMyNoteByStartVerse(verseRange);

		// return an empty note dto
		String noteText = "";
		if (myNote!=null) {
			noteText = myNote.getNoteText();
		}

		return noteText;
	}

	/**
	 * get all myNotes
	 * @param sortOrder
	 */
	public List<MyNoteDto> getAllMyNotes(MyNoteSortOrder sortOrder) {
		MyNoteDBAdapter db = new MyNoteDBAdapter();
		List<MyNoteDto> myNoteList = null;
		try {
			db.open();
			myNoteList = db.getAllMyNotes();
			myNoteList = getSortedMyNotes(myNoteList, sortOrder);
		} finally {
			db.close();
		}

		return myNoteList;
	}

	/**
	 * get user note with this key if it exists or return null
	 */
	public MyNoteDto getMyNoteByStartVerse(Key key) {
		Verse startVerse = KeyUtil.getVerse(key);

		MyNoteDBAdapter db = new MyNoteDBAdapter();
		MyNoteDto myNote = null;
		try {
			db.open();
			myNote = db.getMyNoteByStartVerse(startVerse.getOsisRef());
		} finally {
			db.close();
		}

		return myNote;
	}

	/**
	 * delete this user note (and any links to labels)
	 */
	public boolean deleteMyNote(MyNoteDto myNote) {
		boolean bOk = false;
		if (myNote != null && myNote.getId() != null) {
			MyNoteDBAdapter db = new MyNoteDBAdapter();
			try {
				db.open();
				bOk = db.removeMyNote(myNote);
			} finally {
				db.close();
			}
		}
		return bOk;
	}

	/**
	 * create a new myNote
	 */
	MyNoteDto addMyNote(MyNoteDto myNote) {
		MyNoteDBAdapter db = new MyNoteDBAdapter();
		MyNoteDto newMyNote = null;
		try {
			db.open();
			newMyNote = db.insertMyNote(myNote);
		} finally {
			db.close();
		}
		return newMyNote;
	}

	/**
	 * create a new myNote
	 */
	MyNoteDto updateMyNote(MyNoteDto myNote) {
		MyNoteDBAdapter db = new MyNoteDBAdapter();
		MyNoteDto updatedMyNote = null;
		try {
			db.open();
			updatedMyNote = db.updateMyNote(myNote);
		} finally {
			db.close();
		}
		return updatedMyNote;
	}

	private List<MyNoteDto> getSortedMyNotes(List<MyNoteDto> myNoteList, MyNoteSortOrder sortOrder) {
		Comparator<MyNoteDto> comparator;
		switch (sortOrder) {
			case DATE_CREATED:
				comparator = new MyNoteCreationDateComparator();
				break;
			case BIBLE_BOOK:
			default:
				comparator = new MyNoteDtoBibleOrderComparator(myNoteList);
				break;
		}

		// the new Java 7 sort is stricter and occasionally generates errors, so prevent total crash on listing notes
		try {
			Collections.sort(myNoteList, comparator);
		} catch (Exception e) {
			Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e);
		}
		return myNoteList;
	}
}