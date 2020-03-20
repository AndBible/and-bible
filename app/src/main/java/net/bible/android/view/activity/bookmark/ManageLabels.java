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
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import net.bible.android.activity.R;
import net.bible.android.control.bookmark.BookmarkControl;
import net.bible.android.view.activity.base.Callback;
import net.bible.android.view.activity.base.ListActivityBase;
import net.bible.service.db.bookmark.LabelDto;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

/**
 * Choose a bible or commentary to use
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class ManageLabels extends ListActivityBase {

	private List<LabelDto> labels = new ArrayList<>();

	private BookmarkControl bookmarkControl;

	private LabelDialogs labelDialogs;

	private static final String TAG = "BookmarkLabels";
	
	// this resource returns a CheckedTextView which has setChecked(..), isChecked(), and toggle() methods
	private static final int LIST_ITEM_TYPE = R.layout.manage_labels_list_item;
	
    /** Called when the activity is first created. */
    @SuppressLint("MissingSuperCall")
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState, false);
        setContentView(R.layout.manage_labels);

		super.buildActivityComponent().inject(this);

        initialiseView();
    }

    private void initialiseView() {
    	getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

    	loadLabelList();

		// prepare the document list view
    	ArrayAdapter<LabelDto> listArrayAdapter = new ManageLabelItemAdapter(this, LIST_ITEM_TYPE, labels, this);
    	setListAdapter(listArrayAdapter);
    }

	public void delete(LabelDto label) {
		// delete label from db
		bookmarkControl.deleteLabel(label);
		
		// now refetch the list of labels
		loadLabelList();
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
				loadLabelList();
			}
		});
	}

	/**
	 * New Label requested
	 */
	public void editLabel(LabelDto label) {
		Log.i(TAG, "Edit label clicked");

		labelDialogs.editLabel(this, label, new Callback() {
			@Override
			public void okay() {
				loadLabelList();
			}
		});
	}

	/** Finished editing labels
	 */
	public void onOkay(View v) {
		finish();
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

	@Inject
	void setBookmarkControl(BookmarkControl bookmarkControl) {
		this.bookmarkControl = bookmarkControl;
	}

	@Inject
	public void setLabelDialogs(LabelDialogs labelDialogs) {
		this.labelDialogs = labelDialogs;
	}
}
