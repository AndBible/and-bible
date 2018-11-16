/**
 * 
 */
package net.bible.android.control.mynote;

import android.util.Log;
import android.widget.Toast;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.ApplicationScope;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider;
import net.bible.service.common.CommonUtils;
import net.bible.service.db.mynote.MyNoteDto;

import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.passage.VerseRange;
import org.crosswire.jsword.versification.Versification;

import java.util.List;

import javax.inject.Inject;

/**
 * User Note controller methods
 *
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author John D. Lewis [balinjdl at gmail dot com]
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
public class MyNoteControl {

	private final ActiveWindowPageManagerProvider activeWindowPageManagerProvider;

	private static final String MYNOTE_SORT_ORDER = "MyNoteSortOrder";

	private final MyNoteDAO myNoteDAO;

	private static final String TAG = "MyNoteControl";

	@Inject
	public MyNoteControl(ActiveWindowPageManagerProvider activeWindowPageManagerProvider, MyNoteDAO myNoteDAO) {
		this.activeWindowPageManagerProvider = activeWindowPageManagerProvider;
		this.myNoteDAO = myNoteDAO;
	}

	/**
	 * Start chain of actions to switch to MyNote view
	 * @param verseRange
	 */
	public void showMyNote(VerseRange verseRange) {
		// if existing MyNote exists with same start verse then adjust range to match the note that will be edited
		final MyNoteDto existingMyNoteWithSameStartVerse = myNoteDAO.getMyNoteByStartVerse(verseRange);
		if (existingMyNoteWithSameStartVerse!=null) {
			verseRange = existingMyNoteWithSameStartVerse.getVerseRange(verseRange.getVersification());
		}

		getCurrentPageManager().showMyNote(verseRange);
	}

	public void showNoteView(MyNoteDto noteDto) {
		getCurrentPageManager().showMyNote(noteDto.getVerseRange());
	}

	public String getMyNoteVerseKey(MyNoteDto myNote) {
		String keyText = "";
		try {
			Versification versification = getCurrentPageManager().getCurrentBible().getVersification();
			keyText = myNote.getVerseRange(versification).getName();
		} catch (Exception e) {
			Log.e(TAG, "Error getting verse text", e);
		}
		return keyText;
	}

	public boolean saveMyNoteText(String myNote) {
		MyNoteDto dto = getCurrentMyNoteDto();
		dto.setNoteText(myNote);
		return saveMyNote(dto);
	}

	public MyNoteDto getCurrentMyNoteDto() {
		//
		Key key = getCurrentPageManager().getCurrentMyNotePage().getKey();
		VerseRange verseRange;
		// The key should be a VerseRange
		if (key instanceof VerseRange) {
			verseRange = (VerseRange)key;
		} else {
			Verse verse = KeyUtil.getVerse(key);
			verseRange = new VerseRange(verse.getVersification(), verse);
		}
		
		// get a dto
		MyNoteDto myNote = myNoteDAO.getMyNoteByStartVerse(verseRange);
		
		// return an empty note dto
		if (myNote==null) {
			myNote = new MyNoteDto();
			myNote.setVerseRange(verseRange);
		}

		return myNote;
	}

	/** save the note to the database if it is new or has been updated
	 */
	public boolean saveMyNote(MyNoteDto myNoteDto) {
		Log.d(TAG, "saveMyNote started...");
		boolean isSaved = false;
		
		if (myNoteDto.isNew()) {
			if (!myNoteDto.isEmpty()) {
				myNoteDAO.addMyNote(myNoteDto);
				isSaved = true;
			}
		} else {
			MyNoteDto oldNote = myNoteDAO.getMyNoteByStartVerse(myNoteDto.getVerseRange());
			// delete empty notes
			if (myNoteDto.isEmpty()) {
				myNoteDAO.deleteMyNote(myNoteDto);
			} else if (!myNoteDto.equals(oldNote)) {
				// update changed notes
				myNoteDAO.updateMyNote(myNoteDto);
				isSaved = true;
			}
		}
		if (isSaved) {
			Toast.makeText(BibleApplication.getApplication().getApplicationContext(), R.string.mynote_saved, Toast.LENGTH_SHORT).show();
		}
		return isSaved;
	}

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

		return myNoteDAO.getAllMyNotes(getSortOrder());
	}

	/** delete this user note (and any links to labels) */
	public boolean deleteMyNote(MyNoteDto myNote) {
		return myNoteDAO.deleteMyNote(myNote);
	}

	public void changeSortOrder() {
		if (getSortOrder().equals(MyNoteSortOrder.BIBLE_BOOK)) {
			setSortOrder(MyNoteSortOrder.DATE_CREATED);
		} else {
			setSortOrder(MyNoteSortOrder.BIBLE_BOOK);
		}
	}
	
	public MyNoteSortOrder getSortOrder() {
		String sortOrderStr = CommonUtils.getSharedPreference(MYNOTE_SORT_ORDER, MyNoteSortOrder.BIBLE_BOOK.toString());
		return MyNoteSortOrder.valueOf(sortOrderStr);
	}
	
	private void setSortOrder(MyNoteSortOrder sortOrder) {
		CommonUtils.saveSharedPreference(MYNOTE_SORT_ORDER, sortOrder.toString());
	}

	public String getSortOrderDescription() {
		if (MyNoteSortOrder.BIBLE_BOOK.equals(getSortOrder())) {
			return CommonUtils.getResourceString(R.string.sort_by_bible_book);
		} else {
			return CommonUtils.getResourceString(R.string.sort_by_date);
		}
	}

	public CurrentPageManager getCurrentPageManager() {
		return activeWindowPageManagerProvider.getActiveWindowPageManager();
	}
}
