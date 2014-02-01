package net.bible.android.view.activity.base;

import net.bible.android.activity.R;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;

/** 
 * Base class for List activities.  Copied from Android source.  
 * A copy of ListActivity from Android source which also extends ActionBarActivity and the And Bible Activity base class.
 *
 * ListActivity does not extend ActionBarActivity so when implementing ActionBar functionality I created this, which does.
 * 
 */
public class ListActivityBase extends CustomTitlebarActivityBase {
	/**
	 * This field should be made private, so it is hidden from the SDK. {@hide
	 * }
	 */
	protected ListAdapter mAdapter;
	/**
	 * This field should be made private, so it is hidden from the SDK. {@hide
	 * }
	 */
	protected ListView mList;

	private Handler mHandler = new Handler();
	private boolean mFinishedStart = false;
	
	private static final String TAG = "ActionBarListActivity";

	
	
	public ListActivityBase() {
		super();
	}

	public ListActivityBase(int optionsMenuId) {
		super(optionsMenuId);
	}

	protected void notifyDataSetChanged() {
		ListAdapter listAdapter = getListAdapter();
    	if (listAdapter!=null && listAdapter instanceof ArrayAdapter) {
    		((ArrayAdapter<?>)listAdapter).notifyDataSetChanged();
    	} else {
    		Log.w(TAG, "Could not update list Array Adapter");
    	}
	}

	private Runnable mRequestFocus = new Runnable() {
		public void run() {
			mList.focusableViewAvailable(mList);
		}
	};

	/**
	 * This method will be called when an item in the list is selected.
	 * Subclasses should override. Subclasses can call
	 * getListView().getItemAtPosition(position) if they need to access the data
	 * associated with the selected item.
	 * 
	 * @param l
	 *            The ListView where the click happened
	 * @param v
	 *            The view that was clicked within the ListView
	 * @param position
	 *            The position of the view in the list
	 * @param id
	 *            The row id of the item that was clicked
	 */
	protected void onListItemClick(ListView l, View v, int position, long id) {
	}

	/**
	 * Ensures the list view has been created before Activity restores all of
	 * the view states.
	 * 
	 * @see Activity#onRestoreInstanceState(Bundle)
	 */
	@Override
	protected void onRestoreInstanceState(Bundle state) {
		ensureList();
		super.onRestoreInstanceState(state);
	}

	/**
	 * @see Activity#onDestroy()
	 */
	@Override
	protected void onDestroy() {
		mHandler.removeCallbacks(mRequestFocus);
		super.onDestroy();
	}

	/**
	 * Updates the screen state (current list and other views) when the content
	 * changes.
	 * 
	 * @see Activity#onContentChanged()
	 */
	@Override
//	public void onContentChanged() {
	public void onSupportContentChanged() {
		super.onSupportContentChanged();
		View emptyView = findViewById(android.R.id.empty);
		mList = (ListView) findViewById(android.R.id.list);
		if (mList == null) {
			throw new RuntimeException(
					"Your content must have a ListView whose id attribute is "
							+ "'android.R.id.list'");
		}
		if (emptyView != null) {
			mList.setEmptyView(emptyView);
		}
		mList.setOnItemClickListener(mOnClickListener);
		if (mFinishedStart) {
			setListAdapter(mAdapter);
		}
		mHandler.post(mRequestFocus);
		mFinishedStart = true;
	}

	/**
	 * Provide the cursor for the list view.
	 */
	public void setListAdapter(ListAdapter adapter) {
		synchronized (this) {
			ensureList();
			mAdapter = adapter;
			mList.setAdapter(adapter);
		}
	}

	/**
	 * Set the currently selected list item to the specified position with the
	 * adapter's data
	 * 
	 * @param position
	 */
	public void setSelection(int position) {
		mList.setSelection(position);
	}

	/**
	 * Get the position of the currently selected list item.
	 */
	public int getSelectedItemPosition() {
		return mList.getSelectedItemPosition();
	}

	/**
	 * Get the cursor row ID of the currently selected list item.
	 */
	public long getSelectedItemId() {
		return mList.getSelectedItemId();
	}

	/**
	 * Get the activity's list view widget.
	 */
	public ListView getListView() {
		ensureList();
		return mList;
	}

	/**
	 * Get the ListAdapter associated with this activity's ListView.
	 */
	public ListAdapter getListAdapter() {
		return mAdapter;
	}

	private void ensureList() {
		if (mList != null) {
			return;
		}
		setContentView(R.layout.list_content_simple);
	}

	private AdapterView.OnItemClickListener mOnClickListener = new AdapterView.OnItemClickListener() {
		public void onItemClick(AdapterView<?> parent, View v, int position,
				long id) {
			onListItemClick((ListView) parent, v, position, id);
		}
	};
}
