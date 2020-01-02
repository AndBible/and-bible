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

package net.bible.android.view.activity.navigation.genbookmap;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import net.bible.android.activity.R;
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.android.view.activity.base.ListActivityBase;
import net.bible.android.view.activity.page.MainBibleActivity;

import org.crosswire.jsword.passage.Key;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

/** show a list of keys and allow to select an item
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public abstract class ChooseKeyBase extends ListActivityBase {

	private static final int FINISHED = 99;

	private static final String TAG = "ChooseKeyBase";
	
	private List<Key> mKeyList;
    private ArrayAdapter<Key> mKeyArrayAdapter;

	private ActiveWindowPageManagerProvider activeWindowPageManagerProvider;

	private static final int LIST_ITEM_TYPE = android.R.layout.simple_list_item_1;

	abstract Key getCurrentKey();
	abstract List<Key> getKeyList();
    abstract void itemSelected(Key key);
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Displaying Key chooser");
        setContentView(R.layout.choose_general_book_key);

		buildActivityComponent().inject(this);

        prepareList();

        mKeyArrayAdapter = new KeyItemAdapter(this, LIST_ITEM_TYPE, mKeyList);
        setListAdapter(mKeyArrayAdapter);
        
        // if an item was selected previously then try to scroll to it
        Key currentKey = getCurrentKey();
        if (currentKey!=null && mKeyList.contains(currentKey)) {
        	setSelection(mKeyList.indexOf(currentKey));
        }
        
        Log.d(TAG, "Finished displaying Search view");
    }

    /**
     * Creates and returns a list adapter for the current list activity
     * @return
     */
    protected void prepareList()
    {
    	Log.d(TAG, "Getting book keys");
    	mKeyList = new ArrayList<Key>();
    	try {
	    	List<Key> keyList = getKeyList();
	    	
	    	for (Key key : keyList) {
	        	mKeyList.add(key);
	    	}
    	} catch (Exception e) {
    		Log.e(TAG, "Error getting key");
    	}
    }
    
    @Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
    	try {
    		Key selected = mKeyList.get(position);
    		Log.i(TAG, "Selected:"+selected);
    		itemSelected(selected);

    		returnToMainScreen();
		} catch (Exception e) {
			Log.e(TAG, "Selection error", e);
			Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e);
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

	protected ActiveWindowPageManagerProvider getActiveWindowPageManagerProvider() {
		return activeWindowPageManagerProvider;
	}

	@Inject
	void setActiveWindowPageManagerProvider(ActiveWindowPageManagerProvider activeWindowPageManagerProvider) {
		this.activeWindowPageManagerProvider = activeWindowPageManagerProvider;
	}
}
