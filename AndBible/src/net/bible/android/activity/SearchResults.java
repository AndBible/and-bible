package net.bible.android.activity;

 import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import net.bible.android.CurrentPassage;
import net.bible.android.util.ListActivityBase;
import net.bible.service.sword.SwordApi;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.passage.Key;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

/** do the search and show the search results
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class SearchResults extends ListActivityBase {
	private static final String TAG = "SearchResults";
	private static final int MAX_SEARCH_RESULTS = 100;
	
	private TextView mStatusTextView;
	
    static final protected String LIST_ITEM_LINE1 = "line1";
    static final protected String LIST_ITEM_LINE2 = "line2";	
    private List<ResultItem> mResultList;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Displaying Search results view");
        setContentView(R.layout.search_results);
    
        mStatusTextView =  (TextView)findViewById(R.id.statusText);

        prepareResults();
        
        setListAdapter(createAdapter());
        
        Log.d(TAG, "Finished displaying Search view");
    }

    /**
     * Creates and returns a list adapter for the current list activity
     * @return
     */
    protected ListAdapter createAdapter()
    {
    	ListAdapter listAdapter = new SimpleAdapter(this, mResultList, 
                android.R.layout.two_line_list_item, 
                new String[] {LIST_ITEM_LINE1, LIST_ITEM_LINE2}, 
                new int[] {android.R.id.text1, android.R.id.text2});
    	
    	return listAdapter;
    }
    
    private void prepareResults() {
    	Log.d(TAG, "Preparing search results");
    	
    	try {
	    	String searchText = getIntent().getExtras().getString(Search.SEARCH_TEXT);
	    	
	        Book bible = CurrentPassage.getInstance().getCurrentDocument();
	    	SwordApi swordApi = SwordApi.getInstance();
	    	Key result = swordApi.search(bible, searchText);
	    	if (result!=null) {
	    		int resNum = result.getCardinality();
	        	Log.d(TAG, "Number of results:"+resNum);
	        	String msg = resNum+" matches found";
	    		if (resNum>MAX_SEARCH_RESULTS) {
	    			msg = "Too many matches.  Showing first "+MAX_SEARCH_RESULTS;
	    		}
	    		showMsg(msg);
	    		mResultList = new ArrayList<ResultItem>();
	    		for (int i=0; i<Math.min(resNum, MAX_SEARCH_RESULTS); i++) {
	    			mResultList.add(new ResultItem(result.get(i)));
	    		}
	    	}
	    	//mResultAdapter.notifyDataSetChanged();
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
    
    private void showMsg(String msg) {
    	mStatusTextView.setText(msg);
    }

    @Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
    	verseSelected(mResultList.get(position));
	}
    
    private void verseSelected(ResultItem resultItem) {
    	Log.i(TAG, "chose:"+resultItem);
    	CurrentPassage.getInstance().setKey(resultItem.verse);
    	doFinish();
    }
    
    static class ResultItem extends HashMap<String, String> {
    	private Key verse;
    	ResultItem(Key verse) {
    		this.verse = verse;
    	}
    	public String toString() {
    		try {
	    		return verse.getName();
    		} catch (Exception e) {
    			Log.e(TAG, "Error getting found verse", e);
    			return "";
    		}
    	}
    	
    	@Override
		public String get(Object key) {
    		String retval = "";
    		try {
	    		if (key.equals(LIST_ITEM_LINE1)) {
	    			retval = verse.getName();
	    		} else {
	    			retval = SwordApi.getInstance().getPlainText(CurrentPassage.getInstance().getCurrentDocument(), verse.getName(), 1);
	    		}
    		} catch (Exception e) {
    			Log.e(TAG, "Error getting search result", e);
    		}
    		return retval;
    	}
    }
}
