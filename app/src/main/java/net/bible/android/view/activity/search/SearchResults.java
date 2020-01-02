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

package net.bible.android.view.activity.search;

 import android.annotation.SuppressLint;
 import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import net.bible.android.activity.R;
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider;
import net.bible.android.control.search.SearchControl;
import net.bible.android.control.search.SearchResultsDto;
import net.bible.android.view.activity.base.Callback;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.android.view.activity.base.ListActivityBase;
import net.bible.android.view.activity.search.searchresultsactionbar.SearchResultsActionBarManager;

import org.apache.commons.lang3.StringUtils;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

/** do the search and show the search results
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class SearchResults extends ListActivityBase {
	private static final String TAG = "SearchResults";
	
    private SearchResultsDto mSearchResultsHolder;
    
    private List<Key> mCurrentlyDisplayedSearchResults = new ArrayList<>();
    private ArrayAdapter<Key> mKeyArrayAdapter;

    private boolean isScriptureResultsCurrentlyShown = true;
    
	private SearchResultsActionBarManager searchResultsActionBarManager;
	
	private SearchControl searchControl;

	private ActiveWindowPageManagerProvider activeWindowPageManagerProvider;

	private static final int LIST_ITEM_TYPE = android.R.layout.simple_list_item_2;

    public SearchResults() {
		super(R.menu.empty_menu);
		
	}
	
    /** Called when the activity is first created. */
    @SuppressLint("MissingSuperCall")
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState, true);
        Log.i(TAG, "Displaying Search results view");
        setContentView(R.layout.list);

		buildActivityComponent().inject(this);

		searchResultsActionBarManager.registerScriptureToggleClickListener(scriptureToggleClickListener);
		setActionBarManager(searchResultsActionBarManager);

        isScriptureResultsCurrentlyShown = searchControl.isCurrentDefaultScripture();

        if (fetchSearchResults()) {
            // initialise adapters before result population - easier when updating due to later Scripture toggle 
        	mKeyArrayAdapter = new SearchItemAdapter(this, LIST_ITEM_TYPE, mCurrentlyDisplayedSearchResults, searchControl);
            setListAdapter(mKeyArrayAdapter);

			populateViewResultsAdapter();
        }
    }

    /** do the search query and prepare results in lists ready for display
     * 
     */
    private boolean fetchSearchResults() {
    	Log.d(TAG, "Preparing search results");
    	boolean isOk;

    	try {
    		// get search string - passed in using extras so extras cannot be null
            Bundle extras = getIntent().getExtras();
			String searchText = extras.getString(SearchControl.SEARCH_TEXT);
			String searchDocument = extras.getString(SearchControl.SEARCH_DOCUMENT);
			if (StringUtils.isEmpty(searchDocument)) {
				searchDocument = activeWindowPageManagerProvider.getActiveWindowPageManager().getCurrentPage().getCurrentDocument().getInitials();
			}
			mSearchResultsHolder = searchControl.getSearchResults(searchDocument, searchText);
			
			// tell user how many results were returned
			String msg;
			if (mCurrentlyDisplayedSearchResults.size()>=SearchControl.MAX_SEARCH_RESULTS) {
				msg = getString(R.string.search_showing_first, SearchControl.MAX_SEARCH_RESULTS);
			} else {
				msg = getString(R.string.search_result_count, mSearchResultsHolder.size());
			}
			Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
			isOk = true;
    	} catch (Exception e) {
    		Log.e(TAG, "Error processing search query", e);
    		isOk = false;
    		Dialogs.getInstance().showErrorMsg(R.string.error_executing_search, new Callback() {
    			@Override
    			public void okay() {
    				onBackPressed();
    			}
    		});
    	}
    	return isOk;
    }

    /** 
     * Move search results into view Adapter
     */
	private void populateViewResultsAdapter() {
		if (isScriptureResultsCurrentlyShown) {
			mCurrentlyDisplayedSearchResults = mSearchResultsHolder.getMainSearchResults();
		} else {
			mCurrentlyDisplayedSearchResults = mSearchResultsHolder.getOtherSearchResults();
		}
		
		// addAll is only supported in Api 11+
		mKeyArrayAdapter.clear();
		for (Key key : mCurrentlyDisplayedSearchResults) {
			mKeyArrayAdapter.add(key);	
		}
		
	}
    
    @Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
    	try {
    		// no need to call HistoryManager.addHistoryItem() here because PassageChangeMediator will tell HistoryManager a change is about to occur
    		
	    	verseSelected(mCurrentlyDisplayedSearchResults.get(position));
		} catch (Exception e) {
			Log.e(TAG, "Selection error", e);
			Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e);
		}
	}
    
    private void verseSelected(Key key) {
    	Log.i(TAG, "chose:"+key);
    	if (key!=null) {
    		// which doc do we show
			String targetDocInitials = getIntent().getExtras().getString(SearchControl.TARGET_DOCUMENT);
			if (StringUtils.isEmpty(targetDocInitials)) {
				targetDocInitials = activeWindowPageManagerProvider.getActiveWindowPageManager().getCurrentPage().getCurrentDocument().getInitials();
			}
			Book targetBook = getSwordDocumentFacade().getDocumentByInitials(targetDocInitials);

			activeWindowPageManagerProvider.getActiveWindowPageManager().setCurrentDocumentAndKey(targetBook, key);
    		
    		// this also calls finish() on this Activity.  If a user re-selects from HistoryList then a new Activity is created
    		returnToPreviousScreen();
    	}
    }

    /**
     * Handle scripture/Appendix toggle
     */
    private OnClickListener scriptureToggleClickListener = new OnClickListener( ) {
		
		@Override
		public void onClick(View view) {
			isScriptureResultsCurrentlyShown = !isScriptureResultsCurrentlyShown;
			populateViewResultsAdapter();
			mKeyArrayAdapter.
			notifyDataSetChanged();
			searchResultsActionBarManager.setScriptureShown(isScriptureResultsCurrentlyShown);
		}
	};

	@Inject
	void setSearchControl(SearchControl searchControl) {
		this.searchControl = searchControl;
	}

	@Inject
	void setSearchResultsActionBarManager(SearchResultsActionBarManager searchResultsActionBarManager) {
		this.searchResultsActionBarManager = searchResultsActionBarManager;
	}

	@Inject
	void setActiveWindowPageManagerProvider(ActiveWindowPageManagerProvider activeWindowPageManagerProvider) {
		this.activeWindowPageManagerProvider = activeWindowPageManagerProvider;
	}
}
