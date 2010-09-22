package net.bible.android.activity;

import net.bible.android.CurrentPassage;

import org.crosswire.jsword.versification.BibleInfo;
import org.crosswire.jsword.versification.BookName;

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
 * Choose a chapter to view
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class ChoosePassageChapter extends ListActivity {
	private static final String TAG = "ChoosePassageChapter";
	
	private static final int LIST_ITEM_TYPE = android.R.layout.simple_list_item_1; 
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.passage_chapter_chooser);

        ListAdapter adapter = createAdapter();
        setListAdapter(adapter);    
    }
    
    /**
     * Creates and returns a list adapter for the current list activity
     * @return
     */
    protected ListAdapter createAdapter()
    {
    	ListAdapter adapter = null;
    	
    	try {
    		BookName book = CurrentPassage.getInstance().getCurrentBibleBook();

    		if (book!=null) {
        		Log.d(TAG, "Populating chapters list");
		    	// get string array of book names
		        String[] chapters = new String[BibleInfo.chaptersInBook(book.getNumber())];
		        for (int i=0; i<chapters.length; i++) {
		        	chapters[i] = book.getLongName()+" "+Integer.toString(i+1);
		        }
		        
		    	adapter = new ArrayAdapter<String>(this, LIST_ITEM_TYPE, chapters);
    		} else {
		    	adapter = new ArrayAdapter<String>(this, LIST_ITEM_TYPE, new String[0]);
    		}
    	} catch (Exception e) {
    		Log.e(TAG, "Error populating chapter list", e);
    	}
    	return adapter;
    }

    
    @Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
    	chapterSelected(position);
	}

    private void chapterSelected(int position) {
		Log.d(TAG, "Chapter selected:"+position);
		try {
			CurrentPassage.getInstance().setCurrentChapter( position+1 );
			onSave(null);

		} catch (Exception e) {
			Log.e(TAG, "error on select of bible book", e);
		}
    }

    public void onSave(View v) {
    	Log.i(TAG, "CLICKED");
    	Intent resultIntent = new Intent(this, ChoosePassageBook.class);
    	setResult(Activity.RESULT_OK, resultIntent);
    	finish();    
    }
}
