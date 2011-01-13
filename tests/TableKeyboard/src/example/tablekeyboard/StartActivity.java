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
    		return Color.rgb(0xCC, 0xCC, 0xFE);
    	} else if (bookNo<18) {
    		// History
    		return Color.rgb(0xFE, 0xCC, 0x9B);
    	} else if (bookNo<23) {
    		// Wisdom
    		return Color.rgb(0x99, 0xFF, 0x99);
    	} else if (bookNo<28) {
    		// Major prophets
    		return Color.rgb(0xFF, 0x99, 0xFF);
    	} else if (bookNo<40) {
    		// Minor prophets
    		return Color.rgb(0xFF, 0xFE, 0xCD);
    	} else if (bookNo<44) {
    		// Gospels
    		return Color.rgb(0xFF, 0x97, 0x03);
    	} else if (bookNo<45) {
    		// Acts
    		return Color.rgb(0x00, 0x99, 0xFF);
    	} else if (bookNo<58) {
    		// Pauline epistles
    		return Color.rgb(0xFF, 0xFF, 0x31);
    	} else if (bookNo<66) {
    		// General epistles
    		return Color.rgb(0x67, 0x99, 0x66);
    	} else {
    		// Revelation
    		return Color.rgb(0xFE, 0x33, 0xFF);
    	}
    }
}