package net.bible.android.view.activity.readingplan;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.readingplan.ReadingPlanControl;
import net.bible.android.view.activity.base.ExpandableListActivityBase;
import net.bible.android.view.activity.page.MainBibleActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ExpandableListAdapter;

/** show a history list and allow to go to history item
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class DailyReadingList extends ExpandableListActivityBase {

	private static final int FINISHED = 99;

	private static final String TAG = "ChooseGeneralBookKey";
	
	private ReadingPlanControl mReadingPlanControl = ControlFactory.getInstance().getReadingPlanControl();
	
//	private List<Key> mGeneralBookKeyList;
    private ExpandableListAdapter mAdapter;

//	private static final int LIST_ITEM_TYPE = android.R.layout.simple_list_item_1;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Displaying General Book Key chooser");
//        setContentView(R.layout.choose_general_book_key);
    
//        prepareList();

        mAdapter = new DailyReadingExpandableListAdapter(this, mReadingPlanControl.getCurrentDailyPlan()); //, LIST_ITEM_TYPE, mGeneralBookKeyList);
        setListAdapter(mAdapter);
        
        // if an item was selected previously then try to scroll to it
//        Key currentKey = getCurrentGeneralBookPage().getKey();
//        if (currentKey!=null && mGeneralBookKeyList.contains(currentKey)) {
//        	setSelection(TreeKeyHelper.findIndexOf(currentKey, mGeneralBookKeyList));
//        }
        
        Log.d(TAG, "Finished displaying Search view");
    }

//    /**
//     * Creates and returns a list adapter for the current list activity
//     * @return
//     */
//    protected void prepareList()
//    {
//    	Log.d(TAG, "Getting book keys");
//    	mGeneralBookKeyList = new ArrayList<Key>();
//    	try {
//	    	List<Key> keyList = getCurrentGeneralBookPage().getCachedGlobalKeyList();
//	    	
//	    	for (Key key : keyList) {
//	        	mGeneralBookKeyList.add(key);
//	    	}
//    	} catch (Exception e) {
//    		Log.e(TAG, "Error getting key");
//    	}
//    }
//    
//    @Override
//	protected void onListItemClick(ListView l, View v, int position, long id) {
//    	try {
//    		itemSelected(mGeneralBookKeyList.get(position));
//		} catch (Exception e) {
//			Log.e(TAG, "Selection error", e);
//			showErrorMsg(R.string.error_occurred);
//		}
//	}
//    
//    private void itemSelected(Key key) {
//    	Log.d(TAG, "Key selected:"+key);
//    	Log.d(TAG, "Key selected:"+key.getName());
//    	try {
//    		CurrentPageManager.getInstance().getCurrentGeneralBook().setKey(key);
//    		returnToMainScreen();
//    	} catch (Exception e) {
//    		Log.e(TAG, "error on select of gen book key", e);
//    	}
//    }
//
//    private CurrentGeneralBookPage getCurrentGeneralBookPage() {
//    	return ControlFactory.getInstance().getCurrentPageControl().getCurrentGeneralBook();
//    }
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
