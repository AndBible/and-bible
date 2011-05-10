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
import org.crosswire.jsword.versification.BibleNames;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;

/**
 * Choose a bible book e.g. Psalms
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class GridChoosePassageBook extends ActivityBase implements OnButtonGridActionListener {

	static final String BOOK_NO = "BOOK_NO";
	static final String CHAPTER_NO = "CHAPTER_NO";
	
	// colour and grouping taken from http://en.wikipedia.org/wiki/Books_of_the_Bible
	private static final int PENTATEUCH_COLOR = Color.rgb(0xCC, 0xCC, 0xFE);
	private static final int HISTORY_COLOR = Color.rgb(0xFE, 0xCC, 0x9B);
	private static final int WISDOM_COLOR = Color.rgb(0x99, 0xFF, 0x99);
	private static final int MAJOR_PROPHETS_COLOR = Color.rgb(0xFF, 0x99, 0xFF);
	private static final int MINOR_PROPHETS_COLOR = Color.rgb(0xFF, 0xFE, 0xCD);
	private static final int GOSPEL_COLOR = Color.rgb(0xFF, 0x97, 0x03);
	private static final int ACTS_COLOR = Color.rgb(0x00, 0x99, 0xFF);
	private static final int PAULINE_COLOR = Color.rgb(0xFF, 0xFF, 0x31);
	private static final int GENERAL_EPISTLES_COLOR = Color.rgb(0x67, 0xCC, 0x66); // changed 99 to CC to make a little clearer on dark background
	private static final int REVELATION_COLOR = Color.rgb(0xFE, 0x33, 0xFF);
	
	private static final String TAG = "GridChoosePassageBook";

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ButtonGrid grid = new ButtonGrid(this);
        
        grid.setOnButtonGridActionListener( this );
        
        grid.addButtons(getBibleBookButtonInfo());
        
        setContentView(grid);
    }
    
	@Override
	public void buttonPressed(ButtonInfo buttonInfo) {
		Log.d(TAG, "Book:"+buttonInfo.id+" "+buttonInfo.name);
    	bookSelected(buttonInfo.id);
	}

    private void bookSelected(int bibleBookNo) {
    	Log.d(TAG, "Book selected:"+bibleBookNo);
    	try {
    		// if there is only 1 chapter then no need to select chapter, but may need to select verse still
    		if (BibleInfo.chaptersInBook(bibleBookNo)==1) {
    			if (!GridChoosePassageChapter.navigateToVerse()) {
    				CurrentPageManager.getInstance().getCurrentBible().setKey(new Verse(bibleBookNo, 1, 1));
    				returnToPreviousScreen();
    			} else {
        			// select verse (only 1 chapter)
    	        	Intent myIntent = new Intent(this, GridChoosePassageVerse.class);
    	        	myIntent.putExtra(GridChoosePassageBook.BOOK_NO, bibleBookNo);
    	        	myIntent.putExtra(GridChoosePassageBook.CHAPTER_NO, 1);
    	        	startActivityForResult(myIntent, 1);
    			}
    		} else {
    			// select chapter
	        	Intent myIntent = new Intent(this, GridChoosePassageChapter.class);
	        	myIntent.putExtra(GridChoosePassageBook.BOOK_NO, bibleBookNo);
	        	startActivityForResult(myIntent, bibleBookNo);
    		}
    	} catch (Exception e) {
    		Log.e(TAG, "error on select of bible book", e);
    	}
    }

    @Override 
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    	if (resultCode==Activity.RESULT_OK) {
    		returnToPreviousScreen();
    	}
    }

    private List<ButtonInfo> getBibleBookButtonInfo() {
    	boolean isShortBookNamesAvailable = isShortBookNames();
    	
    	List<ButtonInfo> keys = new ArrayList<ButtonInfo>(BibleInfo.booksInBible());
    	for (int i=BibleNames.GENESIS; i<=BibleNames.REVELATION; i++) {
    		ButtonInfo buttonInfo = new ButtonInfo();
    		try {
    			// this is used for preview
    			buttonInfo.id = i;
	    		buttonInfo.name = getShortBookName(i, isShortBookNamesAvailable);
	    		buttonInfo.textColor = getBookTextColor(i);
    		} catch (NoSuchVerseException nsve) {
    			buttonInfo.name = "ERR";
    		}
    		keys.add(buttonInfo);
    	}
    	return keys;
    }

	/**
	 * @return
	 * @throws NoSuchVerseException
	 */
	private boolean isShortBookNames() {
		try {
			return !BibleInfo.getShortBookName(BibleNames.GENESIS).equals(BibleInfo.getLongBookName(BibleNames.GENESIS));
		} catch (NoSuchVerseException nsve) {
			// should never get here
			Log.e(TAG, "No such bible book no: 1", nsve);
			return false;
		}
	}
    
    private String getShortBookName(int bookNo, boolean isShortBookNamesAvailable) throws NoSuchVerseException {
    	// shortened names exist so use them
    	if (isShortBookNamesAvailable) {
    		return BibleInfo.getShortBookName(bookNo);
    	}

    	// getShortName will return the long name in place of the short name
    	String bookName = BibleInfo.getShortBookName(bookNo);
    	
    	// so now we shorten the name programatically
    	StringBuilder shortenedName = new StringBuilder(4);
    	int i=0;
    	while (shortenedName.length()<4 && i<bookName.length()) {
    		char ch = bookName.charAt(i);
    		if (ch!=' ' && ch!='.') {
    			shortenedName.append(ch);
    		}
    		i++;
    	}
    	
    	return shortenedName.toString();
    }
    
    private int getBookTextColor(int bookNo) {
    	// colour and grouping taken from http://en.wikipedia.org/wiki/Books_of_the_Bible
    	if (bookNo<6) {
    		// Pentateuch - books of Moses
    		return PENTATEUCH_COLOR;
    	} else if (bookNo<18) {
    		// History
    		return HISTORY_COLOR;
    	} else if (bookNo<23) {
    		// Wisdom
    		return WISDOM_COLOR;
    	} else if (bookNo<28) {
    		// Major prophets
    		return MAJOR_PROPHETS_COLOR;
    	} else if (bookNo<40) {
    		// Minor prophets
    		return MINOR_PROPHETS_COLOR;
    	} else if (bookNo<44) {
    		// Gospels
    		return GOSPEL_COLOR;
    	} else if (bookNo<45) {
    		// Acts
    		return ACTS_COLOR;
    	} else if (bookNo<58) {
    		// Pauline epistles
    		return PAULINE_COLOR;
    	} else if (bookNo<66) {
    		// General epistles
    		return GENERAL_EPISTLES_COLOR;
    	} else {
    		// Revelation
    		return REVELATION_COLOR;
    	}
    }
}
