package net.bible.android.activity;

import java.util.List;

import net.bible.android.util.ActivityBase;
import net.bible.service.sword.SwordApi;

import org.crosswire.jsword.book.Book;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

public class Download extends ActivityBase {
	private static final String TAG = "ChooseDocument";
	
	private ListView bookList;

	private List<Book> bibles;

	private static final int LIST_ITEM_TYPE = android.R.layout.simple_list_item_1; 
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.download);

        initialiseView();
    }

    private void initialiseView() {
    	bookList = (ListView)findViewById(R.id.bookList);

    	bibles = SwordApi.getInstance().getDownloadableDocuments();
    	Log.i(TAG, "number of documents available:"+bibles.size());

    	populateBooks();
    	
    	{
	    	bookList.setOnItemClickListener(new OnItemClickListener() {
	    	    @Override
	    	    public void onItemClick(AdapterView<?> parentView, View selectedItemView, int position, long id) {
	    	    	documentSelected(bibles.get(position));
	    	    	onSave(null);
	    	    }
	    	});
    	}
    }
    private void populateBooks() {
    	try {
	    	ArrayAdapter<Book> listArrayAdapter = new ArrayAdapter<Book>(this,
	    	        LIST_ITEM_TYPE,
	    	        bibles);
	    	bookList.setAdapter(listArrayAdapter);
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
