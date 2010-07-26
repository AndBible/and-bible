package net.bible.android.activity;

 import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import net.bible.android.CurrentPassage;
import net.bible.service.sword.SwordApi;

import org.crosswire.common.progress.JobManager;
import org.crosswire.common.progress.WorkEvent;
import org.crosswire.common.progress.WorkListener;
import org.crosswire.common.util.CWProject;
import org.crosswire.common.util.NetUtil;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookMetaData;
import org.crosswire.jsword.index.lucene.PdaLuceneIndexManager;
import org.crosswire.jsword.passage.Key;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

public class Search extends Activity {
	private static final String TAG = "Search";
	private static final int MAX_SEARCH_RESULTS = 100;
	
	private EditText mSearchTextInput;
	private ListView mSearchResults;
	
	private TextView mStatusTextView;
	
    static final protected String LIST_ITEM_LINE1 = "line1";
    static final protected String LIST_ITEM_LINE2 = "line2";	
    private List<ResultItem> mResultList;
	private SimpleAdapter mResultAdapter; 
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Displaying Search view");
        setContentView(R.layout.search);
    
        mSearchTextInput =  (EditText)findViewById(R.id.searchText);
        mStatusTextView =  (TextView)findViewById(R.id.statusText);
        mSearchResults =  (ListView)findViewById(R.id.searchResults);
        
        initialiseView();
        Log.d(TAG, "Finished displaying Search view");
    }

    private void initialiseView() {
    	mResultList = new ArrayList<ResultItem>();
        mResultAdapter = new SimpleAdapter(this, mResultList, 
                    android.R.layout.two_line_list_item, 
                    new String[] {LIST_ITEM_LINE1, LIST_ITEM_LINE2}, 
                    new int[] {android.R.id.text1, android.R.id.text2});
        	
        	new ArrayAdapter<ResultItem>(this, android.R.layout.simple_list_item_1,
                    mResultList);

        mSearchResults.setAdapter(mResultAdapter);
        
    	mSearchResults.setOnItemClickListener(new OnItemClickListener() {
    	    @Override
    	    public void onItemClick(AdapterView<?> parentView, View selectedItemView, int position, long id) {
    	    	verseSelected(mResultList.get(position));
    	    }
    	});
    }

    // Indexing is too slow and fails aftr 1 hour - the experimental method below does not improve things enough to make indexing succeed 
    public void onIndex(View v) {
    	Log.i(TAG, "CLICKED");
    	showMsg("Starting index");
    	try {
	        Book book = CurrentPassage.getInstance().getCurrentDocument();

	        PdaLuceneIndexManager lim = new PdaLuceneIndexManager();
	        lim.scheduleIndexCreation(book);
//			URI indexStorage = getIndexStorageArea(book);
//			
//			PdaLuceneIndex fastLuceneIndex = new PdaLuceneIndex(book, indexStorage, true);
	    	Log.i(TAG, "Finished indexing");
			
    	} catch (Exception e) {
    		Log.e(TAG, "error indexing:"+e.getMessage());
    		e.printStackTrace();
    	}

//        new PdaLuceneIndexManager().scheduleIndexCreation(bible);
		JobManager.addWorkListener(new WorkListener() {

			@Override
			public void workProgressed(WorkEvent ev) {
				int total = ev.getJob().getTotalWork();
				int done = ev.getJob().getWork();
				String section = ev.getJob().getSectionName();
				String msg = "Done "+done+" of "+ total+" on "+section;
				final String status = msg;
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						showMsg(status);
					}
				});
			}

			@Override
			public void workStateChanged(WorkEvent ev) {
				int total = ev.getJob().getTotalWork();
				int done = ev.getJob().getWork();
				String section = ev.getJob().getSectionName();
				String msg = "state bDone "+done+" of "+ total+" on "+section;
				final String status = msg;
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						showMsg(status);
					}
				});
			}
			
		});
    }
    
    public void onSearch(View v) {
    	Log.i(TAG, "CLICKED");
        Book bible = CurrentPassage.getInstance().getCurrentDocument();
    	
    	SwordApi swordApi = SwordApi.getInstance();
    	String searchText = mSearchTextInput.getText().toString();
    	Key result = swordApi.search(bible, searchText);
    	if (result!=null) {
    		int resNum = result.getCardinality();
        	Log.d(TAG, "Number of results:"+resNum);
        	String msg = resNum+" matches found";
    		if (resNum>MAX_SEARCH_RESULTS) {
    			msg = "Too many matches.  Showing first "+MAX_SEARCH_RESULTS;
    		}
    		showMsg(msg);
    		mResultList.clear();
    		for (int i=0; i<Math.min(resNum, MAX_SEARCH_RESULTS); i++) {
    			mResultList.add(new ResultItem(result.get(i)));
    		}
    	}
    	mResultAdapter.notifyDataSetChanged();
    }
    
    public void onFinish(View v) {
    	Log.i(TAG, "CLICKED");
    	doFinish();    
    }
    public void doFinish() {
    	Intent resultIntent = new Intent();
    	setResult(Activity.RESULT_OK, resultIntent);
    	finish();    
    }
    
    private void showMsg(String msg) {
    	mStatusTextView.setText(msg);
    }
    
    private void verseSelected(ResultItem resultItem) {
    	Log.i(TAG, "chose:"+resultItem);
    	CurrentPassage.getInstance().setKey(resultItem.verse);
    	doFinish();
    }
    
    static class ResultItem extends HashMap<String, String> {
    	private Key verse;
    	ResultItem(Key verse) {
    		this.verse = verse;
    	}
    	public String toString() {
    		try {
	    		return verse.getName();
    		} catch (Exception e) {
    			Log.e(TAG, "Error getting found verse", e);
    			return "";
    		}
    	}
    	
    	
    	@Override
		public String get(Object key) {
    		String retval = "";
    		try {
	    		if (key.equals(LIST_ITEM_LINE1)) {
	    			retval = verse.getName();
	    		} else {
	    			retval = SwordApi.getInstance().getPlainText(CurrentPassage.getInstance().getCurrentDocument(), verse.getName(), 1);
	    		}
    		} catch (Exception e) {
    			Log.e(TAG, "Error getting search result", e);
    		}
    		return retval;
    	}
    }
}
