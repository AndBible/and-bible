package net.bible.android.control.page;

import java.util.List;

import net.bible.android.activity.R;
import net.bible.android.view.activity.navigation.ChooseGeneralBookKey;

import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.passage.Key;

import android.view.Menu;

/** Reference to current passage shown by viewer
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class CurrentGeneralBookPage extends CachedKeyPage implements CurrentPage {
	
	private Key key;
	private List<Key> mCachedGlobalKeyList;

	private static final String TAG = "CurrentGeneralBookPage";
	
	
	/* default */ CurrentGeneralBookPage() {
		super(false);
	}
	
	public BookCategory getBookCategory() {
		return BookCategory.GENERAL_BOOK;
	}

	@Override
	public Class getKeyChooserActivity() {
		return ChooseGeneralBookKey.class;
	}
	
	/** set key without notification
	 * 
	 * @param key
	 */
	protected void doSetKey(Key key) {
		this.key = key;
	}

	/* (non-Javadoc)
	 * @see net.bible.android.control.CurrentPage#getKey()
	 */
	@Override
	public Key getKey() {
		return key;
    }

	@Override
	public void next() {
		Key next = getKeyPlus(1);
		if (next!=null) {
			setKey(next);
		}
	}

	@Override
	public void previous() {
		Key prev = getKeyPlus(-1);
		if (prev!=null) {
			setKey(prev);
		}
	}

	@Override
	public void updateOptionsMenu(Menu menu) {
		menu.findItem(R.id.selectPassageButton).setTitle(R.string.general_book_contents);		
		menu.findItem(R.id.searchButton).setEnabled(false);		
		menu.findItem(R.id.bookmarksButton).setEnabled(false);		
	}
	
	@Override
	public void updateContextMenu(Menu menu) {
		super.updateContextMenu(menu);
		// by default disable notes but bible will enable
		menu.findItem(R.id.add_bookmark).setVisible(false);
	}


	@Override
	public boolean isSingleKey() {
		return true;
	}

	/** can we enable the main menu search button 
	 */
	@Override
	public boolean isSearchable() {
		return false;
	}
}