package net.bible.android.view.activity.readingplan;

 import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import net.bible.android.activity.R;
import net.bible.android.control.readingplan.ReadingPlanControl;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.android.view.activity.base.ListActivityBase;
import net.bible.service.readingplan.ReadingPlanInfoDto;

import java.util.List;

 import javax.inject.Inject;

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

    private ReadingPlanControl readingPlanControl;

    private static final int LIST_ITEM_TYPE = android.R.layout.simple_list_item_2;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState, true);
        Log.i(TAG, "Displaying Reading Plan List");
        setContentView(R.layout.list);

        buildActivityComponent().inject(this);
        try {
            mReadingPlanList = readingPlanControl.getReadingPlanList();
    
               mPlanArrayAdapter = new ReadingPlanItemAdapter(this, LIST_ITEM_TYPE, mReadingPlanList);
            setListAdapter(mPlanArrayAdapter);
               
            registerForContextMenu(getListView());
        } catch (Exception e) {
            Log.e(TAG, "Error occurred analysing reading lists", e);
            Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e);
            finish();
        }
        Log.d(TAG, "Finished displaying Reading Plan list");
    }
 
    /** if a plan is selected then ask confirmation, save plan, and go straight to first day
     */
    @Override
    protected void onListItemClick(ListView l, View v, final int position, long id) {
        try {
            readingPlanControl.startReadingPlan(mReadingPlanList.get(position));
            
            Intent intent = new Intent(ReadingPlanSelectorList.this, DailyReading.class);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            Log.e(TAG, "Plan selection error", e);
            Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e);
        }
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.reading_plan_list_context_menu, menu);
    }


    @Override
    public boolean onContextItemSelected(MenuItem item) {
        super.onContextItemSelected(item);
        AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
        ReadingPlanInfoDto plan = mReadingPlanList.get(menuInfo.position);
        Log.d(TAG, "Selected "+plan.getCode());
        if (plan!=null) {
            switch (item.getItemId()) {
            case (R.id.reset):
                readingPlanControl.reset(plan);
                return true;
            }
        }
        return false; 
    }

    @Inject
    void setReadingPlanControl(ReadingPlanControl readingPlanControl) {
        this.readingPlanControl = readingPlanControl;
    }
}