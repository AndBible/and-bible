package net.bible.android.control.search;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.SharedConstants;
import net.bible.android.activity.R;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.android.view.activity.search.Search;
import net.bible.android.view.activity.search.SearchIndex;
import net.bible.service.common.CommonUtils;
import net.bible.service.sword.SwordContentFacade;
import net.bible.service.sword.SwordDocumentFacade;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.index.IndexStatus;
import org.crosswire.jsword.index.lucene.PdaLuceneIndexCreator;
import org.crosswire.jsword.index.search.SearchType;
import org.crosswire.jsword.passage.Key;

import android.app.Activity;
import android.content.Intent;
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

	public static final String SEARCH_TEXT = "SearchText";
	public static final String SEARCH_DOCUMENT = "SearchDocument";
	public static final String TARGET_DOCUMENT = "TargetDocument";
	
	private static final String STRONG_COLON_STRING = PdaLuceneIndexCreator.FIELD_STRONG+":";
	private static final String STRONG_COLON_STRING_PLACE_HOLDER = PdaLuceneIndexCreator.FIELD_STRONG+"COLON";
	
	public static final int MAX_SEARCH_RESULTS = 1000;

	private static final String TAG = "SearchControl";
	
	/** if current document is indexed then go to search else go to download index page
	 * 
	 * @return required Intent
	 */
    public Intent getSearchIntent(Book document) {

    	IndexStatus indexStatus = document.getIndexStatus();
    	Log.d(TAG, "Index status:"+indexStatus);
    	Activity currentActivity = CurrentActivityHolder.getInstance().getCurrentActivity();
    	if (indexStatus.equals(IndexStatus.DONE)) {
    		Log.d(TAG, "Index status is DONE");
    	    return new Intent(currentActivity, Search.class);
    	} else {
    		Log.d(TAG, "Index status is NOT DONE");
    	    return new Intent(currentActivity, SearchIndex.class);
    	}
    }

    public boolean validateIndex(Book document) {
    	return document.getIndexStatus().equals(IndexStatus.DONE);
    }
    
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
    public List<Key> getSearchResults(String document, String searchText) throws BookException {
    	Log.d(TAG, "Preparing search results");
    	List<Key> resultKeys = new ArrayList<Key>();
    	
    	// search the current book
        Book book = SwordDocumentFacade.getInstance().getDocumentByInitials(document);
    	Key result = SwordContentFacade.getInstance().search(book, searchText);
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
			verseText = SwordContentFacade.getInstance().getPlainText(CurrentPageManager.getInstance().getCurrentBible().getCurrentDocument(), key.getOsisRef(), 1);
			verseText = CommonUtils.limitTextLength(verseText);
		} catch (Exception e) {
			Log.e(TAG, "Error getting verse text", e);
		}
		return verseText;
	}

	/** double spaces, :, and leading or trailing space cause lucene errors
	 * 
	 * @param search
	 * @return
	 */
	private String cleanSearchString(String search) {
		// remove colons but leave Strong lookups
		// replace "strong:" with a place holder, remove ':', replace "strong:"
		search = search.replace(STRONG_COLON_STRING, STRONG_COLON_STRING_PLACE_HOLDER);
		search = search.replace(":", " ");
		search = search.replace(STRONG_COLON_STRING_PLACE_HOLDER, STRONG_COLON_STRING);
		
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
	public boolean downloadIndex(Book book) {
		boolean ok = false;
    	try {
        	if (CommonUtils.getSDCardMegsFree()<SharedConstants.REQUIRED_MEGS_FOR_DOWNLOADS) {
            	Dialogs.getInstance().showErrorMsg(R.string.storage_space_warning);
        	} else if (!CommonUtils.isInternetAvailable()) {
            	Dialogs.getInstance().showErrorMsg(R.string.no_internet_connection);
            	ok = false;
        	} else {
		        
		        if (SwordDocumentFacade.getInstance().isIndexDownloadAvailable(book)) {
			        // this starts a new thread to do the indexing and returns immediately
			        // if index creation is already in progress then nothing will happen
			        SwordDocumentFacade.getInstance().downloadIndex(book);
			        
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
	public boolean createIndex(Book book) {
		boolean ok = false;
    	try {
	        // this starts a new thread to do the indexing and returns immediately
	        // if index creation is already in progress then nothing will happen
	        SwordDocumentFacade.getInstance().ensureIndexCreation(book);
	        
	        ok = true;
    	} catch (Exception e) {
    		Log.e(TAG, "error indexing:"+e.getMessage());
    		e.printStackTrace();
    	}
    	return ok;
	}

}
