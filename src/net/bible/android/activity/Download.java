package net.bible.android.activity;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.util.ActivityBase;
import net.bible.android.util.Hourglass;
import net.bible.service.sword.SwordApi;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookFilter;
import org.crosswire.jsword.book.BookFilters;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;

public class Download extends ActivityBase {
	private static final String TAG = "Download";

	private static final BookFilter[] DOCUMENT_TYPE_SPINNER_FILTERS = new BookFilter[] {BookFilters.getBibles(), BookFilters.getCommentaries()};
	private int selectedDocumentFilterNo = 0;
	
	// the document list
	private ListView bookList;
	private ArrayAdapter<String> listArrayAdapter;
	private List<Book> allDocuments;
	private List<Book> displayedDocuments;
	private List<String> displayedDocumentDescriptions = new ArrayList<String>();

	private static final int LIST_ITEM_TYPE = android.R.layout.simple_list_item_1; 
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.download);

        initialiseView();
    }

    private void initialiseView() {
    	// prepare the document list view
    	bookList = (ListView)findViewById(R.id.bookList);
    	populateMasterDocumentList();
    	listArrayAdapter = new ArrayAdapter<String>(this,
    	        LIST_ITEM_TYPE,
    	        displayedDocumentDescriptions);
    	bookList.setAdapter(listArrayAdapter);
    	bookList.setOnItemClickListener(new OnItemClickListener() {
    	    @Override
    	    public void onItemClick(AdapterView<?> parentView, View selectedItemView, int position, long id) {
    	    	documentSelected(displayedDocuments.get(position));
    	    }
    	});
    	
    	//prepare the documentType spinner
    	Spinner documentTypeSpinner = (Spinner)findViewById(R.id.documentTypeSpinner);
    	documentTypeSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
		    	selectedDocumentFilterNo = arg2;
		    	Download.this.filterDocuments();
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
			}
		});
    }
    
    private void populateMasterDocumentList() {
    	try {
    		if (allDocuments==null || allDocuments.size()==0) {
	    	    new AsyncTask<Void, Boolean, Void>() {
	    	    	
	    	        @Override
	    	        protected void onPreExecute() {
	    	        	showDialog(Hourglass.HOURGLASS_KEY);
	    	        }
	    			@Override
	    	        protected Void doInBackground(Void... noparam) {
	    	        	allDocuments = SwordApi.getInstance().getDownloadableDocuments(BookFilters.getAll());
	    	        	Log.i(TAG, "number of documents available:"+allDocuments.size());
	    	        	return null;
	    			}
	    			
	    	        @Override
					protected void onPostExecute(Void result) {
	    	        	try {
	    	        		filterDocuments();
	    	        	} finally {
	    	        		//todo implement this: http://stackoverflow.com/questions/891451/android-dismissdialog-does-not-dismiss-the-dialog
	        	        	dismissHourglass();
	    	        	}
	    	        }
	
	    	    }.execute(null);
    		}
    	} catch (Exception e) {
    		Log.e(TAG, "Error initialising view", e);
    		Toast.makeText(this, getString(R.string.error)+e.getMessage(), Toast.LENGTH_SHORT);
    	}
    }
    
    /** a spinner has changed so refilter the doc list
     */
    private void filterDocuments() {
    	try {
    		if (allDocuments!=null && allDocuments.size()>0) {
   	        	Log.i(TAG, "filtering documents");
	        	displayedDocumentDescriptions.clear();
	        	for (Book doc : allDocuments) {
	        		BookFilter filter = DOCUMENT_TYPE_SPINNER_FILTERS[selectedDocumentFilterNo];
	        		if (filter.test(doc)) {
		        		displayedDocuments.add(doc);
		        		displayedDocumentDescriptions.add(doc.getName());
	        		}
	        	}
	        	if (listArrayAdapter!=null) {
	        		Download.this.listArrayAdapter.notifyDataSetChanged();
	        	}
    		}
    	} catch (Exception e) {
    		Log.e(TAG, "Error initialising view", e);
    		Toast.makeText(this, getString(R.string.error)+e.getMessage(), Toast.LENGTH_SHORT);
    	}
    }

    /** user selected a document so download it
     * 
     * @param document
     */
    private void documentSelected(Book document) {
    	Log.d(TAG, "Document selected:"+document.getInitials());
    	try {
    		// the download happens in another thread
    		SwordApi.getInstance().downloadDocument(document);
        	Log.d(TAG, "Download requested");

        	// monitor the download
        	//todo a simple popup ProgressDialog may be better - not sure
        	Intent myIntent = new Intent(this, DownloadStatus.class);
        	startActivityForResult(myIntent, 1);
        	
    	} catch (Exception e) {
    		Log.e(TAG, "Error on attempt to download", e);
    		Toast.makeText(this, R.string.error_downloading, Toast.LENGTH_SHORT).show();
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
    	Intent resultIntent = new Intent(this, Download.class);
    	setResult(Activity.RESULT_OK, resultIntent);
    	finish();    
    }
}
