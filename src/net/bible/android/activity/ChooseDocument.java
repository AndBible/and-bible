package net.bible.android.activity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.bible.android.activity.base.AndBibleActivity;
import net.bible.android.activity.base.ListActivityBase;
import net.bible.android.control.page.CurrentPage;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.service.sword.SwordApi;

import org.crosswire.jsword.book.Book;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;

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
	private List<String> documentNames = new ArrayList<String>();
	
	private static final int LIST_ITEM_TYPE = android.R.layout.simple_list_item_1; 
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.document_chooser);
        initialiseView();
    }

    private void initialiseView() {
    	loadDocumentList();
    	
    	ArrayAdapter<String> listArrayAdapter = new ArrayAdapter<String>(this,
    	        LIST_ITEM_TYPE,
    	        documentNames);
    	setListAdapter(listArrayAdapter);
    	
    	registerForContextMenu(getListView());
    }

	/** load list of docs to display
	 * 
	 */
	private void loadDocumentList() {
		documents = SwordApi.getInstance().getDocuments();
    	
    	// get long book names to show in the select list
		// must clear rather than create because the adapter is linked to this specific list
    	documentNames.clear();
    	for (Book book : documents) {
    		documentNames.add(book.getName());
    	}
	}

    @Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
    	bookSelected(documents.get(position));
    }

    @Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.document_context_menu, menu);
		
		Book document = documents.get( ((AdapterContextMenuInfo)menuInfo).position);
		MenuItem deleteItem = menu.findItem(R.id.delete);
		deleteItem.setEnabled(document != null && document.getDriver().isDeletable(document));
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		super.onContextItemSelected(item);
        AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
		Book document = documents.get(menuInfo.position);
		if (document!=null) {
			switch (item.getItemId()) {
			case (R.id.about):
				showAbout(document);
				return true;
			case (R.id.delete):
				delete(document);
				return true;
			}
		}
		return false; 
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

	private void showAbout(Book document) {
		
		//get about text
		Map props = document.getBookMetaData().getProperties();
		String about = (String)props.get("About");
		about = about.replace("\\par", "\n");

    	new AlertDialog.Builder(this)
		   .setMessage(about)
	       .setCancelable(false)
	       .setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
	           public void onClick(DialogInterface dialog, int buttonId) {
	        	   
	           }
	       }).create().show();
	}

	private void delete(Book document) {
		try {
			//TODO pop up a confirmation dialog
			SwordApi.getInstance().deleteDocument(document);
			
			// the doc list should now change
			loadDocumentList();
			((ArrayAdapter)getListAdapter()).notifyDataSetChanged();
		} catch (Exception e) {
			//TODO internationalise
			showErrorMsg("Error deleting document");
		}
		
	}
	private void returnToMainBibleView() {
    	Log.i(TAG, "returning to main bible view");
    	Intent resultIntent = new Intent();
    	setResult(Activity.RESULT_OK, resultIntent);
    	finish();    
    }
}
