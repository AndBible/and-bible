package net.bible.android.control.page;

import java.io.IOException;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.service.common.ParseException;
import net.bible.service.download.FakeSwordBookFactory;
import net.bible.service.format.FormattedDocument;

import org.crosswire.jsword.book.Book;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

/** Provide information for My Note page
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class CurrentMyNotePage extends CurrentCommentaryPage implements CurrentPage {

	// just one fake book for every note
	private Book fakeMyNoteBook;
	
	private static final String TAG = "CurrentMyNotePage";
	
	/* default */ CurrentMyNotePage(CurrentBibleVerse currentVerse) {
		super(currentVerse);
		
		try {
			fakeMyNoteBook = FakeSwordBookFactory.createFakeRepoBook("My Note", "[MyNote]\nDescription=My Note\nModDrv=zCom\nBlockType=CHAPTER\nLang=en\nEncoding=UTF-8\nLCSH=Bible--Commentaries.\nDataPath=./modules/comments/zcom/mynote/\nAbout=", "");
		} catch (IOException e) {
			Log.e(TAG, "Error creating fake MyNote book", e);
		}
	}

	@Override
	public FormattedDocument getCurrentPageContent() throws ParseException {
        FormattedDocument formattedDocument = new FormattedDocument();
        formattedDocument.setHtmlPassage(ControlFactory.getInstance().getMyNoteControl().getMyNoteTextByKey(getKey()));
        return formattedDocument;	
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
		return fakeMyNoteBook; 
	}

	/** can we enable the main menu search button 
	 */
	@Override
	public boolean isSearchable() {
		return false;
	}
}