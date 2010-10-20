package net.bible.android.activity;

import net.bible.android.activity.base.ActivityBase;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.service.sword.SwordApi;

import org.crosswire.jsword.book.Book;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

/** Create a Lucene search index
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class SearchIndex extends ActivityBase {
	private static final String TAG = "SearchIndex";
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Displaying SearchIndex view");
        setContentView(R.layout.search_index);
    
        Log.d(TAG, "Finished displaying Search Index view");
    }

    // Indexing is too slow and fails aftr 1 hour - the experimental method below does not improve things enough to make indexing succeed 
    public void onIndex(View v) {
    	Log.i(TAG, "CLICKED");
    	try {
	        Book book = CurrentPageManager.getInstance().getCurrentPage().getCurrentDocument();
	        
	        // this starts a new thread to do the indexing and returns immediately
	        // if index creation is already in progress then nothing will happen
	        SwordApi.getInstance().ensureIndexCreation(book);
			
        	// monitor the progres
        	Intent myIntent = new Intent(this, SearchIndexProgressStatus.class);
        	startActivity(myIntent);
        	finish();

    	} catch (Exception e) {
    		Log.e(TAG, "error indexing:"+e.getMessage());
    		e.printStackTrace();
    	}
    }
}
