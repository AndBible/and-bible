package net.bible.android.view.activity.navigation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.activity.base.ActivityBase;
import net.bible.android.view.activity.page.MainBibleActivity;
import net.bible.android.view.util.keygrid.KeyGridFactory;
import net.bible.android.view.util.keygrid.KeyGridListener;
import net.bible.android.view.util.keygrid.KeyGridFactory.KeyGridViewHolder;
import net.bible.android.view.util.keygrid.KeyGridView.KeyInfo;

import org.crosswire.jsword.passage.NoSuchVerseException;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.BibleInfo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;

/**
 * Choose a bible book e.g. Psalms
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class GridChoosePassageBook extends ActivityBase implements KeyGridListener {
	private static final String TAG = "GridChoosePassageBook";

	private static final String NAME = "NAME";
	private static final String BOOK_NO = "BOOK_NO";

    private ExpandableListAdapter mAdapter;
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        KeyGridViewHolder keyGridViewHolder = KeyGridFactory.getInstance().createKeyGrid(this, getBibleBookKeyInfo(), this); 
        
        setContentView(keyGridViewHolder.topView);
        
    }
    
    private List<KeyInfo> getBibleBookKeyInfo() {
    	List<KeyInfo> keys = new ArrayList<KeyInfo>();
    	for (int i=1; i<=BibleInfo.booksInBible(); i++) {
    		KeyInfo keyInfo = new KeyInfo();
    		try {
    			// this is used for preview
    			keyInfo.id = i;
	    		keyInfo.name = BibleInfo.getShortBookName(i);
    		} catch (NoSuchVerseException nsve) {
    			keyInfo.name = "ERR";
    		}
    		keys.add(keyInfo);
    	}
    	return keys;
    }

	@Override
	public void keyPressed(KeyInfo keyInfo) {
		Log.d(TAG, "***Yay:"+keyInfo.id+" "+keyInfo.name);
    	bookSelected(keyInfo.id);
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
