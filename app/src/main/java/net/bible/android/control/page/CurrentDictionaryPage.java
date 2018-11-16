package net.bible.android.control.page;

import android.app.Activity;
import android.view.Menu;
import android.view.MenuItem;

import net.bible.android.activity.R;
import net.bible.android.view.activity.navigation.ChooseDictionaryWord;
import net.bible.service.sword.SwordContentFacade;
import net.bible.service.sword.SwordDocumentFacade;

import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.passage.Key;

/** Reference to current passage shown by viewer
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class CurrentDictionaryPage extends CachedKeyPage implements CurrentPage {
	
	private Key key;

	@SuppressWarnings("unused")
	private static final String TAG = "CurrentDictionaryPage";
	
	/* default */ CurrentDictionaryPage(SwordContentFacade swordContentFacade, SwordDocumentFacade swordDocumentFacade) {
		super(false, swordContentFacade, swordDocumentFacade);
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
	public void doSetKey(Key key) {
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
		super.updateOptionsMenu(menu);

		MenuItem menuItem = menu.findItem(R.id.bookmarksButton);
		if (menuItem!=null) {
			menuItem.setEnabled(false);
		}
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