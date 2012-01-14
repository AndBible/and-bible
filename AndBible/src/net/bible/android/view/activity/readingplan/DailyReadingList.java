package net.bible.android.view.activity.readingplan;

import java.util.List;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.readingplan.ReadingPlanControl;
import net.bible.android.view.activity.base.ListActivityBase;
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

	private static final String TAG = "DailyReadingList";
	
	private ReadingPlanControl mReadingPlanControl = ControlFactory.getInstance().getReadingPlanControl();
	
	private List<OneDaysReadingsDto> mReadingsList;
    private ArrayAdapter<OneDaysReadingsDto> mAdapter;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState, true);
        Log.i(TAG, "Displaying General Book Key chooser");
        setContentView(R.layout.list);
    
        prepareList();

        mAdapter = new DailyReadingItemAdapter(this, android.R.layout.simple_list_item_2, mReadingsList);
        setListAdapter(mAdapter);
        
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
			Intent intent = new Intent(this, DailyReading.class);
			intent.putExtra(DailyReading.DAY, oneDaysReadingsDto.getDay());
			startActivity(intent);
			finish();
    	} catch (Exception e) {
    		Log.e(TAG, "error on select of gen book key", e);
    	}
    }
}
