package net.bible.android.view.activity.search;

 import java.util.List;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.search.SearchControl;
import net.bible.android.view.activity.base.ListActivityBase;

import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.passage.Key;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
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
	
    private List<Key> mSearchResults;
    private ArrayAdapter<Key> mKeyArrayAdapter;

	private static final int LIST_ITEM_TYPE = android.R.layout.simple_list_item_2;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Displaying Search results view");
        setContentView(R.layout.search_results);
    
        prepareResults();
        
    	mKeyArrayAdapter = new SearchItemAdapter(this, LIST_ITEM_TYPE, mSearchResults);
        setListAdapter(mKeyArrayAdapter);
        
        Log.d(TAG, "Finished displaying Search view");
    }

    /** do the search query and prepare results in lists ready for display
     * 
     */
    private void prepareResults() {
    	Log.d(TAG, "Preparing search results");

    	try {
			// get search string
			String searchText = getIntent().getExtras().getString(Search.SEARCH_TEXT);
			mSearchResults = ControlFactory.getInstance().getSearchControl().getSearchResults(searchText);
		
			// tell user how many results were returned
			String msg;
			if (mSearchResults.size()>=SearchControl.MAX_SEARCH_RESULTS) {
				msg = getString(R.string.search_showing_first, SearchControl.MAX_SEARCH_RESULTS);
			} else {
				msg = getString(R.string.search_result_count, mSearchResults.size());
			}
			Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    	} catch (BookException e) {
    		Log.e(TAG, "Error processing search query", e);
    		Toast.makeText(this, R.string.error_executing_search, Toast.LENGTH_SHORT).show();
    	}
    }
    
    private void doFinish() {
    	Intent resultIntent = new Intent();
    	setResult(Activity.RESULT_OK, resultIntent);
    	finish();    
    }

    @Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
    	try {
	    	verseSelected(mSearchResults.get(position));
		} catch (Exception e) {
			Log.e(TAG, "Selection error", e);
			showErrorMsg(R.string.error_occurred);
		}
	}
    
    private void verseSelected(Key key) {
    	Log.i(TAG, "chose:"+key);
    	if (key!=null) {
    		CurrentPageManager.getInstance().getCurrentPage().setKey(key);
    		doFinish();
    	}
    }
}
