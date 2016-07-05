package net.bible.android.view.util;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.TextView;

import net.bible.android.activity.R;
import net.bible.android.control.bookmark.BookmarkStyle;
import net.bible.android.view.util.widget.CheckedTextViewWithImages;
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

    public BookmarkColourListPrefWrapperAdapter(Context context, ListAdapter origAdapter) {
		super(origAdapter);
		this.context = context;
    }

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		TextView view = new CheckedTextViewWithImages(context);
		String text = sampleText;
		int backgroundColor = Color.WHITE;

		switch (BookmarkStyle.values()[position]) {
			case YELLOW_STAR:
				backgroundColor = BookmarkStyle.YELLOW_STAR.getBackgroundColor();
				text = "[img src=goldstar16x16/]"+text;
				break;
			case RED_HIGHLIGHT:
				backgroundColor = BookmarkStyle.RED_HIGHLIGHT.getBackgroundColor();
				break;
			case YELLOW_HIGHLIGHT:
				backgroundColor = BookmarkStyle.YELLOW_HIGHLIGHT.getBackgroundColor();
				break;
			case GREEN_HIGHLIGHT:
				backgroundColor = BookmarkStyle.GREEN_HIGHLIGHT.getBackgroundColor();
				break;
			case BLUE_HIGHLIGHT:
				backgroundColor = BookmarkStyle.BLUE_HIGHLIGHT.getBackgroundColor();
				break;
		}
		view.setBackgroundColor(backgroundColor);
		view.setText(text);

		view.setTextColor(Color.BLACK);
		view.setGravity(Gravity.CENTER);
		view.setHeight(CommonUtils.convertDipsToPx(30));
		return view;
	}
}