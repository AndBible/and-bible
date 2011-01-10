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
import android.os.Bundle;

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
	    		buttonInfo.name = BibleInfo.getShortBookName(i);
    		} catch (NoSuchVerseException nsve) {
    			buttonInfo.name = "ERR";
    		}
    		keys.add(buttonInfo);
    	}
    	return keys;
    }
}