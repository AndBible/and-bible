package net.bible.android.view.activity.bookmark;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import net.bible.android.activity.R;
import net.bible.android.control.bookmark.BookmarkControl;
import net.bible.android.view.activity.base.ListActionModeHelper;
import net.bible.android.view.util.widget.TwoLine2TitleListItem;
import net.bible.service.common.CommonUtils;
import net.bible.service.db.bookmark.BookmarkDto;

import java.util.List;

/**
 * nice example here: http://shri.blog.kraya.co.uk/2010/04/19/android-multi-line-select-list/
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class BookmarkItemAdapter extends ArrayAdapter<BookmarkDto> {

	private int resource;
	private final ListActionModeHelper.ActionModeActivity actionModeActivity;
	private BookmarkControl bookmarkControl;

	private static int ACTIVATED_COLOUR = CommonUtils.getResourceColor(R.color.list_item_activated);
	
	private static final String TAG = "BookmarkItemAdapter";

	public BookmarkItemAdapter(Context _context, int _resource, List<BookmarkDto> _items, ListActionModeHelper.ActionModeActivity actionModeActivity, BookmarkControl bookmarkControl) {
		super(_context, _resource, _items);
		resource = _resource;
		this.bookmarkControl = bookmarkControl;
		this.actionModeActivity = actionModeActivity;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		BookmarkDto item = getItem(position);

		// Pick up the TwoLineListItem defined in the xml file
		TwoLine2TitleListItem view;
		if (convertView == null) {
			LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			view = (TwoLine2TitleListItem) inflater.inflate(resource, parent, false);
		} else {
			view = (TwoLine2TitleListItem) convertView;
		}

		// Set value for the first text field
		if (view.getText1() != null) {
			String key = bookmarkControl.getBookmarkVerseKey(item);
			view.getText1().setText(key);
		}

		// Set value for the date text field
		if (view.getText3() != null) {
			if (item.getCreatedOn() != null) {
				String sDt = DateFormat.format("yyyy-MM-dd HH:mm", item.getCreatedOn()).toString();
				view.getText3().setText(sDt);
			}
		}

		// set value for the second text field
		if (view.getText2() != null) {
			try {
				String verseText = bookmarkControl.getBookmarkVerseText(item);
				view.getText2().setText(verseText);
			} catch (Exception e) {
				Log.e(TAG, "Error loading label verse text", e);
				view.getText2().setText("");
			}
		}

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			if (actionModeActivity.isItemChecked(position)) {
				view.setBackgroundColor(ACTIVATED_COLOUR);
			} else {
				view.setBackgroundColor(Color.TRANSPARENT);
			}
		}

		return view;
	}
}