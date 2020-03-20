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

package net.bible.android.view.activity.base;

import androidx.appcompat.view.ActionMode;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

/**
 * Assists ListViews with action mode
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class ListActionModeHelper {

	private ActionMode actionMode;

	private final ListView list;
	private final int actionModeMenuResource;

	private AdapterView.OnItemClickListener previousOnItemClickListener;

	private static final String TAG = "ActionModeHelper";
	private boolean inActionMode = false;

	public ListActionModeHelper(ListView list, int actionModeMenuResource) {
		this.list = list;
		this.actionModeMenuResource = actionModeMenuResource;
	}

	public boolean isInActionMode() {
		return inActionMode;
	}

	public boolean startActionMode(final ActionModeActivity activity, int position) {
		inActionMode = true;
		list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
		list.setItemChecked(position, true);
		list.setLongClickable(false);

		actionMode = activity.startSupportActionMode(new ActionMode.Callback() {
			@Override
			public boolean onCreateActionMode(ActionMode mode, Menu menu) {
				MenuInflater inflater = mode.getMenuInflater();
				if (inflater != null) {
					inflater.inflate(actionModeMenuResource, menu);
				}
				return true;
			}

			@Override
			public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
				return false;
			}

			@Override
			public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
				List<Integer> selectedItemPositions = getSelecteditemPositions();

				actionMode.finish();
				return activity.onActionItemClicked(item, selectedItemPositions);
			}

			@Override
			public void onDestroyActionMode(ActionMode mode) {
				if (actionMode!=null) {
					inActionMode = false;
					actionMode = null;
					list.setLongClickable(true);

					// remove clicklistener added at start of action mode
					list.setOnItemClickListener(previousOnItemClickListener);
					previousOnItemClickListener = null;

					list.clearChoices();
					list.requestLayout();

					// Need to delay reset of choicemode otherwise clearChoices is optimised out.
					// see: http://stackoverflow.com/questions/9754170/listview-selection-remains-persistent-after-exiting-choice-mode
					list.post(new Runnable() {
						@Override
						public void run() {
							list.setChoiceMode(ListView.CHOICE_MODE_NONE);
						}
					});
				}
			}
		});

		// going to overwrite the previous listener, save it so it can be restored when action mode ends.
		previousOnItemClickListener = list.getOnItemClickListener();
		list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				// double check Action Mode is in operation
				if (isInActionMode()) {
					if (getSelecteditemPositions().size() == 0) {
						actionMode.finish();
					}
				}
			}
		});

		return(true);
	}

	/**
	 * Force action mode to exit e.g. due to list view change
	 */
	public void exitActionMode() {
		if (isInActionMode()) {
			actionMode.finish();
		}
	}

	private List<Integer> getSelecteditemPositions() {
		SparseBooleanArray positionStates = list.getCheckedItemPositions();
		List<Integer> selectedItemPositions = new ArrayList<>();

		for (int i=0; i<positionStates.size(); i++) {
			int position = positionStates.keyAt(i);
			if (positionStates.get(position)) {
				selectedItemPositions.add(position);
			}
		}

		return selectedItemPositions;
	}

	public interface ActionModeActivity {
		ActionMode startSupportActionMode(ActionMode.Callback callback);
		boolean onActionItemClicked(MenuItem item, List<Integer> selectedItemPositions);
		boolean isItemChecked(int position);
	}
}
