package net.bible.android.view.activity.search;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import net.bible.android.activity.R;
import net.bible.android.control.search.SearchControl;
import net.bible.android.view.activity.base.CustomTitlebarActivityBase;

import org.apache.commons.lang3.StringUtils;
import org.crosswire.jsword.book.Book;

import javax.inject.Inject;

/** Create a Lucene search index
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class SearchIndex extends CustomTitlebarActivityBase {

    private SearchControl searchControl;

    private static final String TAG = "SearchIndex";
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Displaying SearchIndex view");
        setContentView(R.layout.search_index);

        buildActivityComponent().inject(this);
    
        Log.d(TAG, "Finished displaying Search Index view");
    }

    /** Download the index from the sam place that Pocket Sword uses
     *  
     * @param v
     */
    public void onDownload(View v) {
        Log.i(TAG, "CLICKED");
        boolean bOk = searchControl.downloadIndex(getDocumentToIndex());

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
            boolean bOk = searchControl.createIndex(getDocumentToIndex());

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

        Book documentToIndex;
        if (StringUtils.isNotEmpty(documentInitials)) {
            documentToIndex = getSwordDocumentFacade().getDocumentByInitials(documentInitials);
        } else {
            documentToIndex = getPageControl().getCurrentPageManager().getCurrentPage().getCurrentDocument();
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

    @Inject
    void setSearchControl(SearchControl searchControl) {
        this.searchControl = searchControl;
    }
}
