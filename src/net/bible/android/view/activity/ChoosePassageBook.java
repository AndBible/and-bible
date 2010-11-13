package net.bible.android.view.activity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.bible.android.activity.R;
import net.bible.android.activity.R.layout;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.activity.base.ExpandableListActivityBase;

import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.BibleInfo;
import org.crosswire.jsword.versification.SectionNames;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;

/**
 * Choose a bible book e.g. Psalms
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class ChoosePassageBook extends ExpandableListActivityBase {
	private static final String TAG = "ChoosePassageBook";

	private static final String NAME = "NAME";
	private static final String BOOK_NO = "BOOK_NO";

    private ExpandableListAdapter mAdapter;
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.passage_book_chooser);

        ExpandableListAdapter adapter = createAdapter();
        setListAdapter(adapter); 
    }
    
    /**
     * Creates and returns a list adapter for the current list activity
     * @return
     */
    protected ExpandableListAdapter createAdapter()
    {
    	try {
            List<Map<String, String>> groupData = new ArrayList<Map<String, String>>();
            List<List<Map<String, Object>>> childData = new ArrayList<List<Map<String, Object>>>();

            // prepare sections and hashmaps to contain book list for each section
            for (int i = 0; i < SectionNames.REVELATION; i++) {
                Map<String, String> curGroupMap = new HashMap<String, String>();
                groupData.add(curGroupMap);
                curGroupMap.put(NAME, BibleInfo.getSectionName(i+1));
                
                List<Map<String, Object>> children = new ArrayList<Map<String, Object>>();
                childData.add(children);
            }

            // iterate over all books adding info for each book to the hashmaps created above
            for (int i = 0; i < BibleInfo.booksInBible(); i++) {
            	int bookNo = i+1;
                int sectionNo = SectionNames.getSection(bookNo);
                List<Map<String, Object>> sectionChildren = childData.get(sectionNo-1);

                Map<String, Object> curBookInfoMap = new HashMap<String, Object>();
                sectionChildren.add(curBookInfoMap);
                curBookInfoMap.put(NAME, BibleInfo.getLongBookName(bookNo));
                curBookInfoMap.put(BOOK_NO, new Integer(bookNo));
            }
            
            // Set up our adapter
            mAdapter = new SimpleExpandableListAdapter(
                    this,
                    groupData,
                    android.R.layout.simple_expandable_list_item_1,
                    new String[] { NAME},
                    new int[] { android.R.id.text1 },
                    childData,
                    android.R.layout.simple_expandable_list_item_1,
                    new String[] { NAME},
                    new int[] { android.R.id.text1 }
                    );
	 
	    	return mAdapter;
    	} catch (Exception e) {
    		Log.e(TAG, "Error populating books list", e);
    	}
    	return null;
    }
    
    @Override
	public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
    	Map<String, Object> child = (Map<String, Object>)mAdapter.getChild(groupPosition, childPosition);
    	int bookNo = (Integer)child.get(BOOK_NO);
    	bookSelected(bookNo);
    	return true;
	}


    private void bookSelected(int bibleBookNo) {
    	Log.d(TAG, "Book selected:"+bibleBookNo);
    	try {
    		// if there is only 1 chapter then no need to select chapter
    		if (BibleInfo.chaptersInBook(bibleBookNo)==1) {
        		CurrentPageManager.getInstance().getCurrentBible().setKey(new Verse(bibleBookNo, 1, 1));
        		returnToMainScreen();
    		} else {
    			// select chapter
	        	Intent myIntent = new Intent(this, ChoosePassageChapter.class);
	        	myIntent.putExtra("BOOK_NO", bibleBookNo);
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
