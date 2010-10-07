package net.bible.android.activity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.bible.android.CurrentPassage;

import org.crosswire.jsword.versification.BibleInfo;
import org.crosswire.jsword.versification.BookName;

import android.app.Activity;
import android.app.ExpandableListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ListAdapter;
import android.widget.SimpleExpandableListAdapter;

/**
 * Choose a chapter to view
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class ChoosePassageChapter extends ExpandableListActivity {
	private static final String TAG = "ChoosePassageChapter";
	
	private static final String GROUP_DESC = "GROUP_DESC";
	private static final String CHAPTER_DESC = "CHAPTER_DESC";
	private static final String CHAPTER_NO = "CHAPTER_NO";

	private static final int MIN_CHAPTERS_TO_GROUP = 15;
	
	private static final String CHAPTER_PRE = "Chapter ";
	private static final String CHAPTER_SEP = " chapters ";
	private static final String TO = " to ";

	private ExpandableListAdapter mAdapter;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // fix for null context class loader (http://code.google.com/p/android/issues/detail?id=5697)
        // this affected jsword dynamic classloading
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

        setContentView(R.layout.passage_chapter_chooser);

        ExpandableListAdapter adapter = createAdapter();
        setListAdapter(adapter);

        // if there is only 1 group then expand it automatically
        if (getNumberOfGroups(CurrentPassage.getInstance().getCurrentBibleBook())==1) {
            getExpandableListView().expandGroup(0);
        }
}
    
    /**
     * Creates and returns a list adapter for the current list activity
     * @return
     */
    protected ExpandableListAdapter createAdapter()
    {
    	ListAdapter adapter = null;
    	
    	try {
    		BookName book = CurrentPassage.getInstance().getCurrentBibleBook();

    		if (book!=null) {
        		Log.d(TAG, "Populating chapters list");
        		int numGroups = getNumberOfGroups(book);
		        		        
//        		boolean groupChapters = numChapters > MIN_CHAPTERS_TO_GROUP;
        		
                List<Map<String, String>> groupData = new ArrayList<Map<String, String>>();
                List<List<Map<String, Object>>> childData = new ArrayList<List<Map<String, Object>>>();

                // prepare sections and hashmaps to contain book list for each section
                for (int groupNo = 0; groupNo < numGroups; groupNo++) {
                    Map<String, String> curGroupMap = new HashMap<String, String>();
                    groupData.add(curGroupMap);
                    curGroupMap.put(GROUP_DESC, getGroupName(book, groupNo));
                    
                    // add all chapters in current group to list
                    List<Map<String, Object>> children = new ArrayList<Map<String, Object>>();
                    for (int chapter=getGroupStart(groupNo); chapter<=getGroupEnd(book, groupNo); chapter++) {
                        Map<String, Object> curChapterInfoMap = new HashMap<String, Object>();
                        children.add(curChapterInfoMap);
                        curChapterInfoMap.put(CHAPTER_DESC, CHAPTER_PRE+chapter);
                        curChapterInfoMap.put(CHAPTER_NO, new Integer(chapter));
                    }
                    childData.add(children);
                }

                // Set up our adapter
                mAdapter = new SimpleExpandableListAdapter(
                        this,
                        groupData,
                        android.R.layout.simple_expandable_list_item_1,
                        new String[] { GROUP_DESC},
                        new int[] { android.R.id.text1 },
                        childData,
                        android.R.layout.simple_expandable_list_item_1,
                        new String[] { CHAPTER_DESC},
                        new int[] { android.R.id.text1 }
                        );
                
    		} else {
                mAdapter = new SimpleExpandableListAdapter(
                        this,
                        null,
                        android.R.layout.simple_expandable_list_item_1,
                        new String[] { GROUP_DESC},
                        new int[] { android.R.id.text1 },
                        null,
                        android.R.layout.simple_expandable_list_item_1,
                        new String[] { CHAPTER_DESC},
                        new int[] { android.R.id.text1 }
                        );
    		}
    	} catch (Exception e) {
    		Log.e(TAG, "Error populating chapter list", e);
    	}
    	return mAdapter;
    }

    /** user has selected a chapter
     */
    @Override
	public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
    	// get the selected chapter from the selected list items map
    	Map<String, Object> child = (Map<String, Object>)mAdapter.getChild(groupPosition, childPosition);
    	int chapterNo = (Integer)child.get(CHAPTER_NO);
    	chapterSelected(chapterNo);
    	return true;
	}

    private void chapterSelected(int chapter) {
		Log.d(TAG, "Chapter selected:"+chapter);
		try {
			CurrentPassage.getInstance().setCurrentChapter( chapter );
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

    private int getNumberOfGroups(BookName book) {
    	try {
	        int numChapters = BibleInfo.chaptersInBook(book.getNumber());
	        return (int)Math.ceil( (double)numChapters / 10.0d );
		} catch (Exception e) {
			Log.e(TAG, "Error calculating number of chapter groups", e);
			return 1;
		}
    }
    
    private String getGroupName(BookName book, int groupNo) {
    	StringBuffer buff = new StringBuffer();
    	buff.append(book.getLongName()).append(CHAPTER_SEP);
    	buff.append(getGroupStart(groupNo))
    		.append(TO)
    		.append(getGroupEnd(book, groupNo));
    	
    	return buff.toString();
    }
    
    private int getGroupStart(int groupNo) {
    	return (groupNo*10)+1;
    }
    private int getGroupEnd(BookName book, int groupNo) {
    	try {
	    	int chaptersInBook = BibleInfo.chaptersInBook(book.getNumber());
	    	return Math.min(chaptersInBook, (groupNo+1)*10);
    	} catch (Exception e) {
    		Log.e(TAG, "Error calculating chapters in book", e);
    		return 1;
    	}
    }
}
