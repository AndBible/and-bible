package net.bible.android.currentpagecontrol;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.bible.android.activity.ChooseDictionaryWord;
import net.bible.android.activity.R;

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
		Key currentKey = getKey();
		if (mCachedGlobalKeyList!=null && currentKey!=null) {
			for (int i=0; i<mCachedGlobalKeyList.size(); i++ ) {
				Key possKey = mCachedGlobalKeyList.get(i);
				if (currentKey.equals(possKey) && i<mCachedGlobalKeyList.size()-2) {
					setKey(mCachedGlobalKeyList.get(i+1));
					pageChange();
				}
			}
		}
	}

	@Override
	public void previous() {
		Key currentKey = getKey();
		if (mCachedGlobalKeyList!=null && currentKey!=null) {
			for (int i=0; i<mCachedGlobalKeyList.size(); i++ ) {
				Key possKey = mCachedGlobalKeyList.get(i);
				if (currentKey.equals(possKey) && i>0) {
					setKey(mCachedGlobalKeyList.get(i-1));
					pageChange();
				}
			}
		}
	}

	@Override
	public void updateOptionsMenu(Menu menu) {
		// these are fine for Bible and commentary
		menu.findItem(R.id.selectPassageButton).setTitle(R.string.dictionary_contents);		
		menu.findItem(R.id.searchButton).setEnabled(false);		
		menu.findItem(R.id.notesButton).setEnabled(false);		
	}

	@Override
	public boolean isSingleKey() {
		return true;
	}
}