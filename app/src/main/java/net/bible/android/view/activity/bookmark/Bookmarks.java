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

package net.bible.android.view.activity.bookmark;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;

import net.bible.android.activity.R;
import net.bible.android.control.bookmark.BookmarkControl;
import net.bible.android.control.speak.SpeakControl;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.android.view.activity.base.ListActionModeHelper;
import net.bible.android.view.activity.base.ListActivityBase;
import net.bible.service.common.CommonUtils;
import net.bible.service.db.bookmark.BookmarkDto;
import net.bible.service.db.bookmark.LabelDto;

import org.crosswire.jsword.book.Book;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

/**
 * Choose Document (Book) to download
 * 
 * NotificationManager with ProgressBar example here:
 * http://united-coders.com/nico-heid/show-progressbar-in-notification-area-like-google-does-when-downloading-from-android
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class Bookmarks extends ListActivityBase implements ListActionModeHelper.ActionModeActivity {
	private BookmarkControl bookmarkControl;
	private SpeakControl speakControl;
	
	// language spinner
	private Spinner labelSpinner;
	private List<LabelDto> labelList = new ArrayList<>();
	private int selectedLabelNo = 0;
	private ArrayAdapter<LabelDto> labelArrayAdapter; 
	
	// the document list
	private final List<BookmarkDto> bookmarkList = new ArrayList<>();

	private ListActionModeHelper listActionModeHelper;

	private static final int LIST_ITEM_TYPE = R.layout.bookmark_list_item;
	private static final String TAG = "Bookmarks";

    /** Called when the activity is first created. */
    @SuppressLint("MissingSuperCall")
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState, true);
        setContentView(R.layout.bookmarks);

		CommonUtils.INSTANCE.getSharedPreferences().edit().putLong("bookmarks-last-used", System.currentTimeMillis()).apply();
		buildActivityComponent().inject(this);

        // if coming Back using History then the LabelNo will be in the intent allowing the correct label to be pre-selected
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			if (extras.containsKey(BookmarkControl.LABEL_NO_EXTRA)) {
				int labelNo = extras.getInt(BookmarkControl.LABEL_NO_EXTRA);
				if (labelNo>=0) {
					selectedLabelNo = labelNo;
				}
			}
		}
        
       	initialiseView();
    }

    private void initialiseView() {
		listActionModeHelper =  new ListActionModeHelper(getListView(), R.menu.bookmark_context_menu);

		getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
			return listActionModeHelper.startActionMode(Bookmarks.this, position);
			}
		});

    	//prepare the Label spinner
    	loadLabelList();
    	labelArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labelList);
    	labelArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    	labelSpinner = (Spinner)findViewById(R.id.labelSpinner);
    	labelSpinner.setAdapter(labelArrayAdapter);

    	// check for pre-selected label e.g. when returning via History using Back button 
    	if (selectedLabelNo>=0 && selectedLabelNo<labelList.size()) {
    		labelSpinner.setSelection(selectedLabelNo);
    	}

    	labelSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
		    	selectedLabelNo = position;
		    	Bookmarks.this.loadBookmarkList();
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
			}
		});
    	
    	loadBookmarkList();
    	
    	// prepare the document list view
		ArrayAdapter<BookmarkDto> bookmarkArrayAdapter = new BookmarkItemAdapter(this, LIST_ITEM_TYPE, bookmarkList, this, bookmarkControl);
    	setListAdapter(bookmarkArrayAdapter);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
    	try {
			// check to see if Action Mode is in operation
			if (!listActionModeHelper.isInActionMode()) {
				bookmarkSelected(bookmarkList.get(position));
			}
    	} catch (Exception e) {
    		Log.e(TAG, "document selection error", e);
    		Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e);
    	}
	}

    @SuppressLint("MissingSuperCall")
	@Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.d(TAG, "Restoring state after return from label editing");
    	// the bookmarkLabels activity may have added/deleted labels or changed the bookmarks with the current label
    	LabelDto prevLabel = labelList.get(selectedLabelNo);
    	
    	// reload labels
    	loadLabelList();
    	
    	int prevLabelPos = labelList.indexOf(prevLabel);
    	if (prevLabelPos>=0) {
    		selectedLabelNo = prevLabelPos;
    	} else {
    		// this should be 'All'
    		selectedLabelNo = 0;
    	}
    	labelSpinner.setSelection(selectedLabelNo);
    	
    	// the label may have been renamed so cause the list to update it's text
    	labelArrayAdapter.notifyDataSetChanged();
    	
    	loadBookmarkList();
    }

    /** allow activity to enhance intent to correctly restore state */
	public Intent getIntentForHistoryList() {
		Log.d(TAG, "Saving label no in History Intent");
		Intent intent = getIntent();
		
		intent.putExtra(BookmarkControl.LABEL_NO_EXTRA, selectedLabelNo);

		return intent;
	}

    
    private void assignLabels(List<BookmarkDto> bookmarks) {
		long[] bookmarkIds = new long[bookmarks.size()];
		for (int i=0; i<bookmarks.size(); i++) {
			bookmarkIds[i] = bookmarks.get(i).getId();
		}

		Intent intent = new Intent(this, BookmarkLabels.class);
		intent.putExtra(BookmarkControl.BOOKMARK_IDS_EXTRA, bookmarkIds);
		startActivityForResult(intent, 1);
	}

	private void delete(List<BookmarkDto> bookmarks) {
		for (BookmarkDto bookmark : bookmarks) {
			bookmarkControl.deleteBookmark(bookmark, false);
		}
		loadBookmarkList();
	}
    
	private void loadLabelList() {
    	labelList.clear();
    	labelList.addAll(bookmarkControl.getAllLabels());
	}

	/** a spinner has changed so refilter the doc list
     */
    private void loadBookmarkList() {
    	try {
    		if (selectedLabelNo>-1 && selectedLabelNo<labelList.size()) {
   	        	Log.i(TAG, "filtering bookmarks");
   	        	LabelDto selectedLabel = labelList.get(selectedLabelNo);
   	        	bookmarkList.clear();
   	        	bookmarkList.addAll( bookmarkControl.getBookmarksWithLabel(selectedLabel) );
   	        	
        		notifyDataSetChanged();

				// if in action mode then must exit because the data has changed, invalidating selections
				listActionModeHelper.exitActionMode();
    		}
    	} catch (Exception e) {
    		Log.e(TAG, "Error initialising view", e);
    		Toast.makeText(this, getString(R.string.error)+" "+e.getMessage(), Toast.LENGTH_SHORT).show();
    	}
    }

    private void bookmarkSelected(BookmarkDto bookmark) {
    	Log.d(TAG, "Bookmark selected:"+bookmark.getVerseRange());
    	try {
			if(bookmarkControl.isSpeakBookmark(bookmark)) {
				speakControl.speakFromBookmark(bookmark);
			}

			Intent resultIntent= new Intent(this, Bookmarks.class);
			resultIntent.putExtra("verse", bookmark.getVerseRange().getStart().getOsisID());
			setResult(Activity.RESULT_OK, resultIntent);

			finish();
		} catch (Exception e) {
    		Log.e(TAG, "Error on bookmarkSelected", e);
    		Toast.makeText(this, R.string.error_occurred, Toast.LENGTH_SHORT).show();
    	}
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.bookmark_actionbar_menu, menu);
        return true;
    }

	/** 
     * on Click handlers
     */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		boolean isHandled = false;

		switch (item.getItemId()) {
			// Change sort order
			case (R.id.sortByToggle):
				isHandled = true;
				try {
					bookmarkControl.changeBookmarkSortOrder();
					String sortDesc = bookmarkControl.getBookmarkSortOrderDescription();
					Toast.makeText(this, sortDesc, Toast.LENGTH_SHORT).show();
					loadBookmarkList();
				} catch (Exception e) {
					Log.e(TAG, "Error sorting bookmarks", e);
					Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e);
				}

				break;
			case (R.id.manageLabels):
				isHandled = true;
				Intent intent = new Intent(this, ManageLabels.class);
				startActivityForResult(intent, 1);
				break;
		}

		if (!isHandled) {
			isHandled = super.onOptionsItemSelected(item);
		}

		return isHandled;
	}

	@Override
	public boolean onActionItemClicked(MenuItem item, List<Integer> selectedItemPositions) {
		List<BookmarkDto> selectedBookmarks = getSelectedBookmarks(selectedItemPositions);
		switch (item.getItemId()) {
			case (R.id.assign_labels):
				assignLabels(selectedBookmarks);
				break;
			case (R.id.delete):
				delete(selectedBookmarks);
				break;
		}
		return true;
	}

	@Override
	public boolean isItemChecked(int position) {
		return getListView().isItemChecked(position);
	}

	private List<BookmarkDto> getSelectedBookmarks(List<Integer> selectedItemPositions) {
		List<BookmarkDto> selectedBookmarks = new ArrayList<>();

		for (int position : selectedItemPositions) {
			selectedBookmarks.add(bookmarkList.get(position));
		}

		return selectedBookmarks;
	}

	@Inject
	void setBookmarkControl(BookmarkControl bookmarkControl) {
		this.bookmarkControl = bookmarkControl;
	}

	@Inject
	void setSpeakControl(SpeakControl speakControl) {
		this.speakControl = speakControl;
	}
}
