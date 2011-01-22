package net.bible.android.view.activity.search;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.activity.base.ActivityBase;
import net.bible.service.common.CommonUtils;
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

    /** Download the index from the sam place that Pocket Sword uses
     *  
     * @param v
     */
    public void onDownload(View v) {
    	Log.i(TAG, "CLICKED");
    	boolean bOk = ControlFactory.getInstance().getSearchControl().downloadIndex();

    	if (bOk) {
        	// monitor the progress
        	Intent myIntent = new Intent(this, SearchIndexProgressStatus.class);
        	startActivity(myIntent);
        	finish();
    	}
    }

    /** Indexing is very slow
     *  
     * @param v
     */
    public void onIndex(View v) {
    	Log.i(TAG, "CLICKED");
    	try {
    		// start background thread to create index
        	boolean bOk = ControlFactory.getInstance().getSearchControl().createIndex();

        	if (bOk) {
	        	// monitor the progress
	        	Intent myIntent = new Intent(this, SearchIndexProgressStatus.class);
	        	startActivity(myIntent);
	        	finish();
        	}
    	} catch (Exception e) {
    		Log.e(TAG, "error indexing:"+e.getMessage());
    		e.printStackTrace();
    	}
    }
}
