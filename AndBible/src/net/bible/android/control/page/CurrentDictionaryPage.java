package net.bible.android.control.page;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.bible.android.activity.R;
import net.bible.android.view.activity.navigation.ChooseDictionaryWord;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.passage.Key;

import android.util.Log;
import android.view.Menu;

/** Reference to current passage shown by viewer
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class CurrentDictionaryPage extends CurrentPageBase implements CurrentPage {
	
	private Key key;
	private List<Key> mCachedGlobalKeyList;

	private static final String TAG = "CurrentDictionaryPage";
	
	
	/* default */ CurrentDictionaryPage() {
		super(false);
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
	 * @see net.bible.android.control.CurrentPage#getKey()
	 */
	@Override
	public Key getKey() {
        return key;
    }

	@Override
	public void setCurrentDocument(Book doc) {
		// if doc changes then clear any caches from the previous doc
		if (doc!=null && !doc.equals(getCurrentDocument())) {
			mCachedGlobalKeyList = null;
		}
		super.setCurrentDocument(doc);
	}

	//TODO remove this and do binary search of globalkeylist
	/** make dictionary key lookup much faster
	 * 
	 * @return
	 */
	public List<Key> getCachedGlobalKeyList() {
		if (getCurrentDocument()!=null && mCachedGlobalKeyList==null) {
			try {
				Log.d(TAG, "Start to create cached key list");
				// this cache is cleared in setCurrentDoc
		    	mCachedGlobalKeyList = new ArrayList<Key>();
		    	Iterator iter = getCurrentDocument().getGlobalKeyList().iterator();
				while (iter.hasNext()) {
					Key key = (Key)iter.next();
					mCachedGlobalKeyList.add(key);
				}
			} catch (OutOfMemoryError oom) {
				mCachedGlobalKeyList = null;
				System.gc();
				Log.e(TAG, "out of memory", oom);
				throw oom;
			}
			Log.d(TAG, "Finished creating cached key list len:"+mCachedGlobalKeyList.size());
		}
		return mCachedGlobalKeyList;
	}
	
	@Override
	public void next() {
		setKey(getKeyPlus(1));
	}

	@Override
	public void previous() {
		setKey(getKeyPlus(-1));
	}

	/** add or subtract a number of pages from the current position and return Verse
	 */
	public Key getKeyPlus(int num) {
		Key currentKey = getKey();
		Key globalList = getCurrentDocument().getGlobalKeyList();
		int keyPos = globalList.indexOf(currentKey);
		// move forward or backward to new posn
		int newKeyPos = keyPos+num;
		// check bounds
		newKeyPos = Math.min(newKeyPos, globalList.getCardinality()-1);
		newKeyPos = Math.max(newKeyPos, 0);
		// get the actual key at that posn
		return globalList.get(newKeyPos);
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