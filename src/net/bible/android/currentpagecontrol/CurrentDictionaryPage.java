package net.bible.android.currentpagecontrol;

import java.util.List;

import net.bible.service.sword.SwordApi;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;

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

	public String getKeyDescription() {
		return getKey().toString();
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

	/* (non-Javadoc)
	 * @see net.bible.android.currentpagecontrol.CurrentPage#setCurrentDocument(org.crosswire.jsword.book.Book)
	 */
	@Override
	public void setCurrentDocument(Book doc) {
		this.currentDocument = doc;
		// not yet because we currently always go to the index first and pick a key at which point a refresh will occur
		// pageChange();
	}

	@Override
	public boolean isSingleKey() {
		return true;
	}
}