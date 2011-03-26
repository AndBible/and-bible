package net.bible.android.control.page;

import net.bible.android.activity.R;
import net.bible.android.view.activity.navigation.ChooseDictionaryWord;

import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.passage.Key;

import android.app.Activity;
import android.view.Menu;

/** Reference to current passage shown by viewer
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class CurrentDictionaryPage extends CachedKeyPage implements CurrentPage {
	
	private Key key;

	private static final String TAG = "CurrentDictionaryPage";
	
	/* default */ CurrentDictionaryPage() {
		super(false);
	}
	
	public BookCategory getBookCategory() {
		return BookCategory.DICTIONARY;
	}

	public Class<? extends Activity> getKeyChooserActivity() {
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
	 * @see net.bible.android.control.CurrentPage#getKey()
	 */
	@Override
	public Key getKey() {
        return key;
    }

	@Override
	public void next() {
		setKey(getKeyPlus(1));
	}

	@Override
	public void previous() {
		setKey(getKeyPlus(-1));
	}

	@Override
	public void updateOptionsMenu(Menu menu) {
		menu.findItem(R.id.selectPassageButton).setTitle(R.string.dictionary_contents);		
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