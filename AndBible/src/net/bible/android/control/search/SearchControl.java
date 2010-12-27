package net.bible.android.control.search;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.activity.R;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.android.view.activity.search.Search;
import net.bible.service.common.CommonUtils;
import net.bible.service.db.bookmark.BookmarkDto;
import net.bible.service.sword.SwordApi;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.passage.Key;

import android.util.Log;
import android.widget.Toast;

public class SearchControl {

	public static final int MAX_SEARCH_RESULTS = 100;

	private static final String TAG = "SearchControl";
	
    /** do the search query and prepare results in lists ready for display
     * 
     */
    public List<Key> getSearchResults(String searchText) throws BookException {
    	Log.d(TAG, "Preparing search results");
    	List<Key> resultKeys = new ArrayList<Key>();
    	
    	// search the current book
        Book book = CurrentPageManager.getInstance().getCurrentPage().getCurrentDocument();
    	SwordApi swordApi = SwordApi.getInstance();
    	Key result = swordApi.search(book, searchText);
    	if (result!=null) {
    		int resNum = result.getCardinality();
        	Log.d(TAG, "Number of results:"+resNum);

    		for (int i=0; i<Math.min(resNum, MAX_SEARCH_RESULTS+1); i++) {
    			resultKeys.add(result.get(i));
    		}
    	}
    	
    	return resultKeys;
    }

    /** get the verse for a search result
     */
	public String getSearchResultVerseText(Key key) {
		// There is similar functionality in BookmarkControl
		String verseText = "";
		try {
			verseText = SwordApi.getInstance().getPlainText(CurrentPageManager.getInstance().getCurrentBible().getCurrentDocument(), key.getOsisRef(), 1);
			verseText = CommonUtils.limitTextLength(verseText);
		} catch (Exception e) {
			Log.e(TAG, "Error getting verse text", e);
		}
		return verseText;
	}
	
	/** download index 
	 * 
	 * @return true if managed to start download in background
	 */
	public boolean downloadIndex() {
		boolean ok = false;
    	try {
        	if (!CommonUtils.isInternetAvailable()) {
            	Dialogs.getInstance().showErrorMsg(R.string.no_internet_connection);
            	ok = false;
        	} else {
		        Book book = CurrentPageManager.getInstance().getCurrentPage().getCurrentDocument();
		        
		        if (SwordApi.getInstance().isIndexDownloadAvailable(book)) {
			        // this starts a new thread to do the indexing and returns immediately
			        // if index creation is already in progress then nothing will happen
			        SwordApi.getInstance().downloadIndex(book);
			        
			        ok = true;
		        } else {
		        	Dialogs.getInstance().showErrorMsg(R.string.index_not_available_for_download);
		        	ok = false;
		        }
        	}
    	} catch (Exception e) {
    		Log.e(TAG, "error indexing:"+e.getMessage());
    		e.printStackTrace();
    		ok = false;
    	}
    	return ok;
	}
	
	/** download index 
	 * 
	 * @return true if managed to start download in background
	 */
	public boolean createIndex() {
		boolean ok = false;
    	try {
	        Book book = CurrentPageManager.getInstance().getCurrentPage().getCurrentDocument();
	        
	        // this starts a new thread to do the indexing and returns immediately
	        // if index creation is already in progress then nothing will happen
	        SwordApi.getInstance().ensureIndexCreation(book);
	        
	        ok = true;
    	} catch (Exception e) {
    		Log.e(TAG, "error indexing:"+e.getMessage());
    		e.printStackTrace();
    	}
    	return ok;
	}

}
