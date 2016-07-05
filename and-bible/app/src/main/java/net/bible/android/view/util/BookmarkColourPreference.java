package net.bible.android.view.util;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.preference.ListPreference;
import android.util.AttributeSet;
import android.widget.ListAdapter;
import android.widget.ListView;

/**
 * Allow selection of default Bookmark colour preference.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
public class BookmarkColourPreference extends ListPreference {

	public BookmarkColourPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public BookmarkColourPreference(Context context) {
		super(context);
	}

	@Override
	protected void showDialog(Bundle state) {
		super.showDialog(state);
		AlertDialog dialog = (AlertDialog) getDialog();
		ListView listView = dialog.getListView();
		ListAdapter adapter = listView.getAdapter();
		final BookmarkColourListPrefWrapperAdapter fontTypeAdapter = createWrapperAdapter(adapter);

		// Adjust the selection because resetting the adapter loses the selection.
		int selectedPosition = findIndexOfValue(getValue());
		listView.setAdapter(fontTypeAdapter);
		if (selectedPosition != -1) {
			listView.setItemChecked(selectedPosition, true);
			listView.setSelection(selectedPosition);
		}
	}

	protected BookmarkColourListPrefWrapperAdapter createWrapperAdapter(ListAdapter origAdapter) {
		return new BookmarkColourListPrefWrapperAdapter(getContext(), origAdapter);
	}
}
