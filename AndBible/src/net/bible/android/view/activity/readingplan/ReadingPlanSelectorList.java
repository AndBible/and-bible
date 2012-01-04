package net.bible.android.view.activity.readingplan;

 import java.util.List;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.readingplan.ReadingPlanControl;
import net.bible.android.view.activity.base.ListActivityBase;
import net.bible.service.readingplan.ReadingPlanInfoDto;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

/** do the search and show the search results
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class ReadingPlanSelectorList extends ListActivityBase {
	private static final String TAG = "ReadingPlanList";
	
    private List<ReadingPlanInfoDto> mReadingPlanList;
    private ArrayAdapter<ReadingPlanInfoDto> mPlanArrayAdapter;

	private ReadingPlanControl mReadingPlanControl = ControlFactory.getInstance().getReadingPlanControl();

	private static final int LIST_ITEM_TYPE = android.R.layout.simple_list_item_2;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState, true);
        Log.i(TAG, "Displaying Reading Plan List");
        setContentView(R.layout.list);
        
        mReadingPlanList = mReadingPlanControl.getReadingPlanList();

       	mPlanArrayAdapter = new ReadingPlanItemAdapter(this, LIST_ITEM_TYPE, mReadingPlanList);
        setListAdapter(mPlanArrayAdapter);
            
        Log.d(TAG, "Finished displaying Reading Plan list");
    }
 
    /** if a plan is selected then ask confirmation, save plan, and go straight to first day
     */
    @Override
	protected void onListItemClick(ListView l, View v, final int position, long id) {
    	try {
    		new AlertDialog.Builder(ReadingPlanSelectorList.this)
			   .setMessage(R.string.rdg_plan_selected_confirmation)
			   .setCancelable(true)
			   .setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
				   public void onClick(DialogInterface dialog, int buttonId) {
						mReadingPlanControl.startReadingPlan(mReadingPlanList.get(position));
						
						Intent intent = new Intent(ReadingPlanSelectorList.this, DailyReading.class);
						startActivity(intent);
						finish();
			       }
			   }).show();
		} catch (Exception e) {
			Log.e(TAG, "Plan selection error", e);
			showErrorMsg(R.string.error_occurred);
		}
	}
}