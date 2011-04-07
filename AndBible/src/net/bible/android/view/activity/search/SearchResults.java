package net.bible.android.view.activity.search;

 import java.util.List;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.search.SearchControl;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.android.view.activity.base.ListActivityBase;
import net.bible.service.sword.SwordApi;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.book.Book;
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
    
        if (prepareResults()) {
        	mKeyArrayAdapter = new SearchItemAdapter(this, LIST_ITEM_TYPE, mSearchResults);
            setListAdapter(mKeyArrayAdapter);
            
            Log.d(TAG, "Finished displaying Search view");
        }
    }

    /** do the search query and prepare results in lists ready for display
     * 
     */
    private boolean prepareResults() {
    	Log.d(TAG, "Preparing search results");
    	boolean isOk;

    	try {
			// get search string
			String searchText = getIntent().getExtras().getString(SearchControl.SEARCH_TEXT);
			String searchDocument = getIntent().getExtras().getString(SearchControl.SEARCH_DOCUMENT);
			if (StringUtils.isEmpty(searchDocument)) {
				searchDocument = ControlFactory.getInstance().getCurrentPageControl().getCurrentPage().getCurrentDocument().getInitials();
			}
			mSearchResults = ControlFactory.getInstance().getSearchControl().getSearchResults(searchDocument, searchText);
		
			// tell user how many results were returned
			String msg;
			if (mSearchResults.size()>=SearchControl.MAX_SEARCH_RESULTS) {
				msg = getString(R.string.search_showing_first, SearchControl.MAX_SEARCH_RESULTS);
			} else {
				msg = getString(R.string.search_result_count, mSearchResults.size());
			}
			Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
			isOk = true;
    	} catch (Exception e) {
    		Log.e(TAG, "Error processing search query", e);
    		Dialogs.getInstance().showErrorMsg(R.string.error_executing_search);
    		isOk = false;
    	}
    	return isOk;
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
    		// which doc do we show
			String targetDocInitials = getIntent().getExtras().getString(SearchControl.TARGET_DOCUMENT);
			if (StringUtils.isEmpty(targetDocInitials)) {
				targetDocInitials = ControlFactory.getInstance().getCurrentPageControl().getCurrentPage().getCurrentDocument().getInitials();
			}
			Book targetBook = SwordApi.getInstance().getDocumentByInitials(targetDocInitials); 
    		
    		CurrentPageManager.getInstance().setCurrentDocumentAndKey(targetBook, key);
    		doFinish();
    	}
    }
}
