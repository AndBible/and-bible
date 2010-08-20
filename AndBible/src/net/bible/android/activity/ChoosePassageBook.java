package net.bible.android.activity;

import net.bible.android.CurrentPassage;

import org.crosswire.jsword.versification.BibleInfo;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;

/**
 * Choose a bible book e.g. Psalms
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class ChoosePassageBook extends ListActivity {
	private static final String TAG = "ChoosePassageBook";
	
	private static final int LIST_ITEM_TYPE = android.R.layout.simple_list_item_1; 
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.passage_book_chooser);

        ListAdapter adapter = createAdapter();
        setListAdapter(adapter); 
    }
    
    /**
     * Creates and returns a list adapter for the current list activity
     * @return
     */
    protected ListAdapter createAdapter()
    {
    	try {
	    	// get string array of book names
	        String[] books = new String[BibleInfo.booksInBible()];
	        for (int i=0; i<BibleInfo.booksInBible(); i++) {
	        	books[i] = BibleInfo.getLongBookName(i+1);
	        }
	        
	    	// Create a simple array adapter (of type string) containing the list of books
	    	ListAdapter adapter = new ArrayAdapter(this, 
	    			LIST_ITEM_TYPE, books);
	 
	    	return adapter;
    	} catch (Exception e) {
    		Log.e(TAG, "Error populating books list", e);
    	}
    	return null;
    }

    
    @Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
    	bookSelected(position+1);
	}

    private void bookSelected(int bibleBookNo) {
    	Log.d(TAG, "Book selected:"+bibleBookNo);
    	try {
    		//TODO should delay saving until end of tx but just get it working for now
    		CurrentPassage.getInstance().setCurrentBibleBookNo( bibleBookNo );

    		// if there is only 1 chapter then no need to select chapter
    		if (BibleInfo.chaptersInBook(bibleBookNo)==1) {
        		CurrentPassage.getInstance().setCurrentChapter(1);
        		returnToMainScreen();
    		} else {
    			// select chapter
	        	Intent myIntent = new Intent(this, ChoosePassageChapter.class);
	        	startActivityForResult(myIntent, bibleBookNo);
    		}
    	} catch (Exception e) {
    		Log.e(TAG, "error on select of bible book", e);
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
    	Intent resultIntent = new Intent(this, MainBibleActivity.class);
    	setResult(Activity.RESULT_OK, resultIntent);
    	finish();    
    }
}
