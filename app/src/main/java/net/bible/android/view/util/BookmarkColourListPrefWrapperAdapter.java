package net.bible.android.view.util;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.TextView;

import net.bible.android.activity.R;
import net.bible.android.control.bookmark.BookmarkStyle;
import net.bible.android.view.util.widget.BookmarkStyleAdapterHelper;
import net.bible.android.view.util.widget.ListPrefWrapperAdapter;
import net.bible.service.common.CommonUtils;

/**
 * Set each list view item to represent background colour od icon of the relevant bookmark style.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
class BookmarkColourListPrefWrapperAdapter extends ListPrefWrapperAdapter {

	private Context context;

	private String sampleText = CommonUtils.getResourceString(R.string.prefs_text_size_sample_text);

	private BookmarkStyleAdapterHelper bookmarkStyleAdapterHelper;

    public BookmarkColourListPrefWrapperAdapter(Context context, ListAdapter origAdapter) {
		super(origAdapter);
		this.context = context;
		this.bookmarkStyleAdapterHelper = new BookmarkStyleAdapterHelper();
    }

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		TextView view = (TextView)super.getView(position, convertView, parent);
		final BookmarkStyle bookmarkStyle = BookmarkStyle.values()[position];

		bookmarkStyleAdapterHelper.styleView(view, bookmarkStyle, context, true, true);
		return view;
	}
}