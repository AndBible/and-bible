package net.bible.android.view.activity.readingplan;

import java.util.List;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.readingplan.ReadingPlanControl;
import net.bible.android.view.activity.base.ListActivityBase;
import net.bible.android.view.activity.page.MainBibleActivity;
import net.bible.service.readingplan.OneDaysReadingsDto;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

/** show a history list and allow to go to history item
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class DailyReadingList extends ListActivityBase {

	private static final int FINISHED = 99;

	private static final String TAG = "DailyReadingList";
	
	private ReadingPlanControl mReadingPlanControl = ControlFactory.getInstance().getReadingPlanControl();
	
	private List<OneDaysReadingsDto> mReadingsList;
    private ArrayAdapter mAdapter;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Displaying General Book Key chooser");
//        setContentView(R.layout.choose_general_book_key);
    
        prepareList();

        mAdapter = new DailyReadingItemAdapter(this, android.R.layout.simple_list_item_2, mReadingsList);
        setListAdapter(mAdapter);
        
        // if an item was selected previously then try to scroll to it
//        Key currentKey = getCurrentGeneralBookPage().getKey();
//        if (currentKey!=null && mGeneralBookKeyList.contains(currentKey)) {
//        	setSelection(TreeKeyHelper.findIndexOf(currentKey, mGeneralBookKeyList));
//        }
        
        Log.d(TAG, "Finished displaying Search view");
    }

    /**
     * Creates and returns a list adapter for the current list activity
     * @return
     */
    protected void prepareList()
    {
    	Log.d(TAG, "Readingss");
    	mReadingsList = mReadingPlanControl.getCurrentPlansReadingList();
    }
    
    @Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
    	try {
    		itemSelected(mReadingsList.get(position));
		} catch (Exception e) {
			Log.e(TAG, "Selection error", e);
			showErrorMsg(R.string.error_occurred);
		}
	}
    
    private void itemSelected(OneDaysReadingsDto oneDaysReadingsDto) {
    	Log.d(TAG, "Day selected:"+oneDaysReadingsDto);
    	try {
//    		CurrentPageManager.getInstance().getCurrentGeneralBook().setKey(key);
    		returnToMainScreen();
    	} catch (Exception e) {
    		Log.e(TAG, "error on select of gen book key", e);
    	}
    }

    @Override 
    public void onActivityResult(int requestCode, int resultCode, Intent data) { 
    	Log.d(TAG, "Activity result:"+resultCode);
    	super.onActivityResult(requestCode, resultCode, data);
    	
    	if (resultCode == FINISHED) {
    		Log.i(TAG, "Leaf key selected so finish");
    		returnToMainScreen();
    	}
    }

    private void returnToMainScreen() {
    	// just pass control back to the main screen
    	Intent resultIntent = new Intent(this, MainBibleActivity.class);
    	setResult(FINISHED, resultIntent);
    	finish();    
    }
}
