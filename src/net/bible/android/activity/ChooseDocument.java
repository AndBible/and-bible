package net.bible.android.activity;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.activity.base.ListActivityBase;
import net.bible.android.control.page.CurrentPage;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.service.sword.SwordApi;

import org.crosswire.jsword.book.Book;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

/**
 * Choose a bible or commentary to use
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class ChooseDocument extends ListActivityBase {
	private static final String TAG = "ChooseDocument";
	
	private List<Book> documents;

	private static final int LIST_ITEM_TYPE = android.R.layout.simple_list_item_1; 
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.document_chooser);
        initialiseView();
    }

    private void initialiseView() {
    	documents = SwordApi.getInstance().getDocuments();
    	
    	// get long book names to show in the select list
    	List<String> docNames = new ArrayList<String>();
    	for (Book book : documents) {
    		docNames.add(book.getName());
    	}
    	
    	ArrayAdapter<String> listArrayAdapter = new ArrayAdapter<String>(this,
    	        LIST_ITEM_TYPE,
    	        docNames);
    	setListAdapter(listArrayAdapter);
    }

    @Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
    	bookSelected(documents.get(position));
    }

    private void bookSelected(Book selectedBook) {
    	Log.d(TAG, "Book selected:"+selectedBook.getInitials());
    	try {
    		CurrentPage newPage = CurrentPageManager.getInstance().setCurrentDocument( selectedBook );
    		
    		// page will change due to above
    		// if there is a valid key then show the page straight away
    		if (newPage.getKey()==null) {
    			// no key set for this doc type so show a key chooser
    			//TODO this code is generic and needs to be performed whenever a doc changes so think where to put it
    	    	Intent intent = new Intent(this, newPage.getKeyChooserActivity());
    	    	startActivity(intent);
    	    	finish();    
    		} else {
    			// if key is valid then the new doc will have been shown already
    			returnToMainBibleView();
    		}
    	} catch (Exception e) {
    		Log.e(TAG, "error on select of bible book", e);
    	}
    }
    
    private void returnToMainBibleView() {
    	Log.i(TAG, "returning to main bible view");
    	Intent resultIntent = new Intent();
    	setResult(Activity.RESULT_OK, resultIntent);
    	finish();    
    }
}
