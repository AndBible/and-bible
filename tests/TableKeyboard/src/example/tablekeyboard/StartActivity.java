package example.tablekeyboard;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.view.util.buttongrid.ButtonGrid;
import net.bible.android.view.util.buttongrid.OnButtonGridActionListener;
import net.bible.android.view.util.buttongrid.ButtonGrid.ButtonInfo;

import org.crosswire.jsword.passage.NoSuchVerseException;
import org.crosswire.jsword.versification.BibleInfo;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;

public class StartActivity extends Activity {//implements OnTouchListener {
	
	private static final int PENTATEUCH_COLOR = Color.rgb(0xCC, 0xCC, 0xFE);
	private static final int HISTORY_COLOR = Color.rgb(0xFE, 0xCC, 0x9B);
	private static final int WISDOM_COLOR = Color.rgb(0x99, 0xFF, 0x99);
	private static final int MAJOR_PROPHETS_COLOR = Color.rgb(0xFF, 0x99, 0xFF);
	private static final int MINOR_PROPHETS_COLOR = Color.rgb(0xFF, 0xFE, 0xCD);
	private static final int GOSPEL_COLOR = Color.rgb(0xFF, 0x97, 0x03);
	private static final int ACTS_COLOR = Color.rgb(0x00, 0x99, 0xFF);
	private static final int PAULINE_COLOR = Color.rgb(0xFF, 0xFF, 0x31);
	private static final int GENERAL_EPISTLES_COLOR = Color.rgb(0x67, 0x99, 0x66);
	private static final int REVELATION_COLOR = Color.rgb(0xFE, 0x33, 0xFF);
	
	private static final String TAG = "StartActivity";
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // fudge to simulate a method in the real CommonUtils
        CommonUtils.context = getApplicationContext();
        
        ButtonGrid grid = new ButtonGrid(this);
        
        grid.setOnButtonGridActionListener(new OnButtonGridActionListener() {
			
			@Override
			public void buttonPressed(ButtonInfo buttonInfo) {
				Intent intent = new Intent(StartActivity.this, ChapterActivity.class);
				intent.putExtra("book", buttonInfo.id);
				startActivity(intent);
			}
		});
        
        grid.addButtons(getBibleBookButtonInfo());
        setContentView(grid);
    }

    private List<ButtonInfo> getBibleBookButtonInfo() {
    	List<ButtonInfo> keys = new ArrayList<ButtonInfo>();
    	for (int i=1; i<=BibleInfo.booksInBible(); i++) {
    		ButtonInfo buttonInfo = new ButtonInfo();
    		try {
    			// this is used for preview
    			buttonInfo.id = i;
	    		buttonInfo.name = getBookName(i);
	    		buttonInfo.textColor = getBookTextColor(i);
    		} catch (NoSuchVerseException nsve) {
    			buttonInfo.name = "ERR";
    		}
    		keys.add(buttonInfo);
    	}
    	return keys;
    }
    
    private String getBookName(int bookNo) throws NoSuchVerseException {
    	String bookName = BibleInfo.getShortBookName(bookNo);
    	if (bookName.length()<6) {
    		return bookName;
    	}
    	
    	StringBuilder shortenedName = new StringBuilder(4);
    	int i=0;
    	while (shortenedName.length()<4 && i<bookName.length()) {
    		char ch = bookName.charAt(i);
    		if (ch!=' ') {
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