package net.bible.android.activity;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.CurrentPassage;
import net.bible.android.activity.base.ActivityBase;
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

/**
 * Choose a bible or commentary to use
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class ChooseDocument extends ActivityBase {
	private static final String TAG = "ChooseDocument";
	
	private ListView bookList;

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
    	bookList = (ListView)findViewById(R.id.bookList);
    	documents = SwordApi.getInstance().getDocuments();
    	
    	// get long book names to show in the select list
    	List<String> docNames = new ArrayList<String>();
    	for (Book book : documents) {
    		docNames.add(book.getName());
    	}
    	
    	ArrayAdapter<String> listArrayAdapter = new ArrayAdapter<String>(this,
    	        LIST_ITEM_TYPE,
    	        docNames);
    	bookList.setAdapter(listArrayAdapter);
    	
    	{
	    	bookList.setOnItemClickListener(new OnItemClickListener() {
	    	    @Override
	    	    public void onItemClick(AdapterView<?> parentView, View selectedItemView, int position, long id) {
	    	    	bookSelected(position);
	    	    	onSave(null);
	    	    }
	    	});
    	}
    }
    
    private void bookSelected(int position) {
    	Log.d(TAG, "Book selected:"+position);
    	try {
    		CurrentPassage.getInstance().setCurrentDocument( documents.get(position) );
    	} catch (Exception e) {
    		Log.e(TAG, "error on select of bible book", e);
    	}
    }
    
    public void onSave(View v) {
    	Log.i(TAG, "CLICKED");
    	Intent resultIntent = new Intent();
    	setResult(Activity.RESULT_OK, resultIntent);
    	finish();    
    }
}
