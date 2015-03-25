package net.bible.android.view.activity.search;

 import java.util.ArrayList;
import java.util.List;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.search.SearchControl;
import net.bible.android.control.search.SearchResultsDto;
import net.bible.android.view.activity.base.Callback;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.android.view.activity.base.ListActivityBase;
import net.bible.android.view.activity.search.searchresultsactionbar.SearchResultsActionBarManager;
import net.bible.service.sword.SwordDocumentFacade;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

/** do the search and show the search results
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class SearchResults extends ListActivityBase {
	private static final String TAG = "SearchResults";
	
    private SearchResultsDto mSearchResultsHolder;
    
    private List<Key> mCurrentlyDisplayedSearchResults = new ArrayList<Key>();
    private ArrayAdapter<Key> mKeyArrayAdapter;

    private boolean isScriptureResultsCurrentlyShown = true;
    
	private static SearchResultsActionBarManager searchResultsActionBarManager = new SearchResultsActionBarManager();
	
	private static final SearchControl searchControl = ControlFactory.getInstance().getSearchControl();

	private static final int LIST_ITEM_TYPE = android.R.layout.simple_list_item_2;

    public SearchResults() {
		super(searchResultsActionBarManager, R.menu.empty_menu);
		
		searchResultsActionBarManager.registerScriptureToggleClickListener(scriptureToggleClickListener);
	}
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState, true);
        Log.i(TAG, "Displaying Search results view");
        setContentView(R.layout.list);

        isScriptureResultsCurrentlyShown = searchControl.isCurrentDefaultScripture();

        if (fetchSearchResults()) {
            // initialise adapters before result population - easier when updating due to later Scripture toggle 
        	mKeyArrayAdapter = new SearchItemAdapter(this, LIST_ITEM_TYPE, mCurrentlyDisplayedSearchResults);
            setListAdapter(mKeyArrayAdapter);

			populateViewResultsAdapter();
        }
    }

    /** do the search query and prepare results in lists ready for display
     * 
     */
    private boolean fetchSearchResults() {
    	Log.d(TAG, "Preparing search results");
    	boolean isOk = false;

    	try {
    		// get search string - passed in using extras so extras cannot be null
            Bundle extras = getIntent().getExtras();
			String searchText = extras.getString(SearchControl.SEARCH_TEXT);
			String searchDocument = extras.getString(SearchControl.SEARCH_DOCUMENT);
			if (StringUtils.isEmpty(searchDocument)) {
				searchDocument = ControlFactory.getInstance().getCurrentPageControl().getCurrentPage().getCurrentDocument().getInitials();
			}
			mSearchResultsHolder = searchControl.getSearchResults(searchDocument, searchText);
			
			// tell user how many results were returned
			String msg;
			if (mCurrentlyDisplayedSearchResults.size()>=SearchControl.MAX_SEARCH_RESULTS) {
				msg = getString(R.string.search_showing_first, SearchControl.MAX_SEARCH_RESULTS);
			} else {
				msg = getString(R.string.search_result_count, mSearchResultsHolder.size());
			}
			Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
			isOk = true;
    	} catch (Exception e) {
    		Log.e(TAG, "Error processing search query", e);
    		isOk = false;
    		Dialogs.getInstance().showErrorMsg(R.string.error_executing_search, new Callback() {
    			@Override
    			public void okay() {
    				onBackPressed();
    			}
    		});
    	}
    	return isOk;
    }

    /** 
     * Move search results into view Adapter
     */
	private void populateViewResultsAdapter() {
		if (isScriptureResultsCurrentlyShown) {
			mCurrentlyDisplayedSearchResults = mSearchResultsHolder.getMainSearchResults();
		} else {
			mCurrentlyDisplayedSearchResults = mSearchResultsHolder.getOtherSearchResults();
		}
		
		// addAll is only supported in Api 11+
		mKeyArrayAdapter.clear();
		for (Key key : mCurrentlyDisplayedSearchResults) {
			mKeyArrayAdapter.add(key);	
		}
		
	}
    
    @Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
    	try {
    		// no need to call HistoryManager.beforePageChange() here because PassageChangeMediator will tell HistoryManager a change is about to occur 
    		
	    	verseSelected(mCurrentlyDisplayedSearchResults.get(position));
		} catch (Exception e) {
			Log.e(TAG, "Selection error", e);
			showErrorMsg(R.string.error_occurred);
		}
	}
    
    private void verseSelected(Key key) {
    	Log.i(TAG, "chose:"+key);
    	if (key!=null) {
    		// which doc do we show
			String targetDocInitials = getIntent().getExtras().getString(SearchControl.TARGET_DOCUMENT);
			if (StringUtils.isEmpty(targetDocInitials)) {
				targetDocInitials = ControlFactory.getInstance().getCurrentPageControl().getCurrentPage().getCurrentDocument().getInitials();
			}
			Book targetBook = SwordDocumentFacade.getInstance().getDocumentByInitials(targetDocInitials); 
    		
    		ControlFactory.getInstance().getCurrentPageControl().setCurrentDocumentAndKey(targetBook, key);
    		
    		// this also calls finish() on this Activity.  If a user re-selects from HistoryList then a new Activity is created
    		returnToPreviousScreen();
    	}
    }

    /**
     * Handle scripture/Appendix toggle
     */
    private OnClickListener scriptureToggleClickListener = new OnClickListener( ) {
		
		@Override
		public void onClick(View view) {
			isScriptureResultsCurrentlyShown = !isScriptureResultsCurrentlyShown;
			populateViewResultsAdapter();
			mKeyArrayAdapter.
			notifyDataSetChanged();
			searchResultsActionBarManager.setScriptureShown(isScriptureResultsCurrentlyShown);
		}
	};
}
