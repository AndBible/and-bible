package net.bible.android.currentpagecontrol;

import java.util.List;

import net.bible.android.BibleApplication;
import net.bible.android.activity.ChooseDictionaryWord;
import net.bible.android.activity.ChoosePassageBook;
import net.bible.service.sword.SwordApi;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.passage.Key;

import android.content.Intent;
import android.util.Log;

/** Reference to current passage shown by viewer
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class CurrentDictionaryPage extends CurrentPageBase implements CurrentPage {
	
	private Book currentDocument;
	private Key key;

	private static final String TAG = "CurrentDictionaryPage";
	
	
	/* default */ CurrentDictionaryPage() {
	}
	
	public BookCategory getBookCategory() {
		return BookCategory.DICTIONARY;
	}

	public Class getKeyChooserActivity() {
		return ChooseDictionaryWord.class;
	}
	
	/** set key without notification
	 * 
	 * @param key
	 */
	protected void doSetKey(Key key) {
		this.key = key;
	}

	/* (non-Javadoc)
	 * @see net.bible.android.currentpagecontrol.CurrentPage#getKey()
	 */
	@Override
	public Key getKey() {
        return key;
    }

	/* (non-Javadoc)
	 * @see net.bible.android.currentpagecontrol.CurrentPage#getCurrentDocument()
	 */
	@Override
	public Book getCurrentDocument() {
		if (currentDocument==null) {
			List<Book> books = SwordApi.getInstance().getDictionaries();
			if (books.size()>0) {
				currentDocument = books.get(0);
			}
		}
		return currentDocument;
	}

	@Override
	public boolean isSingleKey() {
		return true;
	}
}