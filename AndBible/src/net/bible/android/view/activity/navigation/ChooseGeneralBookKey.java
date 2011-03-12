package net.bible.android.view.activity.navigation;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentGeneralBookPage;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.activity.base.ListActivityBase;
import net.bible.android.view.activity.page.MainBibleActivity;

import org.crosswire.jsword.passage.Key;

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
public class ChooseGeneralBookKey extends ListActivityBase {

	private static final int FINISHED = 99;

	private static final String TAG = "ChooseGeneralBookKey";
	
	private List<Key> mGeneralBookKeyList;
    private ArrayAdapter<Key> mKeyArrayAdapter;

	private static final int LIST_ITEM_TYPE = android.R.layout.simple_list_item_1;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Displaying General Book Key chooser");
        setContentView(R.layout.choose_general_book_key);
    
        prepareList();

        mKeyArrayAdapter = new GeneralBookKeyItemAdapter(this, LIST_ITEM_TYPE, mGeneralBookKeyList);
        setListAdapter(mKeyArrayAdapter);
        
        Log.d(TAG, "Finished displaying Search view");
    }

    /**
     * Creates and returns a list adapter for the current list activity
     * @return
     */
    protected void prepareList()
    {
    	Log.d(TAG, "Getting book keys");
    	mGeneralBookKeyList = new ArrayList<Key>();
    	try {
	    	CurrentGeneralBookPage currentGeneralBookPage = ControlFactory.getInstance().getCurrentPageControl().getCurrentGeneralBook();
	    	List<Key> keyList = currentGeneralBookPage.getCachedGlobalKeyList();
	    	
	    	for (Key key : keyList) {
	        	mGeneralBookKeyList.add(key);
	    	}
    	} catch (Exception e) {
    		Log.e(TAG, "Error getting key");
    	}
    }
    
    @Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
    	try {
    		itemSelected(mGeneralBookKeyList.get(position));
		} catch (Exception e) {
			Log.e(TAG, "Selection error", e);
			showErrorMsg(R.string.error_occurred);
		}
	}
    
    private void itemSelected(Key key) {
    	Log.d(TAG, "Key selected:"+key);
    	Log.d(TAG, "Key selected:"+key.getName());
    	try {
    		CurrentPageManager.getInstance().getCurrentGeneralBook().setKey(key);
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
