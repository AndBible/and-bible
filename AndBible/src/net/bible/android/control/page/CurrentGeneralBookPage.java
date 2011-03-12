package net.bible.android.control.page;

import net.bible.android.activity.R;
import net.bible.android.view.activity.navigation.ChooseGeneralBookKey;

import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.TreeKey;

import android.util.Log;
import android.view.Menu;

/** Reference to current passage shown by viewer
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class CurrentGeneralBookPage extends CurrentPageBase implements CurrentPage {
	
	private Key key;

	private static final String TAG = "CurrentGeneralBookPage";
	
	
	/* default */ CurrentGeneralBookPage() {
		super(false);
	}
	
	public BookCategory getBookCategory() {
		return BookCategory.GENERAL_BOOK;
	}

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

	/** add or subtract a number of pages from the current position and return new key or null if run off end
	 */
	public Key getKeyPlus(int num) {
		Key key = getKey();
		// GenBooks normally have TreeKeys - if it isn't then prevent a crash by bailing out now
		if (!(key instanceof TreeKey)) {
			return key;
		}

		TreeKey treeKey = (TreeKey)key;
		for (int i=0; i<Math.abs(num); i++) {
			if (num>0) {
				treeKey = getNextSibling(treeKey);
			} else {
				treeKey = getPrevSibling(treeKey);
			}
		}
		return treeKey;
	}

	/** move forward one place in a KeyTree
	 * 
	 * @param treeKey
	 * @return
	 */
	private TreeKey getNextSibling(TreeKey treeKey) {
		TreeKey sibling = null;
		Key parent = treeKey.getParent();
		if (parent!=null) {
			int indexOfKey = parent.indexOf(treeKey);
			if (indexOfKey < parent.getChildCount()-1) {
				sibling = (TreeKey)parent.get(indexOfKey+1);
			} else if (parent instanceof TreeKey) {
				sibling = getNextSibling((TreeKey)parent);
			}

			if (sibling!=null) {
				// ensure we always end at a leaf node
				while (sibling.getChildCount()>0) {
					sibling = (TreeKey)sibling.get(0);
				}
			}
		}
		return sibling;
	}

	private TreeKey getPrevSibling(TreeKey treeKey) {
		TreeKey sibling = null;
		Key parent = treeKey.getParent();
		if (parent!=null) {
			int indexOfKey = parent.indexOf(treeKey);
			if (indexOfKey > 0) {
				sibling = (TreeKey)parent.get(indexOfKey-1);
			} else if (parent instanceof TreeKey) {
				sibling = getPrevSibling((TreeKey)parent);
			}

			if (sibling!=null) {
				// ensure we always end at a leaf node
				while (sibling.getChildCount()>0) {
					sibling = (TreeKey)sibling.get(sibling.getChildCount()-1);
				}
			}
		}
		return sibling;
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