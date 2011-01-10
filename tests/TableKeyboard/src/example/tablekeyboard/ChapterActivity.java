package example.tablekeyboard;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.view.util.buttongrid.ButtonGrid;
import net.bible.android.view.util.buttongrid.OnButtonGridActionListener;
import net.bible.android.view.util.buttongrid.ButtonGrid.ButtonInfo;

import org.crosswire.jsword.passage.NoSuchVerseException;
import org.crosswire.jsword.versification.BibleInfo;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

public class ChapterActivity extends Activity {//implements OnTouchListener {
	
	private static final String TAG = "StartActivity";
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ButtonGrid grid = new ButtonGrid(this);
        
        grid.setOnButtonGridActionListener(new OnButtonGridActionListener() {
			
			@Override
			public void buttonPressed(ButtonInfo buttonInfo) {
				Log.d(TAG, "selected "+buttonInfo.name);
				finish();
			}
		});
        
        int bookNo = getIntent().getIntExtra("book", -1);
        grid.addButtons(getBibleChaptersButtonInfo(bookNo));
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
}