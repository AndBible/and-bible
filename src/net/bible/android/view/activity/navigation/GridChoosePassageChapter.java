package net.bible.android.view.activity.navigation;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.activity.base.ActivityBase;
import net.bible.android.view.util.buttongrid.ButtonGrid;
import net.bible.android.view.util.buttongrid.OnButtonGridActionListener;
import net.bible.android.view.util.buttongrid.ButtonGrid.ButtonInfo;

import org.crosswire.jsword.passage.NoSuchVerseException;
import org.crosswire.jsword.passage.Verse;
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
	
	private int mBibleBookNo=1;

	private static final String GROUP_DESC = "GROUP_DESC";
	private static final String CHAPTER_DESC = "CHAPTER_DESC";
	private static final String CHAPTER_NO = "CHAPTER_NO";

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBibleBookNo = getIntent().getIntExtra("BOOK_NO", 1);
        ButtonGrid grid = new ButtonGrid(this);
        grid.setOnButtonGridActionListener(this);
        
        grid.addButtons(getBibleChaptersButtonInfo(mBibleBookNo));
        setContentView(grid);
    }
    
    private List<ButtonInfo> getBibleChaptersButtonInfo(int bookNo) {
    	int chapters = -1;
    	try {
	    	chapters = BibleInfo.chaptersInBook(bookNo);
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
			CurrentPageManager.getInstance().getCurrentPage().setKey(new Verse(mBibleBookNo, chapter, 1));
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
