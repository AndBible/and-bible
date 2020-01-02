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
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;

import net.bible.android.activity.R;
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.android.view.activity.base.ListActivityBase;

import org.crosswire.jsword.passage.Key;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.inject.Inject;

/**
 * Choose a bible or commentary to use
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class ChooseDictionaryWord extends ListActivityBase {

	private List<Key> mDictionaryGlobalList;
	private List<Key> mMatchingKeyList;

	private ActiveWindowPageManagerProvider activeWindowPageManagerProvider;

	private static final int LIST_ITEM_TYPE = android.R.layout.simple_list_item_1;

	private static final String TAG = "ChooseDictionaryWord";

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.choose_dictionary_page);

		buildActivityComponent().inject(this);

        // ensure there is actually a dictionary
        if (activeWindowPageManagerProvider.getActiveWindowPageManager().getCurrentDictionary().getCurrentDocument()==null) {
        	Log.e(TAG, "No Dictionary");
        	finish();
        	return;
        }
        
        initialise();
        
        EditText searcheditText = (EditText)findViewById(R.id.searchText);
        searcheditText.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence searchText, int arg1, int arg2, int arg3) {
				showPossibleDictionaryKeys(searchText.toString());
			}

			@Override
			public void afterTextChanged(Editable searchText) {
			}
			@Override
			public void beforeTextChanged(CharSequence arg0, int arg1,	int arg2, int arg3) {
			}
        });
        searcheditText.requestFocus();
    }

    /**
     * init the list adapter for the current list activity
     * @return
     */
    protected void initialise()
    {
    	Log.d(TAG, "Initialising");
    	// setting up an initially empty list of matches
    	mMatchingKeyList = new ArrayList<Key>();
        setListAdapter(new ArrayAdapter<Key>(ChooseDictionaryWord.this,
    	        LIST_ITEM_TYPE,
    	        mMatchingKeyList));
    	
    	final Handler uiHandler = new Handler();
    	Dialogs.getInstance().showHourglass();
    	
    	new Thread( new Runnable() {

			@Override
			public void run() {
				try {
					// getting all dictionary keys is slow so do in another thread in order to show hourglass
					//TODO need to optimise this using binary search of globalkeylist without caching
					
			    	//already checked a dictionary exists
			    	mDictionaryGlobalList = activeWindowPageManagerProvider.getActiveWindowPageManager().getCurrentDictionary().getCachedGlobalKeyList();
			    	
			    	Log.d(TAG, "Finished Initialising");
				} catch (Exception e) {
					Log.e(TAG, "Error creating dictionary key list");
					Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e);
				} finally {
			    	// must dismiss hourglass in ui thread
			    	uiHandler.post(new Runnable() {
						@Override
						public void run() {
							dismissHourglass();
						}
			    	});
				}
			}
    	}).start();
    }
    
    /** user has typed something so show keys starting with user's text
     * @param searchText
     */
    private void showPossibleDictionaryKeys(String searchText) {
		Log.d(TAG, "Search for:"+searchText);
		try {
			if (mDictionaryGlobalList!=null) {
				searchText = searchText.toLowerCase();
		
				Iterator<Key> iter = mDictionaryGlobalList.iterator();
				mMatchingKeyList.clear();
				while (iter.hasNext()) {
					Key key = iter.next();
					if (key.getName().toLowerCase().contains(searchText)) {
						mMatchingKeyList.add(key);
					}
				}
				Log.d(TAG, "matches found:"+mMatchingKeyList.size());
		
		    	notifyDataSetChanged();
				Log.d(TAG, "Finished searching for:"+searchText);
			} else {
				Log.d(TAG, "Cached global key list is null");
			}
		} catch (Exception e) {
			Log.e(TAG, "Error finding matching keys", e);
			Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e);
		}
    }
    
    @Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
    	itemSelected(mMatchingKeyList.get(position));
	}

    private void itemSelected(Key selectedKey) {
    	try {
    		if (selectedKey!=null) {
		    	Log.i(TAG, "chose:"+selectedKey);
		    	activeWindowPageManagerProvider.getActiveWindowPageManager().getCurrentDictionary().setKey(selectedKey);
				activeWindowPageManagerProvider.getActiveWindowPageManager().setCurrentDocument(activeWindowPageManagerProvider.getActiveWindowPageManager().getCurrentDictionary().getCurrentDocument());

		    	doFinish();
    		}
    	} catch (Exception e) {
    		Log.e(TAG, "Key not found", e);
    		Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e);
    	}
    }

    private void doFinish() {
    	Intent resultIntent = new Intent();
    	setResult(Activity.RESULT_OK, resultIntent);
    	finish();    
    }

	@Inject
	void setActiveWindowPageManagerProvider(ActiveWindowPageManagerProvider activeWindowPageManagerProvider) {
		this.activeWindowPageManagerProvider = activeWindowPageManagerProvider;
	}
}
