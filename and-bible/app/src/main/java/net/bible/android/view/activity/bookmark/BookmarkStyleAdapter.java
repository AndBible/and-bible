package net.bible.android.view.activity.bookmark;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import net.bible.android.activity.R;
import net.bible.android.control.bookmark.BookmarkStyle;
import net.bible.android.view.util.widget.BookmarkStyleAdapterHelper;
import net.bible.service.common.CommonUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Manage the Spinner content for Label Bookmark-stle
 * The list includes all the BookMarkstyle enums styles plus a Default value at the top
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class BookmarkStyleAdapter extends ArrayAdapter<String> {

	private BookmarkStyleAdapterHelper bookmarkStyleAdapterHelper = new BookmarkStyleAdapterHelper();

	private Context context;

	private static final String DEFAULT_TEXT = CommonUtils.getResourceString(R.string.default_value);

	private static final String TAG = "BookmarkItemAdapter";

	public BookmarkStyleAdapter(Context context, int resource) {
		super(context, resource, getBookmarkStylesList());
		this.context = context;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		TextView view = (TextView) super.getView(position, convertView, parent);

		return styleView(position, view);
	}

	@Override
	public View getDropDownView(int position, View convertView, ViewGroup parent) {
		TextView view = (TextView) super.getDropDownView(position, convertView, parent);

		return styleView(position, view);
	}

	private View styleView(int position, TextView view) {
		// textAppearanceMedium represents 18sp
		view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);

		if (position == 0) {
			view.setText(DEFAULT_TEXT);
		} else {
			final BookmarkStyle bookmarkStyle = BookmarkStyle.values()[position - 1];
			bookmarkStyleAdapterHelper.styleView(view, bookmarkStyle, context, true, false);
		}

		return view;
	}

	private static List<String> getBookmarkStylesList() {
		List<String> styles = new ArrayList<>();
		styles.add(DEFAULT_TEXT);
		for (BookmarkStyle style : BookmarkStyle.values()) {
			styles.add(style.name());
		}
		return styles;
	}

	public int getBookmarkStyleOffset(BookmarkStyle style) {
		if (style == null) {
			return 0;
		}

		return style.ordinal() + 1;
	}

	public BookmarkStyle getBookmarkStyleForOffset(int offset) {
		if (offset == 0) {
			return null;
		}

		return BookmarkStyle.values()[offset - 1];
	}
}