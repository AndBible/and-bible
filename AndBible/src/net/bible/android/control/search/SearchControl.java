package net.bible.android.control.search;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.SharedConstants;
import net.bible.android.activity.R;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.service.common.CommonUtils;
import net.bible.service.sword.SwordApi;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.index.search.SearchType;
import org.crosswire.jsword.passage.Key;

import android.util.Log;

public class SearchControl {

	public static enum SearchBibleSection {
		OT,
		NT,
		CURRENT_BOOK,
		ALL
	}
	private static final String SEARCH_OLD_TESTAMENT = "+[Gen-Mal]";
	private static final String SEARCH_NEW_TESTAMENT = "+[Mat-Rev]";
//	private BookName currentBibleBook; 

	public static final int MAX_SEARCH_RESULTS = 100;

	private static final String TAG = "SearchControl";
	
    public String decorateSearchString(String searchString, SearchType searchType, SearchBibleSection bibleSection) {
    	String cleanSearchString = cleanSearchString(searchString);
    	
    	String decorated;

    	// add search type (all/any/phrase) to search string
    	decorated = searchType.decorate(cleanSearchString);

    	// add bible section limitation to search text
    	decorated = getBibleSectionTerm(bibleSection)+" "+decorated;
    	
    	return decorated;
    }

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

	/** double spaces and leading or trailing space cause lucene errors
	 * 
	 * @param search
	 * @return
	 */
	private String cleanSearchString(String search) {
		return search.replace("  ", " ").trim();
	}
    /** get OT, NT, or all query limitation
     * 
     * @return
     */
    private String getBibleSectionTerm(SearchBibleSection bibleSection) {
    	switch (bibleSection) {
    	case ALL:
    		return "";
    	case OT:
            return SEARCH_OLD_TESTAMENT;
    	case NT:
            return SEARCH_NEW_TESTAMENT;
//    	case R.id.searchCurrentBook:
//            return "+["+currentBibleBook.getShortName()+"];
        default:
        	Log.e(TAG, "Unexpected radio selection");
            return "";
    	}
    }

	/** download index 
	 * 
	 * @return true if managed to start download in background
	 */
	public boolean downloadIndex() {
		boolean ok = false;
    	try {
        	if (CommonUtils.getSDCardMegsFree()<SharedConstants.REQUIRED_MEGS_FOR_DOWNLOADS) {
            	Dialogs.getInstance().showErrorMsg(R.string.storage_space_warning);
        	} else if (!CommonUtils.isInternetAvailable()) {
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
