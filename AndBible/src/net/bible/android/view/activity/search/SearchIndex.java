package net.bible.android.view.activity.search;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.search.SearchControl;
import net.bible.android.view.activity.base.ActivityBase;
import net.bible.service.sword.SwordDocumentFacade;

import org.apache.commons.lang.StringUtils;
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
    	boolean bOk = ControlFactory.getInstance().getSearchControl().downloadIndex(getDocumentToIndex());

    	if (bOk) {
        	monitorProgress();
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
        	boolean bOk = ControlFactory.getInstance().getSearchControl().createIndex(getDocumentToIndex());

        	if (bOk) {
	        	monitorProgress();
        	}
    	} catch (Exception e) {
    		Log.e(TAG, "error indexing:"+e.getMessage());
    		e.printStackTrace();
    	}
    }
    
    private Book getDocumentToIndex() {
    	String documentInitials = getIntent().getStringExtra(SearchControl.SEARCH_DOCUMENT);

    	Book documentToIndex = null;
        if (StringUtils.isNotEmpty(documentInitials)) {
        	documentToIndex = SwordDocumentFacade.getInstance().getDocumentByInitials(documentInitials);
        } else {
        	documentToIndex = CurrentPageManager.getInstance().getCurrentPage().getCurrentDocument();
        }

        return documentToIndex;
    }

    /**
	 * Show progress monitor screen
	 */
	private void monitorProgress() {
		// monitor the progress
		Intent intent = new Intent(this, SearchIndexProgressStatus.class);
		
		// a search may be pre-defined, if so then pass the pre-defined search through so it can be executed directly
		if (getIntent().getExtras()!=null) {
			intent.putExtras(getIntent().getExtras());
		}
		
		// always need to specify which document is being indexed
		if (StringUtils.isEmpty(intent.getStringExtra(SearchControl.SEARCH_DOCUMENT))) {
			// must tell the progress status screen which doc is being downloaded because it checks it downloaded successfully
			intent.putExtra(SearchControl.SEARCH_DOCUMENT, getDocumentToIndex().getInitials());
		}
		
		startActivity(intent);
		finish();
	}
}
