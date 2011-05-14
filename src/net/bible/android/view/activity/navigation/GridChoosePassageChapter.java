package net.bible.android.view.activity.navigation;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.activity.base.ActivityBase;
import net.bible.android.view.util.buttongrid.ButtonGrid;
import net.bible.android.view.util.buttongrid.OnButtonGridActionListener;
import net.bible.android.view.util.buttongrid.ButtonGrid.ButtonInfo;
import net.bible.service.common.CommonUtils;

import org.crosswire.jsword.passage.NoSuchVerseException;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.BibleInfo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

/**
 * Choose a chapter to view
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class GridChoosePassageChapter extends ActivityBase implements OnButtonGridActionListener {
	
	private static final String TAG = "GridChoosePassageChapter";
	
	private BibleBook mBibleBook=BibleBook.GEN;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int bibleBookNo = getIntent().getIntExtra(GridChoosePassageBook.BOOK_NO, 0);
        mBibleBook = BibleBook.getBooks()[bibleBookNo];
                             
        // show chosen book in page title to confirm user choice
        try {
        	setTitle(mBibleBook.getLongName());
        } catch (NoSuchVerseException nsve) {
        	Log.e(TAG, "Error in selected book no", nsve);
        }
        
        ButtonGrid grid = new ButtonGrid(this);
        grid.setOnButtonGridActionListener(this);
        
        grid.addButtons(getBibleChaptersButtonInfo(mBibleBook));
        setContentView(grid);
    }
    
    private List<ButtonInfo> getBibleChaptersButtonInfo(BibleBook book) {
    	int chapters = -1;
    	try {
	    	chapters = BibleInfo.chaptersInBook(book);
		} catch (NoSuchVerseException nsve) {
			chapters = -1;
		}
    	
    	List<ButtonInfo> keys = new ArrayList<ButtonInfo>();
    	for (int i=1; i<=chapters; i++) {
    		ButtonInfo buttonInfo = new ButtonInfo();
			// this is used for preview
			buttonInfo.id = i;
    		buttonInfo.name = Integer.toString(i);
    		keys.add(buttonInfo);
    	}
    	return keys;
    }
    
	@Override
	public void buttonPressed(ButtonInfo buttonInfo) {
		int chapter = buttonInfo.id;
		Log.d(TAG, "Chapter selected:"+chapter);
		try {
			if (!navigateToVerse()) {
				CurrentPageManager.getInstance().getCurrentPage().setKey(new Verse(mBibleBook, chapter, 1));
				onSave(null);
			} else {
    			// select verse
	        	Intent myIntent = new Intent(this, GridChoosePassageVerse.class);
	        	myIntent.putExtra(GridChoosePassageBook.BOOK_NO, mBibleBook.ordinal());
	        	myIntent.putExtra(GridChoosePassageBook.CHAPTER_NO, chapter);
	        	startActivityForResult(myIntent, chapter);
			}
		} catch (Exception e) {
			Log.e(TAG, "error on select of bible book", e);
		}
	}
	
	static boolean navigateToVerse() {
		return CommonUtils.getSharedPreferences().getBoolean("navigate_to_verse_pref", false);
	}

    public void onSave(View v) {
    	Log.i(TAG, "CLICKED");
    	Intent resultIntent = new Intent(this, GridChoosePassageBook.class);
    	setResult(Activity.RESULT_OK, resultIntent);
    	finish();    
    }

    @Override 
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    	if (resultCode==Activity.RESULT_OK) {
    		returnToPreviousScreen();
    	}
    }
}
