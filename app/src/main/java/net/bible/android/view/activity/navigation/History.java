/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

package net.bible.android.view.activity.navigation;

 import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;

import net.bible.android.activity.R;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.android.view.activity.base.ListActivityBase;
 import net.bible.android.view.activity.base.SharedActivityState;
 import net.bible.service.history.HistoryItem;
import net.bible.service.history.HistoryManager;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

/** show a history list and allow to go to history item
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class History extends ListActivityBase {
	private static final String TAG = "History";
	
	private List<HistoryItem> mHistoryItemList;

	private HistoryManager historyManager;
	
	private static final int LIST_ITEM_TYPE = android.R.layout.simple_list_item_1;

	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Displaying History view");
        setContentView(R.layout.history);

		buildActivityComponent().inject(this);

        setListAdapter(createAdapter());

        String name = SharedActivityState.getCurrentWorkspaceName();

        setTitle(String.format("%s (%s)", getTitle(), name));
        Log.d(TAG, "Finished displaying Search view");
    }

    /**
     * Creates and returns a list adapter for the current list activity
     * @return
     */
    protected ListAdapter createAdapter()
    {
    	mHistoryItemList = historyManager.getHistory();
    	List<CharSequence> historyTextList = new ArrayList<>();
    	for (HistoryItem item : mHistoryItemList) {
    		historyTextList.add(item.getDescription());
    	}
    	
    	return new ArrayAdapter<>(this,
    	        LIST_ITEM_TYPE,
    	        historyTextList);
    }
    
    @Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
    	try {
    		historyItemSelected(mHistoryItemList.get(position));
		} catch (Exception e) {
			Log.e(TAG, "Selection error", e);
			Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e);
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

	@Inject
	void setHistoryManager(HistoryManager historyManager) {
		this.historyManager = historyManager;
	}
}
