package net.bible.android.activity;

 import net.bible.android.CurrentPassage;
import net.bible.android.util.ActivityBase;
import net.bible.service.sword.SwordApi;

import org.crosswire.jsword.book.Book;

import android.app.Activity;
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
	        Book book = CurrentPassage.getInstance().getCurrentDocument();
	        
	        // this starts a new thread to do the indexing and returns immediately
	        // if index creation is already in progress then nothing will happen
	        SwordApi.getInstance().ensureIndexCreation(book);
			
        	// monitor the progres
        	//todo a simple popup ProgressDialog may be better - not sure
        	Intent myIntent = new Intent(this, ProgressStatus.class);
        	startActivityForResult(myIntent, 1);

    	} catch (Exception e) {
    		Log.e(TAG, "error indexing:"+e.getMessage());
    		e.printStackTrace();
    	}
    }
    
    @Override 
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    	if (resultCode==Activity.RESULT_OK) {
    		returnToMainScreen();
    	}
    }
    
    private void returnToMainScreen() {
    	// just pass control back to teh main screen
    	Intent resultIntent = new Intent(this, SearchIndex.class);
    	setResult(Activity.RESULT_OK, resultIntent);
    	finish();
    }
}
