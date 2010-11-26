package net.bible.android.control.search;

import net.bible.android.activity.R;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.android.view.activity.search.SearchIndexProgressStatus;
import net.bible.service.common.CommonUtils;
import net.bible.service.sword.SwordApi;

import org.crosswire.jsword.book.Book;

import android.content.Intent;
import android.util.Log;

public class SearchControl {

	private static final String TAG = "SearchControl";
	
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
