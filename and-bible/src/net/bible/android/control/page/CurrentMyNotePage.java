package net.bible.android.control.page;

import java.io.IOException;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.service.common.ParseException;
import net.bible.service.download.FakeSwordBookFactory;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.versification.Versification;

import android.util.Log;
import android.view.Menu;

/** Provide information for My Note page
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class CurrentMyNotePage extends CurrentCommentaryPage implements CurrentPage {

	private static final String MY_NOTE_DUMMY_CONF = "[MyNote]\nDescription=My Note\nCategory=OTHER\nModDrv=zCom\nBlockType=CHAPTER\nLang=en\nEncoding=UTF-8\nLCSH=Bible--Commentaries.\nDataPath=./modules/comments/zcom/mynote/\nAbout=\nVersification=";
	// just one fake book for every note
	private Book fakeMyNoteBook;
	private Versification fakeMyNoteBookVersification;
	
	private static final String TAG = "CurrentMyNotePage";
	
	/* default */ CurrentMyNotePage(CurrentBibleVerse currentVerse) {
		super(currentVerse);
	}

	@Override
	public String getCurrentPageContent() throws ParseException {
        return ControlFactory.getInstance().getMyNoteControl().getMyNoteTextByKey(getKey());
	}
	
	@Override
	public void updateContextMenu(Menu menu) {
		super.updateContextMenu(menu);

		menu.findItem(R.id.myNoteAddEdit).setVisible(false);
		menu.findItem(R.id.add_bookmark).setVisible(false);	
		menu.findItem(R.id.copy).setVisible(false);	
		menu.findItem(R.id.shareVerse).setVisible(false);	
		menu.findItem(R.id.selectText).setVisible(false);	
	}
	
	@Override
	public Book getCurrentDocument() {
		try {
			if (fakeMyNoteBook==null || fakeMyNoteBookVersification==null || !fakeMyNoteBookVersification.equals(getCurrentVersification())) {
				Versification v11n = getCurrentVersification();
				fakeMyNoteBook = FakeSwordBookFactory.createFakeRepoBook("My Note", MY_NOTE_DUMMY_CONF+v11n.getName(), "");
				fakeMyNoteBookVersification = v11n;
			}
		} catch (IOException | BookException e) {
			Log.e(TAG, "Error creating fake MyNote book", e);
		}
		return fakeMyNoteBook; 
	}

	/** can we enable the main menu search button 
	 */
	@Override
	public boolean isSearchable() {
		return false;
	}
	
	/** can we enable the main menu Speak button 
	 */
	@Override
	public boolean isSpeakable() {
		//TODO doesn't work currently - enable later
		return false;
	}

	public BookCategory getBookCategory() {
		return BookCategory.OTHER;
	}
	
	private Versification getCurrentVersification() {
		return getCurrentBibleVerse().getVersificationOfLastSelectedVerse();		
	}
}