package net.bible.android.view.activity.navigation;

 import java.util.ArrayList;
import java.util.List;

import net.bible.android.activity.R;
import net.bible.android.view.activity.base.ListActivityBase;
import net.bible.service.history.HistoryItem;
import net.bible.service.history.HistoryManager;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;

/** show a history list and allow to go to history item
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class History extends ListActivityBase {
	private static final String TAG = "History";
	
	private List<HistoryItem> mHistoryItemList;
	
	private static final int LIST_ITEM_TYPE = android.R.layout.simple_list_item_1; 

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Displaying History view");
        setContentView(R.layout.history);
    
        setListAdapter(createAdapter());
        
        Log.d(TAG, "Finished displaying Search view");
    }

    /**
     * Creates and returns a list adapter for the current list activity
     * @return
     */
    protected ListAdapter createAdapter()
    {
    	
    	mHistoryItemList = HistoryManager.getInstance().getHistory();
    	List<String> historyTextList = new ArrayList<String>();
    	for (HistoryItem item : mHistoryItemList) {
    		historyTextList.add(item.getDescription());
    	}
    	
    	return new ArrayAdapter<String>(this,
    	        LIST_ITEM_TYPE,
    	        historyTextList);
    }
    
    @Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
    	try {
    		historyItemSelected(mHistoryItemList.get(position));
		} catch (Exception e) {
			Log.e(TAG, "Selection error", e);
			showErrorMsg(R.string.error_occurred);
		}
	}
    
    private void historyItemSelected(HistoryItem historyItem) {
    	Log.i(TAG, "chose:"+historyItem);
    	historyItem.revertTo();
    	doFinish();
    }

    private void doFinish() {
    	Intent resultIntent = new Intent();
    	setResult(Activity.RESULT_OK, resultIntent);
    	finish();    
    }
}
