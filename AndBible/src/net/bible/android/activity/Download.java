package net.bible.android.activity;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.CurrentPassage;
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
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;

public class Download extends ActivityBase {
	private static final String TAG = "ChooseDocument";

	private static final BookFilter[] DOCUMENT_TYPE_SPINNER_FILTERS = new BookFilter[] {BookFilters.getBibles(), BookFilters.getCommentaries()};
	private int selectedDocumentFilterNo = 0;
	
	// the document list
	private ListView bookList;
	private ArrayAdapter<String> listArrayAdapter;
	private List<Book> documents;
	private List<String> documentDescriptions = new ArrayList<String>();

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
    	populateDocumentList();
    	listArrayAdapter = new ArrayAdapter<String>(this,
    	        LIST_ITEM_TYPE,
    	        documentDescriptions);
    	bookList.setAdapter(listArrayAdapter);
    	bookList.setOnItemClickListener(new OnItemClickListener() {
    	    @Override
    	    public void onItemClick(AdapterView<?> parentView, View selectedItemView, int position, long id) {
    	    	documentSelected(documents.get(position));
    	    	onSave(null);
    	    }
    	});
    	
    	//prepare the documentType spinner
    	Spinner documentTypeSpinner = (Spinner)findViewById(R.id.documentTypeSpinner);
    	documentTypeSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
		    	selectedDocumentFilterNo = arg2;
		    	Download.this.populateDocumentList();
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
				
			}
		});
    }
    
    private void populateDocumentList() {
    	try {
    	    new AsyncTask<Void, Boolean, Void>() {
    	    	
    	        @Override
    	        protected void onPreExecute() {
    	        	showDialog(Hourglass.HOURGLASS_KEY);
    	        }
    			@Override
    	        protected Void doInBackground(Void... noparam) {
    	        	documents = SwordApi.getInstance().getDownloadableDocuments(DOCUMENT_TYPE_SPINNER_FILTERS[selectedDocumentFilterNo]);
    	        	documentDescriptions.clear();
    	        	for (Book doc : documents) {
    	        		documentDescriptions.add(doc.getName());
    	        	}
    	        	return null;
    			}
    			
    	        @Override
				protected void onPostExecute(Void result) {
    	        	try {
	    	        	if (listArrayAdapter!=null) {
	    	        		Download.this.listArrayAdapter.notifyDataSetChanged();
	    	        	}
    	        	} finally {
    	        		//todo implement this: http://stackoverflow.com/questions/891451/android-dismissdialog-does-not-dismiss-the-dialog
        	        	dismissHourglass();
    	        	}
    	        }

    	    }.execute(null);
        	Log.i(TAG, "number of documents available:"+documents.size());
    	} catch (Exception e) {
    		Log.e(TAG, "Error initialising view", e);
    	}
    }
    
    private void documentSelected(Book document) {
    	Log.d(TAG, "Document selected:"+document.getInitials());
    	try {
    		SwordApi.getInstance().downloadDocument(document);
        	Log.d(TAG, "Download requested");
    	} catch (Exception e) {
    		Log.e(TAG, "Error on attempt to download", e);
    		Toast.makeText(this, R.string.error_downloading, Toast.LENGTH_SHORT).show();
    	}
    }
    
    public void onSave(View v) {
    	Log.i(TAG, "CLICKED");
    	Intent resultIntent = new Intent();
    	setResult(Activity.RESULT_OK, resultIntent);
    	finish();    
    }
}
