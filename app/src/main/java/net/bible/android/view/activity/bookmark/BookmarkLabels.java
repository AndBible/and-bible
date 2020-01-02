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
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import net.bible.android.activity.R;
import net.bible.android.control.bookmark.BookmarkControl;
import net.bible.android.view.activity.base.Callback;
import net.bible.android.view.activity.base.IntentHelper;
import net.bible.android.view.activity.base.ListActivityBase;
import net.bible.service.db.bookmark.BookmarkDto;
import net.bible.service.db.bookmark.LabelDto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

/**
 * Choose which labels to associate with a bookmark
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class BookmarkLabels extends ListActivityBase {

	private List<BookmarkDto> bookmarks;

	private BookmarkControl bookmarkControl;

	private static final String TAG = "BookmarkLabels";
	
	private List<LabelDto> labels = new ArrayList<>();

	private LabelDialogs labelDialogs;

	// this resource returns a CheckedTextView which has setChecked(..), isChecked(), and toggle() methods
	private static final int LIST_ITEM_TYPE = android.R.layout.simple_list_item_multiple_choice; 
	
    /** Called when the activity is first created. */
    @SuppressLint("MissingSuperCall")
	@Override
    public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState, false);
		setContentView(R.layout.bookmark_labels);

		buildActivityComponent().inject(this);

		long[] bookmarkIds = getIntent().getLongArrayExtra(BookmarkControl.BOOKMARK_IDS_EXTRA);
		bookmarks = bookmarkControl.getBookmarksById(bookmarkIds);

		initialiseView();
	}

    private void initialiseView() {
    	getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

    	loadLabelList();

    	ArrayAdapter<LabelDto> listArrayAdapter = new BookmarkLabelItemAdapter(this, LIST_ITEM_TYPE, labels);
    	setListAdapter(listArrayAdapter);
    	
		initialiseCheckedLabels(bookmarks);
    }

    /** Finished selecting labels
     */
    public void onOkay(View v) {
    	Log.i(TAG, "Okay clicked");
    	// get the labels that are currently checked
    	List<LabelDto> selectedLabels = getCheckedLabels();

    	//associate labels with bookmarks that were passed in
		for (BookmarkDto bookmark : bookmarks) {
			bookmarkControl.setBookmarkLabels(bookmark, selectedLabels);
		}
       	finish();
    }

	/**
	 * New Label requested
	 */
	public void onNewLabel(View v) {
		Log.i(TAG, "New label clicked");

		LabelDto newLabel = new LabelDto();
		labelDialogs.createLabel(this, newLabel, new Callback() {
			@Override
			public void okay() {
				List<LabelDto> selectedLabels = getCheckedLabels();
				Log.d(TAG, "Num labels checked pre reload:"+selectedLabels.size());

				loadLabelList();

				setCheckedLabels(selectedLabels);
				Log.d(TAG, "Num labels checked finally:"+selectedLabels.size());			}
		});
	}

	/** load list of docs to display
	 * 
	 */
	private void loadLabelList() {
    	
    	// get long book names to show in the select list
		// must clear rather than create because the adapter is linked to this specific list
    	labels.clear();
		labels.addAll(bookmarkControl.getAssignableLabels());

    	// ensure ui is updated
		notifyDataSetChanged();
	}

	/** check labels associated with the bookmark
	 */
	private void initialiseCheckedLabels(List<BookmarkDto> bookmarks) {
		Set<LabelDto> allCheckedLabels = new HashSet<>();
    	for (BookmarkDto bookmark : bookmarks) {
			// pre-tick any labels currently associated with the bookmark
			allCheckedLabels.addAll(bookmarkControl.getBookmarkLabels(bookmark));
		}
		setCheckedLabels(allCheckedLabels);
	}

	/**
	 * get checked status of all labels
	 */
	private List<LabelDto> getCheckedLabels() {
		// get selected labels
    	ListView listView = getListView();
    	List<LabelDto> checkedLabels = new ArrayList<>();
    	for (int i=0; i<labels.size(); i++) {
    		if (listView.isItemChecked(i)) {
    			LabelDto label = labels.get(i);
    			checkedLabels.add(label);
    			Log.d(TAG, "Selected "+label.getName());
    		}
    	}
		return checkedLabels;
	}

	/**
	 * set checked status of all labels
	 */
	private void setCheckedLabels(Collection<LabelDto> labelsToCheck) {
		for (int i=0; i<labels.size(); i++) {
    		if (labelsToCheck.contains(labels.get(i))) {
    			getListView().setItemChecked(i, true);
    		} else {
    			getListView().setItemChecked(i, false);
    		}
    	}

    	// ensure ui is updated
		notifyDataSetChanged();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.bookmark_labels_actionbar_menu, menu);
		return true;
	}

	/**
	 * on Click handlers
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		boolean isHandled = false;

		switch (item.getItemId()) {
			case (R.id.manageLabels):
				isHandled = true;
				Intent intent = new Intent(this, ManageLabels.class);
				startActivityForResult(intent, IntentHelper.REFRESH_DISPLAY_ON_FINISH);
				break;
		}

		if (!isHandled) {
			isHandled = super.onOptionsItemSelected(item);
		}

		return isHandled;
	}

	@SuppressLint("MissingSuperCall")
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.d(TAG, "Restoring state after return from label editing");

		if (requestCode == IntentHelper.REFRESH_DISPLAY_ON_FINISH) {
			// find checked labels prior to refresh
			List<LabelDto> selectedLabels = getCheckedLabels();

			// reload labels with new and/or amended labels
			loadLabelList();

			// re-check labels as they were before leaving this screen
			setCheckedLabels(selectedLabels);
		}
	}

	@Inject
	void setBookmarkControl(BookmarkControl bookmarkControl) {
		this.bookmarkControl = bookmarkControl;
	}

	@Inject
	public void setLabelDialogs(LabelDialogs labelDialogs) {
		this.labelDialogs = labelDialogs;
	}
}
