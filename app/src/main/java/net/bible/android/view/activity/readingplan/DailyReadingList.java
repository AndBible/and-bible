/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.android.view.activity.readingplan;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import net.bible.android.activity.R;
import net.bible.android.control.readingplan.ReadingPlanControl;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.android.view.activity.base.ListActivityBase;
import net.bible.service.readingplan.OneDaysReadingsDto;

import java.util.List;

import javax.inject.Inject;

/** show a history list and allow to go to history item
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class DailyReadingList extends ListActivityBase {

	private static final String TAG = "DailyReadingList";
	
	private ReadingPlanControl readingPlanControl;
	
	private List<OneDaysReadingsDto> readingsList;
    private ArrayAdapter<OneDaysReadingsDto> adapter;

    /** Called when the activity is first created. */
    @SuppressLint("MissingSuperCall")
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState, true);
        Log.i(TAG, "Displaying General Book Key chooser");
        setContentView(R.layout.list);

		buildActivityComponent().inject(this);

        prepareList();

        adapter = new DailyReadingItemAdapter(this, android.R.layout.simple_list_item_2, readingsList);
        setListAdapter(adapter);
        
        getListView().setFastScrollEnabled(true);
        
        Log.d(TAG, "Finished displaying Search view");
    }

    /**
     * Creates and returns a list adapter for the current list activity
     * @return
     */
    protected void prepareList()
    {
    	Log.d(TAG, "Readingss");
    	readingsList = readingPlanControl.getCurrentPlansReadingList();
    }
    
    @Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
    	try {
    		itemSelected(readingsList.get(position));
		} catch (Exception e) {
			Log.e(TAG, "Selection error", e);
			Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e);
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

	@Inject
	void setReadingPlanControl(ReadingPlanControl readingPlanControl) {
		this.readingPlanControl = readingPlanControl;
	}
}
